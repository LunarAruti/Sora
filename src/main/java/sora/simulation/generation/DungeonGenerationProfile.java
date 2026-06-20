package sora.simulation.generation;

import java.util.HashMap;
import java.util.Map;

public final class DungeonGenerationProfile {
    private final String name;
    private final double passageBias;
    private final double junctionBias;
    private final double deadEndBias;
    private final double roomBias;
    private final double connectorPenalty;
    private final int reconnectionRadius;
    private final int maxReconnectionDepth;
    private final int maxReconnectionSearchNodes;
    private final int maxReconnectionPasses;
    private final Map<Integer, Double> categoryWeights;

    private DungeonGenerationProfile(
            String name,
            double passageBias,
            double junctionBias,
            double deadEndBias,
            double roomBias,
            double connectorPenalty,
            int reconnectionRadius,
            int maxReconnectionDepth,
            int maxReconnectionSearchNodes,
            int maxReconnectionPasses,
            Map<Integer, Double> categoryWeights
    ) {
        this.name = name;
        this.passageBias = passageBias;
        this.junctionBias = junctionBias;
        this.deadEndBias = deadEndBias;
        this.roomBias = roomBias;
        this.connectorPenalty = connectorPenalty;
        this.reconnectionRadius = reconnectionRadius;
        this.maxReconnectionDepth = maxReconnectionDepth;
        this.maxReconnectionSearchNodes = maxReconnectionSearchNodes;
        this.maxReconnectionPasses = maxReconnectionPasses;
        this.categoryWeights = Map.copyOf(categoryWeights);
    }

    public static DungeonGenerationProfile balanced() {
        Map<Integer, Double> weights = new HashMap<>();
        weights.put(1, 0.82); // basic hallways
        weights.put(2, 0.88); // exotic hallways
        weights.put(3, 1.08); // rooms
        weights.put(4, 1.18); // large chambers
        weights.put(5, 0.70); // junctions
        weights.put(6, 2.40); // dead ends
        weights.put(7, 0.72); // connectors are mostly for reconnection planning
        weights.put(8, 1.30); // setpieces
        return new DungeonGenerationProfile(
                "balanced",
                0.84,
                0.72,
                2.10,
                1.03,
                -0.12,
                18,
                14,
                18_000,
                10,
                weights
        );
    }

    public String getName() { return name; }
    public int getReconnectionRadius() { return reconnectionRadius; }
    public int getMaxReconnectionDepth() { return maxReconnectionDepth; }
    public int getMaxReconnectionSearchNodes() { return maxReconnectionSearchNodes; }
    public int getMaxReconnectionPasses() { return maxReconnectionPasses; }

    public boolean canUseForReconnection(DungeonArtifactTraits traits) {
        int category = traits.getCategory();
        return category == 1 || category == 2 || category == 3 || category == 5 || category == 7;
    }

    public double scoreMultiplier(DungeonArtifactTraits traits) {
        double score = categoryWeights.getOrDefault(traits.getCategory(), 1.0);
        if (traits.isPassageLike()) score *= passageBias;
        if (traits.isJunctionLike()) score *= junctionBias;
        if (traits.isDeadEndLike()) score *= deadEndBias;
        if (traits.getOccupiedArea() >= 80.0) score *= roomBias;
        if (traits.getCategory() == 7) score += connectorPenalty;
        return Math.max(0.05, score);
    }

    public double reconnectionCost(DungeonArtifactMove move, DungeonArtifactTraits traits) {
        double cost = 1.0 + Math.max(0, move.getManhattanDistance()) * 0.05;
        if (traits.getCategory() == 7) cost *= 0.62;
        if (traits.getCategory() == 1) cost *= 0.72;
        if (traits.getCategory() == 2) cost *= 0.90;
        if (traits.getCategory() == 5) cost *= 0.95;
        if (traits.getCategory() == 3) cost *= 1.05;
        if (traits.isPassageLike()) cost *= 0.82;
        if (traits.isJunctionLike()) cost *= 0.95;
        if (traits.isDeadEndLike()) cost *= 3.50;
        if (traits.getOccupiedArea() >= 80.0) cost *= 1.35;
        return cost;
    }
}
