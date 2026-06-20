package sora.simulation.effects;

public final class ActiveCharacterEffect {
    private final String effectId;
    private double durationRemaining;
    private double strength;
    private CharacterEffectMode mode;
    private double tickAccumulator;
    private double remainingTickAmount;
    private double elapsedSeconds;

    public ActiveCharacterEffect(
            String effectId,
            double durationRemaining,
            double strength,
            CharacterEffectMode mode
    ) {
        this.effectId = (effectId == null || effectId.isBlank()) ? "unknown" : effectId.trim();
        if (Double.isInfinite(durationRemaining) && durationRemaining > 0.0) {
            this.durationRemaining = Double.POSITIVE_INFINITY;
        } else if (Double.isFinite(durationRemaining)) {
            this.durationRemaining = Math.max(0.0, durationRemaining);
        } else {
            this.durationRemaining = 0.0;
        }
        this.strength = Double.isFinite(strength) ? strength : 0.0;
        this.mode = mode == null ? CharacterEffectMode.ADD : mode;
        this.remainingTickAmount = Math.max(0.0, this.strength);
    }

    public String getEffectId() {
        return effectId;
    }

    public double getDurationRemaining() {
        return durationRemaining;
    }

    public double getStrength() {
        return strength;
    }

    public CharacterEffectMode getMode() {
        return mode;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    void refresh(double duration, double strength, CharacterEffectMode mode) {
        if (Double.isInfinite(duration) && duration > 0.0) {
            durationRemaining = Double.POSITIVE_INFINITY;
        } else if (Double.isFinite(duration)) {
            if ("elevated_stamina".equals(effectId) || "endurance_boost".equals(effectId)) {
                durationRemaining = Math.max(0.0, durationRemaining) + Math.max(0.0, duration);
            } else {
                durationRemaining = Math.max(durationRemaining, Math.max(0.0, duration));
            }
        }
        if (Double.isFinite(strength)) {
            this.strength = strength;
            remainingTickAmount = Math.max(remainingTickAmount, Math.max(0.0, strength));
        }
        this.mode = mode == null ? CharacterEffectMode.ADD : mode;
    }

    boolean update(double deltaSeconds) {
        elapsedSeconds += Math.max(0.0, deltaSeconds);
        if (Double.isInfinite(durationRemaining)) {
            return true;
        }
        durationRemaining = Math.max(0.0, durationRemaining - Math.max(0.0, deltaSeconds));
        return durationRemaining > 0.0;
    }

    int consumeTicks(double deltaSeconds, double intervalSeconds) {
        if (intervalSeconds <= 0.0) {
            return 0;
        }
        tickAccumulator += Math.max(0.0, deltaSeconds);
        int ticks = 0;
        while (tickAccumulator >= intervalSeconds) {
            tickAccumulator -= intervalSeconds;
            ticks++;
        }
        return ticks;
    }

    double consumeLimitedTick(double requestedAmount) {
        if (remainingTickAmount <= 0.0 || requestedAmount <= 0.0) {
            return 0.0;
        }
        double consumed = Math.min(requestedAmount, remainingTickAmount);
        remainingTickAmount -= consumed;
        return consumed;
    }

    boolean isDepleted() {
        return remainingTickAmount <= 0.0 && Double.isInfinite(durationRemaining);
    }
}
