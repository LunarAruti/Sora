package sora.simulation.effects;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonEffectLibrary {
    private static final DungeonEffectLibrary INSTANCE = new DungeonEffectLibrary();

    private final Map<String, CharacterEffectDefinition> effectsById = new LinkedHashMap<>();

    private DungeonEffectLibrary() {
        register("lit", "Comfortable", modifier(CharacterProperty.SANITY_RATE, 10.0 / 60.0));
        register("darkness", "Psychosis", modifier(CharacterProperty.SANITY_RATE, -2.5 / 60.0));
        register("running", "Running",
                modifier(CharacterProperty.STAMINA_REGEN, -12.0),
                modifier(CharacterProperty.SANITY_RATE, -0.25));
        registerTicking("eating", "Eating", cappedTick(CharacterProperty.HUNGER, 5.0, 1.0));
        registerTicking("restoration", "Restoration", tick(CharacterProperty.HEALTH, 1.0, 1.0));
        registerTicking("regeneration", "Regeneration", tick(CharacterProperty.HEALTH, 10.0, 5.0));
        register("elevated_stamina", "Elevated Stamina", true,
                modifier(CharacterProperty.MAX_STAMINA, 50.0));
        register("endurance_boost", "Endurance Boost", true,
                modifier(CharacterProperty.STAMINA_REGEN, 5.0));
        register("stamina_boost", "Endurance Boost", true,
                modifier(CharacterProperty.STAMINA_REGEN, 5.0));
        registerPlaceholder("elevated_health", "Elevated Health");
        registerPlaceholder("neutralization", "Neutralization");
        registerTicking("starving", "Starving", tick(CharacterProperty.HEALTH, -5.0, 5.0));
        registerNegativeTicking("sick", "Food Poisoning", true, tick(CharacterProperty.HEALTH, -2.0, 5.0));
        registerNegativeTicking("poisoned", "Poisoned", true, tick(CharacterProperty.HEALTH, -2.0, 2.0));
        registerNegativeTicking("toxic_exposure", "Toxic Exposure", false, tick(CharacterProperty.HEALTH, -1.0, 1.0));
        registerNegativeTicking("gas", "Toxic Exposure", false, tick(CharacterProperty.HEALTH, -1.0, 1.0));
        registerNegativeTicking("asphyxiation", "Asphyxiation", false, tick(CharacterProperty.HEALTH, -1.0, 1.0));
        registerNegativeTicking("choking", "Asphyxiation", false, tick(CharacterProperty.HEALTH, -1.0, 1.0));
        registerNegativeTicking("bleeding", "Bleeding", false, tick(CharacterProperty.HEALTH, -1.0, 3.0));
        register("encumbered", "Encumbered", modifier(CharacterProperty.MOVEMENT_SPEED, 1.0));
        register("slowed", "Encumbered", modifier(CharacterProperty.MOVEMENT_SPEED, 1.0));
        registerPlaceholder("refreshed", "Refreshed");
        registerPlaceholder("bandaged", "Bandaged");
        registerNegativeTicking("burning", "Burning", false, tick(CharacterProperty.HEALTH, -2.0, 1.0));
        registerNegative("blind", "Blinded");
        registerNegative("explosion", "Blast Trauma");
        registerPlaceholder("cure_poison", "Antitoxin");
    }

    public static DungeonEffectLibrary instance() {
        return INSTANCE;
    }

    public List<CharacterEffectDefinition> all() {
        return List.copyOf(effectsById.values());
    }

    public Optional<CharacterEffectDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(effectsById.get(id.trim()));
    }

    private void registerPlaceholder(String id, String displayName) {
        effectsById.put(id, new CharacterEffectDefinition(id, displayName, List.of()));
    }

    private void register(String id, String displayName, CharacterEffectModifier... modifiers) {
        effectsById.put(id, new CharacterEffectDefinition(id, displayName, List.of(modifiers)));
    }

    private void register(String id, String displayName, boolean hiddenDuration, CharacterEffectModifier... modifiers) {
        effectsById.put(id, new CharacterEffectDefinition(
                id,
                displayName,
                List.of(modifiers),
                List.of(),
                false,
                false,
                hiddenDuration
        ));
    }

    private void registerTicking(String id, String displayName, CharacterEffectTick... ticks) {
        effectsById.put(id, new CharacterEffectDefinition(id, displayName, List.of(), List.of(ticks)));
    }

    private void registerNegative(String id, String displayName) {
        effectsById.put(id, new CharacterEffectDefinition(
                id,
                displayName,
                List.of(),
                List.of(),
                true,
                true,
                false
        ));
    }

    private void registerNegativeTicking(
            String id,
            String displayName,
            boolean hiddenDuration,
            CharacterEffectTick... ticks
    ) {
        effectsById.put(id, new CharacterEffectDefinition(
                id,
                displayName,
                List.of(),
                List.of(ticks),
                true,
                true,
                hiddenDuration
        ));
    }

    private CharacterEffectModifier modifier(CharacterProperty property, double amount) {
        return new CharacterEffectModifier(property, amount);
    }

    private CharacterEffectTick tick(CharacterProperty property, double amount, double intervalSeconds) {
        return new CharacterEffectTick(property, amount, intervalSeconds);
    }

    private CharacterEffectTick cappedTick(CharacterProperty property, double amount, double intervalSeconds) {
        return new CharacterEffectTick(property, amount, intervalSeconds, true);
    }
}
