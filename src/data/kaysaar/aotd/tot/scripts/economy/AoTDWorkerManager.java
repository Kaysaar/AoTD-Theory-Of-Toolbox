package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AoTDWorkerManager {
    private AoTDWorkerManager() {}

    private static final Logger log = Global.getLogger(AoTDWorkerManager.class);

    private static final int THREAD_COUNT = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));

    private static final Object LOCK = new Object();

    /*
     * Global lock for Starsector campaign/economy API access from AoTD workers.
     *
     * Save barrier protects save/load boundaries, but it does not make Starsector's
     * economy collections thread-safe. Anything that touches MarketAPI, Economy,
     * CommodityOnMarket, getDemandPrice(), getSupplyPrice(), etc. should be run
     * through runEconomyLocked(...) when called from a worker thread.
     */
    private static final Object ECONOMY_ACCESS_LOCK = new Object();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT, runnable -> {
        final Thread thread = new Thread(runnable, "AoTD Worker");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<Future<?>> TASKS = Collections.synchronizedSet(new HashSet<>());

    private static boolean saveBarrierActive = false;
    private static int runningWorkers = 0;

    public static Future<?> submit(String name, Runnable task) {
        final Future<?> future = EXECUTOR.submit(() -> {
            enterWorker();

            try {
                task.run();
            } catch (Throwable t) {
                log.error("AoTD worker crashed: " + name, t);
            } finally {
                exitWorker();
            }
        });

        TASKS.add(future);
        cleanupFinishedTasks();
        return future;
    }

    private static void enterWorker() {
        boolean interrupted = false;
        try {
            synchronized (LOCK) {
                while (saveBarrierActive) {
                    try {
                        LOCK.wait(250L);
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
                runningWorkers++;
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void exitWorker() {
        synchronized (LOCK) {
            runningWorkers--;
            if (runningWorkers < 0) {
                runningWorkers = 0;
            }

            LOCK.notifyAll();
        }
    }

    public static void checkpoint() {
        boolean interrupted = false;
        try {
            synchronized (LOCK) {
                if (saveBarrierActive) {
                    runningWorkers--;
                    if (runningWorkers < 0) runningWorkers = 0;
                    LOCK.notifyAll();

                    try {
                        while (saveBarrierActive) {
                            try {
                                LOCK.wait();
                            } catch (InterruptedException ex) {
                                interrupted = true;
                            }
                        }
                    } finally {
                        runningWorkers++;
                    }
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public static void beginSaveAndWait() {
        boolean interrupted = false;
        try {
            synchronized (LOCK) {
                saveBarrierActive = true;
                LOCK.notifyAll();

                while (runningWorkers > 0) {
                    try {
                        LOCK.wait(250L);
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
            }

            cleanupFinishedTasks();
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public static void endSave() {
        synchronized (LOCK) {
            saveBarrierActive = false;
            LOCK.notifyAll();
        }

        cleanupFinishedTasks();
    }

    public static void runEconomyLocked(Runnable task) {
        synchronized (ECONOMY_ACCESS_LOCK) {
            task.run();
        }
    }

    public static boolean isSaveBarrierActive() {
        synchronized (LOCK) {
            return saveBarrierActive;
        }
    }

    public static int getRunningWorkers() {
        synchronized (LOCK) {
            return runningWorkers;
        }
    }

    public static void shutdownNow() {
        synchronized (LOCK) {
            saveBarrierActive = false;
            LOCK.notifyAll();
        }

        EXECUTOR.shutdownNow();
        TASKS.clear();
    }

    private static void cleanupFinishedTasks() {
        synchronized (TASKS) {
            TASKS.removeIf(Future::isDone);
        }
    }
}
