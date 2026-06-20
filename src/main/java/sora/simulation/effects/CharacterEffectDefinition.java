package sora.simulation.effects;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class CharacterEffectDefinition {
    private final String id;
    private final String displayName;
    private final List<CharacterEffectModifier> modifiers;
    private final List<CharacterEffectTick> ticks;
    private final boolean negative;
    private final boolean neutralizable;
    private final boolean hiddenDuration;

    public CharacterEffectDefinition(String id, List<CharacterEffectModifier> modifiers) {
        this(id, prettifyId(id), modifiers, List.of());
    }

    public CharacterEffectDefinition(String id, String displayName, List<CharacterEffectModifier> modifiers) {
        this(id, displayName, modifiers, List.of());
    }

    public CharacterEffectDefinition(
            String id,
            List<CharacterEffectModifier> modifiers,
            List<CharacterEffectTick> ticks
    ) {
        this(id, prettifyId(id), modifiers, ticks);
    }

    public CharacterEffectDefinition(
            String id,
            String displayName,
            List<CharacterEffectModifier> modifiers,
            List<CharacterEffectTick> ticks
    ) {
        this(id, displayName, modifiers, ticks, false, false, false);
    }

    public CharacterEffectDefinition(
            String id,
            String displayName,
            List<CharacterEffectModifier> modifiers,
            List<CharacterEffectTick> ticks,
            boolean negative,
            boolean neutralizable,
            boolean hiddenDuration
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Effect id cannot be blank.");
        }
        this.id = id.trim();
        this.displayName = normalizeDisplayName(displayName, this.id);
        this.modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        this.ticks = List.copyOf(Objects.requireNonNull(ticks, "ticks"));
        this.negative = negative;
        this.neutralizable = neutralizable;
        this.hiddenDuration = hiddenDuration;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<CharacterEffectModifier> getModifiers() {
        return modifiers;
    }

    public List<CharacterEffectTick> getTicks() {
        return ticks;
    }

    public boolean isNegative() {
        return negative;
    }

    public boolean isNeutralizable() {
        return neutralizable;
    }

    public boolean hasHiddenDuration() {
        return hiddenDuration;
    }

    void apply(ActiveCharacterEffect effect, EnumMap<CharacterProperty, Double> properties) {
        for (CharacterEffectModifier modifier : modifiers) {
            double amount = modifier.amount() * effect.getStrength();
            if (effect.getMode() == CharacterEffectMode.SET) {
                properties.put(modifier.property(), amount);
            } else {
                properties.put(modifier.property(), properties.getOrDefault(modifier.property(), 0.0) + amount);
            }
        }
    }

    void update(ActiveCharacterEffect effect, DungeonCharacterState characterState, double deltaSeconds) {
        for (CharacterEffectTick tick : ticks) {
            int count = effect.consumeTicks(deltaSeconds, tick.intervalSeconds());
            if (count > 0) {
                double amount = tick.amount() * effect.getStrength() * count;
                if (tick.cappedByEffectStrength()) {
                    amount = 0.0;
                    for (int i = 0; i < count; i++) {
                        amount += effect.consumeLimitedTick(tick.amount());
                    }
                }
                characterState.addBase(
                        tick.property(),
                        amount
                );
            }
        }
    }

    private static String normalizeDisplayName(String displayName, String id) {
        if (displayName == null || displayName.isBlank()) {
            return prettifyId(id);
        }
        return displayName.trim();
    }

    private static String prettifyId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String[] parts = id.trim().split("[_\\s-]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
