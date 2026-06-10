package ucadmin.simulation.generation;

import ucadmin.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class DungeonGenerator {
    private final DungeonArtifactCatalog catalog;
    private final DungeonGenerationConfig config;

    public DungeonGenerator(DungeonArtifactCatalog catalog, DungeonGenerationConfig config) {
        if (catalog == null) throw new IllegalArgumentException("catalog cannot be null.");
        if (config == null) throw new IllegalArgumentException("config cannot be null.");
        this.catalog = catalog;
        this.config = config;
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
        }

        Logger.log(Logger.TAG.INFO, "DungeonGenerator: generated seed=" + seed
                + " placements=" + placements.size()
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
        List<Candidate> candidates = buildCandidates(seed, source, sourceOpeningIndex, sourceOpening);
        for (Candidate candidate : candidates) {
            DungeonPlacedArtifact placement = candidate.toPlacement(placements.size(), sourceOpening);
            if (canPlace(placements, placement)) {
                return new PlacementResult(placement, candidate.openingIndex());
            }
        }
        return null;
    }

    private List<Candidate> buildCandidates(
            long seed,
            DungeonPlacedArtifact source,
            int sourceOpeningIndex,
            DungeonOpening sourceOpening
    ) {
        List<Candidate> candidates = new ArrayList<>();
        for (DungeonArtifactTemplate template : catalog.getTemplates()) {
            if (template.getSpawnProbability() <= 0 ||
                    "default".equals(template.getName()) ||
                    template.getOccupiedAreas().isEmpty() ||
                    template.getOpenings().isEmpty()) {
                continue;
            }
            for (int openingIndex = 0; openingIndex < template.getOpenings().size(); openingIndex++) {
                for (int rotation = 0; rotation < 360; rotation += 90) {
                    DungeonOpening rotated = template.getOpenings().get(openingIndex).rotateClockwise(rotation);
                    if (rotated.direction() != sourceOpening.direction().opposite()) {
                        continue;
                    }
                    long key = hash(
                            seed,
                            catalog.getFingerprint(),
                            source.getPlacementIndex(),
                            sourceOpeningIndex,
                            template.getId().hashCode(),
                            openingIndex,
                            rotation
                    );
                    double weightedScore = unsignedUnit(key) / template.getWeight();
                    candidates.add(new Candidate(template, openingIndex, rotation, weightedScore));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score)
                .thenComparing(candidate -> candidate.template().getId())
                .thenComparingInt(Candidate::openingIndex)
                .thenComparingInt(Candidate::rotationDegrees));
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

    private record Candidate(
            DungeonArtifactTemplate template,
            int openingIndex,
            int rotationDegrees,
            double score
    ) {
        DungeonPlacedArtifact toPlacement(int placementIndex, DungeonOpening sourceOpening) {
            DungeonOpening rotatedOpening = template.getOpenings().get(openingIndex).rotateClockwise(rotationDegrees);
            DungeonPoint center = new DungeonPoint(
                    sourceOpening.position().x() - rotatedOpening.position().x(),
                    sourceOpening.position().y() - rotatedOpening.position().y()
            );
            return new DungeonPlacedArtifact(placementIndex, template, center, rotationDegrees);
        }
    }
}
