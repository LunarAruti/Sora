package ucadmin.simulation.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DungeonArtifactCatalog {
    private final List<DungeonArtifactTemplate> templates;
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
        this.fingerprint = calculateFingerprint(this.templates);
    }

    public List<DungeonArtifactTemplate> getTemplates() { return templates; }
    public DungeonArtifactTemplate getDefaultTemplate() { return defaultTemplate; }
    public long getFingerprint() { return fingerprint; }

    private static long calculateFingerprint(List<DungeonArtifactTemplate> templates) {
        long hash = 1125899906842597L;
        for (DungeonArtifactTemplate template : templates) {
            hash = mix(hash, template.getId().hashCode());
            hash = mix(hash, template.getName().hashCode());
            hash = mix(hash, template.getSpawnProbability());
            hash = mix(hash, template.getWalls().hashCode());
            hash = mix(hash, template.getOpenings().hashCode());
            hash = mix(hash, template.getOccupiedAreas().hashCode());
        }
        return hash;
    }

    private static long mix(long current, long value) {
        return current * 31L + value;
    }
}
