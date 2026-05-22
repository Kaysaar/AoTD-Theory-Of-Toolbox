package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Cooperative worker save barrier.
 *
 * Use AoTDWorkerManager.submit() to start AoTD workers.
 * Workers should call checkpoint() inside long loops.
 *
 * beforeGameSave() should call beginSaveAndWait().
 * afterGameSave() should call endSave().
 */
public final class AoTDWorkerManager {

    private static final Logger log = Global.getLogger(AoTDWorkerManager.class);

    private static final int THREAD_COUNT =
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));

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
        Thread thread = new Thread(runnable, "AoTD Worker");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<Future<?>> TASKS = Collections.synchronizedSet(new HashSet<>());

    private static boolean saveBarrierActive = false;
    private static int runningWorkers = 0;

    private AoTDWorkerManager() {
    }

    public static Future<?> submit(String name, Runnable task) {
        Future<?> future = EXECUTOR.submit(() -> {
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
        synchronized (LOCK) {
            while (saveBarrierActive) {
                waitQuietly();
            }

            runningWorkers++;
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
        synchronized (LOCK) {
            if (!saveBarrierActive) {
                return;
            }

            runningWorkers--;
            if (runningWorkers < 0) {
                runningWorkers = 0;
            }

            LOCK.notifyAll();

            try {
                while (saveBarrierActive) {
                    LOCK.wait();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                runningWorkers++;
            }
        }
    }

    public static void beginSaveAndWait() {
        synchronized (LOCK) {
            saveBarrierActive = true;
            LOCK.notifyAll();

            while (runningWorkers > 0) {
                waitQuietly();
            }
        }

        cleanupFinishedTasks();
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

    private static void waitQuietly() {
        try {
            LOCK.wait(250L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
