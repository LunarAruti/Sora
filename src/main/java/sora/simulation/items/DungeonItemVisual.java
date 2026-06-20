package sora.simulation.items;

public record DungeonItemVisual(
        String shape,
        String fillColor,
        String outlineColor,
        String accentColor,
        String glyph,
        String description
) {
    public DungeonItemVisual {
        shape = textOrDefault(shape, "block");
        fillColor = textOrDefault(fillColor, "#cccccc");
        outlineColor = textOrDefault(outlineColor, "#202020");
        accentColor = textOrDefault(accentColor, "#ffffff");
        glyph = textOrDefault(glyph, "");
        description = textOrDefault(description, "");
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
