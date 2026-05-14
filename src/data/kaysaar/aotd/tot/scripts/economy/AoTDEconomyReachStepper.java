package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.ContractEconomy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.*;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.*;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.scripts.trade.tasks.AoTDExternalTradeSolver;
import data.kaysaar.aotd.tot.scripts.trade.tasks.AoTDFactionInternalTradeTask;
import data.kaysaar.aotd.tot.ui.income.AoTDMonthlyTooltipCreator;

import java.util.ArrayList;
import java.util.List;

public class AoTDEconomyReachStepper extends ReachEconomyStepper {
    protected List<MultiFrameTask> tasks = null;
    private ReachEconomy econ;
    private State state;
    private float elapsed;
    private float untilNext;
    private int iterLeft;
    private int prevMonth;

    public AoTDEconomyReachStepper(ReachEconomy reachEconomy) {
        super(reachEconomy);
        this.state = ReachEconomyStepper.State.WAITING;
        this.elapsed = 1000.0F;
        this.untilNext = 3.0F;
        this.iterLeft = Economy.NUM_ITER_PER_MONTH;
        this.prevMonth = -1;
        this.econ = reachEconomy;
    }

    public void doEconomyTick(){
        this.doEndOfStepStuff(-1);
    }

    @Override
    protected void doEndOfMonthStuff() {


        MonthlyReport report = SharedData.getData().getCurrentReport();
        if(report!=null){
            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            marketsNode.name = "Colonies";
            marketsNode.custom = MonthlyReport.OUTPOSTS;
            marketsNode.tooltipCreator = report.getMonthlyReportTooltip();

            for (MarketAPI market : Misc.getPlayerMarkets(true)) {
                MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
                MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
                for (Industry industry : market.getIndustries()) {
                    MonthlyReport.FDNode iNode = report.getNode(indNode, industry.getId());
                    iNode.tooltipCreator = new AoTDMonthlyTooltipCreator();
                }
                MonthlyReport.FDNode exportNode = report.getNode(mNode, "exports");
                for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
                    MonthlyReport.FDNode eNode = report.getNode(exportNode, com.getId());
                    eNode.income = AoTDTradeManager.getExportIncome(com);
                    eNode.tooltipCreator = new AoTDMonthlyTooltipCreator();
                }
            }
        }
        AoTDTradeManager.endOfMonth = false;
        super.doEndOfMonthStuff();
    }

    @Override
    public ReachEconomy getEconomy() {
        return this.econ;
    }

    @Override
    public void setEcon(ReachEconomy var1) {
        this.econ = var1;
    }
    public void performBeforeMonthEnds(int prevMonth) {
        SectorSurplusConsumptionStats.getInstance().clear();

        for (AoTDFactionTradeData tradeData : AoTDTradeManager.getInstance().getAllFactionTradeData().values()) {
            tradeData.doEndOfMonthStuffForHistory(prevMonth);
        }

        if (!AoTDEconomy.runningPrePlayerEconomy) {

            // NEW: contracts phase (after internal, before external)
            AoTDTradeContractManager.getInstance().runMonthlyContracts();

            // Build external index from remainingNet (post-internal, post-contract)
            AoTDSectorExternalIndex idx = new AoTDSectorExternalIndex();
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDFactionTradeData f = AoTDTradeManager.getInstance().getFactionTradeData(market.getFactionId());
                AoTDMarketData md = f.getTradeData().get(market.getId());
                if (md == null) continue;

                md.resetExternalResults(); // excess-exported tracking for this month
                if(!market.hasSpaceport()||market.getAccessibilityMod().computeEffective(0f)<=0f)continue;
                idx.addMarket(market, md);
            }

            new AoTDExternalTradeSolver().runMonthEndExternalTrade(idx);

            // 5) Apply leftover deficit/excess to AoTDExcDefData
            float durDays = 31;

            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDFactionTradeData f = AoTDTradeManager.getInstance().getFactionTradeData(market.getFactionId());

                AoTDMarketData md = f.getTradeData().get(market.getId());
                if (md == null) continue;
                AoTDIndustryData.getInstance(market).applyEndOfMonthChange(market);
                for (CommodityOnMarketAPI c : market.getAllCommodities()) {
                    if (!(c instanceof AoTDCommodityOnMarket com)) continue;
                    com.getExcDefData().clearExternalTrade();
                    String commodityId = com.getId();
                    int r = md.getRemainingNet(commodityId);

                    int deficit = Math.max(0, -r);
                    int excess = Math.max(0, r);

                    com.getExcDefData().recordDemandForThisMonth(com);
                    com.getExcDefData().applyExternalTrade(deficit, excess, durDays,com);
                }

            }
        }
        else{
            for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDIndustryData.getInstance(marketAPI).applyEndOfMonthChange(marketAPI);
            }
        }
    }

    public void nextFrame(float var1) {
        this.elapsed += var1;
        ContractEconomy.DEBUG = false;
        if (this.state == ReachEconomyStepper.State.WAITING) {
            int var2 = Global.getSector().getClock().getMonth();
            if (var2 != this.prevMonth) {
                int prevMonth = this.prevMonth;
                this.prevMonth = var2;
                this.iterLeft = Economy.NUM_ITER_PER_MONTH;
                float daysInThisMonth = this.getNumDaysInCurrMonth() - Global.getSector().getClock().getDay();
                this.untilNext = daysInThisMonth / ((float) Economy.NUM_ITER_PER_MONTH + 0.0F);
                this.elapsed = this.untilNext / 2.0F;
                this.performBeforeMonthEnds(prevMonth);
                AoTDTradeManager.endOfMonth = true;
                this.doEndOfStepStuff(Economy.NUM_ITER_PER_MONTH - 1);
                this.doEndOfMonthStuff();
            }
        }

        if (this.state == ReachEconomyStepper.State.WAITING) {

            if (this.elapsed >= this.untilNext && this.iterLeft > 0) {
                --this.iterLeft;
                this.state = ReachEconomyStepper.State.DOING_TASKS;
                this.tasks = null;
                this.elapsed = 0.0F;
            }

        }

        if (this.state == ReachEconomyStepper.State.DOING_TASKS) {
            if (this.tasks == null) {
                this.createTasks();
            }

            if (this.isDone()) {
                return;
            }
            MultiFrameTask var4 = (MultiFrameTask) this.tasks.get(0);
            var4.advance(var1);
            if (var4.isDone()) {
                this.tasks.remove(0);
            }

            if (this.isDone()) {
                if (this.iterLeft > 0) {
                    this.doEndOfStepStuff(Economy.NUM_ITER_PER_MONTH - this.iterLeft - 1);
                }

                this.state = ReachEconomyStepper.State.WAITING;
            }

        }

    }

    private void createTasks() {
        this.tasks = new ArrayList();
        boolean var1 = this.iterLeft <= 0;
        MainWorkTask.EconWorkParams var2 = new MainWorkTask.EconWorkParams();
        var2.withIncomeAndUpkeep = true;
        var2.withStockpileUpdate = var1;
        this.tasks.add(new AoTdMainWorkTask2(this.econ.getMarkets(), this.econ, var2));
        this.tasks.add(new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy()));
        this.tasks.add(new ImmigrationTask(this.econ.getMarkets(), this.econ, false));
        this.tasks.add(new AoTDFactionInternalTradeTask((Economy) Global.getSector().getEconomy()));
        this.tasks.add(new AoTDFinishEconomyUpdateTask((Economy) Global.getSector().getEconomy()));
    }

    @Override
    public boolean isDone() {
        return this.tasks == null || this.tasks.isEmpty();
    }

    public float getNumDaysInCurrMonth() {
        return Global.getSector().getClock().getCal().getActualMaximum(5);
    }
}
