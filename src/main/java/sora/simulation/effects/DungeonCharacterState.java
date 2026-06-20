package sora.simulation.effects;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DungeonCharacterState {
    private static final double MAX_HUNGER = 100.0;
    private static final double MAX_SANITY = 100.0;
    private static final double STARVATION_THRESHOLD = 5.0;
    private static final double RESTORATION_HUNGER_THRESHOLD = 25.0;
    private static final double RESTORATION_DAMAGE_COOLDOWN_SECONDS = 10.0;
    private static final int MAX_ACTIVE_EFFECTS = 64;

    private final EnumMap<CharacterProperty, Double> baseProperties = new EnumMap<>(CharacterProperty.class);
    private final EnumMap<CharacterProperty, Double> currentProperties = new EnumMap<>(CharacterProperty.class);
    private final List<ActiveCharacterEffect> activeEffects = new ArrayList<>();
    private double restorationCooldownRemaining;
    private double noiseLevel;

    public DungeonCharacterState() {
        setBase(CharacterProperty.HEALTH, 100.0);
        setBase(CharacterProperty.MAX_HEALTH, 100.0);
        setBase(CharacterProperty.STAMINA, 100.0);
        setBase(CharacterProperty.MAX_STAMINA, 100.0);
        setBase(CharacterProperty.STAMINA_REGEN, 6.0);
        setBase(CharacterProperty.MOVEMENT_SPEED, 1.0);
        setBase(CharacterProperty.HUNGER, 50.0);
        setBase(CharacterProperty.HUNGER_RATE, -2.5 / 60.0);
        setBase(CharacterProperty.SANITY, 100.0);
        setBase(CharacterProperty.SANITY_RATE, 0.0);
        setBase(CharacterProperty.VISION_RADIUS, 1.0);
        setBase(CharacterProperty.NOISE, 0.0);
        setBase(CharacterProperty.INTERACTION_SPEED, 1.0);
        resetCurrentProperties();
    }

    public synchronized double get(CharacterProperty property) {
        if (property == null) {
            return 0.0;
        }
        return currentProperties.getOrDefault(property, baseProperties.getOrDefault(property, 0.0));
    }

    public synchronized boolean isDead() {
        return baseProperties.getOrDefault(CharacterProperty.HEALTH, 0.0) <= 0.0;
    }

    public synchronized double getEffectiveVisionRadiusMultiplier() {
        double sanity = clamp(baseProperties.getOrDefault(CharacterProperty.SANITY, MAX_SANITY), 0.0, MAX_SANITY);
        double sanityVisionMultiplier = 0.4 + 0.6 * (sanity / MAX_SANITY);
        return Math.max(0.1, get(CharacterProperty.VISION_RADIUS) * sanityVisionMultiplier);
    }

    public synchronized void setBase(CharacterProperty property, double value) {
        if (property != null && Double.isFinite(value)) {
            baseProperties.put(property, value);
            currentProperties.put(property, value);
        }
    }

    synchronized void addBase(CharacterProperty property, double amount) {
        if (property != null && Double.isFinite(amount)) {
            baseProperties.put(property, baseProperties.getOrDefault(property, 0.0) + amount);
        }
    }

    public synchronized void addEffect(ActiveCharacterEffect effect) {
        setEffect(effect);
    }

    public synchronized void setEffect(ActiveCharacterEffect effect) {
        if (effect == null) {
            return;
        }
        for (ActiveCharacterEffect activeEffect : activeEffects) {
            if (activeEffect.getEffectId().equals(effect.getEffectId())) {
                activeEffect.refresh(effect.getDurationRemaining(), effect.getStrength(), effect.getMode());
                return;
            }
        }
        if (activeEffects.size() >= MAX_ACTIVE_EFFECTS) {
            activeEffects.remove(0);
        }
        activeEffects.add(effect);
    }

    public synchronized void removeEffect(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return;
        }
        String normalized = effectId.trim();
        activeEffects.removeIf(effect -> normalized.equals(effect.getEffectId()));
    }

    public synchronized void removeNeutralizableEffects(DungeonEffectLibrary effectLibrary) {
        if (effectLibrary == null) {
            return;
        }
        activeEffects.removeIf(effect -> effectLibrary.find(effect.getEffectId())
                .map(CharacterEffectDefinition::isNeutralizable)
                .orElse(false));
        recalculateCurrentProperties(effectLibrary);
    }

    public synchronized void addSanity(double amount) {
        if (Double.isFinite(amount)) {
            setGauge(
                    CharacterProperty.SANITY,
                    baseProperties.getOrDefault(CharacterProperty.SANITY, MAX_SANITY) + amount,
                    0.0,
                    MAX_SANITY
            );
        }
    }

    public synchronized List<ActiveCharacterEffect> getActiveEffects() {
        return List.copyOf(activeEffects);
    }

    public synchronized double getNoiseLevel() {
        return noiseLevel;
    }

    public synchronized void setNoiseLevel(double noiseLevel) {
        this.noiseLevel = Double.isFinite(noiseLevel) ? Math.max(0.0, Math.min(1000.0, noiseLevel)) : 0.0;
    }

    public synchronized void clearEffects() {
        activeEffects.clear();
        restorationCooldownRemaining = 0.0;
        noiseLevel = 0.0;
        resetCurrentProperties();
    }

    public synchronized void updateEffects(double deltaSeconds, DungeonEffectLibrary effectLibrary) {
        double normalizedDelta = Math.max(0.0, deltaSeconds);
        double healthBeforeTicks = baseProperties.getOrDefault(CharacterProperty.HEALTH, 0.0);
        restorationCooldownRemaining = Math.max(0.0, restorationCooldownRemaining - normalizedDelta);
        activeEffects.removeIf(effect -> !effect.update(deltaSeconds));
        recalculateCurrentProperties(effectLibrary);
        updateVitals(normalizedDelta);
        updateConditionalEffects();
        recalculateCurrentProperties(effectLibrary);
        updateEffectTicks(normalizedDelta, effectLibrary);
        enforceCaps();
        if (baseProperties.getOrDefault(CharacterProperty.HEALTH, 0.0) < healthBeforeTicks) {
            restorationCooldownRemaining = RESTORATION_DAMAGE_COOLDOWN_SECONDS;
            removeEffect("restoration");
        }
        activeEffects.removeIf(ActiveCharacterEffect::isDepleted);
        updateConditionalEffects();
        recalculateCurrentProperties(effectLibrary);
    }

    private void recalculateCurrentProperties(DungeonEffectLibrary effectLibrary) {
        resetCurrentProperties();
        for (ActiveCharacterEffect effect : activeEffects) {
            effectLibrary.find(effect.getEffectId())
                    .ifPresent(definition -> definition.apply(effect, currentProperties));
        }
    }

    private void updateVitals(double deltaSeconds) {
        double maxHealth = Math.max(1.0, currentProperties.get(CharacterProperty.MAX_HEALTH));
        double maxStamina = Math.max(0.0, currentProperties.get(CharacterProperty.MAX_STAMINA));

        setGauge(CharacterProperty.STAMINA,
                baseProperties.get(CharacterProperty.STAMINA)
                        + currentProperties.get(CharacterProperty.STAMINA_REGEN) * deltaSeconds,
                0.0,
                maxStamina);
        setGauge(CharacterProperty.HUNGER,
                baseProperties.get(CharacterProperty.HUNGER)
                        + currentProperties.get(CharacterProperty.HUNGER_RATE) * deltaSeconds,
                0.0,
                MAX_HUNGER);
        setGauge(CharacterProperty.SANITY,
                baseProperties.get(CharacterProperty.SANITY)
                        + currentProperties.get(CharacterProperty.SANITY_RATE) * deltaSeconds,
                0.0,
                MAX_SANITY);
    }

    private void updateConditionalEffects() {
        if (baseProperties.get(CharacterProperty.HUNGER) < STARVATION_THRESHOLD) {
            setEffect(new ActiveCharacterEffect(
                    "starving",
                    Double.POSITIVE_INFINITY,
                    1.0,
                    CharacterEffectMode.ADD
            ));
        } else {
            removeEffect("starving");
        }

        double health = baseProperties.get(CharacterProperty.HEALTH);
        double maxHealth = Math.max(1.0, currentProperties.get(CharacterProperty.MAX_HEALTH));
        double hunger = baseProperties.get(CharacterProperty.HUNGER);
        if (health < maxHealth && hunger >= RESTORATION_HUNGER_THRESHOLD && restorationCooldownRemaining <= 0.0) {
            setEffect(new ActiveCharacterEffect(
                    "restoration",
                    Double.POSITIVE_INFINITY,
                    Math.min(1.0, hunger / MAX_HUNGER),
                    CharacterEffectMode.ADD
            ));
        } else {
            removeEffect("restoration");
        }
    }

    private void updateEffectTicks(double deltaSeconds, DungeonEffectLibrary effectLibrary) {
        for (ActiveCharacterEffect effect : activeEffects) {
            effectLibrary.find(effect.getEffectId())
                    .ifPresent(definition -> definition.update(effect, this, deltaSeconds));
        }
    }

    private void enforceCaps() {
        double maxHealth = Math.max(1.0, currentProperties.get(CharacterProperty.MAX_HEALTH));
        double maxStamina = Math.max(0.0, currentProperties.get(CharacterProperty.MAX_STAMINA));
        setGauge(CharacterProperty.HEALTH,
                baseProperties.get(CharacterProperty.HEALTH),
                0.0,
                maxHealth);
        setGauge(CharacterProperty.STAMINA,
                baseProperties.get(CharacterProperty.STAMINA),
                0.0,
                maxStamina);
        setGauge(CharacterProperty.HUNGER,
                baseProperties.get(CharacterProperty.HUNGER),
                0.0,
                MAX_HUNGER);
        setGauge(CharacterProperty.SANITY,
                baseProperties.get(CharacterProperty.SANITY),
                0.0,
                MAX_SANITY);
    }

    private void setGauge(CharacterProperty property, double value, double min, double max) {
        baseProperties.put(property, clamp(value, min, max));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void resetCurrentProperties() {
        currentProperties.clear();
        for (CharacterProperty property : CharacterProperty.values()) {
            currentProperties.put(property, baseProperties.getOrDefault(property, 0.0));
        }
    }
}
