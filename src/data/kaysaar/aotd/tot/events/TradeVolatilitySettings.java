package data.kaysaar.aotd.tot.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class TradeVolatilitySettings {
    public static boolean enabled = true;

    public enum PlayerTargeting { BOTH, NPC_ONLY, PLAYER_ONLY }

    // Missing entries allow both. Global exclusions still take precedence.
    public static final LinkedHashMap<String, PlayerTargeting> playerTargeting = new LinkedHashMap<>();
    static {
        playerTargeting.put(AoTDContrabandDemandEvent.BUYING_FRENZY, PlayerTargeting.NPC_ONLY);
        playerTargeting.put(AoTDContrabandDemandEvent.CONSUMPTION_BOOM, PlayerTargeting.NPC_ONLY);
        playerTargeting.put(AoTDContrabandDemandEvent.STOCKPILING, PlayerTargeting.NPC_ONLY);
        playerTargeting.put(AoTDTradeDemandEvent.FACTION_EVENT_ID, PlayerTargeting.NPC_ONLY);
        playerTargeting.put(AoTDTradeDemandEvent.MARKET_EVENT_ID, PlayerTargeting.NPC_ONLY);
    }

    public static boolean canTargetPlayer(String eventId, MarketAPI market) {
        boolean player = market.isPlayerOwned()
                || Global.getSector().getPlayerFaction().getId().equals(market.getFactionId());
        PlayerTargeting policy = playerTargeting.getOrDefault(eventId, PlayerTargeting.BOTH);
        return policy == PlayerTargeting.BOTH || (policy == PlayerTargeting.PLAYER_ONLY) == player;
    }

    // Master allowlist for all deliberate shortage events, including contraband.
    // Per-family lists below can narrow this list, never expand it.
    public static final Set<String> shortageCommodities = new LinkedHashSet<>(Arrays.asList(
            "food", "drugs", "luxury_goods", "domestic_goods"
    ));

    public static boolean colonyShocksEnabled = true;
    public static final Set<String> colonyShockCommodities = new LinkedHashSet<>(Arrays.asList(
            "food", "drugs", "luxury_goods", "domestic_goods"
    ));
    public static int[] colonyShockPercent = {25, 30, 40, 50};
    // Two independently budgeted batches, one and two days after the actual allocation.
    public static int colonyShockAttemptsPerDay = 3;
    public static float colonyShockChance = 0.80f;


    public static final Set<String> excludedCommodities = new LinkedHashSet<>(Arrays.asList(
            "crew", "marines"
    ));
    public static final Set<String> excludedFactions = new LinkedHashSet<>();
    public static String excludedCommodityTag = "aotd_no_trade_volatility";

    public static int[] sectorDemandPercent = {15, 20, 25, 30, 35};
    public static int[] factionDemandPercent = {20, 25, 30, 35, 40, 45, 50, 55};
    public static int[] marketDemandPercent = {20, 25, 30, 35, 40, 45, 50, 55};
    public static int minimumDurationMonths = 3;
    public static int maximumDurationMonths = 48;
    // Kept for old intel records whose effects used native day-based timers.
    public static float daysPerMonth = 30f;
    public static float deficitSelectionWeight = 4f;

    public static boolean contrabandEventsEnabled = true;
    public static final Set<String> contrabandCommodities = new LinkedHashSet<>(Arrays.asList("drugs"));
    public static int[] contrabandReservePercent = {15, 20, 25, 30};
    public static int contrabandMinimumDurationMonths = 3;
    public static int contrabandMaximumDurationMonths = 12;
    public static float contrabandSectorCooldownDays = 1080f;
    public static float contrabandFactionCooldownDays = 540f;
    public static float contrabandMarketCooldownDays = 360f;
}
