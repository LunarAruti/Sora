package sora.simulation.editor;

import sora.simulation.items.DungeonItemDefinition;
import sora.simulation.items.DungeonItemLibrary;
import sora.simulation.items.DungeonItemKind;

import java.util.List;
import java.util.Optional;

final class ArtifactItemLibrary {
    private ArtifactItemLibrary() {}

    static List<DungeonItemDefinition> placeableDefinitions() {
        return DungeonItemLibrary.byKind(DungeonItemKind.MAP);
    }

    static DungeonItemDefinition require(String id) {
        return DungeonItemLibrary.require(id);
    }

    static Optional<DungeonItemDefinition> find(String id) {
        return DungeonItemLibrary.find(id);
    }
}
