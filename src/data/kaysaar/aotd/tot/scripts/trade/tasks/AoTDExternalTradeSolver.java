
package data.kaysaar.aotd.tot.scripts.trade.tasks;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.AoTDSectorExternalIndex;
import data.kaysaar.aotd.tot.scripts.trade.ScavengerGuildUtils;
import data.kaysaar.aotd.tot.scripts.trade.SectorSurplusConsumptionStats;
import data.kaysaar.aotd.tot.scripts.trade.SurplusConsumptionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AoTDExternalTradeSolver {


    private void applyScavengerGuildIfNeeded(
            AoTDSectorExternalIndex idx,
            String commodityId,
            ArrayList<AoTDSectorExternalIndex.Offer> exporters,
            ArrayList<AoTDSectorExternalIndex.Offer> importers
    ) {
        if (importers == null || importers.isEmpty()) return;

        int extra = ScavengerGuildUtils.getCoveredAmountFromSector(commodityId);
        if (extra <= 0) return;

        if (exporters == null) exporters = new ArrayList<>();

        float maxW = 0f;
        for (AoTDSectorExternalIndex.Offer e : exporters) maxW = Math.max(maxW, e.weight);
        for (AoTDSectorExternalIndex.Offer i : importers) maxW = Math.max(maxW, i.weight);
        if (maxW <= 0f) maxW = 100f;

        float scavWeight = maxW * 1.25f + 1f;

        AoTDSectorExternalIndex.Offer scav = idx.createScavengerOffer(commodityId, extra, scavWeight);
        exporters.add(scav);

        idx.exportersByCommodity.put(commodityId, exporters);
    }

    /**
     * Sector Surplus Cap (AFTER matching):
     * For covered commodities, reduce leftover (unmatched) exporter supply so that:
     *
     *   sectorSupply <= sectorDemand * (1 + cap)
     *
     * This is done ONLY on remaining exporter offer amounts after matching,
     * so it cannot create shortages when the sector can satisfy them.
     *
     * It records:
     *  - per faction totals in SectorSurplusConsumptionStats
     *  - per market amount removed in AoTDMarketData.externalExcessExported
     */
    private void applySurplusCapAfterMatching(
            String commodityId,
            ArrayList<AoTDSectorExternalIndex.Offer> exporters
    ) {
        if (!SurplusConsumptionUtils.doesCoverCommodity(commodityId)) return;
        if (exporters == null || exporters.isEmpty()) return;

        // leftover supply after matching = sum of remaining exporter offer amounts
        long leftoverSupply = 0;
        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (e == null) continue;
            if (e.isScavenger) continue; // don't cap synthetic supply
            if (e.amount > 0) leftoverSupply += e.amount;
        }
        if (leftoverSupply <= 0) return;

        int sectorSupply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int sectorDemand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        if (sectorSupply <= 0 || sectorDemand < 0) return;

        float cap = SurplusConsumptionUtils.getCapPercent(commodityId); // default 0.10f

        // allowed TOTAL supply = demand * (1 + cap)
        // => allowed net surplus = allowedSupply - demand = demand*cap
        long allowedNetSurplus = (long) Math.ceil(sectorDemand * (double) cap);

        // if sector is not actually beyond cap globally, do nothing
        long actualNetSurplus = (long) sectorSupply - (long) sectorDemand;
        if (actualNetSurplus <= allowedNetSurplus) return;

        // BUT we only can reduce what is leftover after matching (unmatched exports).
        // amount to remove = min(leftoverSupply, actualNetSurplus - allowedNetSurplus)
        long toRemove = Math.min(leftoverSupply, actualNetSurplus - allowedNetSurplus);
        if (toRemove <= 0) return;

        // deterministic: remove from biggest/most accessible exporters first
        exporters.sort((a, b) -> Float.compare(b.weight, a.weight));

        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (toRemove <= 0) break;
            if (e == null) continue;
            if (e.isScavenger) continue;
            if (e.amount <= 0) continue;

            int removed = (int) Math.min((long) e.amount, toRemove);
            if (removed <= 0) continue;

            // reduce leftover export offer
            e.amount -= removed;

            // reduce remainingNet as well (less export surplus remains)
            e.data.remainingNet.merge(commodityId, -removed, Integer::sum);
            if (e.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                e.data.remainingNet.remove(commodityId);
            }

            // record per-faction stats
            if (e.market != null) {
                SectorSurplusConsumptionStats.getInstance()
                        .record(commodityId, e.market.getFactionId(), removed);
            }

            // record per-market "excess exported" removed by cap
            e.data.externalExcessExported.merge(commodityId, removed, Integer::sum);

            toRemove -= removed;
        }
    }

    /** Deliver unused harvest to satisfied importer markets so it becomes real excess.
     * The month-end stepper already turns positive remainingNet into an excess package.
     */
    private void distributeHarvestOverflow(String commodityId,
                                           ArrayList<AoTDSectorExternalIndex.Offer> exporters,
                                           ArrayList<AoTDSectorExternalIndex.Offer> importers) {
        if (!ScavengerGuildUtils.isOverflowAllowed()) return;
        boolean hasUnusedHarvest = false;
        for (AoTDSectorExternalIndex.Offer exporter : exporters) {
            if (exporter != null && exporter.isScavenger && exporter.amount > 0) {
                hasUnusedHarvest = true;
                break;
            }
        }
        if (!hasUnusedHarvest) return;
        ArrayList<AoTDSectorExternalIndex.Offer> recipients = new ArrayList<>();
        double totalWeight = 0d;
        for (AoTDSectorExternalIndex.Offer importer : importers) {
            if (importer == null || !importer.hasMarket() || importer.amount > 0) continue;
            int net = importer.data.remainingNet.getOrDefault(commodityId, 0);
            if (net < 0 || net == Integer.MAX_VALUE) continue;
            recipients.add(importer);
            totalWeight += overflowWeight(importer);
        }
        if (recipients.isEmpty()) return;

        for (AoTDSectorExternalIndex.Offer exporter : exporters) {
            if (exporter == null || !exporter.isScavenger || exporter.amount <= 0) continue;
            double weightLeft = totalWeight;
            for (int i = 0; i < recipients.size() && exporter.amount > 0; i++) {
                AoTDSectorExternalIndex.Offer recipient = recipients.get(i);
                double weight = overflowWeight(recipient);
                int oldNet = recipient.data.remainingNet.getOrDefault(commodityId, 0);
                long capacity = (long) Integer.MAX_VALUE - oldNet;
                long share = i == recipients.size() - 1 ? exporter.amount
                        : (long) Math.floor(exporter.amount * (weight / weightLeft));
                int moved = (int) Math.min(capacity, Math.min(exporter.amount, Math.max(0L, share)));
                if (moved > 0) {
                    recipient.data.remainingNet.put(commodityId, oldNet + moved);
                    exporter.amount -= moved;
                    // Transfer existing synthetic units; don't mint extra units or report a sale.
                    if (exporter.amount == 0) exporter.data.remainingNet.remove(commodityId);
                    else exporter.data.remainingNet.put(commodityId, exporter.amount);
                }
                weightLeft -= weight;
            }
        }
    }

    private static double overflowWeight(AoTDSectorExternalIndex.Offer offer) {
        return Float.isFinite(offer.weight) && offer.weight > 0f ? offer.weight : 1d;
    }

    /**
     * Runs month-end matching.
     */
    public void runMonthEndExternalTrade(AoTDSectorExternalIndex idx) {
        Set<String> commodities = new HashSet<>();
        commodities.addAll(idx.exportersByCommodity.keySet());
        commodities.addAll(idx.importersByCommodity.keySet());

        for (String commodityId : commodities) {
            runForCommodity(idx, commodityId);
        }
    }

    private void runForCommodity(AoTDSectorExternalIndex idx, String commodityId) {
        ArrayList<AoTDSectorExternalIndex.Offer> exporters = idx.exportersByCommodity.get(commodityId);
        ArrayList<AoTDSectorExternalIndex.Offer> importers = idx.importersByCommodity.get(commodityId);

        // If nobody needs it, nothing to match.
        // (Keep this early return: scavengers and matching only matter if there is demand.)
        if (importers == null || importers.isEmpty()) return;

        // allow scavengers even if exporters == null/empty
        applyScavengerGuildIfNeeded(idx, commodityId, exporters, importers);

        exporters = idx.exportersByCommodity.get(commodityId);
        if (exporters == null || exporters.isEmpty()) return;

        WeightedRandomPicker<AoTDSectorExternalIndex.Offer> expPicker = new WeightedRandomPicker<>();
        WeightedRandomPicker<AoTDSectorExternalIndex.Offer> impPicker = new WeightedRandomPicker<>();

        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (e != null && e.amount > 0) expPicker.add(e, e.weight);
        }
        for (AoTDSectorExternalIndex.Offer i : importers) {
            if (i != null && i.amount > 0) impPicker.add(i, i.weight);
        }

        int guard = 0;
        while (!expPicker.isEmpty() && !impPicker.isEmpty()) {
            if (++guard > 200000) break;

            AoTDSectorExternalIndex.Offer imp = impPicker.pick();
            AoTDSectorExternalIndex.Offer exp = expPicker.pick();
            if (imp == null || exp == null) break;

            if (imp.amount <= 0) { impPicker.remove(imp); continue; }
            if (exp.amount <= 0) { expPicker.remove(exp); continue; }

            int moved = Math.min(exp.amount, imp.amount);

            // exporter moves toward 0
            exp.data.remainingNet.merge(commodityId, -moved, Integer::sum);
     ;
            if (exp.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                exp.data.remainingNet.remove(commodityId);
            }

            // importer moves toward 0
            imp.data.remainingNet.merge(commodityId, moved, Integer::sum);
            if (imp.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                imp.data.remainingNet.remove(commodityId);
            }

            exp.amount -= moved;
            exp.data.addSoldOutside(commodityId, moved);
            imp.amount -= moved;

            if (exp.amount <= 0) expPicker.remove(exp);
            if (imp.amount <= 0) impPicker.remove(imp);
        }

        distributeHarvestOverflow(commodityId, exporters, importers);

    }
}
