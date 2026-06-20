package sora.simulation.effects;

public record CharacterEffectTick(
        CharacterProperty property,
        double amount,
        double intervalSeconds,
        boolean cappedByEffectStrength
) {
    public CharacterEffectTick(CharacterProperty property, double amount, double intervalSeconds) {
        this(property, amount, intervalSeconds, false);
    }

    public CharacterEffectTick {
        if (property == null) {
            throw new IllegalArgumentException("Effect tick property cannot be null.");
        }
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Effect tick amount must be finite.");
        }
        if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0.0) {
            throw new IllegalArgumentException("Effect tick interval must be positive.");
        }
    }
}
