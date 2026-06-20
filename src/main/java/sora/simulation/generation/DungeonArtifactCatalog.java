package sora.simulation.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DungeonArtifactCatalog {
    private final List<DungeonArtifactTemplate> templates;
    private final Map<String, DungeonArtifactTraits> traitsByTemplateId = new HashMap<>();
    private final List<DungeonArtifactMove> moves;
    private final DungeonArtifactTemplate defaultTemplate;
    private final long fingerprint;

    public DungeonArtifactCatalog(List<DungeonArtifactTemplate> templates) {
        List<DungeonArtifactTemplate> sortedTemplates = new ArrayList<>(Objects.requireNonNull(templates, "templates"));
        sortedTemplates.sort(Comparator.comparing(DungeonArtifactTemplate::getId));
        this.templates = List.copyOf(sortedTemplates);
        if (this.templates.isEmpty()) {
            throw new IllegalArgumentException("Artifact catalog cannot be empty.");
        }
        this.defaultTemplate = this.templates.stream()
                .filter(template -> "default".equals(template.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Artifact catalog must contain an artifact named default."));
        for (DungeonArtifactTemplate template : this.templates) {
            traitsByTemplateId.put(template.getId(), new DungeonArtifactTraits(template));
        }
        this.moves = buildMoves(this.templates);
        this.fingerprint = calculateFingerprint(this.templates);
    }

    public List<DungeonArtifactTemplate> getTemplates() { return templates; }
    public List<DungeonArtifactMove> getMoves() { return moves; }
    public DungeonArtifactTemplate getDefaultTemplate() { return defaultTemplate; }
    public long getFingerprint() { return fingerprint; }

    public DungeonArtifactTraits getTraits(DungeonArtifactTemplate template) {
        return traitsByTemplateId.get(template.getId());
    }

    public List<DungeonArtifactMove> getMovesStartingFrom(DungeonDirection openDirection) {
        List<DungeonArtifactMove> matching = new ArrayList<>();
        for (DungeonArtifactMove move : moves) {
            if (move.startsFrom(openDirection)) {
                matching.add(move);
            }
        }
        return List.copyOf(matching);
    }

    private static List<DungeonArtifactMove> buildMoves(List<DungeonArtifactTemplate> templates) {
        List<DungeonArtifactMove> moves = new ArrayList<>();
        for (DungeonArtifactTemplate template : templates) {
            if (template.getOpenings().size() == 1) {
                for (int rotation = 0; rotation < 360; rotation += 90) {
                    moves.add(new DungeonArtifactMove(template, 0, -1, rotation, false));
                    moves.add(new DungeonArtifactMove(template, 0, -1, rotation, true));
                }
                continue;
            }
            for (int entrance = 0; entrance < template.getOpenings().size(); entrance++) {
                for (int exit = 0; exit < template.getOpenings().size(); exit++) {
                    if (entrance == exit) continue;
                    for (int rotation = 0; rotation < 360; rotation += 90) {
                        moves.add(new DungeonArtifactMove(template, entrance, exit, rotation, false));
                        moves.add(new DungeonArtifactMove(template, entrance, exit, rotation, true));
                    }
                }
            }
        }
        moves.sort(Comparator
                .comparing((DungeonArtifactMove move) -> move.getTemplate().getId())
                .thenComparingInt(DungeonArtifactMove::getEntranceOpeningIndex)
                .thenComparingInt(DungeonArtifactMove::getExitOpeningIndex)
                .thenComparingInt(DungeonArtifactMove::getRotationDegrees)
                .thenComparingInt(move -> move.isMirroredVertically() ? 1 : 0));
        return List.copyOf(moves);
    }

    private static long calculateFingerprint(List<DungeonArtifactTemplate> templates) {
        long hash = 1125899906842597L;
        for (DungeonArtifactTemplate template : templates) {
            hash = mix(hash, template.getId().hashCode());
            hash = mix(hash, template.getName().hashCode());
            hash = mix(hash, template.getCategory());
            hash = mix(hash, template.getWalls().hashCode());
            hash = mix(hash, template.getOpenings().hashCode());
            hash = mix(hash, template.getOccupiedAreas().hashCode());
            hash = mix(hash, template.getItems().hashCode());
        }
        return hash;
    }

    private static long mix(long current, long value) {
        return current * 31L + value;
    }
}
