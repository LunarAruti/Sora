package sora.simulation.effects;

public record CharacterEffectModifier(CharacterProperty property, double amount) {
    public CharacterEffectModifier {
        if (property == null) {
            throw new IllegalArgumentException("Effect modifier property cannot be null.");
        }
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Effect modifier amount must be finite.");
        }
    }
}
