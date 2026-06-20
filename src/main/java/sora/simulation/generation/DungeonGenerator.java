package sora.simulation.generation;

import sora.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class DungeonGenerator {
    private final DungeonArtifactCatalog catalog;
    private final DungeonGenerationConfig config;
    private final DungeonGenerationProfile profile;

    public DungeonGenerator(DungeonArtifactCatalog catalog, DungeonGenerationConfig config) {
        if (catalog == null) throw new IllegalArgumentException("catalog cannot be null.");
        if (config == null) throw new IllegalArgumentException("config cannot be null.");
        this.catalog = catalog;
        this.config = config;
        this.profile = DungeonGenerationProfile.balanced();
    }

    public DungeonMap generate(long seed) {
        List<DungeonPlacedArtifact> placements = new ArrayList<>();
        PriorityQueue<FrontierOpening> frontier = new PriorityQueue<>(
                Comparator.comparingLong(FrontierOpening::priority)
                        .thenComparingInt(FrontierOpening::placementIndex)
                        .thenComparingInt(FrontierOpening::openingIndex)
        );

        DungeonPlacedArtifact start = new DungeonPlacedArtifact(
                0,
                catalog.getDefaultTemplate(),
                new DungeonPoint(0, 0),
                0
        );
        placements.add(start);
        addFrontiers(seed, frontier, start);

        int failedFrontiers = 0;
        while (!frontier.isEmpty() &&
                placements.size() < config.getMaxPlacements() &&
                failedFrontiers < config.getMaxFailedFrontiers()) {
            FrontierOpening current = frontier.poll();
            DungeonPlacedArtifact source = placements.get(current.placementIndex());
            if (source.isOpeningConnected(current.openingIndex())) {
                continue;
            }

            PlacementResult next = findPlacement(seed, placements, source, current.openingIndex());
            if (next == null) {
                failedFrontiers++;
                continue;
            }

            source.connectOpening(current.openingIndex());
            next.placement().connectOpening(next.openingIndex());
            placements.add(next.placement());
            addFrontiers(seed, frontier, next.placement());
            connectOverlappingOpenings(placements);
            failedFrontiers = 0;
        }

        int overlapConnections = 0;
        int nearbyConnections = 0;
        for (int pass = 0; pass < profile.getMaxReconnectionPasses(); pass++) {
            int passOverlapConnections = connectOverlappingOpenings(placements);
            int passNearbyConnections = connectNearbyOpenings(seed, placements);
            overlapConnections += passOverlapConnections;
            nearbyConnections += passNearbyConnections;
            if (passOverlapConnections == 0 && passNearbyConnections == 0) {
                break;
            }
        }
        int followupOverlapConnections = connectOverlappingOpenings(placements);
        Logger.log(Logger.TAG.INFO, "DungeonGenerator: generated seed=" + seed
                + " placements=" + placements.size()
                + " overlapConnections=" + overlapConnections
                + " nearbyConnections=" + nearbyConnections
                + " followupOverlapConnections=" + followupOverlapConnections
                + " artifactFingerprint=" + catalog.getFingerprint());
        return new DungeonMap(seed, catalog.getFingerprint(), config, placements);
    }

    private PlacementResult findPlacement(
            long seed,
            List<DungeonPlacedArtifact> placements,
            DungeonPlacedArtifact source,
            int sourceOpeningIndex
    ) {
        DungeonOpening sourceOpening = source.getWorldOpening(sourceOpeningIndex);
        PlacementResult continuing = findPlacementFromCandidates(
                placements,
                sourceOpening,
                buildCandidates(seed, source, sourceOpeningIndex, sourceOpening, false)
        );
        if (continuing != null) {
            return continuing;
        }
        if (!shouldAllowDeadEndFallback(seed, source, sourceOpeningIndex)) {
            return null;
        }
        return findPlacementFromCandidates(
                placements,
                sourceOpening,
                buildCandidates(seed, source, sourceOpeningIndex, sourceOpening, true)
        );
    }

    private boolean shouldAllowDeadEndFallback(long seed, DungeonPlacedArtifact source, int sourceOpeningIndex) {
        if (source.getPlacementIndex() == 0) {
            return false;
        }
        long key = hash(seed, catalog.getFingerprint(), source.getPlacementIndex(), sourceOpeningIndex, 0xDEAD_EADEL);
        return unsignedUnit(key) < 0.18;
    }

    private PlacementResult findPlacementFromCandidates(
            List<DungeonPlacedArtifact> placements,
            DungeonOpening sourceOpening,
            List<Candidate> candidates
    ) {
        for (Candidate candidate : candidates) {
            DungeonPlacedArtifact placement = candidate.toPlacement(placements.size(), sourceOpening);
            if (canPlace(placements, placement)) {
                return new PlacementResult(placement, candidate.move().getEntranceOpeningIndex());
            }
        }
        return null;
    }

    private List<Candidate> buildCandidates(
            long seed,
            DungeonPlacedArtifact source,
            int sourceOpeningIndex,
            DungeonOpening sourceOpening,
            boolean allowDeadEnds
    ) {
        List<Candidate> candidates = new ArrayList<>();
        for (DungeonArtifactMove move : catalog.getMovesStartingFrom(sourceOpening.direction())) {
            DungeonArtifactTemplate template = move.getTemplate();
            if (template.getCategory() == 0 ||
                    template.getCategory() == 7 ||
                    "default".equals(template.getName()) ||
                    template.getOccupiedAreas().isEmpty() ||
                    template.getOpenings().isEmpty()) {
                continue;
            }
            if (!allowDeadEnds && !move.hasExit()) {
                continue;
            }
            if (allowDeadEnds && move.hasExit()) {
                continue;
            }
            long key = hash(
                    seed,
                    catalog.getFingerprint(),
                    source.getPlacementIndex(),
                    sourceOpeningIndex,
                    template.getId().hashCode(),
                    move.getEntranceOpeningIndex(),
                    move.getExitOpeningIndex(),
                    move.getRotationDegrees(),
                    move.isMirroredVertically() ? 1L : 0L,
                    allowDeadEnds ? 1L : 0L
            );
            double weightedScore = unsignedUnit(key) * profile.scoreMultiplier(catalog.getTraits(template));
            candidates.add(new Candidate(move, weightedScore));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score)
                .thenComparing(candidate -> candidate.move().getTemplate().getId())
                .thenComparingInt(candidate -> candidate.move().getEntranceOpeningIndex())
                .thenComparingInt(candidate -> candidate.move().getExitOpeningIndex())
                .thenComparingInt(candidate -> candidate.move().getRotationDegrees())
                .thenComparingInt(candidate -> candidate.move().isMirroredVertically() ? 1 : 0));
        return candidates;
    }

    private boolean canPlace(List<DungeonPlacedArtifact> placements, DungeonPlacedArtifact candidate) {
        List<DungeonOccupiedArea> occupiedAreas = candidate.getWorldOccupiedAreas();
        for (DungeonOccupiedArea area : occupiedAreas) {
            if (!config.getWorldBounds().contains(area.getBounds())) {
                return false;
            }
            for (DungeonPlacedArtifact existing : placements) {
                for (DungeonOccupiedArea existingArea : existing.getWorldOccupiedAreas()) {
                    if (area.intersects(existingArea)) {
                        return false;
                    }
                }
            }
        }
        return !occupiedAreas.isEmpty();
    }

    private void addFrontiers(long seed, PriorityQueue<FrontierOpening> frontier, DungeonPlacedArtifact placement) {
        for (int i = 0; i < placement.getTemplate().getOpenings().size(); i++) {
            frontier.offer(new FrontierOpening(
                    placement.getPlacementIndex(),
                    i,
                    hash(seed, catalog.getFingerprint(), placement.getPlacementIndex(), i)
            ));
        }
    }

    private int connectOverlappingOpenings(List<DungeonPlacedArtifact> placements) {
        Map<DungeonPoint, List<OpeningRef>> openingsByPosition = new HashMap<>();
        for (DungeonPlacedArtifact placement : placements) {
            for (int i = 0; i < placement.getTemplate().getOpenings().size(); i++) {
                if (placement.isOpeningConnected(i)) {
                    continue;
                }
                DungeonOpening opening = placement.getWorldOpening(i);
                openingsByPosition
                        .computeIfAbsent(opening.position(), ignored -> new ArrayList<>())
                        .add(new OpeningRef(placement, i, opening.position(), opening.direction()));
            }
        }

        int connected = 0;
        for (List<OpeningRef> refs : openingsByPosition.values()) {
            refs.sort(Comparator
                    .comparingInt((OpeningRef ref) -> ref.placement().getPlacementIndex())
                    .thenComparingInt(OpeningRef::openingIndex));
            for (int i = 0; i < refs.size(); i++) {
                OpeningRef a = refs.get(i);
                if (a.placement().isOpeningConnected(a.openingIndex())) {
                    continue;
                }
                for (int j = i + 1; j < refs.size(); j++) {
                    OpeningRef b = refs.get(j);
                    if (b.placement().isOpeningConnected(b.openingIndex()) ||
                            a.placement() == b.placement()) {
                        continue;
                    }
                    if (a.direction().opposite() == b.direction()) {
                        a.placement().connectOpening(a.openingIndex());
                        b.placement().connectOpening(b.openingIndex());
                        connected++;
                        break;
                    }
                }
            }
        }
        return connected;
    }

    private int connectNearbyOpenings(long seed, List<DungeonPlacedArtifact> placements) {
        List<OpeningRef> openings = collectUnconnectedOpenings(placements);
        int connected = 0;
        List<OpeningPair> pairs = collectReconnectPairs(openings);

        for (OpeningPair pair : pairs) {
            if (placements.size() >= config.getMaxPlacements()) {
                break;
            }
            OpeningRef a = pair.a();
            OpeningRef b = pair.b();
            if (a.placement().isOpeningConnected(a.openingIndex()) ||
                    b.placement().isOpeningConnected(b.openingIndex())) {
                continue;
            }
            if (tryConnectNearbyPair(seed, placements, a, b)) {
                connected++;
            }
        }
        return connected;
    }

    private List<OpeningPair> collectReconnectPairs(List<OpeningRef> openings) {
        List<OpeningPair> pairs = new ArrayList<>();
        int radius = profile.getReconnectionRadius();
        for (int i = 0; i < openings.size(); i++) {
            OpeningRef a = openings.get(i);
            for (int j = i + 1; j < openings.size(); j++) {
                OpeningRef b = openings.get(j);
                if (a.placement() == b.placement()) {
                    continue;
                }
                int distance = manhattanDistance(a.position(), b.position());
                if (distance <= radius) {
                    pairs.add(new OpeningPair(a, b, distance));
                }
            }
        }
        pairs.sort(Comparator
                .comparingInt(OpeningPair::distance)
                .thenComparingInt(pair -> pair.a().placement().getPlacementIndex())
                .thenComparingInt(pair -> pair.a().openingIndex())
                .thenComparingInt(pair -> pair.b().placement().getPlacementIndex())
                .thenComparingInt(pair -> pair.b().openingIndex()));
        return pairs;
    }

    private boolean tryConnectNearbyPair(
            long seed,
            List<DungeonPlacedArtifact> placements,
            OpeningRef a,
            OpeningRef b
    ) {
        List<ReconnectStep> path = findReconnectPath(seed, placements, a, b);
        if (path.isEmpty()) {
            return false;
        }
        if (placements.size() + path.size() > config.getMaxPlacements()) {
            return false;
        }

        a.placement().connectOpening(a.openingIndex());
        b.placement().connectOpening(b.openingIndex());
        for (ReconnectStep step : path) {
            step.placement().connectOpening(step.move().getEntranceOpeningIndex());
            step.placement().connectOpening(step.move().getExitOpeningIndex());
            placements.add(step.placement());
        }
        return true;
    }

    private List<ReconnectStep> findReconnectPath(
            long seed,
            List<DungeonPlacedArtifact> placements,
            OpeningRef start,
            OpeningRef target
    ) {
        PriorityQueue<ReconnectNode> queue = new PriorityQueue<>(
                Comparator.comparingDouble(ReconnectNode::estimatedTotalCost)
                        .thenComparingInt(ReconnectNode::depth)
                        .thenComparing(node -> node.position().x())
                        .thenComparing(node -> node.position().y())
                        .thenComparing(node -> node.direction().name())
        );
        queue.offer(new ReconnectNode(
                start.position(),
                start.direction(),
                List.of(),
                0.0,
                heuristic(start.position(), target.position()),
                0
        ));

        int searched = 0;
        while (!queue.isEmpty() && searched < profile.getMaxReconnectionSearchNodes()) {
            searched++;
            ReconnectNode node = queue.poll();
            if (node.depth() >= profile.getMaxReconnectionDepth()) {
                continue;
            }

            DungeonOpening currentOpening = new DungeonOpening(node.position(), node.direction(), 1);
            for (DungeonArtifactMove move : sortedReconnectMoves(node, target)) {
                if (!move.hasExit()) {
                    continue;
                }
                DungeonArtifactTemplate template = move.getTemplate();
                DungeonArtifactTraits traits = catalog.getTraits(template);
                if (template.getCategory() == 0 ||
                        "default".equals(template.getName()) ||
                        !profile.canUseForReconnection(traits)) {
                    continue;
                }

                DungeonPlacedArtifact placement = move.toPlacement(
                        placements.size() + node.steps().size(),
                        currentOpening
                );
                if (!canPlace(placements, node.steps(), placement)) {
                    continue;
                }

                DungeonOpening exit = placement.getWorldOpening(move.getExitOpeningIndex());
                if (!canStillReach(exit.position(), target.position(), node.depth() + 1)) {
                    continue;
                }

                List<ReconnectStep> nextSteps = new ArrayList<>(node.steps());
                nextSteps.add(new ReconnectStep(move, placement));
                if (exit.position().equals(target.position()) && exit.direction().opposite() == target.direction()) {
                    return List.copyOf(nextSteps);
                }

                long key = hash(
                        seed,
                        catalog.getFingerprint(),
                        start.placement().getPlacementIndex(),
                        start.openingIndex(),
                        target.placement().getPlacementIndex(),
                        target.openingIndex(),
                        template.getId().hashCode(),
                        move.getEntranceOpeningIndex(),
                        move.getExitOpeningIndex(),
                        move.getRotationDegrees(),
                        move.isMirroredVertically() ? 1L : 0L,
                        node.depth()
                );
                double stepCost = profile.reconnectionCost(move, traits) + unsignedUnit(key) * 0.01;
                double nextCost = node.cost() + stepCost;
                queue.offer(new ReconnectNode(
                        exit.position(),
                        exit.direction(),
                        List.copyOf(nextSteps),
                        nextCost,
                        nextCost + heuristic(exit.position(), target.position()),
                        node.depth() + 1
                ));
            }
        }
        return List.of();
    }

    private List<DungeonArtifactMove> sortedReconnectMoves(ReconnectNode node, OpeningRef target) {
        List<DungeonArtifactMove> moves = new ArrayList<>(catalog.getMovesStartingFrom(node.direction()));
        moves.sort(Comparator
                .comparingDouble((DungeonArtifactMove move) -> reconnectMoveEstimate(node, target, move))
                .thenComparing(move -> move.getTemplate().getId())
                .thenComparingInt(DungeonArtifactMove::getEntranceOpeningIndex)
                .thenComparingInt(DungeonArtifactMove::getExitOpeningIndex)
                .thenComparingInt(DungeonArtifactMove::getRotationDegrees)
                .thenComparingInt(move -> move.isMirroredVertically() ? 1 : 0));
        return moves;
    }

    private double reconnectMoveEstimate(ReconnectNode node, OpeningRef target, DungeonArtifactMove move) {
        DungeonPoint exitPosition = node.position().translate(move.getExitOffsetFromEntrance());
        DungeonArtifactTraits traits = catalog.getTraits(move.getTemplate());
        double categoryPenalty = profile.canUseForReconnection(traits) ? 0.0 : 1_000.0;
        return categoryPenalty
                + profile.reconnectionCost(move, traits)
                + heuristic(exitPosition, target.position());
    }

    private boolean canPlace(
            List<DungeonPlacedArtifact> existingPlacements,
            List<ReconnectStep> pathSteps,
            DungeonPlacedArtifact candidate
    ) {
        if (!canPlace(existingPlacements, candidate)) {
            return false;
        }
        for (ReconnectStep step : pathSteps) {
            for (DungeonOccupiedArea candidateArea : candidate.getWorldOccupiedAreas()) {
                for (DungeonOccupiedArea pathArea : step.placement().getWorldOccupiedAreas()) {
                    if (candidateArea.intersects(pathArea)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean canStillReach(DungeonPoint current, DungeonPoint target, int usedDepth) {
        int remaining = profile.getMaxReconnectionDepth() - usedDepth;
        int allowedDistance = Math.max(0, remaining * profile.getReconnectionRadius());
        return manhattanDistance(current, target) <= allowedDistance;
    }

    private double heuristic(DungeonPoint current, DungeonPoint target) {
        return manhattanDistance(current, target) * 0.10;
    }

    private int manhattanDistance(DungeonPoint a, DungeonPoint b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    private List<OpeningRef> collectUnconnectedOpenings(List<DungeonPlacedArtifact> placements) {
        List<OpeningRef> openings = new ArrayList<>();
        for (DungeonPlacedArtifact placement : placements) {
            for (int i = 0; i < placement.getTemplate().getOpenings().size(); i++) {
                if (placement.isOpeningConnected(i)) {
                    continue;
                }
                DungeonOpening opening = placement.getWorldOpening(i);
                openings.add(new OpeningRef(placement, i, opening.position(), opening.direction()));
            }
        }
        openings.sort(Comparator
                .comparingInt((OpeningRef ref) -> ref.placement().getPlacementIndex())
                .thenComparingInt(OpeningRef::openingIndex));
        return openings;
    }

    private static long hash(long... values) {
        long result = 0x9E3779B97F4A7C15L;
        for (long value : values) {
            result ^= value + 0x9E3779B97F4A7C15L + (result << 6) + (result >>> 2);
            result = mix64(result);
        }
        return result;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unsignedUnit(long value) {
        return (double) (value >>> 1) / (double) Long.MAX_VALUE;
    }

    private record FrontierOpening(int placementIndex, int openingIndex, long priority) {}

    private record PlacementResult(DungeonPlacedArtifact placement, int openingIndex) {}

    private record OpeningRef(
            DungeonPlacedArtifact placement,
            int openingIndex,
            DungeonPoint position,
            DungeonDirection direction
    ) {}

    private record OpeningPair(OpeningRef a, OpeningRef b, int distance) {}

    private record ReconnectStep(DungeonArtifactMove move, DungeonPlacedArtifact placement) {}

    private record ReconnectNode(
            DungeonPoint position,
            DungeonDirection direction,
            List<ReconnectStep> steps,
            double cost,
            double estimatedTotalCost,
            int depth
    ) {}

    private record Candidate(
            DungeonArtifactMove move,
            double score
    ) {
        DungeonPlacedArtifact toPlacement(int placementIndex, DungeonOpening sourceOpening) {
            return move.toPlacement(placementIndex, sourceOpening);
        }
    }
}
