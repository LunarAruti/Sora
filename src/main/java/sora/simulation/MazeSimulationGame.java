package sora.simulation;

import sora.simulation.effects.ActiveCharacterEffect;
import sora.simulation.effects.CharacterEffectDefinition;
import sora.simulation.effects.CharacterEffectMode;
import sora.simulation.effects.CharacterProperty;
import sora.simulation.effects.DungeonCharacterState;
import sora.simulation.effects.DungeonEffectLibrary;
import sora.simulation.equipment.DungeonCarryableDefinition;
import sora.simulation.equipment.DungeonCarryableLibrary;
import sora.simulation.equipment.DungeonEquipmentDefinition;
import sora.simulation.equipment.DungeonEquipmentLibrary;
import sora.simulation.equipment.DungeonEquipmentState;
import sora.simulation.equipment.EquipmentAllowance;
import sora.simulation.equipment.EquipmentSlot;
import sora.simulation.generation.DungeonGenerationConfig;
import sora.simulation.generation.DungeonGenerationService;
import sora.simulation.generation.DungeonDirection;
import sora.simulation.generation.DungeonItem;
import sora.simulation.generation.DungeonLine;
import sora.simulation.generation.DungeonLoadedArea;
import sora.simulation.generation.DungeonOccupiedArea;
import sora.simulation.generation.DungeonOpening;
import sora.simulation.generation.DungeonPlacedArtifact;
import sora.simulation.generation.DungeonPoint;
import sora.simulation.generation.DungeonRect;
import sora.simulation.items.DungeonItemCategory;
import sora.simulation.items.DungeonItemDefinition;
import sora.simulation.items.DungeonInventory;
import sora.simulation.items.DungeonInventoryItem;
import sora.simulation.items.DungeonItemKind;
import sora.simulation.items.DungeonItemLibrary;
import sora.simulation.items.DungeonItemSize;
import sora.simulation.items.DungeonItemVisual;
import sora.util.Logger;

import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Dungeon simulation game object.
 */
public final class MazeSimulationGame implements SimulationGame {
    private static final int SCREEN_SIZE = 1000;
    private static final double VISIBLE_BLOCKS_ACROSS = 45.0;
    private static final int VIEW_BUFFER = 10;
    private static final double PLAYER_SPEED = 8.0;
    private static final double PLAYER_SHIFT_SPEED = 11.0;
    private static final double ADMIN_SPEED = 24.0;
    private static final double ADMIN_SHIFT_SPEED = 40.0;
    private static final double PLAYER_SIZE = 2.0;
    private static final double MAX_PLAYER_COLLISION_STEP = 0.35;
    private static final double PLAYER_CLEAR_RADIUS_BLOCKS = VISIBLE_BLOCKS_ACROSS / 4.0;
    private static final double PLAYER_FADE_RADIUS_BLOCKS = 5.0;
    private static final double DEFAULT_LIGHT_RADIUS_BLOCKS = PLAYER_CLEAR_RADIUS_BLOCKS * 1.35;
    private static final double LIGHT_FADE_RADIUS_BLOCKS = 6.0;
    private static final double ITEM_SIZE_BLOCKS = 0.72;
    private static final double MAX_MAP_LIGHT_RADIUS_BLOCKS = maxMapLightRadius();
    private static final int DIM_OVERLAY_ALPHA = 220;
    private static final double ENVIRONMENT_EFFECT_REFRESH_SECONDS = 0.25;
    private static final double TERRAIN_EFFECT_REFRESH_SECONDS = 0.25;
    private static final double RUNNING_EFFECT_REFRESH_SECONDS = 0.25;
    private static final double RUN_EXHAUSTION_COOLDOWN_SECONDS = 5.0;
    private static final double INTERACTION_RANGE_BLOCKS = 3.0;
    private static final int GRID_CLICK_DRAG_THRESHOLD_PIXELS = 5;
    private static final int GRID_CONTEXT_ROW_HEIGHT = 24;
    private static final int GRID_CONTEXT_INFO_WIDTH = 190;
    private static final double MAX_LIGHT_FUEL = 1000.0;
    private static final double LIGHT_FUEL_FADE_THRESHOLD = 50.0;
    private static final double SICK_DURATION_MIN_SECONDS = 30.0;
    private static final double SICK_DURATION_MAX_SECONDS = 120.0;
    private static final double ENDURANCE_DURATION_MIN_SECONDS = 60.0;
    private static final double ENDURANCE_DURATION_MAX_SECONDS = 120.0;
    private static final double GAS_EFFECT_REFRESH_SECONDS = 1.2;
    private static final double GAS_ASPHYXIATION_DELAY_SECONDS = 10.0;
    private static final double EQUIPMENT_TEMPLATE_LOOT_WEIGHT = 3.5;
    private static final double RANDOM_CONTAINER_CHANCE = 0.05;
    private static final double RANDOM_ROOM_CONTAINER_CHANCE_BONUS = 0.10;
    private static final double RANDOM_DETAIL_CHANCE = 0.22;
    private static final double RANDOM_ROOM_DETAIL_CHANCE_BONUS = 0.12;
    private static final double RANDOM_HAZARD_CHANCE = 0.035;
    private static final double RANDOM_ROOM_HAZARD_CHANCE_BONUS = 0.025;
    private static final double RANDOM_LOOSE_ITEM_CHANCE = 0.10;
    private static final double RANDOM_ROOM_ITEM_CHANCE_BONUS = 0.10;
    private static final double OWNERSHIP_AUDIT_INTERVAL_SECONDS = 5.0;
    private static final double WALK_NOISE = 18.0;
    private static final double RUN_NOISE = 45.0;
    private static final double SMALL_INTERACTION_NOISE = 4.0;
    private static final double SEARCH_INTERACTION_NOISE = 25.0;
    private static final double CONTAINER_INTERACTION_NOISE = 18.0;
    private static final double PUSH_INTERACTION_NOISE = 40.0;
    private static final double KEYRING_INTERACTION_NOISE = 3.0;
    private static final double INTERACTION_NOISE_HOLD_SECONDS = 0.45;
    private static final int KEYRING_VISIBLE_KEYS = 6;
    private static final double NOTIFICATION_DURATION_SECONDS = 5.0;
    private static final int MAX_NOTIFICATIONS = 5;
    private static final Color PRIMARY_INTERACTION_COLOR = new Color(178, 232, 255);
    private static final Color SECONDARY_INTERACTION_COLOR = new Color(255, 156, 166);
    private static final Color ERROR_PROMPT_COLOR = new Color(255, 72, 72);
    private static final float WALL_STROKE_PIXELS = 6.0f;
    private static final float SEALED_WALL_STROKE_PIXELS = 8.0f;
    private static final float OCCUPIED_FILL_STROKE_PIXELS = 3.0f;
    private static final int OPENING_NODE_RADIUS_PIXELS = 5;
    private static final Font ARTIFACT_LABEL_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final Font DEATH_TITLE_FONT = new Font("SansSerif", Font.BOLD, 42);
    private static final Font DEATH_BUTTON_FONT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font INVENTORY_TITLE_FONT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font INVENTORY_TEXT_FONT = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font STATUS_BAR_FONT = new Font("SansSerif", Font.BOLD, 11);
    private static final SecureRandom SEED_SOURCE = new SecureRandom();

    private final long seed;
    private final long itemSeed;
    private final boolean adminMode;
    private final SimulationEngine engine;
    private final DungeonGenerationService generationService = new DungeonGenerationService();
    private final DungeonGenerationConfig generationConfig = DungeonGenerationConfig.defaultConfig();
    private final DungeonCharacterState characterState = new DungeonCharacterState();
    private final DungeonEquipmentState equipmentState = new DungeonEquipmentState();
    private final DungeonInventory inventory = new DungeonInventory();
    private final Map<String, PersistentItemState> persistentItemStates = new HashMap<>();
    private final Map<String, ContainerPersistentState> containerPersistentStates = new HashMap<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<DungeonItem> droppedWorldItems = new ArrayList<>();
    private final List<DungeonItem> randomWorldItems = new ArrayList<>();
    private final Map<DungeonItem, String> randomWorldItemKeys = new HashMap<>();
    private final Map<DungeonItem, Map<String, Object>> randomWorldItemProperties = new HashMap<>();
    private DungeonLoadedArea loadedArea;
    private BufferedImage visibilityOverlay;
    private String openContainerKey;
    private DungeonItem openContainerItem;
    private DungeonItemDefinition openContainerDefinition;
    private DraggedGridItem draggedGridItem;
    private GridContextMenu gridContextMenu;
    private PendingOilUse pendingOilUse;
    private PendingKeyPlacement pendingKeyPlacement;
    private ReadDocumentView activeDocument;
    private double gasExposureSeconds;
    private int mousePressX = -1;
    private int mousePressY = -1;
    private double cameraX;
    private double cameraY;
    private double playerX;
    private double playerY;
    private double facingX = 0.0;
    private double facingY = 1.0;
    private double ownershipAuditSeconds;
    private double interactionNoiseLevel;
    private double interactionNoiseRemaining;
    private int keyringScrollOffset;
    private int mouseScreenX = -1;
    private int mouseScreenY = -1;
    private double runCooldownRemaining;
    private boolean playerDead;
    private boolean runningThisFrame;
    private boolean inventoryOpen;
    private boolean tabHeld;
    private boolean swapHeld;
    private boolean interactPrimaryHeld;
    private boolean interactSecondaryHeld;
    private boolean moveUp;
    private boolean moveLeft;
    private boolean moveDown;
    private boolean moveRight;
    private boolean shiftHeld;

    public MazeSimulationGame() {
        this(SEED_SOURCE.nextLong(), SEED_SOURCE.nextLong(), false);
    }

    public MazeSimulationGame(long seed) {
        this(seed, SEED_SOURCE.nextLong(), false);
    }

    public MazeSimulationGame(boolean adminMode) {
        this(SEED_SOURCE.nextLong(), SEED_SOURCE.nextLong(), adminMode);
    }

    public MazeSimulationGame(long seed, boolean adminMode) {
        this(seed, SEED_SOURCE.nextLong(), adminMode);
    }

    public MazeSimulationGame(long seed, long itemSeed) {
        this(seed, itemSeed, false);
    }

    public MazeSimulationGame(long seed, long itemSeed, boolean adminMode) {
        this.seed = seed;
        this.itemSeed = itemSeed;
        this.adminMode = adminMode;
        this.engine = new SimulationEngine(defaultConfig(), this);
    }

    public boolean run() {
        return engine.run();
    }

    public void exit() {
        engine.exit();
    }

    public boolean isRunning() {
        return engine.isRunning();
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    public double getVisionRadiusMultiplier() {
        return characterState.get(CharacterProperty.VISION_RADIUS);
    }

    public void setVisionRadiusMultiplier(double visionRadiusMultiplier) {
        if (Double.isFinite(visionRadiusMultiplier)) {
            characterState.setBase(CharacterProperty.VISION_RADIUS, Math.max(0.1, visionRadiusMultiplier));
        }
    }

    public DungeonCharacterState getCharacterState() {
        return characterState;
    }

    public DungeonInventory getInventory() {
        return inventory;
    }

    public DungeonEquipmentState getEquipmentState() {
        return equipmentState;
    }

    public boolean addInventoryItem(String itemId, int x, int y, int quantity) {
        return inventory.add(itemId, x, y, quantity);
    }

    public boolean addInventoryItem(String itemId, int x, int y, int quantity, Map<String, Object> properties) {
        return inventory.add(itemId, x, y, quantity, properties);
    }

    public void addActiveEffect(String effectId, double duration, double strength, CharacterEffectMode mode) {
        characterState.addEffect(new ActiveCharacterEffect(effectId, duration, strength, mode));
    }

    public long getSeed() {
        return seed;
    }

    public long getItemSeed() {
        return itemSeed;
    }

    public boolean isAdminMode() {
        return adminMode;
    }

    @Override
    public void onStart(SimulationContext context) {
        playerDead = false;
        playerX = 0.0;
        playerY = 0.0;
        facingX = 0.0;
        facingY = 1.0;
        centerCameraOnPlayer();
        loadVisibleArea(context);
        Logger.log(Logger.TAG.INFO, "MazeSimulationGame: onStart seed=" + seed
                + " loadedPlacements=" + loadedArea.getPlacements().size());
    }

    @Override
    public void update(SimulationContext context, double deltaSeconds) {
        if (playerDead) {
            return;
        }
        updateEnvironmentalSanityEffect();
        updateTerrainEncumberedEffect();
        updateRunningEffect(deltaSeconds);
        updateNotifications(deltaSeconds);
        characterState.updateEffects(deltaSeconds, DungeonEffectLibrary.instance());
        updateRunExhaustion();
        if (characterState.isDead()) {
            markPlayerDead();
            return;
        }
        movePlayer(context, deltaSeconds);
        updateFacing(context);
        centerCameraOnPlayer();
        loadVisibleArea(context);
        closeOpenContainerIfOutOfRange();
        updateLoadedLightFuel(deltaSeconds);
        updateGasExposure(deltaSeconds);
        updateEnvironmentalSanityEffect();
        updateNoise(deltaSeconds);
        updateOwnershipAudit(deltaSeconds);
    }

    @Override
    public void render(SimulationContext context, Graphics2D graphics) {
        if (playerDead) {
            drawDeathScreen(context, graphics);
            return;
        }
        if (loadedArea == null) {
            return;
        }

        DungeonRect view = visibleWorldArea(context);
        ViewTransform transform = ViewTransform.from(context, cameraX, cameraY, pixelsPerBlock(context));
        drawOccupiedAreas(graphics, view, transform);

        graphics.setStroke(new BasicStroke(WALL_STROKE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(212, 214, 218));
        for (DungeonLine wall : loadedArea.getWallsIntersecting(view)) {
            drawLine(graphics, transform, wall);
        }

        graphics.setStroke(new BasicStroke(SEALED_WALL_STROKE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(238, 190, 92));
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(view)) {
            drawLine(graphics, transform, wall);
        }

        double elapsedSeconds = 0.0;
        List<LightVisibility> lights = List.of();
        if (adminMode) {
            drawHazardZones(graphics, view, transform, 1.0);
            drawMapItems(graphics, view, transform);
            drawOpeningNodes(graphics, view, transform);
            drawArtifactLabels(graphics, view, transform);
        } else {
            elapsedSeconds = (System.currentTimeMillis() - context.getCreatedAtMillis()) / 1000.0;
            lights = collectVisibleLights(expand(
                    view,
                    (int) Math.ceil(MAX_MAP_LIGHT_RADIUS_BLOCKS + LIGHT_FADE_RADIUS_BLOCKS)
            ), elapsedSeconds);
            drawVisibilityOverlay(graphics, context, transform, lights);
            drawVisibleHazardZones(graphics, view, transform, lights);
            drawVisibleMapItems(graphics, view, transform, lights, elapsedSeconds);
        }
        drawPlayer(graphics, transform);
        boolean gameplayInteractionBlocked = gameplayInteractionsBlocked();
        List<InteractionAction> interactionActions = gameplayInteractionBlocked ? List.of() : collectInteractionActions();
        HoveredItem hoveredItem = gameplayInteractionBlocked ? null : findHoveredItem(view, transform, lights, elapsedSeconds);
        drawInteractionOutlines(graphics, transform, interactionActions);
        drawHoveredItemText(context, graphics, hoveredItem);
        drawInteractionPrompt(context, graphics, interactionActions);
        drawNotifications(context, graphics);
        drawModeKeybinds(context, graphics);
        drawStatusBars(context, graphics);
        if (isContainerOpen()) {
            drawContainerOverlay(context, graphics);
        } else if (inventoryOpen) {
            drawInventoryOverlay(context, graphics);
        }
        drawDocumentOverlay(context, graphics);
    }

    @Override
    public void onKeyPressed(SimulationContext context, int keyCode) {
        if (playerDead) {
            return;
        }
        if (activeDocument == null && pendingKeyPlacement == null && keyCode == KeyEvent.VK_R) {
            dropHoveredGridItem(context);
            return;
        }
        if (activeDocument == null && pendingKeyPlacement == null && keyCode == KeyEvent.VK_E && tryUseHoveredGridItem(context)) {
            return;
        }
        if (tryUseEquippedHotkey(keyCode)) {
            return;
        }
        setMovementKey(keyCode, true);
    }

    @Override
    public void onKeyReleased(SimulationContext context, int keyCode) {
        if (playerDead) {
            return;
        }
        setMovementKey(keyCode, false);
    }

    @Override
    public void onMousePressed(SimulationContext context, int x, int y, int button) {
        mouseScreenX = x;
        mouseScreenY = y;
        mousePressX = x;
        mousePressY = y;
        if (playerDead && button == MouseEvent.BUTTON1 && deathQuitButton(context).contains(x, y)) {
            exit();
            return;
        }
        if (activeDocument != null) {
            return;
        }
        if (!playerDead && button == MouseEvent.BUTTON1) {
            if (pendingKeyPlacement != null && handlePendingKeyPlacementClick(context, x, y)) {
                return;
            }
            if (pendingOilUse != null && handleOilSelectionClick(context, x, y)) {
                return;
            }
            if (handleGridContextMenuClick(context, x, y)) {
                return;
            }
            if (shiftHeld && shiftTransferHoveredGridItem(context, x, y)) {
                return;
            }
            gridContextMenu = null;
            draggedGridItem = findGridItemAt(context, x, y);
        }
    }

    @Override
    public void onMouseReleased(SimulationContext context, int x, int y, int button) {
        mouseScreenX = x;
        mouseScreenY = y;
        if (activeDocument != null) {
            return;
        }
        if (button == MouseEvent.BUTTON1 && draggedGridItem != null) {
            if (isGridClick(x, y)) {
                openGridContextMenu(context, x, y, draggedGridItem);
            } else {
                completeGridDrag(context, x, y);
            }
            draggedGridItem = null;
        }
    }

    @Override
    public void onMouseMoved(SimulationContext context, int x, int y) {
        mouseScreenX = x;
        mouseScreenY = y;
    }

    @Override
    public void onMouseDragged(SimulationContext context, int x, int y, int button) {
        mouseScreenX = x;
        mouseScreenY = y;
    }

    @Override
    public void onStop(SimulationContext context) {
        loadedArea = null;
        visibilityOverlay = null;
        characterState.clearEffects();
        equipmentState.clear();
        inventory.clear();
        inventory.resize(DungeonEquipmentState.BASE_INVENTORY_WIDTH, DungeonEquipmentState.BASE_INVENTORY_HEIGHT);
        persistentItemStates.clear();
        containerPersistentStates.clear();
        notifications.clear();
        droppedWorldItems.clear();
        randomWorldItems.clear();
        randomWorldItemKeys.clear();
        randomWorldItemProperties.clear();
        generationService.clear();
        moveUp = false;
        moveLeft = false;
        moveDown = false;
        moveRight = false;
        shiftHeld = false;
        mouseScreenX = -1;
        mouseScreenY = -1;
        runningThisFrame = false;
        facingX = 0.0;
        facingY = 1.0;
        inventoryOpen = false;
        tabHeld = false;
        swapHeld = false;
        interactPrimaryHeld = false;
        interactSecondaryHeld = false;
        openContainerKey = null;
        openContainerItem = null;
        openContainerDefinition = null;
        draggedGridItem = null;
        gridContextMenu = null;
        pendingOilUse = null;
        pendingKeyPlacement = null;
        activeDocument = null;
        gasExposureSeconds = 0.0;
        ownershipAuditSeconds = 0.0;
        interactionNoiseLevel = 0.0;
        interactionNoiseRemaining = 0.0;
        mousePressX = -1;
        mousePressY = -1;
        runCooldownRemaining = 0.0;
        keyringScrollOffset = 0;
        Logger.log(Logger.TAG.INFO, "MazeSimulationGame: onStop.");
    }

    private void markPlayerDead() {
        playerDead = true;
        moveUp = false;
        moveLeft = false;
        moveDown = false;
        moveRight = false;
        shiftHeld = false;
        runningThisFrame = false;
    }

    private void setMovementKey(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.VK_W -> moveUp = pressed;
            case KeyEvent.VK_A -> moveLeft = pressed;
            case KeyEvent.VK_S -> moveDown = pressed;
            case KeyEvent.VK_D -> moveRight = pressed;
            case KeyEvent.VK_SHIFT -> shiftHeld = pressed;
            case KeyEvent.VK_TAB -> {
                if (pressed && !tabHeld) {
                    if (activeDocument != null) {
                        activeDocument = null;
                    } else if (pendingOilUse != null) {
                        pendingOilUse = null;
                    } else if (pendingKeyPlacement != null) {
                        cancelPendingKeyPlacement();
                    } else if (isContainerOpen()) {
                        closeContainerMenu();
                    } else {
                        inventoryOpen = !inventoryOpen;
                        if (!inventoryOpen) {
                            draggedGridItem = null;
                            gridContextMenu = null;
                        }
                    }
                }
                tabHeld = pressed;
            }
            case KeyEvent.VK_ESCAPE -> {
                if (pressed) {
                    if (activeDocument != null) {
                        activeDocument = null;
                    } else if (pendingKeyPlacement != null) {
                        cancelPendingKeyPlacement();
                    } else if (isContainerOpen()) {
                        closeContainerMenu();
                    } else if (inventoryOpen) {
                        inventoryOpen = false;
                        draggedGridItem = null;
                        gridContextMenu = null;
                    }
                    pendingOilUse = null;
                }
            }
            case KeyEvent.VK_E -> {
                if (pressed && !interactPrimaryHeld &&
                        pendingOilUse == null && !inventoryOpen && !isContainerOpen()) {
                    interactWithButton(InteractionButton.PRIMARY);
                }
                interactPrimaryHeld = pressed;
            }
            case KeyEvent.VK_Q -> {
                if (pressed && !interactSecondaryHeld &&
                        pendingOilUse == null && !inventoryOpen && !isContainerOpen()) {
                    interactWithButton(InteractionButton.SECONDARY);
                }
                interactSecondaryHeld = pressed;
            }
            case KeyEvent.VK_G -> {
                if (pressed && !swapHeld && pendingOilUse == null && !inventoryOpen && !isContainerOpen()) {
                    swapPrimaryWithFirstSecondary();
                }
                swapHeld = pressed;
            }
            default -> {
                // Ignore non-movement keys for now.
            }
        }
    }

    private void updateRunningEffect(double deltaSeconds) {
        runCooldownRemaining = Math.max(0.0, runCooldownRemaining - Math.max(0.0, deltaSeconds));
        if (adminMode || !shiftHeld || !movementRequested() ||
                runCooldownRemaining > 0.0 ||
                characterState.get(CharacterProperty.STAMINA) <= 0.0) {
            runningThisFrame = false;
            characterState.removeEffect("running");
            return;
        }

        runningThisFrame = true;
        characterState.setEffect(new ActiveCharacterEffect(
                "running",
                RUNNING_EFFECT_REFRESH_SECONDS,
                1.0,
                CharacterEffectMode.ADD
        ));
    }

    private void updateRunExhaustion() {
        if (!runningThisFrame || characterState.get(CharacterProperty.STAMINA) > 0.0) {
            return;
        }
        runningThisFrame = false;
        runCooldownRemaining = RUN_EXHAUSTION_COOLDOWN_SECONDS;
        characterState.removeEffect("running");
    }

    private void updateNoise(double deltaSeconds) {
        if (adminMode) {
            interactionNoiseLevel = 0.0;
            interactionNoiseRemaining = 0.0;
            characterState.setNoiseLevel(0.0);
            return;
        }
        interactionNoiseRemaining = Math.max(0.0, interactionNoiseRemaining - Math.max(0.0, deltaSeconds));
        if (interactionNoiseRemaining <= 0.0) {
            interactionNoiseLevel = 0.0;
        }
        double equipmentConstantNoise = equippedConstantNoise();
        double movementNoise = movementRequested()
                ? Math.max(0.0, (runningThisFrame ? RUN_NOISE : WALK_NOISE) +
                equippedMovementNoise() +
                terrainNoiseAt(playerX, playerY))
                : 0.0;
        characterState.setNoiseLevel(Math.max(Math.max(equipmentConstantNoise, movementNoise), interactionNoiseLevel));
    }

    private void addInteractionNoise(InteractionKind kind) {
        if (adminMode || kind == null) {
            return;
        }
        double amount = switch (kind) {
            case OPEN -> CONTAINER_INTERACTION_NOISE;
            case SEARCH -> SEARCH_INTERACTION_NOISE;
            case TOGGLE_LIGHT, FILL_LIGHT, TOGGLE, PULL, INSPECT, READ, SAVE, MENU -> SMALL_INTERACTION_NOISE;
            case PICK_UP -> 2.0;
            case PUSH -> PUSH_INTERACTION_NOISE;
            case LIGHT_BOMB -> 10.0;
            case BREAK -> 35.0;
        };
        setInteractionNoise(amount);
    }

    private void setInteractionNoise(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return;
        }
        interactionNoiseLevel = Math.max(interactionNoiseLevel, amount);
        interactionNoiseRemaining = INTERACTION_NOISE_HOLD_SECONDS;
        characterState.setNoiseLevel(Math.max(characterState.getNoiseLevel(), interactionNoiseLevel));
    }

    private boolean movementRequested() {
        return moveUp || moveDown || moveLeft || moveRight;
    }

    private void updateFacing(SimulationContext context) {
        if (context == null || gameplayInteractionsBlocked()) {
            return;
        }
        if (equipmentState.get(EquipmentSlot.PRIMARY).isPresent()) {
            updateFacingFromMouse(context);
        } else {
            updateFacingFromMovement();
        }
    }

    private void updateFacingFromMouse(SimulationContext context) {
        if (mouseScreenX < 0 || mouseScreenY < 0) {
            return;
        }
        ViewTransform transform = ViewTransform.from(context, cameraX, cameraY, pixelsPerBlock(context));
        double mouseWorldX = transform.screenToWorldX(mouseScreenX);
        double mouseWorldY = transform.screenToWorldY(mouseScreenY);
        setFacing(mouseWorldX - playerX, mouseWorldY - playerY);
    }

    private void updateFacingFromMovement() {
        double dx = 0.0;
        double dy = 0.0;
        if (moveUp) dy -= 1.0;
        if (moveDown) dy += 1.0;
        if (moveLeft) dx -= 1.0;
        if (moveRight) dx += 1.0;
        setFacing(dx, dy);
    }

    private void setFacing(double dx, double dy) {
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001) {
            return;
        }
        facingX = dx / length;
        facingY = dy / length;
    }

    private boolean gameplayInteractionsBlocked() {
        return inventoryOpen ||
                isContainerOpen() ||
                activeDocument != null ||
                pendingOilUse != null ||
                pendingKeyPlacement != null;
    }

    private double equippedConstantNoise() {
        double noise = 0.0;
        DungeonEquipmentLibrary library = DungeonEquipmentLibrary.instance();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isBodySlot()) {
                continue;
            }
            DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
            if (item == null) {
                continue;
            }
            DungeonEquipmentDefinition definition = library.find(item.itemId()).orElse(null);
            if (definition == null) {
                continue;
            }
            noise += Math.max(0.0, numericProperty(definition.defaultProperties(), "breathing_noise", 0.0));
        }
        return Math.max(0.0, Math.min(1000.0, noise));
    }

    private double equippedMovementNoise() {
        double noise = 0.0;
        DungeonEquipmentLibrary library = DungeonEquipmentLibrary.instance();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isBodySlot()) {
                continue;
            }
            DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
            if (item == null) {
                continue;
            }
            DungeonEquipmentDefinition definition = library.find(item.itemId()).orElse(null);
            if (definition == null) {
                continue;
            }
            noise += numericProperty(definition.defaultProperties(), "movement_noise", 0.0);
        }
        return Math.max(-1000.0, Math.min(1000.0, noise));
    }

    private List<InteractionTarget> collectInteractionTargets() {
        if (loadedArea == null) {
            return List.of();
        }
        DungeonRect view = expand(playerBounds(playerX, playerY), (int) Math.ceil(INTERACTION_RANGE_BLOCKS + 1.0));
        List<InteractionTarget> targets = new ArrayList<>();
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                addInteractionTarget(targets, item);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            addInteractionTarget(targets, item);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            addInteractionTarget(targets, item);
        }
        targets.sort(Comparator.comparingDouble(InteractionTarget::distance));
        return List.copyOf(targets);
    }

    private void addInteractionTarget(List<InteractionTarget> targets, DungeonItem item) {
        if (isPersistentDeleted(item)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (!isInteractable(definition)) {
            return;
        }
        double distance = distance(playerX, playerY, itemCellCenterX(item), itemCellCenterY(item));
        if (distance <= INTERACTION_RANGE_BLOCKS) {
            targets.add(new InteractionTarget(item, definition, distance));
        }
    }

    private boolean isInteractable(DungeonItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.isInteractable()) {
            return true;
        }
        Object interactable = definition.defaultProperties().get("interactable");
        return interactable instanceof Boolean value && value;
    }

    private List<InteractionAction> collectInteractionActions() {
        InteractionAction[] slots = new InteractionAction[2];
        for (InteractionTarget target : collectInteractionTargets()) {
            for (InteractionOption option : interactionOptions(target)) {
                int slot = preferredSlot(option.button(), slots);
                if (slot < 0) {
                    return actionSlots(slots);
                }
                slots[slot] = new InteractionAction(
                        slot == 0 ? InteractionButton.PRIMARY : InteractionButton.SECONDARY,
                        target,
                        option.label(),
                        option.kind()
                );
                if (slots[0] != null && slots[1] != null) {
                    return actionSlots(slots);
                }
            }
        }
        return actionSlots(slots);
    }

    private int preferredSlot(InteractionButton button, InteractionAction[] slots) {
        int preferred = button == InteractionButton.FLEXIBLE || button == InteractionButton.PRIMARY ? 0 : 1;
        if (slots[preferred] == null) {
            return preferred;
        }
        int fallback = preferred == 0 ? 1 : 0;
        return slots[fallback] == null ? fallback : -1;
    }

    private List<InteractionAction> actionSlots(InteractionAction[] slots) {
        List<InteractionAction> actions = new ArrayList<>(2);
        if (slots[0] != null) {
            actions.add(slots[0]);
        }
        if (slots[1] != null) {
            actions.add(slots[1]);
        }
        return List.copyOf(actions);
    }

    private List<InteractionOption> interactionOptions(InteractionTarget target) {
        DungeonItemDefinition definition = target.definition();
        if (definition == null) {
            return List.of();
        }
        String id = definition.id();
        List<InteractionOption> options = new ArrayList<>(2);

        if ("floor_lantern".equals(id)) {
            options.add(new InteractionOption(InteractionButton.PRIMARY, toggleLightLabel(target), InteractionKind.TOGGLE_LIGHT));
            options.add(new InteractionOption(InteractionButton.SECONDARY, "Pick up", InteractionKind.PICK_UP));
            return List.copyOf(options);
        }
        if (canToggleLight(definition)) {
            options.add(new InteractionOption(InteractionButton.PRIMARY, toggleLightLabel(target), InteractionKind.TOGGLE_LIGHT));
            if (DungeonItemLibrary.WALL_LIGHT.equals(id) && equippedLanternOil() != null) {
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Fill", InteractionKind.FILL_LIGHT));
            }
            return List.copyOf(options);
        }
        if (definition.isInteractable()) {
            options.add(new InteractionOption(InteractionButton.PRIMARY, "Pick up", InteractionKind.PICK_UP));
            if ("bomb".equals(id)) {
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Light bomb", InteractionKind.LIGHT_BOMB));
            } else if ("map_scrap".equals(id) || "note".equals(id)) {
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Read", InteractionKind.READ));
            }
            return List.copyOf(options);
        }

        switch (id) {
            case "chest", "barrel", "crate" -> {
                options.add(new InteractionOption(InteractionButton.PRIMARY, "Search", InteractionKind.OPEN));
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Push", InteractionKind.PUSH));
            }
            case "table", "bookshelf" -> {
                options.add(new InteractionOption(InteractionButton.PRIMARY, "Search", InteractionKind.SEARCH));
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Push", InteractionKind.PUSH));
            }
            case "empty_crate", "empty_table", "empty_bookshelf", "pallet" ->
                    options.add(new InteractionOption(InteractionButton.FLEXIBLE, "Push", InteractionKind.PUSH));
            case "bones", "rubble" ->
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Search", InteractionKind.SEARCH));
            case "smashed_lantern", "old_toolbox" ->
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Search", InteractionKind.SEARCH));
            case "cracked_pot" -> {
                options.add(new InteractionOption(InteractionButton.PRIMARY, canBreakWithPrimary(definition) ? "Break" : "Push",
                        canBreakWithPrimary(definition) ? InteractionKind.BREAK : InteractionKind.PUSH));
            }
            case "flower_pot" -> {
                if (canBreakWithPrimary(definition)) {
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Break", InteractionKind.BREAK));
                } else {
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Inspect", InteractionKind.INSPECT));
                }
            }
            case "broken_barrel", "broken_crate", "broken_shelf", "broken_table", "broken_chair" ->
                    options.add(new InteractionOption(InteractionButton.FLEXIBLE, "Push", InteractionKind.PUSH));
            case "wall_painting", "torn_banner", "gas_vent" ->
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Inspect", InteractionKind.INSPECT));
            case "broken_statue" ->
                    options.add(new InteractionOption(InteractionButton.PRIMARY, "Inspect", InteractionKind.INSPECT));
            case "save_table" -> {
                options.add(new InteractionOption(InteractionButton.PRIMARY, "Save", InteractionKind.SAVE));
                options.add(new InteractionOption(InteractionButton.SECONDARY, "Menu", InteractionKind.MENU));
            }
            case "lever" -> options.add(new InteractionOption(InteractionButton.PRIMARY, "Toggle", InteractionKind.TOGGLE));
            case "hanging_chains" -> options.add(new InteractionOption(InteractionButton.PRIMARY, "Pull", InteractionKind.PULL));
            default -> {
                // Some map objects are marked interactable for later systems but have no player action yet.
            }
        }
        return List.copyOf(options);
    }

    private String toggleLightLabel(InteractionTarget target) {
        if (!isItemOn(target.definition(), target.item()) && lightFuelRemaining(target.definition(), target.item()) <= 0.0) {
            return "Out of fuel";
        }
        return isItemOn(target.definition(), target.item()) ? "Turn off" : "Turn on";
    }

    private void interactWithButton(InteractionButton button) {
        List<InteractionAction> actions = collectInteractionActions();
        for (InteractionAction action : actions) {
            if (action.button() == button) {
                performInteraction(action);
                return;
            }
        }
    }

    private void performInteraction(InteractionAction action) {
        InteractionTarget target = action.target();
        addInteractionNoise(action.kind());
        switch (action.kind()) {
            case TOGGLE_LIGHT -> toggleLight(target.item(), target.definition());
            case FILL_LIGHT -> fillWorldLightFromEquippedOil(target);
            case PICK_UP -> pickupWorldItem(target);
            case OPEN -> openContainerMenu(target);
            case SEARCH -> {
                if (isContentContainer(target.definition())) {
                    openContainerMenu(target);
                } else if (hasPopOutDrop(target.definition())) {
                    searchPopOutDrop(target);
                } else {
                    Logger.log(Logger.TAG.INFO, "Interact placeholder: "
                            + target.definition().id() + " " + action.label());
                }
            }
            case READ -> openReadableWorldItem(target);
            case PUSH -> pushWorldItem(target);
            case BREAK -> breakWorldItem(target);
            case INSPECT -> inspectWorldItem(target);
            case LIGHT_BOMB, SAVE, MENU, TOGGLE, PULL ->
                    Logger.log(Logger.TAG.INFO, "Interact placeholder: "
                            + target.definition().id() + " " + action.label());
        }
    }

    private void inspectWorldItem(InteractionTarget target) {
        if (target == null || target.item() == null || target.definition() == null) {
            return;
        }
        String text = propertyString(currentItemProperties(target.definition(), target.item()), "inspect_text");
        addNotification(text.isBlank() ? "Nothing unusual." : text, Color.WHITE);
    }

    private boolean canBreakWithPrimary(DungeonItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        Map<String, Object> properties = definition.defaultProperties();
        if (!(properties.get("can_be_broken") instanceof Boolean canBreak) || !canBreak) {
            return false;
        }
        DungeonInventoryItem primary = equipmentState.get(EquipmentSlot.PRIMARY).orElse(null);
        if (primary == null) {
            return false;
        }
        Object tools = properties.get("break_tool_ids");
        return tools instanceof List<?> list && list.contains(primary.itemId());
    }

    private void pushWorldItem(InteractionTarget target) {
        if (target == null || target.item() == null || target.definition() == null || loadedArea == null) {
            return;
        }
        if (!isAdjacentToPlayer(target.item().position())) {
            addNotification("Move next to it first.", ERROR_PROMPT_COLOR);
            return;
        }
        String breaksInto = propertyString(currentItemProperties(target.definition(), target.item()), "breaks_into");
        if (!breaksInto.isBlank()) {
            replaceWorldItem(target.item(), breaksInto);
            addNotification(target.definition().name() + " broke apart.", Color.WHITE);
            return;
        }
        DungeonPoint offset = pushOffsetFromFacing();
        DungeonPoint targetCell = target.item().position().translate(offset);
        if (!canPushIntoCell(targetCell, target.item())) {
            addNotification("Cannot push in this direction.", ERROR_PROMPT_COLOR);
            return;
        }
        moveWorldItem(target.item(), target.definition(), targetCell);
    }

    private boolean isAdjacentToPlayer(DungeonPoint cell) {
        int playerCellX = (int) Math.floor(playerX);
        int playerCellY = (int) Math.floor(playerY);
        int dx = Math.abs(cell.x() - playerCellX);
        int dy = Math.abs(cell.y() - playerCellY);
        return dx + dy == 1;
    }

    private DungeonPoint pushOffsetFromFacing() {
        if (Math.abs(facingX) >= Math.abs(facingY)) {
            return new DungeonPoint(facingX >= 0.0 ? 1 : -1, 0);
        }
        return new DungeonPoint(0, facingY >= 0.0 ? 1 : -1);
    }

    private boolean canPushIntoCell(DungeonPoint cell, DungeonItem ignoredItem) {
        return isOccupiedCell(cell) &&
                !cellBlockedByWall(cell) &&
                !hasWorldItemAt(cell, ignoredItem);
    }

    private List<RandomPlacementCell> collectRandomPlacementCells(
            DungeonPlacedArtifact placement,
            boolean requireFlatWall
    ) {
        if (placement == null || loadedArea == null || !randomPlacementAllowed(placement)) {
            return List.of();
        }
        List<RandomPlacementCell> cells = new ArrayList<>();
        Set<DungeonPoint> seen = new HashSet<>();
        for (DungeonOccupiedArea occupied : placement.getWorldOccupiedAreas()) {
            DungeonRect bounds = occupied.getBounds();
            for (int y = bounds.minY(); y < bounds.maxY(); y++) {
                for (int x = bounds.minX(); x < bounds.maxX(); x++) {
                    DungeonPoint cell = new DungeonPoint(x, y);
                    if (!seen.add(cell)) {
                        continue;
                    }
                    if (!validRandomPlacementCell(cell)) {
                        continue;
                    }
                    List<DungeonDirection> wallDirections = flatWallDirectionsForCell(cell);
                    if (requireFlatWall && wallDirections.isEmpty()) {
                        continue;
                    }
                    cells.add(new RandomPlacementCell(placement, cell, wallDirections));
                }
            }
        }
        return List.copyOf(cells);
    }

    private boolean randomPlacementAllowed(DungeonPlacedArtifact placement) {
        int category = placement.getTemplate().getCategory();
        return category >= 1 && category <= 6;
    }

    private boolean randomPlacementRoomLike(DungeonPlacedArtifact placement) {
        int category = placement.getTemplate().getCategory();
        return category == 3 || category == 4;
    }

    private boolean validRandomPlacementCell(DungeonPoint cell) {
        double centerX = cell.x() + 0.5;
        double centerY = cell.y() + 0.5;
        return pointInsideOccupiedSpace(centerX, centerY) &&
                !cellBlockedByWall(cell) &&
                !hasWorldItemAt(cell);
    }

    private List<DungeonDirection> flatWallDirectionsForCell(DungeonPoint cell) {
        if (loadedArea == null) {
            return List.of();
        }
        List<DungeonDirection> directions = new ArrayList<>(4);
        DungeonRect bounds = new DungeonRect(cell.x() - 1, cell.y() - 1, cell.x() + 2, cell.y() + 2);
        for (DungeonLine wall : loadedArea.getWallsIntersecting(bounds)) {
            addFlatWallDirectionForCell(directions, cell, wall);
        }
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(bounds)) {
            addFlatWallDirectionForCell(directions, cell, wall);
        }
        return List.copyOf(directions);
    }

    private void addFlatWallDirectionForCell(List<DungeonDirection> directions, DungeonPoint cell, DungeonLine wall) {
        if (wall.start().y() == wall.end().y()) {
            addHorizontalWallDirectionForCell(directions, cell, wall);
        } else if (wall.start().x() == wall.end().x()) {
            addVerticalWallDirectionForCell(directions, cell, wall);
        }
    }

    private void addHorizontalWallDirectionForCell(List<DungeonDirection> directions, DungeonPoint cell, DungeonLine wall) {
        int wallY = wall.start().y();
        int minX = Math.min(wall.start().x(), wall.end().x());
        int maxX = Math.max(wall.start().x(), wall.end().x());
        if (maxX <= cell.x() || minX >= cell.x() + 1) {
            return;
        }
        if (wallY == cell.y() && randomFacingCellWalkable(cell, DungeonDirection.SOUTH)) {
            addUniqueDirection(directions, DungeonDirection.SOUTH);
        } else if (wallY == cell.y() + 1 && randomFacingCellWalkable(cell, DungeonDirection.NORTH)) {
            addUniqueDirection(directions, DungeonDirection.NORTH);
        }
    }

    private void addVerticalWallDirectionForCell(List<DungeonDirection> directions, DungeonPoint cell, DungeonLine wall) {
        int wallX = wall.start().x();
        int minY = Math.min(wall.start().y(), wall.end().y());
        int maxY = Math.max(wall.start().y(), wall.end().y());
        if (maxY <= cell.y() || minY >= cell.y() + 1) {
            return;
        }
        if (wallX == cell.x() && randomFacingCellWalkable(cell, DungeonDirection.EAST)) {
            addUniqueDirection(directions, DungeonDirection.EAST);
        } else if (wallX == cell.x() + 1 && randomFacingCellWalkable(cell, DungeonDirection.WEST)) {
            addUniqueDirection(directions, DungeonDirection.WEST);
        }
    }

    private boolean randomFacingCellWalkable(DungeonPoint cell, DungeonDirection direction) {
        DungeonPoint offset = directionOffset(direction);
        DungeonPoint facingCell = cell.translate(offset);
        return pointInsideOccupiedSpace(facingCell.x() + 0.5, facingCell.y() + 0.5) &&
                !cellBlockedByWall(facingCell);
    }

    private void addUniqueDirection(List<DungeonDirection> directions, DungeonDirection direction) {
        if (!directions.contains(direction)) {
            directions.add(direction);
        }
    }

    private void populateRandomLooseItems() {
        if (loadedArea == null) {
            return;
        }
        List<ContainerLootEntry> pool = looseItemLootPool();
        if (pool.isEmpty()) {
            return;
        }
        for (DungeonPlacedArtifact placement : loadedArea.getPlacements()) {
            if (!randomPlacementAllowed(placement)) {
                continue;
            }
            List<RandomPlacementCell> cells = collectRandomPlacementCells(placement, false);
            if (cells.isEmpty()) {
                continue;
            }
            int rolls = randomPlacementRoomLike(placement) ? 3 : 2;
            double chance = RANDOM_LOOSE_ITEM_CHANCE +
                    (randomPlacementRoomLike(placement) ? RANDOM_ROOM_ITEM_CHANCE_BONUS : 0.0);
            for (int rollIndex = 0; rollIndex < rolls; rollIndex++) {
                SplittableRandom random = new SplittableRandom(randomPlacementSeed(placement, "loose", rollIndex));
                if (random.nextDouble() >= chance) {
                    continue;
                }
                placeRandomLooseItem(placement, rollIndex, cells, pool, random);
            }
        }
    }

    private void populateRandomContainers() {
        if (loadedArea == null) {
            return;
        }
        List<RandomContainerEntry> pool = randomContainerPool();
        if (pool.isEmpty()) {
            return;
        }
        for (DungeonPlacedArtifact placement : loadedArea.getPlacements()) {
            if (!randomPlacementAllowed(placement)) {
                continue;
            }
            List<RandomPlacementCell> floorCells = collectRandomPlacementCells(placement, false);
            List<RandomPlacementCell> wallCells = collectRandomPlacementCells(placement, true);
            if (floorCells.isEmpty() && wallCells.isEmpty()) {
                continue;
            }
            int rolls = randomPlacementRoomLike(placement) ? 3 : 1;
            double chance = RANDOM_CONTAINER_CHANCE +
                    (randomPlacementRoomLike(placement) ? RANDOM_ROOM_CONTAINER_CHANCE_BONUS : 0.0);
            for (int rollIndex = 0; rollIndex < rolls; rollIndex++) {
                SplittableRandom random = new SplittableRandom(randomPlacementSeed(placement, "container", rollIndex));
                if (random.nextDouble() >= chance) {
                    continue;
                }
                placeRandomContainer(placement, rollIndex, floorCells, wallCells, pool, random);
            }
        }
    }

    private void populateRandomMapDetails() {
        populateRandomMapObjects(
                "detail",
                randomMapDetailPool(),
                RANDOM_DETAIL_CHANCE,
                RANDOM_ROOM_DETAIL_CHANCE_BONUS,
                2,
                4
        );
    }

    private void populateRandomHazards() {
        populateRandomMapObjects(
                "hazard",
                randomHazardPool(),
                RANDOM_HAZARD_CHANCE,
                RANDOM_ROOM_HAZARD_CHANCE_BONUS,
                1,
                2
        );
    }

    private void populateRandomMapObjects(
            String group,
            List<RandomMapItemEntry> pool,
            double baseChance,
            double roomChanceBonus,
            int normalRolls,
            int roomRolls
    ) {
        if (loadedArea == null || pool.isEmpty()) {
            return;
        }
        for (DungeonPlacedArtifact placement : loadedArea.getPlacements()) {
            if (!randomPlacementAllowed(placement)) {
                continue;
            }
            List<RandomPlacementCell> floorCells = collectRandomPlacementCells(placement, false);
            List<RandomPlacementCell> wallCells = collectRandomPlacementCells(placement, true);
            if (floorCells.isEmpty() && wallCells.isEmpty()) {
                continue;
            }
            boolean roomLike = randomPlacementRoomLike(placement);
            int rolls = roomLike ? roomRolls : normalRolls;
            double chance = baseChance + (roomLike ? roomChanceBonus : 0.0);
            for (int rollIndex = 0; rollIndex < rolls; rollIndex++) {
                SplittableRandom random = new SplittableRandom(randomPlacementSeed(placement, group, rollIndex));
                if (random.nextDouble() >= chance) {
                    continue;
                }
                placeRandomMapObject(placement, group, rollIndex, floorCells, wallCells, pool, random);
            }
        }
    }

    private void placeRandomMapObject(
            DungeonPlacedArtifact placement,
            String group,
            int rollIndex,
            List<RandomPlacementCell> floorCells,
            List<RandomPlacementCell> wallCells,
            List<RandomMapItemEntry> pool,
            SplittableRandom random
    ) {
        RandomMapItemEntry entry = chooseRandomMapItem(pool, random);
        if (entry == null || entry.definition() == null) {
            return;
        }
        boolean requiresFlatWall = randomContainerRequiresFlatWall(entry.definition());
        RandomPlacementCell cell = chooseRandomPlacementCell(requiresFlatWall ? wallCells : floorCells, random);
        if (cell == null) {
            return;
        }
        DungeonDirection direction = randomContainerDirection(entry.definition(), cell, random);
        DungeonItem item = new DungeonItem(entry.definition().id(), cell.cell(), direction);
        String key = randomPlacementKey(placement, group, rollIndex, item);
        addRandomWorldItem(key, item, randomMapObjectProperties(entry.definition(), random));
    }

    private RandomMapItemEntry chooseRandomMapItem(List<RandomMapItemEntry> pool, SplittableRandom random) {
        double total = 0.0;
        for (RandomMapItemEntry entry : pool) {
            total += entry.weight();
        }
        if (total <= 0.0) {
            return null;
        }
        double cursor = random.nextDouble(total);
        for (RandomMapItemEntry entry : pool) {
            cursor -= entry.weight();
            if (cursor <= 0.0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private Map<String, Object> randomMapObjectProperties(DungeonItemDefinition definition, SplittableRandom random) {
        Map<String, Object> properties = new HashMap<>();
        if (definition.defaultProperties().get("has_item") instanceof Boolean hasItem && hasItem && hasPopOutDrop(definition)) {
            double chance = Math.max(0.0, Math.min(1.0, numericProperty(definition.defaultProperties(), "drop_chance", 1.0)));
            properties.put("has_item", random.nextDouble() < chance);
            properties.put("drop_chance", 1.0);
        }
        return Map.copyOf(properties);
    }

    private void placeRandomContainer(
            DungeonPlacedArtifact placement,
            int rollIndex,
            List<RandomPlacementCell> floorCells,
            List<RandomPlacementCell> wallCells,
            List<RandomContainerEntry> pool,
            SplittableRandom random
    ) {
        RandomContainerEntry entry = chooseRandomContainer(pool, random);
        if (entry == null || entry.definition() == null) {
            return;
        }
        boolean requiresFlatWall = randomContainerRequiresFlatWall(entry.definition());
        RandomPlacementCell cell = chooseRandomPlacementCell(requiresFlatWall ? wallCells : floorCells, random);
        if (cell == null) {
            return;
        }
        DungeonDirection direction = randomContainerDirection(entry.definition(), cell, random);
        DungeonItem item = new DungeonItem(entry.definition().id(), cell.cell(), direction);
        String key = randomPlacementKey(placement, "container", rollIndex, item);
        addRandomWorldItem(key, item);
    }

    private RandomContainerEntry chooseRandomContainer(List<RandomContainerEntry> pool, SplittableRandom random) {
        double total = 0.0;
        for (RandomContainerEntry entry : pool) {
            total += entry.weight();
        }
        if (total <= 0.0) {
            return null;
        }
        double cursor = random.nextDouble(total);
        for (RandomContainerEntry entry : pool) {
            cursor -= entry.weight();
            if (cursor <= 0.0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private DungeonDirection randomContainerDirection(
            DungeonItemDefinition definition,
            RandomPlacementCell cell,
            SplittableRandom random
    ) {
        if (definition.defaultProperties().get("directional") instanceof Boolean directional && directional) {
            if (!cell.flatWallDirections().isEmpty()) {
                return cell.flatWallDirections().get(random.nextInt(cell.flatWallDirections().size()));
            }
            DungeonDirection[] directions = DungeonDirection.values();
            return directions[random.nextInt(directions.length)];
        }
        return DungeonDirection.NORTH;
    }

    private boolean randomContainerRequiresFlatWall(DungeonItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        Object requiresFlatWall = definition.defaultProperties().get("random_requires_flat_wall");
        return requiresFlatWall instanceof Boolean value && value;
    }

    private void placeRandomLooseItem(
            DungeonPlacedArtifact placement,
            int rollIndex,
            List<RandomPlacementCell> cells,
            List<ContainerLootEntry> pool,
            SplittableRandom random
    ) {
        ContainerLootEntry entry = chooseLoot(pool, random);
        DungeonCarryableDefinition carryable = resolveContainerLoot(entry, random);
        if (carryable == null) {
            return;
        }
        RandomPlacementCell cell = chooseRandomPlacementCell(cells, random);
        if (cell == null) {
            return;
        }
        DungeonItem item = new DungeonItem(carryable.id(), cell.cell(), DungeonDirection.NORTH);
        String key = randomPlacementKey(placement, "loose", rollIndex, item);
        Map<String, Object> properties = new HashMap<>(generatedLootProperties(carryable, random));
        int quantity = carryable.isEquipment() ? 1 : generatedStackQuantity(carryable.itemDefinition(), random);
        if (quantity > 1) {
            properties.put("quantity", quantity);
        }
        if (carryable.isItem() && carryable.itemDefinition().category() == DungeonItemCategory.LIGHT) {
            double maxFuel = numericProperty(carryable.itemDefinition().defaultProperties(), "fuel_remaining", MAX_LIGHT_FUEL);
            properties.put("fuel_remaining", Math.floor(random.nextDouble(Math.max(1.0, maxFuel + 1.0))));
            properties.put("is_on", false);
        }
        addRandomWorldItem(key, item, properties);
    }

    private RandomPlacementCell chooseRandomPlacementCell(List<RandomPlacementCell> cells, SplittableRandom random) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        int start = random.nextInt(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            RandomPlacementCell cell = cells.get((start + i) % cells.size());
            if (validRandomPlacementCell(cell.cell())) {
                return cell;
            }
        }
        return null;
    }

    private long randomPlacementSeed(DungeonPlacedArtifact placement, String group, int rollIndex) {
        long hash = itemSeed;
        hash = hash * 31L + placement.getPlacementIndex();
        hash = hash * 31L + placement.getTemplate().getId().hashCode();
        hash = hash * 31L + group.hashCode();
        hash = hash * 31L + rollIndex;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return hash;
    }

    private String randomPlacementKey(DungeonPlacedArtifact placement, String group, int rollIndex, DungeonItem item) {
        return "random:" +
                placement.getPlacementIndex() + ":" +
                group + ":" +
                rollIndex + ":" +
                item.id() + ":" +
                item.position().x() + ":" +
                item.position().y() + ":" +
                item.direction().name();
    }

    private void moveWorldItem(DungeonItem item, DungeonItemDefinition definition, DungeonPoint targetCell) {
        String oldContainerKey = itemStateKey(item);
        String oldPersistentKey = persistentItemStateKey(item);
        boolean movingOpenContainer = openContainerItem != null && itemStateKey(openContainerItem).equals(oldContainerKey);
        DungeonItem moved = new DungeonItem(item.id(), targetCell, item.direction());
        Map<String, Object> properties = currentItemProperties(definition, item);
        if (droppedWorldItems.remove(item)) {
            persistentItemStates.remove(oldPersistentKey);
        } else {
            persistentItemStates.put(oldPersistentKey, PersistentItemState.deletedState());
        }
        droppedWorldItems.add(moved);
        persistentItemStates.put(placedItemStateKey(moved), PersistentItemState.placedState(moved, properties));
        moveContainerPersistentState(oldContainerKey, moved);
        if (movingOpenContainer) {
            closeContainerMenu();
        }
    }

    private void breakWorldItem(InteractionTarget target) {
        if (target == null || target.item() == null || target.definition() == null) {
            return;
        }
        if (!canBreakWithPrimary(target.definition())) {
            addNotification("Equip the right tool first.", ERROR_PROMPT_COLOR);
            return;
        }
        tryPopOutDrop(target);
        replaceWorldItem(target.item(), "debris");
    }

    private boolean hasPopOutDrop(DungeonItemDefinition definition) {
        return definition != null &&
                (!propertyString(definition.defaultProperties(), "drop_item_id").isBlank() ||
                        definition.defaultProperties().get("drop_pool") instanceof List<?>);
    }

    private void searchPopOutDrop(InteractionTarget target) {
        if (target == null || target.item() == null || target.definition() == null) {
            return;
        }
        Map<String, Object> properties = currentItemProperties(target.definition(), target.item());
        if (properties.get("has_item") instanceof Boolean hasItem && !hasItem) {
            addNotification("Nothing useful.", Color.WHITE);
            return;
        }
        tryPopOutDrop(target);
        setPersistentProperty(target.item(), "has_item", false);
    }

    private void tryPopOutDrop(InteractionTarget target) {
        Map<String, Object> properties = currentItemProperties(target.definition(), target.item());
        SplittableRandom random = new SplittableRandom(itemSeed ^ itemStateKey(target.item()).hashCode());
        String dropItemId = choosePopOutDropItem(properties, random);
        if (dropItemId.isBlank()) {
            return;
        }
        double chance = Math.max(0.0, Math.min(1.0, numericProperty(properties, "drop_chance", 1.0)));
        if (random.nextDouble() > chance) {
            addNotification("Nothing useful.", Color.WHITE);
            return;
        }
        DungeonCarryableDefinition carryable = DungeonCarryableLibrary.instance().find(dropItemId).orElse(null);
        if (carryable == null) {
            addNotification("Nothing useful.", Color.WHITE);
            return;
        }
        DungeonPoint dropCell = chooseDropCell(new DropOrigin(
                target.item().position(),
                itemCellCenterX(target.item()),
                itemCellCenterY(target.item())
        ));
        if (dropCell == null) {
            addNotification("Can't drop item here.", ERROR_PROMPT_COLOR);
            return;
        }
        Map<String, Object> dropProperties = generatedLootProperties(carryable, random);
        dropInventoryItemIntoWorld(new DungeonInventoryItem(dropItemId, 0, 0, 1, dropProperties), carryable, dropCell);
    }

    private String choosePopOutDropItem(Map<String, Object> properties, SplittableRandom random) {
        Object pool = properties.get("drop_pool");
        if (!(pool instanceof List<?> entries) || entries.isEmpty()) {
            return propertyString(properties, "drop_item_id");
        }
        double total = 0.0;
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> map) {
                Object weight = map.get("weight");
                if (weight instanceof Number number) {
                    total += Math.max(0.0, number.doubleValue());
                }
            }
        }
        if (total <= 0.0) {
            return "";
        }
        double roll = random.nextDouble(total);
        double cursor = 0.0;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object weight = map.get("weight");
            double entryWeight = weight instanceof Number number ? Math.max(0.0, number.doubleValue()) : 0.0;
            cursor += entryWeight;
            if (roll <= cursor) {
                Object itemId = map.get("item_id");
                return itemId instanceof String text ? text.trim() : "";
            }
        }
        return "";
    }

    private void replaceWorldItem(DungeonItem item, String replacementId) {
        if (replacementId == null || replacementId.isBlank() || DungeonItemLibrary.find(replacementId).isEmpty()) {
            deleteWorldItem(item);
            return;
        }
        String oldPersistentKey = persistentItemStateKey(item);
        DungeonItem replacement = new DungeonItem(replacementId, item.position(), item.direction());
        if (droppedWorldItems.remove(item)) {
            persistentItemStates.remove(oldPersistentKey);
        } else {
            persistentItemStates.put(oldPersistentKey, PersistentItemState.deletedState());
        }
        droppedWorldItems.add(replacement);
        persistentItemStates.put(placedItemStateKey(replacement), PersistentItemState.placedState(replacement, Map.of()));
    }

    private void moveContainerPersistentState(String oldKey, DungeonItem moved) {
        ContainerPersistentState state = containerPersistentStates.remove(oldKey);
        if (state == null) {
            return;
        }
        String newKey = itemStateKey(moved);
        containerPersistentStates.put(newKey, state.withPosition(moved.position(), moved.direction()));
        if (oldKey.equals(openContainerKey)) {
            openContainerKey = newKey;
            openContainerItem = moved;
        }
    }

    private EquippedOil equippedLanternOil() {
        EquippedOil primary = equippedLanternOil(EquipmentSlot.PRIMARY);
        if (primary != null) {
            return primary;
        }
        int unlocked = equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance());
        for (int i = 0; i < unlocked; i++) {
            EquippedOil oil = equippedLanternOil(EquipmentSlot.secondarySlot(i));
            if (oil != null) {
                return oil;
            }
        }
        return null;
    }

    private EquippedOil equippedLanternOil(EquipmentSlot slot) {
        DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
        if (item == null || !"lantern_oil".equals(item.itemId())) {
            return null;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.itemId()).orElse(null);
        return definition == null ? null : new EquippedOil(slot, item, definition);
    }

    private void fillWorldLightFromEquippedOil(InteractionTarget target) {
        if (target == null || target.item() == null || target.definition() == null) {
            return;
        }
        EquippedOil oil = equippedLanternOil();
        if (oil == null) {
            addNotification("Equip lantern oil first.", ERROR_PROMPT_COLOR);
            return;
        }
        double oilAmount = numericProperty(oil.item().properties(), "fuel_amount",
                numericProperty(oil.definition().defaultProperties(), "fuel_amount", 0.0));
        if (oilAmount <= 0.0) {
            addNotification("Oil is empty.", ERROR_PROMPT_COLOR);
            return;
        }
        ensurePersistentLightState(target.definition(), target.item());
        double currentFuel = lightFuelRemaining(target.definition(), target.item());
        double maxFuel = numericProperty(target.definition().defaultProperties(), "burn_time", MAX_LIGHT_FUEL);
        if (currentFuel >= maxFuel) {
            addNotification("Lantern is full.", ERROR_PROMPT_COLOR);
            return;
        }
        setPersistentProperty(target.item(), "fuel_remaining", Math.min(maxFuel, currentFuel + oilAmount));
        consumeEquippedOil(oil);
    }

    private void consumeEquippedOil(EquippedOil oil) {
        DungeonInventoryItem item = oil.item();
        if (item.quantity() <= 1) {
            equipmentState.remove(oil.slot());
            return;
        }
        equipmentState.set(oil.slot(), new DungeonInventoryItem(
                item.itemId(),
                0,
                0,
                item.quantity() - 1,
                item.properties()
        ));
    }

    private void openReadableWorldItem(InteractionTarget target) {
        if (target == null || target.definition() == null || target.item() == null) {
            return;
        }
        activeDocument = readableDocumentView(
                target.definition(),
                currentItemProperties(target.definition(), target.item())
        );
    }

    private boolean isContainerOpen() {
        return openContainerKey != null && openContainerItem != null && openContainerDefinition != null;
    }

    private void openContainerMenu(InteractionTarget target) {
        if (!isContentContainer(target.definition())) {
            return;
        }
        openContainerItem = target.item();
        openContainerDefinition = target.definition();
        openContainerKey = itemStateKey(target.item());
        ensureContainerState(target.item(), target.definition());
        inventoryOpen = false;
        Logger.log(Logger.TAG.INFO, "Container opened: " + target.definition().id() + " key=" + openContainerKey);
    }

    private void closeContainerMenu() {
        openContainerKey = null;
        openContainerItem = null;
        openContainerDefinition = null;
        draggedGridItem = null;
        gridContextMenu = null;
        pendingOilUse = null;
    }

    private void closeOpenContainerIfOutOfRange() {
        if (!isContainerOpen()) {
            return;
        }
        if (isPersistentDeleted(openContainerItem) ||
                distance(playerX, playerY, itemCellCenterX(openContainerItem), itemCellCenterY(openContainerItem))
                        > INTERACTION_RANGE_BLOCKS) {
            closeContainerMenu();
        }
    }

    private boolean isContentContainer(DungeonItemDefinition definition) {
        if (definition == null || containerCapacity(definition.defaultProperties()) == null) {
            return false;
        }
        return "bones".equals(definition.id()) ||
                "rubble".equals(definition.id()) ||
                "chest".equals(definition.id()) ||
                "barrel".equals(definition.id()) ||
                "crate".equals(definition.id()) ||
                "bookshelf".equals(definition.id()) ||
                "table".equals(definition.id());
    }

    private ContainerPersistentState ensureContainerState(DungeonItem item, DungeonItemDefinition definition) {
        String key = itemStateKey(item);
        ContainerPersistentState existing = containerPersistentStates.get(key);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> properties = containerProperties(definition, item);
        ContainerPersistentState generated = ContainerPersistentState.empty(item)
                .withProperties(properties)
                .withContents(generateContainerContents(item, definition, properties));
        containerPersistentStates.put(key, generated);
        return generated;
    }

    private Map<String, Object> containerProperties(DungeonItemDefinition definition, DungeonItem item) {
        Map<String, Object> properties = new HashMap<>(definition.defaultProperties());
        PersistentItemState itemState = persistentItemStates.get(itemStateKey(item));
        if (itemState != null) {
            properties.putAll(itemState.properties());
        }
        return Map.copyOf(properties);
    }

    private List<DungeonInventoryItem> generateContainerContents(
            DungeonItem item,
            DungeonItemDefinition definition,
            Map<String, Object> containerProperties
    ) {
        DungeonItemSize capacity = containerCapacity(containerProperties);
        if (capacity == null) {
            return List.of();
        }
        SplittableRandom random = new SplittableRandom(containerSeed(item));
        int itemCount = generatedContainerItemCount(definition, random);
        if (itemCount <= 0) {
            return List.of();
        }

        List<ContainerLootEntry> lootPool = lootPool(definition);
        if (lootPool.isEmpty()) {
            return List.of();
        }
        List<DungeonInventoryItem> contents = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ContainerLootEntry loot = chooseLoot(lootPool, random);
            if (loot == null) {
                continue;
            }
            DungeonCarryableDefinition carryable = resolveContainerLoot(loot, random);
            if (carryable == null) {
                continue;
            }
            int quantity = carryable.isEquipment() ? 1 : generatedStackQuantity(carryable.itemDefinition(), random);
            DungeonInventoryItem placed = placeContainerItem(contents, capacity, carryable, quantity, random);
            if (placed != null) {
                contents.add(placed);
            }
        }
        return List.copyOf(contents);
    }

    private DungeonItemSize containerCapacity(Map<String, Object> properties) {
        Object capacity = properties.get("capacity");
        return capacity instanceof DungeonItemSize size ? size : null;
    }

    private long containerSeed(DungeonItem item) {
        long hash = itemSeed;
        hash = hash * 31L + item.id().hashCode();
        hash = hash * 31L + item.position().x();
        hash = hash * 31L + item.position().y();
        hash = hash * 31L + item.direction().ordinal();
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return hash;
    }

    private int generatedContainerItemCount(DungeonItemDefinition definition, SplittableRandom random) {
        int max = switch (definition.id()) {
            case "table" -> 2;
            case "bookshelf", "bones" -> 2;
            case "rubble" -> 3;
            case "chest" -> 3;
            case "barrel" -> 4;
            case "crate" -> 5;
            default -> 0;
        };
        if (max <= 0 || random.nextDouble() < 0.16) {
            return 0;
        }
        double centered = (random.nextDouble() + random.nextDouble()) * 0.5;
        return Math.max(1, Math.min(max, 1 + (int) Math.round(centered * (max - 1))));
    }

    private List<ContainerLootEntry> lootPool(DungeonItemDefinition containerDefinition) {
        List<ContainerLootEntry> pool = new ArrayList<>();
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.INTERACTABLE)) {
            if (lootWeight(definition) > 0.0 && definition.inventorySize() != null) {
                pool.add(ContainerLootEntry.item(definition, lootWeight(definition)));
            }
        }
        if (allowsEquipmentLoot(containerDefinition) &&
                DungeonEquipmentLibrary.instance().totalEquipmentWeight() > 0.0) {
            pool.add(ContainerLootEntry.equipmentTemplate(EQUIPMENT_TEMPLATE_LOOT_WEIGHT));
        }
        return List.copyOf(pool);
    }

    private List<ContainerLootEntry> looseItemLootPool() {
        List<ContainerLootEntry> pool = new ArrayList<>();
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.INTERACTABLE)) {
            if (definition.category() == DungeonItemCategory.KEY) {
                continue;
            }
            if (lootWeight(definition) > 0.0 && definition.inventorySize() != null) {
                pool.add(ContainerLootEntry.item(definition, lootWeight(definition)));
            }
        }
        DungeonItemLibrary.find("floor_lantern").ifPresent(definition -> pool.add(ContainerLootEntry.item(definition, 4.0)));
        if (DungeonEquipmentLibrary.instance().totalEquipmentWeight() > 0.0) {
            pool.add(ContainerLootEntry.equipmentTemplate(EQUIPMENT_TEMPLATE_LOOT_WEIGHT));
        }
        return List.copyOf(pool);
    }

    private List<RandomContainerEntry> randomContainerPool() {
        List<RandomContainerEntry> pool = new ArrayList<>();
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.MAP)) {
            if (definition.category() != DungeonItemCategory.CONTAINER) {
                continue;
            }
            pool.add(new RandomContainerEntry(definition, randomContainerWeight(definition)));
        }
        return List.copyOf(pool);
    }

    private double randomContainerWeight(DungeonItemDefinition definition) {
        if (definition == null) {
            return 0.0;
        }
        return switch (definition.id()) {
            case "barrel", "crate" -> 1.1;
            case "chest" -> 1.0;
            case "table" -> 0.8;
            case "bookshelf" -> 0.7;
            case "empty_crate", "empty_table", "empty_bookshelf" -> 0.6;
            default -> 0.5;
        };
    }

    private List<RandomMapItemEntry> randomMapDetailPool() {
        List<RandomMapItemEntry> pool = new ArrayList<>();
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.MAP)) {
            if (definition.category() != DungeonItemCategory.DETAIL) {
                continue;
            }
            pool.add(new RandomMapItemEntry(definition, randomMapDetailWeight(definition)));
        }
        return List.copyOf(pool);
    }

    private double randomMapDetailWeight(DungeonItemDefinition definition) {
        if (definition == null) {
            return 0.0;
        }
        return switch (definition.id()) {
            case "bloodstain", "scratch_marks", "loose_papers", "ash_pile", "scorch_mark",
                    "moss_patch", "fungus_patch" -> 1.25;
            case "debris", "wood_sticks", "discarded_cloth", "torn_bag", "rusted_chain_pile",
                    "gold_mark" -> 1.0;
            case "bones", "pallet", "hanging_chains", "broken_chair", "loose_bricks" -> 0.75;
            case "rubble", "broken_barrel", "broken_crate", "broken_table", "cracked_pot",
                    "flower_pot" -> 0.55;
            case "wall_painting", "torn_banner", "broken_shelf" -> 0.45;
            case "smashed_lantern", "old_toolbox", "broken_statue" -> 0.25;
            default -> 0.5;
        };
    }

    private List<RandomMapItemEntry> randomHazardPool() {
        List<RandomMapItemEntry> pool = new ArrayList<>();
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.MAP)) {
            if (definition.category() != DungeonItemCategory.HAZARD) {
                continue;
            }
            pool.add(new RandomMapItemEntry(definition, randomHazardWeight(definition)));
        }
        return List.copyOf(pool);
    }

    private double randomHazardWeight(DungeonItemDefinition definition) {
        if (definition == null) {
            return 0.0;
        }
        return switch (definition.id()) {
            case "water_puddle" -> 1.3;
            case "oil_puddle" -> 1.0;
            case "steam_vent" -> 0.32;
            case "gas_vent" -> 0.22;
            default -> 0.35;
        };
    }

    private boolean allowsEquipmentLoot(DungeonItemDefinition containerDefinition) {
        if (containerDefinition == null) {
            return false;
        }
        return !"table".equals(containerDefinition.id()) &&
                !"rubble".equals(containerDefinition.id()) &&
                !"bookshelf".equals(containerDefinition.id());
    }

    private ContainerLootEntry chooseLoot(List<ContainerLootEntry> pool, SplittableRandom random) {
        double total = 0.0;
        for (ContainerLootEntry entry : pool) {
            total += entry.weight();
        }
        if (total <= 0.0) {
            return null;
        }
        double cursor = random.nextDouble(total);
        for (ContainerLootEntry entry : pool) {
            cursor -= entry.weight();
            if (cursor <= 0.0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private DungeonCarryableDefinition resolveContainerLoot(ContainerLootEntry entry, SplittableRandom random) {
        if (entry == null) {
            return null;
        }
        if (!entry.equipmentTemplate()) {
            return DungeonCarryableDefinition.item(entry.itemDefinition());
        }
        DungeonEquipmentDefinition equipment = chooseEquipmentLoot(random);
        return equipment == null ? null : DungeonCarryableDefinition.equipment(equipment);
    }

    private DungeonEquipmentDefinition chooseEquipmentLoot(SplittableRandom random) {
        DungeonEquipmentLibrary library = DungeonEquipmentLibrary.instance();
        double total = library.totalEquipmentWeight();
        if (total <= 0.0) {
            return null;
        }
        double cursor = random.nextDouble(total);
        for (DungeonEquipmentDefinition definition : library.definitions()) {
            cursor -= Math.max(0.0, definition.equipmentWeight());
            if (cursor <= 0.0) {
                return definition;
            }
        }
        List<DungeonEquipmentDefinition> definitions = library.definitions();
        return definitions.isEmpty() ? null : definitions.get(definitions.size() - 1);
    }

    private double lootWeight(DungeonItemDefinition definition) {
        Object value = definition.defaultProperties().get("loot_weight");
        return value instanceof Number number ? Math.max(0.0, number.doubleValue()) : 0.0;
    }

    private int generatedStackQuantity(DungeonItemDefinition definition, SplittableRandom random) {
        int stackLimit = stackLimit(definition);
        if (stackLimit <= 1) {
            return 1;
        }
        int quantity = 1;
        while (quantity < stackLimit && random.nextDouble() < 0.35) {
            quantity++;
        }
        return quantity;
    }

    private int stackLimit(DungeonItemDefinition definition) {
        Object value = definition.defaultProperties().get("can_stack");
        return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    private int stackLimit(DungeonCarryableDefinition definition) {
        if (definition == null || definition.isEquipment()) {
            return 1;
        }
        return stackLimit(definition.itemDefinition());
    }

    private DungeonInventoryItem placeContainerItem(
            List<DungeonInventoryItem> existing,
            DungeonItemSize capacity,
            DungeonCarryableDefinition definition,
            int quantity,
            SplittableRandom random
    ) {
        DungeonItemSize size = definition.inventorySize();
        if (size == null || size.width() > capacity.width() || size.height() > capacity.height()) {
            return null;
        }
        int maxX = capacity.width() - size.width();
        int maxY = capacity.height() - size.height();
        Map<String, Object> properties = generatedLootProperties(definition, random);
        for (int i = 0; i < 24; i++) {
            DungeonInventoryItem candidate = new DungeonInventoryItem(
                    definition.id(),
                    random.nextInt(maxX + 1),
                    random.nextInt(maxY + 1),
                    quantity,
                    properties
            );
            if (canPlaceContainerItem(existing, candidate, definition)) {
                return candidate;
            }
        }
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                DungeonInventoryItem candidate = new DungeonInventoryItem(
                        definition.id(),
                        x,
                        y,
                        quantity,
                        properties
                );
                if (canPlaceContainerItem(existing, candidate, definition)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Map<String, Object> generatedLootProperties(DungeonItemDefinition definition, SplittableRandom random) {
        Map<String, Object> properties = new HashMap<>(definition.defaultProperties());
        if ("lantern_oil".equals(definition.id())) {
            double min = numericProperty(properties, "fuel_amount_min", 200.0);
            double max = numericProperty(properties, "fuel_amount_max", 1000.0);
            if (max < min) {
                double temp = min;
                min = max;
                max = temp;
            }
            properties.put("fuel_amount", Math.round(min + random.nextDouble() * (max - min)));
        }
        return Map.copyOf(properties);
    }

    private Map<String, Object> generatedLootProperties(
            DungeonCarryableDefinition definition,
            SplittableRandom random
    ) {
        if (definition.isItem()) {
            return generatedLootProperties(definition.itemDefinition(), random);
        }
        return Map.copyOf(definition.defaultProperties());
    }

    private boolean canPlaceContainerItem(
            List<DungeonInventoryItem> existing,
            DungeonInventoryItem candidate,
            DungeonCarryableDefinition candidateDefinition
    ) {
        DungeonItemSize candidateSize = candidateDefinition.inventorySize();
        if (candidateSize == null) {
            return false;
        }
        for (DungeonInventoryItem item : existing) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition != null &&
                    definition.inventorySize() != null &&
                    inventoryItemsOverlap(candidate, candidateSize, item, definition.inventorySize())) {
                return false;
            }
        }
        return true;
    }

    private boolean inventoryItemsOverlap(
            DungeonInventoryItem a,
            DungeonItemSize aSize,
            DungeonInventoryItem b,
            DungeonItemSize bSize
    ) {
        return a.x() < b.x() + bSize.width() &&
                a.x() + aSize.width() > b.x() &&
                a.y() < b.y() + bSize.height() &&
                a.y() + aSize.height() > b.y();
    }

    private boolean isPickupableMapItem(DungeonItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        Object pickupable = definition.defaultProperties().get("pickupable");
        return pickupable instanceof Boolean value && value;
    }

    private void pickupWorldItem(InteractionTarget target) {
        if (!target.definition().isInteractable() && !isPickupableMapItem(target.definition())) {
            return;
        }
        Map<String, Object> properties = currentItemProperties(target.definition(), target.item());
        int quantity = currentItemQuantity(target.item());
        if (inventory.addNextAvailable(target.item().id(), quantity, properties)) {
            deleteWorldItem(target.item());
        } else {
            addNotification("Your inventory is full.", ERROR_PROMPT_COLOR);
        }
    }

    private int currentItemQuantity(DungeonItem item) {
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        if (state == null) {
            Object randomQuantity = randomWorldItemProperties.getOrDefault(item, Map.of()).get("quantity");
            return randomQuantity instanceof Number number ? Math.max(1, number.intValue()) : 1;
        }
        Object value = state.properties().get("quantity");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        Object randomQuantity = randomWorldItemProperties.getOrDefault(item, Map.of()).get("quantity");
        return randomQuantity instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    private void deleteWorldItem(DungeonItem item) {
        String key = persistentItemStateKey(item);
        if (droppedWorldItems.remove(item)) {
            persistentItemStates.remove(key);
        } else {
            persistentItemStates.put(key, PersistentItemState.deletedState());
        }
    }

    private boolean canToggleLight(DungeonItemDefinition definition) {
        return definition != null &&
                definition.category() == DungeonItemCategory.LIGHT &&
                !isFlickeringLight(definition) &&
                definition.defaultProperties().containsKey("is_on");
    }

    private void toggleLight(DungeonItem item, DungeonItemDefinition definition) {
        boolean currentlyOn = isItemOn(definition, item);
        if (!currentlyOn && lightFuelRemaining(definition, item) <= 0.0) {
            return;
        }
        ensurePersistentLightState(definition, item);
        setPersistentProperty(item, "is_on", !currentlyOn);
    }

    private boolean isPersistentDeleted(DungeonItem item) {
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        return state != null && state.deleted();
    }

    private void markPersistentDeleted(DungeonItem item) {
        String key = persistentItemStateKey(item);
        persistentItemStates.put(key, PersistentItemState.deletedState());
    }

    private void setPersistentProperty(DungeonItem item, String property, Object value) {
        String key = persistentItemStateKey(item);
        PersistentItemState state = persistentItemStates.getOrDefault(key, PersistentItemState.empty());
        PersistentItemState updated = state.withProperty(property, value);
        persistentItemStates.put(key, updated);
    }

    private Boolean persistentBooleanProperty(DungeonItem item, String property) {
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        if (state == null) {
            return null;
        }
        Object value = state.properties().get(property);
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }

    private Double persistentNumberProperty(DungeonItem item, String property) {
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        if (state == null) {
            return null;
        }
        Object value = state.properties().get(property);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Map<String, Object> currentItemProperties(DungeonItemDefinition definition, DungeonItem item) {
        Map<String, Object> properties = new HashMap<>(definition.defaultProperties());
        if (definition.category() == DungeonItemCategory.LIGHT) {
            properties.put("fuel_remaining", lightFuelRemaining(definition, item));
            properties.put("is_on", isItemOn(definition, item));
        }
        properties.putAll(randomWorldItemProperties.getOrDefault(item, Map.of()));
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        if (state != null) {
            properties.putAll(state.properties());
        }
        properties.remove("quantity");
        return Map.copyOf(properties);
    }

    private void initializeLightState(DungeonItemDefinition definition, DungeonItem item) {
        if (!isMapLight(definition) || isPersistentDeleted(item) || !shouldPersistSeededLight(definition, item)) {
            return;
        }
        ensurePersistentLightState(definition, item);
    }

    private boolean shouldPersistSeededLight(DungeonItemDefinition definition, DungeonItem item) {
        PersistentItemState state = persistentItemStates.get(persistentItemStateKey(item));
        if (state != null && !state.properties().isEmpty()) {
            return true;
        }
        Map<String, Object> randomProperties = randomWorldItemProperties.get(item);
        if (randomProperties != null && !randomProperties.isEmpty()) {
            return booleanProperty(randomProperties, "is_on", false) &&
                    numericProperty(randomProperties, "fuel_remaining", 0.0) > 0.0;
        }
        return seededInitialIsOn(definition, item) && seededInitialFuel(item) > 0.0;
    }

    private void ensurePersistentLightState(DungeonItemDefinition definition, DungeonItem item) {
        if (!isMapLight(definition) || isPersistentDeleted(item)) {
            return;
        }
        String key = persistentItemStateKey(item);
        PersistentItemState state = persistentItemStates.getOrDefault(key, PersistentItemState.empty());
        boolean changed = false;
        Map<String, Object> properties = new HashMap<>(state.properties());
        Map<String, Object> randomProperties = randomWorldItemProperties.getOrDefault(item, Map.of());
        if (!properties.containsKey("fuel_remaining")) {
            properties.put("fuel_remaining", numericProperty(randomProperties, "fuel_remaining", seededInitialFuel(item)));
            changed = true;
        }
        if (!properties.containsKey("is_on")) {
            boolean isOn = booleanProperty(randomProperties, "is_on", seededInitialIsOn(definition, item)) &&
                    numericProperty(properties, "fuel_remaining", 0.0) > 0.0;
            properties.put("is_on", isOn);
            changed = true;
        }
        if (numericProperty(properties, "fuel_remaining", 0.0) <= 0.0 &&
                !Boolean.FALSE.equals(properties.get("is_on"))) {
            properties.put("is_on", false);
            changed = true;
        }
        if (changed) {
            PersistentItemState updated = state.withProperties(properties);
            persistentItemStates.put(key, updated);
        }
    }

    private void updateLoadedLightFuel(double deltaSeconds) {
        if (loadedArea == null || deltaSeconds <= 0.0) {
            return;
        }
        for (DungeonPlacedArtifact placement : loadedArea.getPlacements()) {
            for (DungeonItem item : placement.getWorldItems()) {
                updateLightFuel(item, deltaSeconds);
            }
        }
        for (DungeonItem item : droppedWorldItems) {
            updateLightFuel(item, deltaSeconds);
        }
        for (DungeonItem item : randomWorldItems) {
            updateLightFuel(item, deltaSeconds);
        }
        updateEquippedLanternFuel(deltaSeconds);
    }

    private void updateEquippedLanternFuel(double deltaSeconds) {
        updateEquippedLanternFuel(EquipmentSlot.PRIMARY, deltaSeconds);
        if (equipmentState.secondaryLightEnabled(DungeonEquipmentLibrary.instance())) {
            updateEquippedLanternFuel(EquipmentSlot.SECONDARY_1, deltaSeconds);
        }
    }

    private void updateEquippedLanternFuel(EquipmentSlot slot, double deltaSeconds) {
        DungeonInventoryItem lantern = equipmentState.get(slot).orElse(null);
        if (!isEquippedLightOn(lantern)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(lantern.itemId()).orElse(null);
        if (definition == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>(lantern.properties());
        double fuel = equippedLightFuelRemaining(lantern, definition);
        double updated = Math.max(0.0, fuel - deltaSeconds);
        properties.put("fuel_remaining", updated);
        if (updated <= 0.0) {
            properties.put("is_on", false);
        }
        equipmentState.set(slot, new DungeonInventoryItem(
                lantern.itemId(),
                0,
                0,
                lantern.quantity(),
                Map.copyOf(properties)
        ));
    }

    private void updateLightFuel(DungeonItem item, double deltaSeconds) {
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (!isMapLight(definition) || isPersistentDeleted(item)) {
            return;
        }
        initializeLightState(definition, item);
        if (!isItemOn(definition, item)) {
            return;
        }
        double fuel = lightFuelRemaining(definition, item);
        double updated = Math.max(0.0, fuel - deltaSeconds);
        setPersistentProperty(item, "fuel_remaining", updated);
        if (updated <= 0.0) {
            setPersistentProperty(item, "is_on", false);
        }
    }

    private void updateGasExposure(double deltaSeconds) {
        if (loadedArea == null || deltaSeconds <= 0.0 || adminMode) {
            gasExposureSeconds = 0.0;
            characterState.removeEffect("toxic_exposure");
            characterState.removeEffect("asphyxiation");
            return;
        }
        PlayerHazardExposure exposure = playerHazardExposure();
        if (!exposure.toxic() && !exposure.asphyxiation()) {
            gasExposureSeconds = 0.0;
            characterState.removeEffect("toxic_exposure");
            characterState.removeEffect("asphyxiation");
            return;
        }

        gasExposureSeconds += Math.max(0.0, deltaSeconds);
        if (exposure.toxic()) {
            characterState.addEffect(new ActiveCharacterEffect(
                    "toxic_exposure",
                    GAS_EFFECT_REFRESH_SECONDS,
                    1.0,
                    CharacterEffectMode.ADD
            ));
        } else {
            characterState.removeEffect("toxic_exposure");
        }
        if (gasExposureSeconds >= GAS_ASPHYXIATION_DELAY_SECONDS) {
            double rampSeconds = gasExposureSeconds - GAS_ASPHYXIATION_DELAY_SECONDS;
            double strength = Math.min(4.0, 1.0 + rampSeconds / 20.0);
            characterState.addEffect(new ActiveCharacterEffect(
                    "asphyxiation",
                    GAS_EFFECT_REFRESH_SECONDS,
                    strength,
                    CharacterEffectMode.ADD
            ));
        } else {
            characterState.removeEffect("asphyxiation");
        }
    }

    private PlayerHazardExposure playerHazardExposure() {
        DungeonRect check = new DungeonRect(
                (int) Math.floor(playerX) - 3,
                (int) Math.floor(playerY) - 3,
                (int) Math.ceil(playerX) + 3,
                (int) Math.ceil(playerY) + 3
        );
        boolean toxic = false;
        boolean asphyxiation = false;
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(check)) {
            for (DungeonItem item : placement.getWorldItems()) {
                PlayerHazardExposure itemExposure = hazardExposureForPlayer(item);
                toxic = toxic || itemExposure.toxic();
                asphyxiation = asphyxiation || itemExposure.asphyxiation();
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(check)) {
            PlayerHazardExposure itemExposure = hazardExposureForPlayer(item);
            toxic = toxic || itemExposure.toxic();
            asphyxiation = asphyxiation || itemExposure.asphyxiation();
        }
        for (DungeonItem item : randomItemsIntersecting(check)) {
            PlayerHazardExposure itemExposure = hazardExposureForPlayer(item);
            toxic = toxic || itemExposure.toxic();
            asphyxiation = asphyxiation || itemExposure.asphyxiation();
        }
        return new PlayerHazardExposure(toxic, asphyxiation);
    }

    private PlayerHazardExposure hazardExposureForPlayer(DungeonItem item) {
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (!isActiveHazardZone(definition, item) || isPersistentDeleted(item)) {
            return new PlayerHazardExposure(false, false);
        }
        Rectangle2D.Double area = hazardArea(item, definition);
        if (!area.contains(playerX, playerY)) {
            return new PlayerHazardExposure(false, false);
        }
        String effectId = propertyString(currentItemProperties(definition, item), "effect_id");
        return new PlayerHazardExposure(
                "toxic_exposure".equals(effectId) || "gas".equals(effectId),
                "asphyxiation".equals(effectId) || "choking".equals(effectId)
        );
    }

    private double seededInitialFuel(DungeonItem item) {
        return Math.floor(seededUnit(item, "light_fuel") * (MAX_LIGHT_FUEL + 1.0));
    }

    private boolean seededInitialIsOn(DungeonItemDefinition definition, DungeonItem item) {
        Object isOn = definition.defaultProperties().get("is_on");
        if (isOn instanceof Boolean value && !value) {
            return false;
        }
        return seededUnit(item, "light_on") < spawnOnChance(definition);
    }

    private boolean isMapLight(DungeonItemDefinition definition) {
        return definition != null && definition.isMapBased() && definition.category() == DungeonItemCategory.LIGHT;
    }

    private double lightFuelRemaining(DungeonItemDefinition definition, DungeonItem item) {
        Double fuel = persistentNumberProperty(item, "fuel_remaining");
        if (fuel != null) {
            return Math.max(0.0, fuel);
        }
        Object randomFuel = randomWorldItemProperties.getOrDefault(item, Map.of()).get("fuel_remaining");
        if (randomFuel instanceof Number number) {
            return Math.max(0.0, number.doubleValue());
        }
        return Math.max(0.0, seededInitialFuel(item));
    }

    private double numericProperty(Map<String, Object> properties, String key, double fallback) {
        Object value = properties.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean booleanProperty(Map<String, Object> properties, String key, boolean fallback) {
        Object value = properties.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private String propertyString(Map<String, Object> properties, String key) {
        if (properties == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = properties.get(key);
        return value instanceof String text ? text.trim() : "";
    }

    private void addNotification(String text, Color color) {
        if (text == null || text.isBlank()) {
            return;
        }
        while (notifications.size() >= MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }
        notifications.add(new Notification(
                text.trim(),
                color == null ? Color.WHITE : color,
                NOTIFICATION_DURATION_SECONDS
        ));
    }

    private void updateNotifications(double deltaSeconds) {
        double elapsed = Math.max(0.0, deltaSeconds);
        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notification = notifications.get(i);
            double remaining = notification.remainingSeconds() - elapsed;
            if (remaining <= 0.0) {
                notifications.remove(i);
            } else {
                notifications.set(i, notification.withRemainingSeconds(remaining));
            }
        }
    }

    private void movePlayer(SimulationContext context, double deltaSeconds) {
        double dx = 0.0;
        double dy = 0.0;
        if (moveUp) dy -= 1.0;
        if (moveDown) dy += 1.0;
        if (moveLeft) dx -= 1.0;
        if (moveRight) dx += 1.0;
        if (dx == 0.0 && dy == 0.0) {
            return;
        }

        double length = Math.sqrt(dx * dx + dy * dy);
        double speed = adminMode
                ? (shiftHeld ? ADMIN_SHIFT_SPEED : ADMIN_SPEED)
                : (runningThisFrame ? PLAYER_SHIFT_SPEED : PLAYER_SPEED);
        speed *= Math.max(0.0, characterState.get(CharacterProperty.MOVEMENT_SPEED));
        double distance = speed * deltaSeconds;
        double stepX = dx / length * distance;
        double stepY = dy / length * distance;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(stepX), Math.abs(stepY)) / MAX_PLAYER_COLLISION_STEP));
        double subStepX = stepX / steps;
        double subStepY = stepY / steps;
        for (int i = 0; i < steps; i++) {
            movePlayerStep(context, subStepX, subStepY);
        }
    }

    private void movePlayerStep(SimulationContext context, double stepX, double stepY) {
        loadAreaForPlayerCollision(context, stepX, stepY);
        if (adminMode) {
            playerX += stepX;
            playerY += stepY;
            return;
        }
        if (!collidesAt(playerX + stepX, playerY + stepY)) {
            playerX += stepX;
            playerY += stepY;
            return;
        }
        if (slideAlongWall(stepX, stepY)) {
            return;
        }
        if (!collidesAt(playerX + stepX, playerY)) {
            playerX += stepX;
        }
        if (!collidesAt(playerX, playerY + stepY)) {
            playerY += stepY;
        }
    }

    private double terrainSpeedMultiplierAt(double worldX, double worldY) {
        if (loadedArea == null) {
            return 1.0;
        }
        DungeonPoint cell = new DungeonPoint((int) Math.floor(worldX), (int) Math.floor(worldY));
        double multiplier = 1.0;
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(new DungeonRect(
                cell.x(),
                cell.y(),
                cell.x() + 1,
                cell.y() + 1
        ))) {
            for (DungeonItem item : placement.getWorldItems()) {
                multiplier = Math.min(multiplier, terrainSpeedMultiplierForItem(item, cell));
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(new DungeonRect(
                cell.x(),
                cell.y(),
                cell.x() + 1,
                cell.y() + 1
        ))) {
            multiplier = Math.min(multiplier, terrainSpeedMultiplierForItem(item, cell));
        }
        for (DungeonItem item : randomItemsIntersecting(new DungeonRect(
                cell.x(),
                cell.y(),
                cell.x() + 1,
                cell.y() + 1
        ))) {
            multiplier = Math.min(multiplier, terrainSpeedMultiplierForItem(item, cell));
        }
        return Math.max(0.1, Math.min(1.0, multiplier));
    }

    private void updateTerrainEncumberedEffect() {
        if (adminMode || loadedArea == null) {
            characterState.removeEffect("encumbered");
            return;
        }
        double multiplier = terrainSpeedMultiplierAt(playerX, playerY);
        if (multiplier < 0.999) {
            characterState.setEffect(new ActiveCharacterEffect(
                    "encumbered",
                    TERRAIN_EFFECT_REFRESH_SECONDS,
                    multiplier,
                    CharacterEffectMode.SET
            ));
        } else {
            characterState.removeEffect("encumbered");
        }
    }

    private double terrainSpeedMultiplierForItem(DungeonItem item, DungeonPoint cell) {
        if (item == null || isPersistentDeleted(item)) {
            return 1.0;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (definition == null) {
            return 1.0;
        }
        if (!terrainArea(item, definition).contains(cell.x() + 0.5, cell.y() + 0.5)) {
            return 1.0;
        }
        return Math.max(0.1, Math.min(1.0, numericProperty(
                currentItemProperties(definition, item),
                "slow_multiplier",
                1.0
        )));
    }

    private double terrainNoiseAt(double worldX, double worldY) {
        if (loadedArea == null) {
            return 0.0;
        }
        DungeonPoint cell = new DungeonPoint((int) Math.floor(worldX), (int) Math.floor(worldY));
        double noise = 0.0;
        DungeonRect area = new DungeonRect(cell.x(), cell.y(), cell.x() + 1, cell.y() + 1);
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(area)) {
            for (DungeonItem item : placement.getWorldItems()) {
                noise = Math.max(noise, terrainNoiseForItem(item, cell));
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(area)) {
            noise = Math.max(noise, terrainNoiseForItem(item, cell));
        }
        for (DungeonItem item : randomItemsIntersecting(area)) {
            noise = Math.max(noise, terrainNoiseForItem(item, cell));
        }
        return Math.max(0.0, Math.min(1000.0, noise));
    }

    private double terrainNoiseForItem(DungeonItem item, DungeonPoint cell) {
        if (item == null || isPersistentDeleted(item)) {
            return 0.0;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (definition == null || !terrainArea(item, definition).contains(cell.x() + 0.5, cell.y() + 0.5)) {
            return 0.0;
        }
        return switch (item.id()) {
            case "water_puddle" -> 2.0;
            case "oil_puddle" -> 1.0;
            case "bones" -> 8.0;
            case "rubble" -> 10.0;
            default -> 0.0;
        };
    }

    private boolean slideAlongWall(double stepX, double stepY) {
        List<DungeonLine> walls = collidingWallsAt(playerX + stepX, playerY + stepY);
        for (DungeonLine wall : walls) {
            double wallX = wall.end().x() - wall.start().x();
            double wallY = wall.end().y() - wall.start().y();
            double length = Math.sqrt(wallX * wallX + wallY * wallY);
            if (length <= 0.0) {
                continue;
            }
            double unitX = wallX / length;
            double unitY = wallY / length;
            double dot = stepX * unitX + stepY * unitY;
            double slideX = unitX * dot * 0.5;
            double slideY = unitY * dot * 0.5;
            if (Math.abs(slideX) < 0.0001 && Math.abs(slideY) < 0.0001) {
                continue;
            }
            if (!collidesAt(playerX + slideX, playerY + slideY)) {
                playerX += slideX;
                playerY += slideY;
                return true;
            }
        }
        return false;
    }

    private void loadAreaForPlayerCollision(SimulationContext context, double stepX, double stepY) {
        double oldCameraX = cameraX;
        double oldCameraY = cameraY;
        cameraX = playerX + stepX;
        cameraY = playerY + stepY;
        loadVisibleArea(context);
        cameraX = oldCameraX;
        cameraY = oldCameraY;
    }

    private boolean collidesAt(double centerX, double centerY) {
        return !playerInsideOccupiedSpace(centerX, centerY) || !collidingWallsAt(centerX, centerY).isEmpty();
    }

    private boolean playerInsideOccupiedSpace(double centerX, double centerY) {
        double radius = PLAYER_SIZE / 2.0 * 0.85;
        return pointInsideOccupiedSpace(centerX, centerY) &&
                pointInsideOccupiedSpace(centerX - radius, centerY) &&
                pointInsideOccupiedSpace(centerX + radius, centerY) &&
                pointInsideOccupiedSpace(centerX, centerY - radius) &&
                pointInsideOccupiedSpace(centerX, centerY + radius);
    }

    private boolean pointInsideOccupiedSpace(double x, double y) {
        if (loadedArea == null) {
            return false;
        }
        DungeonRect area = new DungeonRect(
                (int) Math.floor(x) - 1,
                (int) Math.floor(y) - 1,
                (int) Math.ceil(x) + 1,
                (int) Math.ceil(y) + 1
        );
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(area)) {
            for (DungeonOccupiedArea occupied : placement.getWorldOccupiedAreas()) {
                if (occupiedAreaContains(occupied, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<DungeonLine> collidingWallsAt(double centerX, double centerY) {
        if (loadedArea == null) {
            return List.of();
        }
        List<DungeonLine> colliding = new ArrayList<>();
        DungeonRect bounds = playerBounds(centerX, centerY);
        for (DungeonLine wall : loadedArea.getWallsIntersecting(bounds)) {
            if (lineIntersectsCircle(wall, centerX, centerY, PLAYER_SIZE / 2.0)) {
                colliding.add(wall);
            }
        }
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(bounds)) {
            if (lineIntersectsCircle(wall, centerX, centerY, PLAYER_SIZE / 2.0)) {
                colliding.add(wall);
            }
        }
        return List.copyOf(colliding);
    }

    private DungeonRect visibleWorldArea(SimulationContext context) {
        double pixelsPerBlock = pixelsPerBlock(context);
        int halfWidth = (int) Math.ceil(context.getConfig().getWidth() / (2.0 * pixelsPerBlock));
        int halfHeight = (int) Math.ceil(context.getConfig().getHeight() / (2.0 * pixelsPerBlock));
        int minX = (int) Math.floor(cameraX - halfWidth - VIEW_BUFFER);
        int minY = (int) Math.floor(cameraY - halfHeight - VIEW_BUFFER);
        int maxX = (int) Math.ceil(cameraX + halfWidth + VIEW_BUFFER);
        int maxY = (int) Math.ceil(cameraY + halfHeight + VIEW_BUFFER);
        return new DungeonRect(minX, minY, maxX, maxY);
    }

    private void drawLine(Graphics2D graphics, ViewTransform transform, DungeonLine line) {
        graphics.drawLine(
                transform.worldToScreenX(line.start().x()),
                transform.worldToScreenY(line.start().y()),
                transform.worldToScreenX(line.end().x()),
                transform.worldToScreenY(line.end().y())
        );
    }

    private void drawOccupiedAreas(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(new Color(42, 44, 48));
        graphics.setStroke(new BasicStroke(OCCUPIED_FILL_STROKE_PIXELS, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonOccupiedArea area : placement.getWorldOccupiedAreas()) {
                int[] xPoints = new int[area.getPoints().size()];
                int[] yPoints = new int[area.getPoints().size()];
                for (int i = 0; i < area.getPoints().size(); i++) {
                    xPoints[i] = transform.worldToScreenX(area.getPoints().get(i).x());
                    yPoints[i] = transform.worldToScreenY(area.getPoints().get(i).y());
                }
                graphics.fillPolygon(xPoints, yPoints, area.getPoints().size());
                graphics.drawPolygon(xPoints, yPoints, area.getPoints().size());
            }
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
    }

    private void drawOpeningNodes(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (int i = 0; i < placement.getTemplate().getOpenings().size(); i++) {
                DungeonOpening opening = placement.getWorldOpening(i);
                int x = transform.worldToScreenX(opening.position().x());
                int y = transform.worldToScreenY(opening.position().y());
                graphics.setColor(placement.isOpeningConnected(i)
                        ? new Color(80, 185, 255)
                        : new Color(255, 120, 88));
                graphics.fillOval(
                        x - OPENING_NODE_RADIUS_PIXELS,
                        y - OPENING_NODE_RADIUS_PIXELS,
                        OPENING_NODE_RADIUS_PIXELS * 2,
                        OPENING_NODE_RADIUS_PIXELS * 2
                );
            }
        }
    }

    private void drawArtifactLabels(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        graphics.setFont(ARTIFACT_LABEL_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            String name = placement.getTemplate().getName();
            int x = transform.worldToScreenX(placement.getCenter().x());
            int y = transform.worldToScreenY(placement.getCenter().y());
            int width = metrics.stringWidth(name);

            graphics.setColor(new Color(0, 0, 0, 170));
            graphics.fillRect(x - width / 2 - 3, y - metrics.getAscent(), width + 6, metrics.getHeight());
            graphics.setColor(new Color(235, 238, 242));
            graphics.drawString(name, x - width / 2, y);
        }
    }

    private DungeonRect expand(DungeonRect rect, int blocks) {
        return new DungeonRect(
                rect.minX() - blocks,
                rect.minY() - blocks,
                rect.maxX() + blocks,
                rect.maxY() + blocks
        );
    }

    private void updateEnvironmentalSanityEffect() {
        if (loadedArea == null) {
            return;
        }
        if (playerIsInSteadyLight()) {
            characterState.removeEffect("darkness");
            characterState.setEffect(new ActiveCharacterEffect(
                    "lit",
                    ENVIRONMENT_EFFECT_REFRESH_SECONDS,
                    1.0,
                    CharacterEffectMode.ADD
            ));
        } else {
            characterState.removeEffect("lit");
            characterState.setEffect(new ActiveCharacterEffect(
                    "darkness",
                    ENVIRONMENT_EFFECT_REFRESH_SECONDS,
                    1.0,
                    CharacterEffectMode.ADD
            ));
        }
    }

    private boolean playerIsInSteadyLight() {
        if (hasComfortEquippedLantern()) {
            return true;
        }
        DungeonRect view = expand(playerBounds(playerX, playerY),
                (int) Math.ceil(MAX_MAP_LIGHT_RADIUS_BLOCKS + LIGHT_FADE_RADIUS_BLOCKS));
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                if (isPlayerInLightFrom(item)) {
                    return true;
                }
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            if (isPlayerInLightFrom(item)) {
                return true;
            }
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            if (isPlayerInLightFrom(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasComfortEquippedLantern() {
        DungeonInventoryItem primary = equipmentState.get(EquipmentSlot.PRIMARY).orElse(null);
        if (isEquippedLightOn(primary)) {
            return true;
        }
        if (!equipmentState.secondaryLightEnabled(DungeonEquipmentLibrary.instance())) {
            return false;
        }
        return isEquippedLightOn(equipmentState.get(EquipmentSlot.SECONDARY_1).orElse(null));
    }

    private boolean isPlayerInLightFrom(DungeonItem item) {
        if (isPersistentDeleted(item)) {
            return false;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        return "lit".equals(effectId(definition)) &&
                isEmittingMapLight(definition, item) &&
                distance(playerX, playerY, itemCellCenterX(item), itemCellCenterY(item)) <= lightRadius(definition, item);
    }

    private List<LightVisibility> collectVisibleLights(DungeonRect view, double elapsedSeconds) {
        List<LightVisibility> lights = new ArrayList<>();
        addEquippedVisibleLights(lights);
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                addVisibleLight(lights, item, elapsedSeconds);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            addVisibleLight(lights, item, elapsedSeconds);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            addVisibleLight(lights, item, elapsedSeconds);
        }
        return lights;
    }

    private void addEquippedVisibleLights(List<LightVisibility> lights) {
        addEquippedVisibleLight(lights, EquipmentSlot.PRIMARY);
        if (equipmentState.secondaryLightEnabled(DungeonEquipmentLibrary.instance())) {
            addEquippedVisibleLight(lights, EquipmentSlot.SECONDARY_1);
        }
    }

    private void addEquippedVisibleLight(List<LightVisibility> lights, EquipmentSlot slot) {
        DungeonInventoryItem lantern = equipmentState.get(slot).orElse(null);
        if (!isEquippedLightOn(lantern)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(lantern.itemId()).orElse(null);
        if (definition == null) {
            return;
        }
        lights.add(new LightVisibility(null, playerX, playerY, equippedLightRadius(lantern, definition), 1.0));
    }

    private void addVisibleLight(List<LightVisibility> lights, DungeonItem item, double elapsedSeconds) {
        if (isPersistentDeleted(item)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (!isEmittingMapLight(definition, item)) {
            return;
        }
        double lightX = itemCellCenterX(item);
        double lightY = itemCellCenterY(item);
        double strength = playerVisibilityStrength(lightX, lightY);
        if (strength <= 0.0) {
            return;
        }
        strength *= flickerStrength(definition, item, elapsedSeconds);
        if (strength <= 0.0) {
            return;
        }
        lights.add(new LightVisibility(item, lightX, lightY, lightRadius(definition, item), strength));
    }

    private void drawVisibilityOverlay(
            Graphics2D graphics,
            SimulationContext context,
            ViewTransform transform,
            List<LightVisibility> lights
    ) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        if (visibilityOverlay == null ||
                visibilityOverlay.getWidth() != width ||
                visibilityOverlay.getHeight() != height) {
            visibilityOverlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D overlayGraphics = visibilityOverlay.createGraphics();
        Object oldAntialiasing = overlayGraphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        overlayGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        overlayGraphics.setComposite(AlphaComposite.Clear);
        overlayGraphics.fillRect(0, 0, width, height);
        overlayGraphics.setComposite(AlphaComposite.SrcOver);
        overlayGraphics.setColor(new Color(22, 24, 28, DIM_OVERLAY_ALPHA));
        overlayGraphics.fillRect(0, 0, width, height);

        overlayGraphics.setComposite(AlphaComposite.DstOut);
        double pixelsPerBlock = transform.pixelsPerBlock();
        punchVisibilityCircle(
                overlayGraphics,
                transform.worldToScreenX(playerX),
                transform.worldToScreenY(playerY),
                playerClearRadiusBlocks() * pixelsPerBlock,
                (playerClearRadiusBlocks() + playerFadeRadiusBlocks()) * pixelsPerBlock,
                1.0
        );
        for (LightVisibility light : lights) {
            punchVisibilityCircle(
                    overlayGraphics,
                    transform.worldToScreenX(light.x()),
                    transform.worldToScreenY(light.y()),
                    light.radius() * pixelsPerBlock,
                    (light.radius() + LIGHT_FADE_RADIUS_BLOCKS) * pixelsPerBlock,
                    light.strength()
            );
        }
        overlayGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
        overlayGraphics.dispose();
        Composite oldComposite = graphics.getComposite();
        Paint oldPaint = graphics.getPaint();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(visibilityOverlay, 0, 0, null);
        graphics.setComposite(oldComposite);
        graphics.setPaint(oldPaint);
    }

    private void punchVisibilityCircle(
            Graphics2D graphics,
            double centerX,
            double centerY,
            double clearRadius,
            double fadeRadius,
            double strength
    ) {
        if (fadeRadius <= 0.0 || strength <= 0.0) {
            return;
        }
        int maxAlpha = (int) Math.round(255 * Math.min(1.0, strength));
        float clearStop = (float) Math.max(0.0, Math.min(0.98, clearRadius / fadeRadius));
        RadialGradientPaint paint = new RadialGradientPaint(
                new Point2D.Double(centerX, centerY),
                (float) fadeRadius,
                new float[] { 0.0f, clearStop, 1.0f },
                new Color[] {
                        new Color(255, 255, 255, maxAlpha),
                        new Color(255, 255, 255, maxAlpha),
                        new Color(255, 255, 255, 0)
                },
                MultipleGradientPaint.CycleMethod.NO_CYCLE
        );
        Paint oldPaint = graphics.getPaint();
        graphics.setPaint(paint);
        graphics.fillRect(
                (int) Math.floor(centerX - fadeRadius),
                (int) Math.floor(centerY - fadeRadius),
                (int) Math.ceil(fadeRadius * 2.0),
                (int) Math.ceil(fadeRadius * 2.0)
        );
        graphics.setPaint(oldPaint);
    }

    private void drawMapItems(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                drawMapItemIfRenderable(graphics, item, transform, 1.0);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            drawMapItemIfRenderable(graphics, item, transform, 1.0);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            drawMapItemIfRenderable(graphics, item, transform, 1.0);
        }
    }

    private void drawHazardZones(Graphics2D graphics, DungeonRect view, ViewTransform transform, double alpha) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                drawHazardZoneIfRenderable(graphics, item, transform, alpha);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            drawHazardZoneIfRenderable(graphics, item, transform, alpha);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            drawHazardZoneIfRenderable(graphics, item, transform, alpha);
        }
    }

    private void drawVisibleMapItems(
            Graphics2D graphics,
            DungeonRect view,
            ViewTransform transform,
            List<LightVisibility> activeLights,
            double elapsedSeconds
    ) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                drawVisibleMapItemIfRenderable(graphics, item, transform, activeLights, elapsedSeconds);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            drawVisibleMapItemIfRenderable(graphics, item, transform, activeLights, elapsedSeconds);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            drawVisibleMapItemIfRenderable(graphics, item, transform, activeLights, elapsedSeconds);
        }
    }

    private void drawVisibleHazardZones(
            Graphics2D graphics,
            DungeonRect view,
            ViewTransform transform,
            List<LightVisibility> activeLights
    ) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                double visibility = itemVisibilityStrength(itemCellCenterX(item), itemCellCenterY(item), activeLights, item);
                if (visibility > 0.0) {
                    drawHazardZoneIfRenderable(graphics, item, transform, visibility);
                }
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            double visibility = itemVisibilityStrength(itemCellCenterX(item), itemCellCenterY(item), activeLights, item);
            if (visibility > 0.0) {
                drawHazardZoneIfRenderable(graphics, item, transform, visibility);
            }
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            double visibility = itemVisibilityStrength(itemCellCenterX(item), itemCellCenterY(item), activeLights, item);
            if (visibility > 0.0) {
                drawHazardZoneIfRenderable(graphics, item, transform, visibility);
            }
        }
    }

    private void drawHazardZoneIfRenderable(
            Graphics2D graphics,
            DungeonItem item,
            ViewTransform transform,
            double alpha
    ) {
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (!isDrawableHazardZone(definition, item) || isPersistentDeleted(item)) {
            return;
        }
        Rectangle2D.Double area = hazardArea(item, definition);
        int x1 = transform.worldToScreenX(area.getMinX());
        int y1 = transform.worldToScreenY(area.getMinY());
        int x2 = transform.worldToScreenX(area.getMaxX());
        int y2 = transform.worldToScreenY(area.getMaxY());
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0.0, Math.min(0.24, alpha * hazardZoneAlpha(definition)))));
        graphics.setColor(hazardZoneColor(definition));
        graphics.fillRect(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.max(1, Math.abs(x2 - x1)),
                Math.max(1, Math.abs(y2 - y1))
        );
        graphics.setComposite(oldComposite);
    }

    private void drawMapItemIfRenderable(
            Graphics2D graphics,
            DungeonItem item,
            ViewTransform transform,
            double alpha
    ) {
        if (isPersistentDeleted(item)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (definition == null || definition.isMapBased() || definition.isInteractable()) {
            drawMapItem(graphics, item, definition, transform, alpha);
        }
    }

    private void drawVisibleMapItemIfRenderable(
            Graphics2D graphics,
            DungeonItem item,
            ViewTransform transform,
            List<LightVisibility> activeLights,
            double elapsedSeconds
    ) {
        if (isPersistentDeleted(item)) {
            return;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (definition != null && !definition.isMapBased() && !definition.isInteractable()) {
            return;
        }
        double visibility = itemVisibilityStrength(itemCellCenterX(item), itemCellCenterY(item), activeLights, item);
        visibility *= itemVisualStrength(definition, item, elapsedSeconds);
        if (visibility > 0.0) {
            drawMapItem(graphics, item, definition, transform, visibility);
        }
    }

    private void drawMapItem(
            Graphics2D graphics,
            DungeonItem item,
            DungeonItemDefinition definition,
            ViewTransform transform,
            double alpha
    ) {
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0.0, Math.min(1.0, alpha))));

        DungeonItemVisual visual = definition == null
                ? new DungeonItemVisual("block", "#cccccc", "#303030", "#ffffff", "?", "Unknown item")
                : definition.visual();
        Rectangle2D.Double box = itemBox(item, definition);
        int x1 = transform.worldToScreenX(box.getMinX());
        int y1 = transform.worldToScreenY(box.getMinY());
        int x2 = transform.worldToScreenX(box.getMaxX());
        int y2 = transform.worldToScreenY(box.getMaxY());
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int width = Math.max(2, Math.abs(x2 - x1));
        int height = Math.max(2, Math.abs(y2 - y1));

        graphics.setColor(color(visual.fillColor(), new Color(204, 204, 204)));
        graphics.fillRect(
                minX,
                minY,
                width,
                height
        );
        graphics.setColor(color(visual.outlineColor(), new Color(32, 32, 32)));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(minX, minY, width, height);

        String glyph = visual.glyph();
        if (!glyph.isBlank() && width >= 8 && height >= 8) {
            Font oldFont = graphics.getFont();
            graphics.setFont(ARTIFACT_LABEL_FONT);
            FontMetrics metrics = graphics.getFontMetrics();
            String clippedGlyph = glyph.length() > 2 ? glyph.substring(0, 2) : glyph;
            graphics.setColor(color(visual.accentColor(), Color.WHITE));
            graphics.drawString(
                    clippedGlyph,
                    minX + width / 2 - metrics.stringWidth(clippedGlyph) / 2,
                    minY + height / 2 + metrics.getAscent() / 2 - 2
            );
            graphics.setFont(oldFont);
        }

        if (definition != null && definition.requiresWall()) {
            DungeonPoint offset = directionOffset(item.direction());
            graphics.setColor(color(visual.accentColor(), Color.WHITE));
            graphics.drawLine(
                    transform.worldToScreenX(itemCellCenterX(item)),
                    transform.worldToScreenY(itemCellCenterY(item)),
                    transform.worldToScreenX(itemCellCenterX(item) + offset.x() * 0.45),
                    transform.worldToScreenY(itemCellCenterY(item) + offset.y() * 0.45)
            );
        }

        graphics.setComposite(oldComposite);
    }

    private Rectangle2D.Double itemBox(DungeonItem item, DungeonItemDefinition definition) {
        double cellX = item.position().x();
        double cellY = item.position().y();
        double minX = cellX + (1.0 - ITEM_SIZE_BLOCKS) / 2.0;
        double minY = cellY + (1.0 - ITEM_SIZE_BLOCKS) / 2.0;

        if (definition != null && definition.requiresWall()) {
            switch (item.direction()) {
                case NORTH -> minY = cellY + 1.0 - ITEM_SIZE_BLOCKS;
                case EAST -> minX = cellX;
                case SOUTH -> minY = cellY;
                case WEST -> minX = cellX + 1.0 - ITEM_SIZE_BLOCKS;
            }
        }

        return new Rectangle2D.Double(minX, minY, ITEM_SIZE_BLOCKS, ITEM_SIZE_BLOCKS);
    }

    private boolean isActiveHazardZone(DungeonItemDefinition definition, DungeonItem item) {
        if (definition == null || item == null) {
            return false;
        }
        String id = definition.id();
        if (!"gas_vent".equals(id) && !"steam_vent".equals(id)) {
            return false;
        }
        Map<String, Object> properties = currentItemProperties(definition, item);
        Object active = properties.get("is_active");
        return !(active instanceof Boolean value) || value;
    }

    private boolean isDrawableHazardZone(DungeonItemDefinition definition, DungeonItem item) {
        if (definition == null || item == null) {
            return false;
        }
        return isActiveHazardZone(definition, item) ||
                "water_puddle".equals(definition.id()) ||
                "oil_puddle".equals(definition.id());
    }

    private Rectangle2D.Double hazardArea(DungeonItem item, DungeonItemDefinition definition) {
        if ("water_puddle".equals(definition.id()) || "oil_puddle".equals(definition.id())) {
            return terrainArea(item, definition);
        }
        Map<String, Object> properties = currentItemProperties(definition, item);
        double size = Math.max(1.0, numericProperty(properties, "radius", 4.0));
        double centerX = itemCellCenterX(item);
        double centerY = itemCellCenterY(item);
        return new Rectangle2D.Double(centerX - size / 2.0, centerY - size / 2.0, size, size);
    }

    private Rectangle2D.Double terrainArea(DungeonItem item, DungeonItemDefinition definition) {
        double size = "water_puddle".equals(definition.id()) || "oil_puddle".equals(definition.id()) ? 3.0 : 1.0;
        double centerX = itemCellCenterX(item);
        double centerY = itemCellCenterY(item);
        return new Rectangle2D.Double(centerX - size / 2.0, centerY - size / 2.0, size, size);
    }

    private Color hazardZoneColor(DungeonItemDefinition definition) {
        return switch (definition.id()) {
            case "gas_vent" -> new Color(92, 210, 122);
            case "steam_vent" -> new Color(202, 232, 238);
            case "water_puddle" -> new Color(74, 155, 220);
            case "oil_puddle" -> new Color(84, 72, 45);
            default -> new Color(180, 180, 180);
        };
    }

    private float hazardZoneAlpha(DungeonItemDefinition definition) {
        return switch (definition.id()) {
            case "gas_vent" -> 0.22f;
            case "steam_vent" -> 0.18f;
            case "water_puddle" -> 0.16f;
            case "oil_puddle" -> 0.18f;
            default -> 0.14f;
        };
    }

    private void drawInteractionOutlines(
            Graphics2D graphics,
            ViewTransform transform,
            List<InteractionAction> actions
    ) {
        if (actions.isEmpty()) {
            return;
        }
        Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setStroke(new BasicStroke(3f));
        if (actions.size() == 2 &&
                itemStateKey(actions.get(0).target().item()).equals(itemStateKey(actions.get(1).target().item()))) {
            drawInteractionOutline(graphics, transform, actions.get(0).target().item(), Color.WHITE);
        } else {
            for (InteractionAction action : actions) {
                drawInteractionOutline(graphics, transform, action.target().item(), interactionButtonColor(action.button()));
            }
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
    }

    private void drawInteractionOutline(Graphics2D graphics, ViewTransform transform, DungeonItem item, Color color) {
        int x1 = transform.worldToScreenX(item.position().x());
        int y1 = transform.worldToScreenY(item.position().y());
        int x2 = transform.worldToScreenX(item.position().x() + 1);
        int y2 = transform.worldToScreenY(item.position().y() + 1);
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int width = Math.abs(x2 - x1);
        int height = Math.abs(y2 - y1);
        graphics.setColor(color);
        graphics.drawRect(minX, minY, width, height);
    }

    private void drawInteractionPrompt(
            SimulationContext context,
            Graphics2D graphics,
            List<InteractionAction> actions
    ) {
        if (pendingOilUse != null) {
            drawCenteredPromptText(context, graphics, "Select lantern to fill", Color.WHITE, interactionPromptY(context));
            return;
        }
        if (actions.isEmpty()) {
            return;
        }

        graphics.setFont(INVENTORY_TEXT_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        int y = interactionPromptY(context);
        int actionGap = actions.size() > 1 ? 36 : 0;
        int totalWidth = metrics.stringWidth("Interact: ");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                totalWidth += actionGap;
            }
            totalWidth += interactionActionWidth(metrics, actions.get(i));
        }
        int x = context.getConfig().getWidth() / 2 - totalWidth / 2;
        drawPromptSegment(graphics, "Interact: ", Color.WHITE, x, y);
        x += metrics.stringWidth("Interact: ");
        int[] segmentXs = new int[actions.size()];
        int[] segmentWidths = new int[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                x += actionGap;
            }
            InteractionAction action = actions.get(i);
            segmentXs[i] = x;
            segmentWidths[i] = interactionActionWidth(metrics, action);
            String buttonLabel = interactionButtonLabel(action.button());
            drawPromptSegment(graphics, buttonLabel, interactionButtonColor(action.button()), x, y);
            x += metrics.stringWidth(buttonLabel);
            drawPromptSegment(graphics, " " + action.label(), Color.WHITE, x, y);
            x += metrics.stringWidth(" " + action.label());
        }
        drawInteractionPromptNames(graphics, metrics, actions, segmentXs, segmentWidths, y - 22);
    }

    private void drawInteractionPromptNames(
            Graphics2D graphics,
            FontMetrics metrics,
            List<InteractionAction> actions,
            int[] segmentXs,
            int[] segmentWidths,
            int y
    ) {
        if (actions.size() == 2 &&
                itemStateKey(actions.get(0).target().item()).equals(itemStateKey(actions.get(1).target().item()))) {
            String name = interactionTargetName(actions.get(0).target());
            int minX = segmentXs[0];
            int maxX = segmentXs[1] + segmentWidths[1];
            drawPromptSegment(graphics, name, Color.WHITE, minX + (maxX - minX) / 2 - metrics.stringWidth(name) / 2, y);
            return;
        }

        int[] nameXs = new int[actions.size()];
        int[] nameWidths = new int[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            String name = interactionTargetName(actions.get(i).target());
            nameWidths[i] = metrics.stringWidth(name);
            nameXs[i] = segmentXs[i] + segmentWidths[i] / 2 - nameWidths[i] / 2;
        }
        if (actions.size() == 2) {
            int minGap = 12;
            int overlap = nameXs[0] + nameWidths[0] + minGap - nameXs[1];
            if (overlap > 0) {
                int shift = overlap / 2 + 1;
                nameXs[0] -= shift;
                nameXs[1] += shift;
            }
        }
        for (int i = 0; i < actions.size(); i++) {
            drawPromptSegment(
                    graphics,
                    interactionTargetName(actions.get(i).target()),
                    interactionButtonColor(actions.get(i).button()),
                    nameXs[i],
                    y
            );
        }
    }

    private int interactionActionWidth(FontMetrics metrics, InteractionAction action) {
        return metrics.stringWidth(interactionButtonLabel(action.button())) + metrics.stringWidth(" " + action.label());
    }

    private String interactionButtonLabel(InteractionButton button) {
        return button == InteractionButton.SECONDARY ? "Q" : "E";
    }

    private Color interactionButtonColor(InteractionButton button) {
        return button == InteractionButton.SECONDARY ? SECONDARY_INTERACTION_COLOR : PRIMARY_INTERACTION_COLOR;
    }

    private String interactionTargetName(InteractionTarget target) {
        return target.definition() == null ? target.item().id() : target.definition().name();
    }

    private HoveredItem findHoveredItem(
            DungeonRect view,
            ViewTransform transform,
            List<LightVisibility> activeLights,
            double elapsedSeconds
    ) {
        if (loadedArea == null || mouseScreenX < 0 || mouseScreenY < 0) {
            return null;
        }
        double worldX = transform.screenToWorldX(mouseScreenX);
        double worldY = transform.screenToWorldY(mouseScreenY);
        HoveredItem best = null;
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                best = closerHoveredItem(best, item, worldX, worldY, activeLights, elapsedSeconds);
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(view)) {
            best = closerHoveredItem(best, item, worldX, worldY, activeLights, elapsedSeconds);
        }
        for (DungeonItem item : randomItemsIntersecting(view)) {
            best = closerHoveredItem(best, item, worldX, worldY, activeLights, elapsedSeconds);
        }
        return best;
    }

    private HoveredItem closerHoveredItem(
            HoveredItem best,
            DungeonItem item,
            double worldX,
            double worldY,
            List<LightVisibility> activeLights,
            double elapsedSeconds
    ) {
        if (isPersistentDeleted(item)) {
            return best;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(item.id()).orElse(null);
        if (definition != null && !definition.isMapBased() && !definition.isInteractable()) {
            return best;
        }
        if (!itemBox(item, definition).contains(worldX, worldY)) {
            return best;
        }
        if (!adminMode) {
            double visibility = itemVisibilityStrength(itemCellCenterX(item), itemCellCenterY(item), activeLights, item);
            visibility *= itemVisualStrength(definition, item, elapsedSeconds);
            if (visibility <= 0.0) {
                return best;
            }
        }
        double distance = distance(playerX, playerY, itemCellCenterX(item), itemCellCenterY(item));
        if (best == null || distance < best.distance()) {
            return new HoveredItem(item, definition, distance);
        }
        return best;
    }

    private void drawHoveredItemText(SimulationContext context, Graphics2D graphics, HoveredItem hoveredItem) {
        if (hoveredItem == null) {
            return;
        }
        DungeonItemDefinition definition = hoveredItem.definition();
        String text = definition == null ? hoveredItem.item().id() : definition.name();
        drawCenteredPromptText(context, graphics, text, Color.WHITE, interactionPromptY(context) - 48);
    }

    private void drawCenteredPromptText(SimulationContext context, Graphics2D graphics, String text, Color color, int y) {
        graphics.setFont(INVENTORY_TEXT_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        int x = context.getConfig().getWidth() / 2 - metrics.stringWidth(text) / 2;
        drawPromptSegment(graphics, text, color, x, y);
    }

    private void drawNotifications(SimulationContext context, Graphics2D graphics) {
        if (notifications.isEmpty()) {
            return;
        }
        graphics.setFont(INVENTORY_TEXT_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        Composite oldComposite = graphics.getComposite();
        int y = 32;
        int rows = Math.min(MAX_NOTIFICATIONS, notifications.size());
        for (int i = 0; i < rows; i++) {
            Notification notification = notifications.get(i);
            float alpha = (float) Math.max(0.0, Math.min(1.0,
                    notification.remainingSeconds() / NOTIFICATION_DURATION_SECONDS));
            graphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
            String text = notification.text();
            int x = context.getConfig().getWidth() / 2 - metrics.stringWidth(text) / 2;
            drawPromptSegment(graphics, text, notification.color(), x, y + i * 22);
        }
        graphics.setComposite(oldComposite);
    }

    private void drawModeKeybinds(SimulationContext context, Graphics2D graphics) {
        List<TextSegment> segments = List.of();
        if (activeDocument != null) {
            segments = List.of(
                    new TextSegment("Keybinds: ", false),
                    new TextSegment("Tab", true),
                    new TextSegment(" or ", false),
                    new TextSegment("Esc", true),
                    new TextSegment(" to close document", false)
            );
        } else if (pendingOilUse != null) {
            segments = isContainerOpen()
                    ? List.of(
                            new TextSegment("Keybinds: ", false),
                            new TextSegment("Tab", true),
                            new TextSegment(" to cancel, ", false),
                            new TextSegment("Esc", true),
                            new TextSegment(" to close container", false)
                    )
                    : List.of(
                            new TextSegment("Keybinds: ", false),
                            new TextSegment("Tab", true),
                            new TextSegment(" to cancel, ", false),
                            new TextSegment("Esc", true),
                            new TextSegment(" to close inventory", false)
                    );
        } else if (isContainerOpen()) {
            segments = List.of(
                    new TextSegment("Keybinds: ", false),
                    new TextSegment("Tab", true),
                    new TextSegment(" or ", false),
                    new TextSegment("Esc", true),
                    new TextSegment(" to close container", false)
            );
        } else if (inventoryOpen) {
            segments = List.of(
                    new TextSegment("Keybinds: ", false),
                    new TextSegment("Tab", true),
                    new TextSegment(" or ", false),
                    new TextSegment("Esc", true),
                    new TextSegment(" to close inventory", false)
            );
        }
        if (segments.isEmpty()) {
            return;
        }

        Font oldFont = graphics.getFont();
        Font boldFont = INVENTORY_TEXT_FONT.deriveFont(Font.BOLD);
        int totalWidth = 0;
        for (TextSegment segment : segments) {
            graphics.setFont(segment.bold() ? boldFont : INVENTORY_TEXT_FONT);
            totalWidth += graphics.getFontMetrics().stringWidth(segment.text());
        }
        int x = context.getConfig().getWidth() / 2 - totalWidth / 2;
        int y = 20;

        for (TextSegment segment : segments) {
            graphics.setFont(segment.bold() ? boldFont : INVENTORY_TEXT_FONT);
            drawPromptSegment(graphics, segment.text(), Color.WHITE, x, y);
            x += graphics.getFontMetrics().stringWidth(segment.text());
        }
        graphics.setFont(oldFont);
    }

    private int interactionPromptY(SimulationContext context) {
        return context.getConfig().getHeight() - 34;
    }

    private void drawPromptSegment(Graphics2D graphics, String text, Color color, int x, int y) {
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private double itemCellCenterX(DungeonItem item) {
        return item.position().x() + 0.5;
    }

    private double itemCellCenterY(DungeonItem item) {
        return item.position().y() + 0.5;
    }

    private DungeonPoint directionOffset(DungeonDirection direction) {
        return switch (direction) {
            case NORTH -> new DungeonPoint(0, -1);
            case EAST -> new DungeonPoint(1, 0);
            case SOUTH -> new DungeonPoint(0, 1);
            case WEST -> new DungeonPoint(-1, 0);
        };
    }

    private double playerVisibilityStrength(double x, double y) {
        return visibilityStrength(
                distance(playerX, playerY, x, y),
                playerClearRadiusBlocks(),
                playerFadeRadiusBlocks()
        );
    }

    private double playerClearRadiusBlocks() {
        return PLAYER_CLEAR_RADIUS_BLOCKS * currentVisionRadiusMultiplier();
    }

    private double playerFadeRadiusBlocks() {
        return PLAYER_FADE_RADIUS_BLOCKS * currentVisionRadiusMultiplier();
    }

    private double currentVisionRadiusMultiplier() {
        return characterState.getEffectiveVisionRadiusMultiplier();
    }

    private double itemVisibilityStrength(
            double x,
            double y,
            List<LightVisibility> activeLights,
            DungeonItem currentItem
    ) {
        double strength = playerVisibilityStrength(x, y);
        for (LightVisibility light : activeLights) {
            if (light.item() == currentItem) {
                continue;
            }
            double lightStrength = visibilityStrength(
                    distance(light.x(), light.y(), x, y),
                    light.radius(),
                    LIGHT_FADE_RADIUS_BLOCKS
            ) * light.strength();
            strength = Math.max(strength, lightStrength);
        }
        return strength;
    }

    private double visibilityStrength(double distance, double clearRadius, double fadeRadius) {
        if (distance <= clearRadius) {
            return 1.0;
        }
        if (distance >= clearRadius + fadeRadius) {
            return 0.0;
        }
        double t = (distance - clearRadius) / fadeRadius;
        double smooth = t * t * (3.0 - 2.0 * t);
        return 1.0 - smooth;
    }

    private boolean isEmittingMapLight(DungeonItemDefinition definition, DungeonItem item) {
        if (!isMapLight(definition)) {
            return false;
        }
        return isItemOn(definition, item) && lightFuelRemaining(definition, item) > 0.0;
    }

    private boolean isEquippedLightOn(DungeonInventoryItem lantern) {
        if (lantern == null) {
            return false;
        }
        DungeonItemDefinition definition = DungeonItemLibrary.find(lantern.itemId()).orElse(null);
        if (definition == null || definition.category() != DungeonItemCategory.LIGHT) {
            return false;
        }
        boolean isOn = lantern.properties().get("is_on") instanceof Boolean value && value;
        return isOn && equippedLightFuelRemaining(lantern, definition) > 0.0;
    }

    private String effectId(DungeonItemDefinition definition) {
        if (definition == null) {
            return "";
        }
        Object effectId = definition.defaultProperties().get("effect_id");
        return effectId instanceof String value ? value.trim() : "";
    }

    private double lightRadius(DungeonItemDefinition definition, DungeonItem item) {
        if (definition == null) {
            return DEFAULT_LIGHT_RADIUS_BLOCKS;
        }
        Object radius = definition.defaultProperties().get("light_radius");
        double baseRadius = DEFAULT_LIGHT_RADIUS_BLOCKS;
        if (radius instanceof Number number) {
            baseRadius = Math.max(1.0, number.doubleValue());
        }
        double fuel = lightFuelRemaining(definition, item);
        if (fuel <= 0.0) {
            return 0.0;
        }
        if (fuel < LIGHT_FUEL_FADE_THRESHOLD) {
            return baseRadius * Math.max(0.0, Math.min(1.0, fuel / LIGHT_FUEL_FADE_THRESHOLD));
        }
        return baseRadius;
    }

    private double equippedLightRadius(DungeonInventoryItem lantern, DungeonItemDefinition definition) {
        double baseRadius = numericProperty(definition.defaultProperties(), "light_radius", DEFAULT_LIGHT_RADIUS_BLOCKS);
        double fuel = equippedLightFuelRemaining(lantern, definition);
        if (fuel <= 0.0) {
            return 0.0;
        }
        if (fuel >= LIGHT_FUEL_FADE_THRESHOLD) {
            return baseRadius;
        }
        return baseRadius * Math.max(0.0, Math.min(1.0, fuel / LIGHT_FUEL_FADE_THRESHOLD));
    }

    private double equippedLightFuelRemaining(DungeonInventoryItem lantern, DungeonItemDefinition definition) {
        return Math.max(0.0, numericProperty(
                lantern.properties(),
                "fuel_remaining",
                numericProperty(definition.defaultProperties(), "fuel_remaining", 0.0)
        ));
    }

    private double itemVisualStrength(DungeonItemDefinition definition, DungeonItem item, double elapsedSeconds) {
        if (definition == null || definition.category() != DungeonItemCategory.LIGHT) {
            return 1.0;
        }
        if (!isItemOn(definition, item)) {
            return 0.45;
        }
        return flickerStrength(definition, item, elapsedSeconds);
    }

    private boolean isItemOn(DungeonItemDefinition definition, DungeonItem item) {
        if (definition == null) {
            return false;
        }
        if (isMapLight(definition)) {
            if (lightFuelRemaining(definition, item) <= 0.0) {
                return false;
            }
        }
        Boolean override = persistentBooleanProperty(item, "is_on");
        if (override != null) {
            return override;
        }
        Object randomOn = randomWorldItemProperties.getOrDefault(item, Map.of()).get("is_on");
        if (randomOn instanceof Boolean value) {
            return value;
        }
        return seededInitialIsOn(definition, item);
    }

    private boolean isFlickeringLight(DungeonItemDefinition definition) {
        Object flicker = definition.defaultProperties().get("flicker");
        return flicker instanceof Boolean value && value;
    }

    private String itemStateKey(DungeonItem item) {
        return item.id() + ":" +
                item.position().x() + ":" +
                item.position().y() + ":" +
                item.direction().name();
    }

    private String persistentItemStateKey(DungeonItem item) {
        if (droppedWorldItems.contains(item)) {
            return placedItemStateKey(item);
        }
        String randomKey = randomWorldItemKeys.get(item);
        return randomKey == null ? itemStateKey(item) : randomKey;
    }

    private String placedItemStateKey(DungeonItem item) {
        return "placed:" + itemStateKey(item);
    }

    private double spawnOnChance(DungeonItemDefinition definition) {
        Object chance = definition.defaultProperties().get("spawn_on_chance");
        if (chance instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        return 1.0;
    }

    private double flickerStrength(DungeonItemDefinition definition, DungeonItem item, double elapsedSeconds) {
        Object flicker = definition.defaultProperties().get("flicker");
        if (!(flicker instanceof Boolean enabled) || !enabled) {
            return 1.0;
        }

        double min = numberProperty(definition, "flicker_min", 0.35);
        double speed = numberProperty(definition, "flicker_speed", 6.0);
        double phase = seededUnit(item, "flicker_phase") * Math.PI * 2.0;
        double jitter = seededUnit(item, "flicker_jitter") * 1.7 + 0.65;
        double wave = Math.sin(elapsedSeconds * speed * jitter + phase);
        double sharpWave = Math.sin(elapsedSeconds * speed * 2.35 + phase * 0.37);
        double combined = Math.max(0.0, (wave * 0.7 + sharpWave * 0.3 + 1.0) / 2.0);
        return Math.max(0.0, Math.min(1.0, min + (1.0 - min) * combined));
    }

    private double numberProperty(DungeonItemDefinition definition, String key, double fallback) {
        Object value = definition.defaultProperties().get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double seededUnit(DungeonItem item, String salt) {
        long hash = seed;
        hash = hash * 31L + item.id().hashCode();
        hash = hash * 31L + item.position().x();
        hash = hash * 31L + item.position().y();
        hash = hash * 31L + item.direction().ordinal();
        hash = hash * 31L + salt.hashCode();
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return (hash >>> 11) * 0x1.0p-53;
    }

    private Color color(String hex, Color fallback) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double maxMapLightRadius() {
        double max = DEFAULT_LIGHT_RADIUS_BLOCKS;
        for (DungeonItemDefinition definition : DungeonItemLibrary.byKind(DungeonItemKind.MAP)) {
            if (definition.category() != DungeonItemCategory.LIGHT) {
                continue;
            }
            Object radius = definition.defaultProperties().get("light_radius");
            if (radius instanceof Number number) {
                max = Math.max(max, number.doubleValue());
            }
        }
        return max;
    }

    private double distance(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void drawDeathScreen(SimulationContext context, Graphics2D graphics) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width, height);

        graphics.setFont(DEATH_TITLE_FONT);
        FontMetrics titleMetrics = graphics.getFontMetrics();
        String title = "You died";
        graphics.setColor(new Color(230, 230, 230));
        graphics.drawString(
                title,
                width / 2 - titleMetrics.stringWidth(title) / 2,
                height / 2 - 70
        );

        Rectangle2D.Double button = deathQuitButton(context);
        graphics.setColor(new Color(165, 165, 165));
        graphics.fill(button);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(2f));
        graphics.draw(button);

        graphics.setFont(DEATH_BUTTON_FONT);
        FontMetrics buttonMetrics = graphics.getFontMetrics();
        String label = "Quit";
        graphics.drawString(
                label,
                (int) (button.getCenterX() - buttonMetrics.stringWidth(label) / 2.0),
                (int) (button.getCenterY() + buttonMetrics.getAscent() / 2.0 - 3)
        );
    }

    private Rectangle2D.Double deathQuitButton(SimulationContext context) {
        double width = 150.0;
        double height = 48.0;
        return new Rectangle2D.Double(
                context.getConfig().getWidth() / 2.0 - width / 2.0,
                context.getConfig().getHeight() / 2.0,
                width,
                height
        );
    }

    private void drawInventoryOverlay(SimulationContext context, Graphics2D graphics) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(900, width - 80);
        int panelHeight = Math.min(860, height - 80);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;

        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.94f));
        graphics.setColor(new Color(135, 135, 135));
        graphics.fillRect(panelX, panelY, panelWidth, panelHeight);
        graphics.setComposite(oldComposite);

        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(panelX, panelY, panelWidth, panelHeight);

        int gridMargin = 18;
        DungeonItemSize inventoryCapacity = currentInventoryCapacity();
        int gridCellSize = Math.max(12, Math.min(
                (panelWidth - 340) / inventoryCapacity.width(),
                (panelHeight - gridMargin * 2) / inventoryCapacity.height()
        ));
        int gridWidth = gridCellSize * inventoryCapacity.width();
        int gridHeight = gridCellSize * inventoryCapacity.height();
        int gridX = panelX + panelWidth - gridMargin - gridWidth;
        int gridY = panelY + panelHeight / 2 - gridHeight / 2;
        int leftX = panelX + 18;
        int leftWidth = Math.max(180, gridX - leftX - 24);
        int y = panelY + 34;

        graphics.setFont(INVENTORY_TITLE_FONT);
        graphics.drawString("Inventory", leftX, y);

        y += 32;
        graphics.setFont(INVENTORY_TEXT_FONT);
        graphics.drawString("Active effects", leftX, y);
        y += 22;

        drawItemGrid(graphics, gridX, gridY, gridCellSize, inventoryCapacity.width(), inventoryCapacity.height());
        drawInventoryItems(graphics, gridX, gridY, gridCellSize);
        drawOilSelectionOutlines(graphics, inventory.getItems(), gridX, gridY, gridCellSize);
        drawItemGridLines(graphics, gridX, gridY, gridCellSize, inventoryCapacity.width(), inventoryCapacity.height());
        HoveredGridItem hovered = hoveredGridItem(inventory.getItems(), gridX, gridY, gridCellSize);
        if (hovered == null) {
            hovered = hoveredEquipmentItem(context);
        }
        drawGridHoverText(graphics, hovered);
        drawDraggedGridItem(graphics, gridCellSize);
        drawPendingKeyPlacement(graphics, gridCellSize);

        List<ActiveCharacterEffect> effects = new ArrayList<>(characterState.getActiveEffects());
        effects.sort(Comparator
                .comparing((ActiveCharacterEffect effect) -> hasVisibleTimer(effect))
                .thenComparing(Comparator.comparingDouble(ActiveCharacterEffect::getStrength).reversed())
                .thenComparing(ActiveCharacterEffect::getEffectId));
        int equipmentAreaHeight = Math.min(380, Math.max(300, panelY + panelHeight - y - 18));
        int effectBottom = Math.max(y + 36, panelY + panelHeight - equipmentAreaHeight - 18);
        if (effects.isEmpty()) {
            graphics.drawString("None", leftX, y);
        } else {
            int stackWidth = leftWidth;
            int rowHeight = 30;
            int maxRows = Math.max(1, (effectBottom - y) / rowHeight);
            int rows = Math.min(maxRows, effects.size());
            for (int i = 0; i < rows; i++) {
                ActiveCharacterEffect effect = effects.get(i);
                graphics.setColor(new Color(96, 96, 96));
                graphics.fillRect(leftX, y - 16, stackWidth, rowHeight - 4);
                graphics.setColor(Color.BLACK);
                graphics.drawRect(leftX, y - 16, stackWidth, rowHeight - 4);
                graphics.setColor(Color.WHITE);
                String line = effectLabel(effect);
                graphics.drawString(line, leftX, y);
                y += rowHeight;
            }
            if (effects.size() > rows) {
                graphics.drawString("+" + (effects.size() - rows) + " more", leftX, y);
            }
        }
        drawEquipmentArea(graphics, leftX, effectBottom + 10, leftWidth, panelY + panelHeight - 18);
        drawGridContextMenu(graphics);
    }

    private void drawContainerOverlay(SimulationContext context, Graphics2D graphics) {
        ContainerPersistentState state = ensureContainerState(openContainerItem, openContainerDefinition);
        DungeonItemSize capacity = containerCapacity(state.properties());
        if (capacity == null) {
            return;
        }

        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(920, width - 60);
        int panelHeight = Math.min(860, height - 80);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;

        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.94f));
        graphics.setColor(new Color(135, 135, 135));
        graphics.fillRect(panelX, panelY, panelWidth, panelHeight);
        graphics.setComposite(oldComposite);

        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(panelX, panelY, panelWidth, panelHeight);

        DungeonItemSize inventoryCapacity = currentInventoryCapacity();
        int gap = 56;
        int titleHeight = 56;
        int gridCellSize = Math.max(10, Math.min(
                (panelWidth - gap - 56) / (inventoryCapacity.width() + capacity.width()),
                (panelHeight - titleHeight - 36) / Math.max(inventoryCapacity.height(), capacity.height())
        ));
        int inventoryGridWidth = gridCellSize * inventoryCapacity.width();
        int containerGridWidth = gridCellSize * capacity.width();
        int totalGridWidth = inventoryGridWidth + gap + containerGridWidth;
        int inventoryGridX = panelX + panelWidth / 2 - totalGridWidth / 2;
        int containerGridX = inventoryGridX + inventoryGridWidth + gap;
        int gridY = panelY + titleHeight;

        graphics.setFont(INVENTORY_TITLE_FONT);
        graphics.setColor(Color.BLACK);
        graphics.drawString("Inventory", inventoryGridX, panelY + 34);
        graphics.drawString(openContainerDefinition.name(), containerGridX, panelY + 34);

        drawItemGrid(graphics, inventoryGridX, gridY, gridCellSize, inventoryCapacity.width(), inventoryCapacity.height());
        drawInventoryItems(graphics, inventoryGridX, gridY, gridCellSize);
        drawOilSelectionOutlines(graphics, inventory.getItems(), inventoryGridX, gridY, gridCellSize);
        drawItemGridLines(graphics, inventoryGridX, gridY, gridCellSize, inventoryCapacity.width(), inventoryCapacity.height());

        drawItemGrid(graphics, containerGridX, gridY, gridCellSize, capacity.width(), capacity.height());
        drawContainerItems(graphics, state.contents(), containerGridX, gridY, gridCellSize);
        drawOilSelectionOutlines(graphics, state.contents(), containerGridX, gridY, gridCellSize);
        drawItemGridLines(graphics, containerGridX, gridY, gridCellSize, capacity.width(), capacity.height());

        HoveredGridItem hovered = hoveredGridItem(inventory.getItems(), inventoryGridX, gridY, gridCellSize);
        if (hovered == null) {
            hovered = hoveredGridItem(state.contents(), containerGridX, gridY, gridCellSize);
        }
        if (hovered == null) {
            hovered = hoveredEquipmentItem(context);
        }
        drawGridHoverText(graphics, hovered);
        drawDraggedGridItem(graphics, gridCellSize);
        drawPendingKeyPlacement(graphics, gridCellSize);
        drawGridContextMenu(graphics);
    }

    private void drawEquipmentArea(Graphics2D graphics, int x, int y, int width, int bottomY) {
        int height = Math.max(220, bottomY - y);
        graphics.setFont(INVENTORY_TEXT_FONT);
        graphics.setColor(Color.BLACK);
        graphics.drawString("Equipment", x, y);

        int top = y + 24;
        int bodySlotSize = Math.max(26, Math.min(34, (width - 18) / 4));
        int labelGap = 12;
        int gap = 12;
        int rowGap = labelGap + 14;
        EquipmentSlot[] bodySlots = {
                EquipmentSlot.FACE, EquipmentSlot.CHEST, EquipmentSlot.BACK, EquipmentSlot.HANDS,
                EquipmentSlot.ACCESSORY, EquipmentSlot.WAIST, EquipmentSlot.LEG, EquipmentSlot.FEET
        };
        String[] bodyLabels = {"Face", "Chest", "Back", "Hands", "Acc", "Waist", "Leg", "Feet"};
        int columns = 4;
        int bodyGridWidth = columns * bodySlotSize + (columns - 1) * gap;
        int bodyX = x + Math.max(0, (width - bodyGridWidth) / 2);
        for (int i = 0; i < bodySlots.length; i++) {
            int column = i % columns;
            int row = i / columns;
            drawEquipmentSlot(
                    graphics,
                    bodySlots[i],
                    bodyLabels[i],
                    bodyX + column * (bodySlotSize + gap),
                    top + row * (bodySlotSize + rowGap),
                    bodySlotSize,
                    labelGap
            );
        }

        int useTop = top + 2 * (bodySlotSize + rowGap) + 18;
        int primarySize = Math.max(40, Math.min(52, width / 4));
        int unlockedSecondary = equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance());
        int secondarySize = unlockedSecondary <= 0
                ? 0
                : Math.max(18, Math.min(30, (width - primarySize - 38) / Math.max(1, unlockedSecondary)));
        int primaryX = x + 2;
        drawEquipmentSlot(graphics, EquipmentSlot.PRIMARY, "F", primaryX, useTop, primarySize, labelGap);

        int secondaryStartX = primaryX + primarySize + 12;
        int secondaryY = useTop + Math.max(0, (primarySize - secondarySize) / 2);
        for (int i = 0; i < unlockedSecondary; i++) {
            drawEquipmentSlot(
                    graphics,
                    EquipmentSlot.secondarySlot(i),
                    Integer.toString(i + 1),
                    secondaryStartX + i * (secondarySize + 4),
                    secondaryY,
                    secondarySize,
                    labelGap
            );
        }
    }

    private List<ItemGridView> equipmentSlotViews(SimulationContext context) {
        if (!inventoryOpen) {
            return List.of();
        }
        EquipmentLayout layout = equipmentLayout(context);
        if (layout == null) {
            return List.of();
        }
        List<ItemGridView> views = new ArrayList<>();
        for (EquipmentSlotBox box : layout.boxes()) {
            views.add(new ItemGridView(
                    GridOwner.EQUIPMENT,
                    box.x(),
                    box.y(),
                    box.size(),
                    1,
                    1,
                    box.slot()
            ));
        }
        return List.copyOf(views);
    }

    private EquipmentLayout equipmentLayout(SimulationContext context) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(900, width - 80);
        int panelHeight = Math.min(860, height - 80);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        DungeonItemSize inventoryCapacity = currentInventoryCapacity();
        int gridMargin = 18;
        int gridCellSize = Math.max(12, Math.min(
                (panelWidth - 340) / inventoryCapacity.width(),
                (panelHeight - gridMargin * 2) / inventoryCapacity.height()
        ));
        int gridWidth = gridCellSize * inventoryCapacity.width();
        int gridX = panelX + panelWidth - gridMargin - gridWidth;
        int leftX = panelX + 18;
        int leftWidth = Math.max(180, gridX - leftX - 24);
        int effectY = panelY + 34 + 32 + 22;
        int equipmentAreaHeight = Math.min(380, Math.max(300, panelY + panelHeight - effectY - 18));
        int effectBottom = Math.max(effectY + 36, panelY + panelHeight - equipmentAreaHeight - 18);
        return equipmentLayout(leftX, effectBottom + 10, leftWidth, panelY + panelHeight - 18);
    }

    private EquipmentLayout equipmentLayout(int x, int y, int width, int bottomY) {
        int height = Math.max(220, bottomY - y);
        int top = y + 24;
        int bodySlotSize = Math.max(26, Math.min(34, (width - 18) / 4));
        int labelGap = 12;
        int gap = 12;
        int rowGap = labelGap + 14;
        int columns = 4;
        int bodyGridWidth = columns * bodySlotSize + (columns - 1) * gap;
        int bodyX = x + Math.max(0, (width - bodyGridWidth) / 2);
        int useTop = top + 2 * (bodySlotSize + rowGap) + 18;
        int primarySize = Math.max(40, Math.min(52, width / 4));
        int unlockedSecondary = equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance());
        int secondarySize = unlockedSecondary <= 0
                ? 0
                : Math.max(18, Math.min(30, (width - primarySize - 38) / Math.max(1, unlockedSecondary)));
        int primaryX = x + 2;
        int secondaryStartX = primaryX + primarySize + 12;
        int secondaryY = useTop + Math.max(0, (primarySize - secondarySize) / 2);

        List<EquipmentSlotBox> boxes = new ArrayList<>();
        EquipmentSlot[] bodySlots = {
                EquipmentSlot.FACE, EquipmentSlot.CHEST, EquipmentSlot.BACK, EquipmentSlot.HANDS,
                EquipmentSlot.ACCESSORY, EquipmentSlot.WAIST, EquipmentSlot.LEG, EquipmentSlot.FEET
        };
        for (int i = 0; i < bodySlots.length; i++) {
            int column = i % columns;
            int row = i / columns;
            boxes.add(new EquipmentSlotBox(
                    bodySlots[i],
                    bodyX + column * (bodySlotSize + gap),
                    top + row * (bodySlotSize + rowGap),
                    bodySlotSize
            ));
        }
        boxes.add(new EquipmentSlotBox(EquipmentSlot.PRIMARY, primaryX, useTop, primarySize));
        for (int i = 0; i < unlockedSecondary; i++) {
            boxes.add(new EquipmentSlotBox(
                    EquipmentSlot.secondarySlot(i),
                    secondaryStartX + i * (secondarySize + 4),
                    secondaryY,
                    secondarySize
            ));
        }
        return new EquipmentLayout(boxes);
    }

    private void drawEquipmentFigure(Graphics2D graphics, int centerX, int topY, int height) {
        int headRadius = Math.max(8, height / 10);
        int headCenterY = topY + headRadius;
        int bodyTop = headCenterY + headRadius + 5;
        int bodyBottom = topY + height - height / 4;
        int armY = bodyTop + (bodyBottom - bodyTop) / 3;
        int legBottom = topY + height;

        graphics.setColor(new Color(62, 62, 62));
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawOval(centerX - headRadius, headCenterY - headRadius, headRadius * 2, headRadius * 2);
        graphics.drawLine(centerX, bodyTop, centerX, bodyBottom);
        graphics.drawLine(centerX - height / 5, armY, centerX + height / 5, armY);
        graphics.drawLine(centerX, bodyBottom, centerX - height / 6, legBottom);
        graphics.drawLine(centerX, bodyBottom, centerX + height / 6, legBottom);
    }

    private void drawEquipmentSlot(
            Graphics2D graphics,
            EquipmentSlot slot,
            String label,
            int x,
            int y,
            int size,
            int labelGap
    ) {
        DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
        graphics.setColor(new Color(54, 54, 54));
        graphics.fillRect(x, y, size, size);
        graphics.setColor(new Color(16, 16, 16));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(x, y, size, size);

        if (item != null) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition != null) {
                drawGridItemBlock(graphics, item, definition, x + 2, y + 2, Math.max(2, size - 4), Math.max(2, size - 4));
            }
        }

        graphics.setFont(STATUS_BAR_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(Color.BLACK);
        int labelX = x + size / 2 - metrics.stringWidth(label) / 2;
        int labelY = y + size + labelGap;
        graphics.drawString(label, labelX + 1, labelY + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(label, labelX, labelY);
    }

    private void drawDocumentOverlay(SimulationContext context, Graphics2D graphics) {
        if (activeDocument == null) {
            return;
        }

        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(560, width - 140);
        int panelHeight = Math.min(420, height - 180);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;

        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.97f));
        graphics.setColor(new Color(170, 162, 136));
        graphics.fillRect(panelX, panelY, panelWidth, panelHeight);
        graphics.setComposite(oldComposite);

        graphics.setColor(new Color(48, 42, 31));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(panelX, panelY, panelWidth, panelHeight);

        int x = panelX + 26;
        int y = panelY + 38;
        int textWidth = panelWidth - 52;
        graphics.setFont(INVENTORY_TITLE_FONT);
        graphics.drawString(activeDocument.title(), x, y);

        y += 34;
        graphics.setFont(INVENTORY_TEXT_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        for (String paragraph : activeDocument.paragraphs()) {
            if (paragraph.isBlank()) {
                y += metrics.getHeight();
                continue;
            }
            for (String line : wrapText(graphics, paragraph, textWidth)) {
                if (y > panelY + panelHeight - 28) {
                    graphics.drawString("...", x, y);
                    return;
                }
                graphics.drawString(line, x, y);
                y += metrics.getHeight() + 3;
            }
            y += 8;
        }
    }

    private ReadDocumentView readableDocumentView(
            DungeonItemDefinition definition,
            Map<String, Object> instanceProperties
    ) {
        Map<String, Object> properties = new HashMap<>(definition.defaultProperties());
        if (instanceProperties != null) {
            properties.putAll(instanceProperties);
        }
        if ("map_scrap".equals(definition.id())) {
            double radius = numericProperty(properties, "reveal_radius", 0.0);
            List<String> paragraphs = new ArrayList<>();
            paragraphs.add("The fragment shows a rough nearby section of dungeon.");
            if (radius > 0.0) {
                paragraphs.add("Reveal radius: " + formatWholeOrDecimal(radius) + " blocks.");
            }
            paragraphs.add("Map reveal behavior is reserved for the mapping pass.");
            return new ReadDocumentView(definition.name(), paragraphs);
        }

        String text = propertyString(properties, "text");
        if (text.isBlank()) {
            text = "The page is blank.";
        }
        return new ReadDocumentView(definition.name(), List.of(text));
    }

    private List<String> wrapText(Graphics2D graphics, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (graphics.getFontMetrics().stringWidth(candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
            } else {
                if (line.length() > 0) {
                    lines.add(line.toString());
                }
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    private void drawItemGrid(Graphics2D graphics, int gridX, int gridY, int cellSize, int gridWidth, int gridHeight) {
        graphics.setColor(new Color(48, 48, 48));
        graphics.fillRect(
                gridX,
                gridY,
                cellSize * gridWidth,
                cellSize * gridHeight
        );
    }

    private void drawItemGridLines(
            Graphics2D graphics,
            int gridX,
            int gridY,
            int cellSize,
            int gridWidthCells,
            int gridHeightCells
    ) {
        int gridWidth = cellSize * gridWidthCells;
        int gridHeight = cellSize * gridHeightCells;
        graphics.setColor(new Color(18, 18, 18));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(gridX, gridY, gridWidth, gridHeight);
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(new Color(86, 86, 86));
        for (int x = 1; x < gridWidthCells; x++) {
            int lineX = gridX + x * cellSize;
            graphics.drawLine(lineX, gridY, lineX, gridY + gridHeight);
        }
        for (int y = 1; y < gridHeightCells; y++) {
            int lineY = gridY + y * cellSize;
            graphics.drawLine(gridX, lineY, gridX + gridWidth, lineY);
        }
    }

    private void drawInventoryItems(Graphics2D graphics, int gridX, int gridY, int cellSize) {
        for (DungeonInventoryItem item : inventory.getItems()) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition == null) {
                continue;
            }
            drawInventoryItem(graphics, item, definition, gridX, gridY, cellSize);
        }
    }

    private void drawContainerItems(
            Graphics2D graphics,
            List<DungeonInventoryItem> items,
            int gridX,
            int gridY,
            int cellSize
    ) {
        for (DungeonInventoryItem item : items) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition == null) {
                continue;
            }
            drawInventoryItem(graphics, item, definition, gridX, gridY, cellSize);
        }
    }

    private void drawOilSelectionOutlines(
            Graphics2D graphics,
            List<DungeonInventoryItem> items,
            int gridX,
            int gridY,
            int cellSize
    ) {
        if (pendingOilUse == null) {
            return;
        }
        graphics.setStroke(new BasicStroke(3f));
        for (DungeonInventoryItem item : items) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition == null || definition.inventorySize() == null) {
                continue;
            }
            DungeonItemSize size = definition.inventorySize();
            int x = gridX + item.x() * cellSize + 2;
            int y = gridY + item.y() * cellSize + 2;
            int width = size.width() * cellSize - 4;
            int height = size.height() * cellSize - 4;
            if (mouseScreenX < x || mouseScreenX >= x + width || mouseScreenY < y || mouseScreenY >= y + height) {
                continue;
            }
            graphics.setColor(isFillableLantern(definition) ? PRIMARY_INTERACTION_COLOR : ERROR_PROMPT_COLOR);
            graphics.drawRect(x, y, width, height);
            return;
        }
    }

    private void drawInventoryItem(
            Graphics2D graphics,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition,
            int gridX,
            int gridY,
            int cellSize
    ) {
        DungeonItemSize size = definition.inventorySize();
        int x = gridX + item.x() * cellSize + 2;
        int y = gridY + item.y() * cellSize + 2;
        int width = size.width() * cellSize - 4;
        int height = size.height() * cellSize - 4;
        drawGridItemBlock(graphics, item, definition, x, y, width, height);
    }

    private void drawGridItemBlock(
            Graphics2D graphics,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition,
            int x,
            int y,
            int width,
            int height
    ) {
        DungeonItemVisual visual = definition.visual();
        graphics.setColor(color(visual.fillColor(), new Color(110, 110, 110)));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(color(visual.outlineColor(), Color.BLACK));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRect(x, y, width, height);

        String glyph = visual.glyph();
        if (!glyph.isBlank()) {
            Font oldFont = graphics.getFont();
            graphics.setFont(ARTIFACT_LABEL_FONT);
            FontMetrics metrics = graphics.getFontMetrics();
            String clippedGlyph = glyph.length() > 2 ? glyph.substring(0, 2) : glyph;
            graphics.setColor(color(visual.accentColor(), Color.WHITE));
            graphics.drawString(
                    clippedGlyph,
                    x + width / 2 - metrics.stringWidth(clippedGlyph) / 2,
                    y + height / 2 + metrics.getAscent() / 2 - 2
            );
            graphics.setFont(oldFont);
        }

        if (item.quantity() > 1) {
            graphics.setFont(STATUS_BAR_FONT);
            String quantity = "X" + item.quantity();
            int textY = y + height - 4;
            graphics.setColor(Color.BLACK);
            graphics.drawString(quantity, x + 4, textY + 1);
            graphics.setColor(Color.WHITE);
            graphics.drawString(quantity, x + 3, textY);
        }
    }

    private void drawDraggedGridItem(Graphics2D graphics, int cellSize) {
        if (draggedGridItem == null || mouseScreenX < 0 || mouseScreenY < 0) {
            return;
        }
        DungeonItemSize size = draggedGridItem.definition().inventorySize();
        if (size == null) {
            return;
        }
        int x = mouseScreenX - draggedGridItem.offsetX() + 2;
        int y = mouseScreenY - draggedGridItem.offsetY() + 2;
        int width = size.width() * cellSize - 4;
        int height = size.height() * cellSize - 4;
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.78f));
        drawGridItemBlock(graphics, draggedGridItem.item(), draggedGridItem.definition(), x, y, width, height);
        graphics.setComposite(oldComposite);
    }

    private void drawPendingKeyPlacement(Graphics2D graphics, int cellSize) {
        if (pendingKeyPlacement == null || mouseScreenX < 0 || mouseScreenY < 0) {
            return;
        }
        DungeonInventoryItem item = pendingKeyPlacement.item();
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
        if (definition == null) {
            return;
        }
        int size = Math.max(12, cellSize);
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.82f));
        drawGridItemBlock(
                graphics,
                item,
                definition,
                mouseScreenX - size / 2,
                mouseScreenY - size / 2,
                size,
                size
        );
        graphics.setComposite(oldComposite);
    }

    private void drawGridContextMenu(Graphics2D graphics) {
        if (gridContextMenu == null || gridContextMenu.actions().isEmpty()) {
            return;
        }
        graphics.setFont(INVENTORY_TEXT_FONT);
        int paddingX = 10;
        int width = gridContextMenuWidth();
        int actionWidth = gridContextActionWidth();
        int height = gridContextMenuHeight();
        int x = gridContextMenu.x();
        int y = gridContextMenu.y();

        graphics.setColor(new Color(30, 30, 30, 238));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRect(x, y, width, height);

        graphics.setColor(new Color(210, 210, 210));
        graphics.drawString(gridContextMenu.definition().name(), x + paddingX, y + 17);
        graphics.setColor(new Color(76, 76, 76));
        graphics.drawLine(x, y + GRID_CONTEXT_ROW_HEIGHT, x + actionWidth, y + GRID_CONTEXT_ROW_HEIGHT);
        graphics.drawLine(x + actionWidth, y, x + actionWidth, y + height);

        int rowY = y + GRID_CONTEXT_ROW_HEIGHT;
        for (int i = 0; i < gridContextMenu.actions().size(); i++) {
            GridContextAction action = gridContextMenu.actions().get(i);
            if (mouseScreenX >= x && mouseScreenX < x + actionWidth &&
                    mouseScreenY >= rowY && mouseScreenY < rowY + GRID_CONTEXT_ROW_HEIGHT) {
                graphics.setColor(new Color(82, 82, 82));
                graphics.fillRect(x + 1, rowY, actionWidth - 1, GRID_CONTEXT_ROW_HEIGHT);
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(action.label(), x + paddingX, rowY + 17);
            rowY += GRID_CONTEXT_ROW_HEIGHT;
        }
        drawGridContextInfo(graphics, x + actionWidth + paddingX, y + 17, GRID_CONTEXT_INFO_WIDTH - paddingX * 2);
    }

    private void drawGridContextInfo(Graphics2D graphics, int x, int y, int width) {
        List<String> lines = gridContextInfoLines();
        if (lines.isEmpty()) {
            return;
        }
        Font oldFont = graphics.getFont();
        graphics.setFont(STATUS_BAR_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(new Color(214, 214, 214));
        for (String line : lines) {
            graphics.drawString(trimToWidth(graphics, line, width), x, y);
            y += metrics.getHeight() + 2;
        }
        drawKeyringContextRows(graphics, x, y + 2, width);
        graphics.setFont(oldFont);
    }

    private void drawKeyringContextRows(Graphics2D graphics, int x, int y, int width) {
        if (!isKeyringMenu()) {
            return;
        }
        List<KeyringKeyEntry> keys = keyringKeys(gridContextMenu.item());
        graphics.setFont(STATUS_BAR_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(new Color(170, 170, 170));
        graphics.drawString("Keys", x, y);
        y += metrics.getHeight() + 2;
        if (keys.isEmpty()) {
            graphics.drawString("Empty", x, y);
            return;
        }
        if (keyringScrollOffset > 0) {
            graphics.drawString("^ More", x, y);
            y += metrics.getHeight() + 2;
        }
        int start = Math.max(0, Math.min(keyringScrollOffset, Math.max(0, keys.size() - KEYRING_VISIBLE_KEYS)));
        int end = Math.min(keys.size(), start + KEYRING_VISIBLE_KEYS);
        for (int i = start; i < end; i++) {
            KeyringKeyEntry key = keys.get(i);
            if (mouseScreenX >= x && mouseScreenX < x + width &&
                    mouseScreenY >= y - metrics.getAscent() && mouseScreenY < y + metrics.getDescent()) {
                graphics.setColor(new Color(82, 82, 82));
                graphics.fillRect(x - 2, y - metrics.getAscent(), width + 4, metrics.getHeight());
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(trimToWidth(graphics, keyringKeyLabel(key), width), x, y);
            y += metrics.getHeight() + 2;
        }
        if (end < keys.size()) {
            graphics.setColor(new Color(170, 170, 170));
            graphics.drawString("v More", x, y);
        }
    }

    private HoveredGridItem hoveredGridItem(List<DungeonInventoryItem> items, int gridX, int gridY, int cellSize) {
        if (mouseScreenX < 0 || mouseScreenY < 0) {
            return null;
        }
        for (DungeonInventoryItem item : items) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition == null || definition.inventorySize() == null) {
                continue;
            }
            DungeonItemSize size = definition.inventorySize();
            int x = gridX + item.x() * cellSize;
            int y = gridY + item.y() * cellSize;
            int width = size.width() * cellSize;
            int height = size.height() * cellSize;
            if (mouseScreenX >= x && mouseScreenX < x + width && mouseScreenY >= y && mouseScreenY < y + height) {
                return new HoveredGridItem(item, definition);
            }
        }
        return null;
    }

    private void drawGridHoverText(Graphics2D graphics, HoveredGridItem hovered) {
        if (hovered == null) {
            return;
        }
        Font oldFont = graphics.getFont();
        String text = hovered.definition().name();
        graphics.setFont(INVENTORY_TEXT_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        int paddingX = 7;
        int paddingY = 4;
        int x = mouseScreenX + 14;
        int y = mouseScreenY - 12;
        int width = metrics.stringWidth(text) + paddingX * 2;
        int height = metrics.getHeight() + paddingY * 2;
        graphics.setColor(new Color(28, 28, 28, 230));
        graphics.fillRect(x, y - height + metrics.getDescent(), width, height);
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x, y - height + metrics.getDescent(), width, height);
        graphics.drawString(text, x + paddingX, y);
        graphics.setFont(oldFont);
    }

    private HoveredGridItem hoveredEquipmentItem(SimulationContext context) {
        if (mouseScreenX < 0 || mouseScreenY < 0) {
            return null;
        }
        for (ItemGridView view : equipmentSlotViews(context)) {
            if (mouseScreenX < view.x() || mouseScreenX >= view.x() + view.cellSize() ||
                    mouseScreenY < view.y() || mouseScreenY >= view.y() + view.cellSize()) {
                continue;
            }
            DungeonInventoryItem item = equipmentState.get(view.equipmentSlot()).orElse(null);
            DungeonCarryableDefinition definition = item == null
                    ? null
                    : DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (item != null && definition != null) {
                return new HoveredGridItem(item, definition);
            }
        }
        return null;
    }

    private List<String> gridContextInfoLines() {
        if (gridContextMenu == null) {
            return List.of();
        }
        DungeonInventoryItem item = gridContextMenu.item();
        DungeonCarryableDefinition definition = gridContextMenu.definition();
        Map<String, Object> properties = new HashMap<>(definition.defaultProperties());
        properties.putAll(item.properties());
        List<String> lines = new ArrayList<>();
        String categoryLabel = publicCategoryLabel(definition);
        if (!categoryLabel.isBlank()) {
            lines.add("Category: " + categoryLabel);
        }

        if (definition.isEquipment()) {
            String slot = propertyString(properties, "apparel_slot");
            if (!slot.isBlank()) {
                lines.add("Slot: " + displayPropertyName(slot));
            }
            int inventoryRows = (int) numericProperty(properties, "inventory_rows", 0.0);
            if (inventoryRows > 0) {
                lines.add("Inventory: 10x" + inventoryRows);
            }
            int secondarySlots = (int) numericProperty(properties, "secondary_slots", 0.0);
            if (secondarySlots > 0) {
                lines.add("Quick slots: +" + secondarySlots);
            }
            String allowance = propertyString(properties, "secondary_allowance");
            if (!allowance.isBlank()) {
                lines.add("Allows: " + displayPropertyName(allowance));
            }
            double defense = numericProperty(properties, "defense", 0.0);
            if (defense > 0.0) {
                lines.add("Defense: +" + formatWholeOrDecimal(defense));
            }
            double gasProtection = numericProperty(properties, "gas_protection", 0.0);
            if (gasProtection > 0.0) {
                lines.add("Gas: " + formatWholeOrDecimal(gasProtection * 100.0) + "%");
            }
            double breathingNoise = numericProperty(properties, "breathing_noise", 0.0);
            if (breathingNoise > 0.0) {
                lines.add("Breathing noise: +" + formatWholeOrDecimal(breathingNoise));
            }
            if (properties.get("enables_primary_swap") instanceof Boolean enabled && enabled) {
                lines.add("Enables G swap");
            }
            if (properties.get("allows_secondary_light") instanceof Boolean enabled && enabled) {
                lines.add("Secondary light");
            }
            double movementNoise = numericProperty(properties, "movement_noise", 0.0);
            if (Math.abs(movementNoise) > 0.0001) {
                lines.add("Noise: " + (movementNoise > 0.0 ? "+" : "") + formatWholeOrDecimal(movementNoise));
            }
            double terrainSlowReduction = numericProperty(properties, "terrain_slow_reduction", 0.0);
            if (terrainSlowReduction > 0.0) {
                lines.add("Terrain slow: -" + formatWholeOrDecimal(terrainSlowReduction));
            }
            double searchBonus = numericProperty(properties, "search_bonus", 0.0);
            if (searchBonus > 0.0) {
                lines.add("Search: +" + formatWholeOrDecimal(searchBonus));
            }
            double pryTimeBonus = numericProperty(properties, "pry_time_bonus", 0.0);
            if (pryTimeBonus > 0.0) {
                lines.add("Pry time: +" + formatWholeOrDecimal(pryTimeBonus));
            }
            int keyCapacity = (int) numericProperty(properties, "key_capacity", 0.0);
            if (keyCapacity > 0) {
                lines.add("Keys: " + keyCapacity);
            }
            return List.copyOf(lines);
        }

        DungeonItemDefinition itemDefinition = definition.itemDefinition();

        if (itemDefinition.category() == DungeonItemCategory.FOOD) {
            double hunger = numericProperty(properties, "hunger_amount", 0.0);
            if (hunger > 0.0) {
                lines.add("Hunger: +" + formatWholeOrDecimal(hunger));
            }
        }
        if ("lantern_oil".equals(itemDefinition.id())) {
            double fuel = numericProperty(properties, "fuel_amount", 0.0);
            lines.add("Fuel: +" + formatWholeOrDecimal(fuel));
        } else if ("floor_lantern".equals(itemDefinition.id()) || "torch".equals(itemDefinition.id()) ||
                itemDefinition.category() == DungeonItemCategory.LIGHT) {
            double fuel = numericProperty(properties, "fuel_remaining",
                    numericProperty(itemDefinition.defaultProperties(), "fuel_remaining", 0.0));
            double maxFuel = numericProperty(itemDefinition.defaultProperties(), "burn_time", MAX_LIGHT_FUEL);
            lines.add("Fuel: " + formatWholeOrDecimal(fuel) + "/" + formatWholeOrDecimal(maxFuel));
            double radius = numericProperty(itemDefinition.defaultProperties(), "light_radius", 0.0);
            if (radius > 0.0) {
                lines.add("Light: " + formatWholeOrDecimal(radius));
            }
        }
        if ("bandage".equals(itemDefinition.id())) {
            lines.add("Heal: +10 / 5s");
            lines.add("Duration: 15s");
        }
        if ("antidote".equals(itemDefinition.id())) {
            lines.add("Removes toxins");
            lines.add("Sanity: +10");
        }
        if ("stamina_draught".equals(itemDefinition.id())) {
            lines.add("Max stamina: 150");
            lines.add("Regen: +5/s");
            lines.add("Duration: ?");
        }
        if ("map_scrap".equals(itemDefinition.id())) {
            double radius = numericProperty(properties, "reveal_radius", 0.0);
            if (radius > 0.0) {
                lines.add("Reveal: " + formatWholeOrDecimal(radius));
            }
            lines.add("Readable");
        }
        if ("note".equals(itemDefinition.id())) {
            String text = propertyString(properties, "text");
            lines.add(text.isBlank() ? "Blank page" : "Readable");
        }
        if (itemDefinition.category() == DungeonItemCategory.COMBAT) {
            double damage = numericProperty(properties, "damage", 0.0);
            double reach = numericProperty(properties, "reach", 0.0);
            if (damage > 0.0) {
                lines.add("Damage: " + formatWholeOrDecimal(damage));
            }
            if (reach > 0.0) {
                lines.add("Reach: " + formatWholeOrDecimal(reach));
            }
        }
        if (itemDefinition.category() == DungeonItemCategory.ARMOR) {
            double block = numericProperty(properties, "block_amount", 0.0);
            if (block > 0.0) {
                lines.add("Block: " + formatWholeOrDecimal(block));
            }
        }
        return List.copyOf(lines);
    }

    private String publicCategoryLabel(DungeonCarryableDefinition definition) {
        if (definition == null) {
            return "";
        }
        if (definition.isEquipment()) {
            return "Equipment";
        }
        if (!definition.isItem()) {
            return "";
        }
        DungeonItemCategory category = definition.itemDefinition().category();
        return switch (category) {
            case LIGHT -> "Light";
            case TRIGGER, SAVE -> "Utility";
            case HAZARD -> "Hazard";
            case CONTAINER -> "Container";
            case DETAIL -> "Detail";
            case FOOD -> "Food";
            case MEDICAL -> "Medical";
            case TOOL -> "Tool";
            case KEY -> "Key";
            case DOCUMENT -> "Document";
            case COMBAT -> "Weapon";
            case ARMOR -> "Armor";
        };
    }

    private String trimToWidth(Graphics2D graphics, String text, int width) {
        if (graphics.getFontMetrics().stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int maxWidth = Math.max(0, width - graphics.getFontMetrics().stringWidth(ellipsis));
        String trimmed = text;
        while (!trimmed.isEmpty() && graphics.getFontMetrics().stringWidth(trimmed) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private String formatWholeOrDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String displayPropertyName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] words = value.trim().toLowerCase(Locale.US).split("[_\\s]+");
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                text.append(word.substring(1));
            }
        }
        return text.toString();
    }

    private boolean handleOilSelectionClick(SimulationContext context, int screenX, int screenY) {
        DraggedGridItem target = findGridItemAt(context, screenX, screenY);
        if (target == null) {
            return true;
        }
        if (!isFillableLantern(target.definition())) {
            addNotification("Select a lantern.", ERROR_PROMPT_COLOR);
            return true;
        }
        fillLanternFromOil(target.source(), target.sourceIndex(), target.item(), target.definition().itemDefinition());
        return true;
    }

    private boolean handlePendingKeyPlacementClick(SimulationContext context, int screenX, int screenY) {
        ItemGridView inventoryView = gridViewForOwner(context, GridOwner.INVENTORY);
        if (inventoryView == null ||
                screenX < inventoryView.x() ||
                screenX >= inventoryView.x() + inventoryView.widthCells() * inventoryView.cellSize() ||
                screenY < inventoryView.y() ||
                screenY >= inventoryView.y() + inventoryView.heightCells() * inventoryView.cellSize()) {
            addNotification("Place key in inventory.", ERROR_PROMPT_COLOR);
            return true;
        }
        DungeonInventoryItem item = pendingKeyPlacement.item();
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
        if (definition == null || definition.inventorySize() == null) {
            cancelPendingKeyPlacement();
            return true;
        }
        DungeonItemSize size = definition.inventorySize();
        int targetX = Math.max(0, Math.min(
                inventoryView.widthCells() - size.width(),
                (screenX - inventoryView.x()) / inventoryView.cellSize()
        ));
        int targetY = Math.max(0, Math.min(
                inventoryView.heightCells() - size.height(),
                (screenY - inventoryView.y()) / inventoryView.cellSize()
        ));
        DungeonInventoryItem placed = new DungeonInventoryItem(
                item.itemId(),
                targetX,
                targetY,
                item.quantity(),
                item.properties()
        );
        List<DungeonInventoryItem> items = new ArrayList<>(inventory.getItems());
        if (!canPlaceInGrid(items, placed, definition, inventoryView)) {
            addNotification("Can't place key there.", ERROR_PROMPT_COLOR);
            return true;
        }
        items.add(placed);
        if (inventory.replaceAll(items)) {
            pendingKeyPlacement = null;
            setInteractionNoise(KEYRING_INTERACTION_NOISE);
        }
        return true;
    }

    private void cancelPendingKeyPlacement() {
        if (pendingKeyPlacement == null) {
            return;
        }
        if (!inventory.addNextAvailable(
                pendingKeyPlacement.item().itemId(),
                pendingKeyPlacement.item().quantity(),
                pendingKeyPlacement.item().properties()
        )) {
            addNotification("Your inventory is full.", ERROR_PROMPT_COLOR);
            return;
        }
        pendingKeyPlacement = null;
    }

    private boolean isFillableLantern(DungeonCarryableDefinition definition) {
        return definition != null && definition.isItem() && "floor_lantern".equals(definition.id());
    }

    private boolean isFillableLantern(DungeonItemDefinition definition) {
        return definition != null && "floor_lantern".equals(definition.id());
    }

    private void fillLanternFromOil(
            GridOwner lanternSource,
            int lanternIndex,
            DungeonInventoryItem lantern,
            DungeonItemDefinition lanternDefinition
    ) {
        if (pendingOilUse == null) {
            return;
        }
        double oilAmount = numericProperty(pendingOilUse.item().properties(), "fuel_amount",
                numericProperty(pendingOilUse.definition().defaultProperties(), "fuel_amount", 0.0));
        if (oilAmount <= 0.0) {
            pendingOilUse = null;
            return;
        }
        List<DungeonInventoryItem> lanternItems = new ArrayList<>(gridItems(lanternSource));
        int currentLanternIndex = currentGridItemIndex(lanternItems, lanternIndex, lantern);
        if (currentLanternIndex < 0) {
            pendingOilUse = null;
            return;
        }
        DungeonInventoryItem currentLantern = lanternItems.get(currentLanternIndex);
        Map<String, Object> properties = new HashMap<>(currentLantern.properties());
        double currentFuel = numericProperty(properties, "fuel_remaining",
                numericProperty(lanternDefinition.defaultProperties(), "fuel_remaining", 0.0));
        double maxFuel = numericProperty(lanternDefinition.defaultProperties(), "burn_time", MAX_LIGHT_FUEL);
        if (currentFuel >= maxFuel) {
            addNotification("Lantern is full.", ERROR_PROMPT_COLOR);
            return;
        }
        properties.put("fuel_remaining", Math.min(maxFuel, currentFuel + oilAmount));
        properties.put("is_on", false);
        lanternItems.set(currentLanternIndex, new DungeonInventoryItem(
                currentLantern.itemId(),
                currentLantern.x(),
                currentLantern.y(),
                currentLantern.quantity(),
                Map.copyOf(properties)
        ));
        if (setGridItems(lanternSource, lanternItems)) {
            consumeOneGridItem(pendingOilUse.source(), pendingOilUse.sourceIndex(), pendingOilUse.item());
            pendingOilUse = null;
        }
    }

    private boolean handleGridContextMenuClick(SimulationContext context, int screenX, int screenY) {
        if (gridContextMenu == null) {
            return false;
        }
        int width = gridContextMenuWidth();
        int actionWidth = gridContextActionWidth();
        int height = gridContextMenuHeight();
        int x = gridContextMenu.x();
        int y = gridContextMenu.y();
        if (screenX < x || screenX >= x + width || screenY < y || screenY >= y + height) {
            gridContextMenu = null;
            return false;
        }
        if (screenX >= x + actionWidth) {
            handleGridContextInfoClick(screenX, screenY);
            return true;
        }
        int actionIndex = (screenY - y) / GRID_CONTEXT_ROW_HEIGHT - 1;
        if (actionIndex >= 0 && actionIndex < gridContextMenu.actions().size()) {
            performGridContextAction(context, gridContextMenu, gridContextMenu.actions().get(actionIndex));
            gridContextMenu = null;
        }
        return true;
    }

    private void handleGridContextInfoClick(int screenX, int screenY) {
        if (!isKeyringMenu()) {
            return;
        }
        List<KeyringKeyEntry> keys = keyringKeys(gridContextMenu.item());
        if (keys.isEmpty()) {
            return;
        }
        int actionWidth = gridContextActionWidth();
        int y = gridContextMenu.y() + 17;
        int rowHeight = STATUS_BAR_FONT.getSize() + 4;
        int firstKeyY = y + (gridContextInfoLines().size() + 1) * rowHeight;
        int start = Math.max(0, Math.min(keyringScrollOffset, Math.max(0, keys.size() - KEYRING_VISIBLE_KEYS)));
        if (start > 0 && screenY >= firstKeyY && screenY < firstKeyY + rowHeight) {
            keyringScrollOffset = Math.max(0, keyringScrollOffset - 1);
            return;
        }
        if (start > 0) {
            firstKeyY += rowHeight;
        }
        int end = Math.min(keys.size(), start + KEYRING_VISIBLE_KEYS);
        int clicked = start + (screenY - firstKeyY) / rowHeight;
        if (clicked >= start && clicked < end) {
            removeKeyFromKeyring(clicked);
            return;
        }
        int downY = firstKeyY + (end - start) * rowHeight;
        if (end < keys.size() && screenY >= downY && screenY < downY + rowHeight) {
            keyringScrollOffset = Math.min(Math.max(0, keys.size() - KEYRING_VISIBLE_KEYS), keyringScrollOffset + 1);
        }
    }

    private int keyringContextRowCount() {
        if (!isKeyringMenu()) {
            return 0;
        }
        List<KeyringKeyEntry> keys = keyringKeys(gridContextMenu.item());
        if (keys.isEmpty()) {
            return 2;
        }
        int rows = 1 + Math.min(KEYRING_VISIBLE_KEYS, keys.size());
        if (keyringScrollOffset > 0) {
            rows++;
        }
        if (keyringScrollOffset + KEYRING_VISIBLE_KEYS < keys.size()) {
            rows++;
        }
        return rows;
    }

    private boolean isKeyringMenu() {
        return gridContextMenu != null && "key_ring".equals(gridContextMenu.item().itemId());
    }

    private boolean isKeyItem(DungeonCarryableDefinition definition) {
        return definition != null &&
                definition.isItem() &&
                definition.itemDefinition().category() == DungeonItemCategory.KEY;
    }

    private boolean canStoreOnKeyring(DungeonInventoryItem keyItem, DungeonCarryableDefinition keyDefinition) {
        return keyItem != null &&
                isKeyItem(keyDefinition) &&
                !"rusted_key".equals(keyItem.itemId());
    }

    private List<KeyringKeyEntry> keyringKeys(DungeonInventoryItem keyring) {
        Object raw = keyring.properties().get("stored_keys");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<KeyringKeyEntry> keys = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object itemId = rawMap.get("item_id");
            Object keyId = rawMap.get("key_id");
            Object properties = rawMap.get("properties");
            if (!(itemId instanceof String itemIdText) || itemIdText.isBlank()) {
                continue;
            }
            Map<String, Object> keyProperties = new HashMap<>();
            if (properties instanceof Map<?, ?> propertyMap) {
                for (Map.Entry<?, ?> property : propertyMap.entrySet()) {
                    if (property.getKey() instanceof String key) {
                        keyProperties.put(key, property.getValue());
                    }
                }
            }
            if (keyId instanceof String keyIdText && !keyIdText.isBlank()) {
                keyProperties.put("key_id", keyIdText);
            }
            keys.add(new KeyringKeyEntry(itemIdText, propertyString(keyProperties, "key_id"), Map.copyOf(keyProperties)));
        }
        return List.copyOf(keys);
    }

    private String keyringKeyLabel(KeyringKeyEntry key) {
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(key.itemId()).orElse(null);
        String name = definition == null ? key.itemId() : definition.name();
        return key.keyId().isBlank() ? name : name + " " + key.keyId();
    }

    private boolean addKeyToKeyring(
            GridOwner keyringOwner,
            int keyringIndex,
            DungeonInventoryItem keyring,
            DungeonInventoryItem keyItem,
            DungeonCarryableDefinition keyDefinition
    ) {
        if (!canStoreOnKeyring(keyItem, keyDefinition)) {
            addNotification("That key cannot go on the key ring.", ERROR_PROMPT_COLOR);
            return false;
        }
        List<KeyringKeyEntry> keys = new ArrayList<>(keyringKeys(keyring));
        if (!appendKeyringKey(keys, keyItem)) {
            return false;
        }
        DungeonInventoryItem updated = keyringWithKeys(keyring, keys);
        if (!replaceGridItem(keyringOwner, keyringIndex, keyring, updated)) {
            addNotification("Could not update key ring.", ERROR_PROMPT_COLOR);
            return false;
        }
        setInteractionNoise(KEYRING_INTERACTION_NOISE);
        return true;
    }

    private boolean appendKeyringKey(List<KeyringKeyEntry> keys, DungeonInventoryItem keyItem) {
        int capacity = (int) numericProperty(
                DungeonEquipmentLibrary.instance().find("key_ring")
                        .map(DungeonEquipmentDefinition::defaultProperties)
                        .orElse(Map.of()),
                "key_capacity",
                24.0
        );
        if (keys.size() >= capacity) {
            addNotification("Key ring is full.", ERROR_PROMPT_COLOR);
            return false;
        }
        keys.add(new KeyringKeyEntry(
                keyItem.itemId(),
                propertyString(keyItem.properties(), "key_id"),
                Map.copyOf(keyItem.properties())
        ));
        return true;
    }

    private DungeonInventoryItem keyringWithKeys(DungeonInventoryItem keyring, List<KeyringKeyEntry> keys) {
        Map<String, Object> properties = new HashMap<>(keyring.properties());
        List<Map<String, Object>> stored = new ArrayList<>();
        for (KeyringKeyEntry key : keys) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("item_id", key.itemId());
            entry.put("key_id", key.keyId());
            entry.put("properties", key.properties());
            stored.add(Map.copyOf(entry));
        }
        properties.put("stored_keys", List.copyOf(stored));
        return new DungeonInventoryItem(
                keyring.itemId(),
                keyring.x(),
                keyring.y(),
                keyring.quantity(),
                Map.copyOf(properties)
        );
    }

    private void removeKeyFromKeyring(int keyIndex) {
        if (!isKeyringMenu()) {
            return;
        }
        List<KeyringKeyEntry> keys = new ArrayList<>(keyringKeys(gridContextMenu.item()));
        if (keyIndex < 0 || keyIndex >= keys.size()) {
            return;
        }
        KeyringKeyEntry removed = keys.remove(keyIndex);
        DungeonInventoryItem updated = keyringWithKeys(gridContextMenu.item(), keys);
        if (!replaceGridItem(gridContextMenu.source(), gridContextMenu.sourceIndex(), gridContextMenu.item(), updated)) {
            addNotification("Could not remove key.", ERROR_PROMPT_COLOR);
            return;
        }
        pendingKeyPlacement = new PendingKeyPlacement(new DungeonInventoryItem(
                removed.itemId(),
                0,
                0,
                1,
                removed.properties()
        ));
        keyringScrollOffset = Math.max(0, Math.min(keyringScrollOffset, Math.max(0, keys.size() - KEYRING_VISIBLE_KEYS)));
        gridContextMenu = null;
        setInteractionNoise(KEYRING_INTERACTION_NOISE);
    }

    private int gridContextMenuWidth() {
        if (gridContextMenu == null) {
            return 0;
        }
        return gridContextActionWidth() + GRID_CONTEXT_INFO_WIDTH;
    }

    private int gridContextMenuHeight() {
        if (gridContextMenu == null) {
            return 0;
        }
        int actionRows = gridContextMenu.actions().size() + 1;
        int infoRows = gridContextInfoLines().size() + keyringContextRowCount() + 1;
        return GRID_CONTEXT_ROW_HEIGHT * Math.max(actionRows, infoRows);
    }

    private int gridContextActionWidth() {
        if (gridContextMenu == null) {
            return 0;
        }
        int width = Math.max(120, gridContextMenu.definition().name().length() * 8 + 20);
        for (GridContextAction action : gridContextMenu.actions()) {
            width = Math.max(width, action.label().length() * 8 + 20);
        }
        return width;
    }

    private boolean isGridClick(int screenX, int screenY) {
        int dx = screenX - mousePressX;
        int dy = screenY - mousePressY;
        return dx * dx + dy * dy <= GRID_CLICK_DRAG_THRESHOLD_PIXELS * GRID_CLICK_DRAG_THRESHOLD_PIXELS;
    }

    private void openGridContextMenu(
            SimulationContext context,
            int screenX,
            int screenY,
            DraggedGridItem source
    ) {
        List<GridContextAction> actions = gridContextActions(source.source(), source.definition());
        if (actions.isEmpty()) {
            return;
        }
        int actionWidth = Math.max(120, source.definition().name().length() * 8 + 20);
        for (GridContextAction action : actions) {
            actionWidth = Math.max(actionWidth, action.label().length() * 8 + 20);
        }
        int width = actionWidth + GRID_CONTEXT_INFO_WIDTH;
        int height = GRID_CONTEXT_ROW_HEIGHT * (actions.size() + 1);
        int x = Math.min(screenX, Math.max(0, context.getConfig().getWidth() - width - 8));
        int y = Math.min(screenY, Math.max(0, context.getConfig().getHeight() - height - 8));
        gridContextMenu = new GridContextMenu(
                source.source(),
                source.sourceIndex(),
                source.item(),
                source.definition(),
                x,
                y,
                actions
        );
        keyringScrollOffset = 0;
        if ("key_ring".equals(source.item().itemId())) {
            int expandedHeight = gridContextMenuHeight();
            if (y + expandedHeight + 8 > context.getConfig().getHeight()) {
                y = Math.max(0, context.getConfig().getHeight() - expandedHeight - 8);
                gridContextMenu = new GridContextMenu(
                        source.source(),
                        source.sourceIndex(),
                        source.item(),
                        source.definition(),
                        x,
                        y,
                        actions
                );
            }
        }
    }

    private List<GridContextAction> gridContextActions(GridOwner source, DungeonCarryableDefinition definition) {
        List<GridContextAction> actions = new ArrayList<>();
        if (source == GridOwner.EQUIPMENT) {
            if (canUseGridItem(definition)) {
                actions.add(new GridContextAction(useGridItemLabel(definition), GridContextActionKind.USE));
            }
            actions.add(new GridContextAction("Unequip", GridContextActionKind.UNEQUIP));
        } else if (source == GridOwner.CONTAINER) {
            actions.add(new GridContextAction("Move to inventory", GridContextActionKind.MOVE_TO_INVENTORY));
        } else if (source == GridOwner.INVENTORY && isContainerOpen()) {
            actions.add(new GridContextAction("Move to container", GridContextActionKind.MOVE_TO_CONTAINER));
        }
        if (source != GridOwner.EQUIPMENT && canUseGridItem(definition) && !definition.isEquipment()) {
            actions.add(new GridContextAction(useGridItemLabel(definition), GridContextActionKind.USE));
        }
        if (source != GridOwner.EQUIPMENT && canEquipGridItem(definition)) {
            actions.add(new GridContextAction("Equip", GridContextActionKind.EQUIP));
        }
        if (canDropGridItem(definition)) {
            actions.add(new GridContextAction("Drop", GridContextActionKind.DROP));
        }
        return List.copyOf(actions);
    }

    private boolean canUseGridItem(DungeonCarryableDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.isEquipment()) {
            return false;
        }
        DungeonItemDefinition itemDefinition = definition.itemDefinition();
        if (itemDefinition.category() == DungeonItemCategory.FOOD) {
            return true;
        }
        return switch (itemDefinition.id()) {
            case "lantern_oil", "bandage", "antidote", "stamina_draught", "map_scrap", "note" -> true;
            default -> false;
        };
    }

    private String useGridItemLabel(DungeonCarryableDefinition definition) {
        if (definition == null) {
            return "Use";
        }
        if (definition.isEquipment()) {
            return "Equip";
        }
        DungeonItemDefinition itemDefinition = definition.itemDefinition();
        if (itemDefinition.category() == DungeonItemCategory.FOOD) {
            return "Eat";
        }
        return switch (itemDefinition.id()) {
            case "lantern_oil" -> "Fill";
            case "bandage" -> "Apply";
            case "antidote", "stamina_draught" -> "Drink";
            case "map_scrap", "note" -> "Read";
            default -> "Use";
        };
    }

    private boolean canEquipGridItem(DungeonCarryableDefinition definition) {
        return definition != null && definition.inventorySize() != null;
    }

    private boolean canDropGridItem(DungeonCarryableDefinition definition) {
        return definition != null;
    }

    private void performGridContextAction(
            SimulationContext context,
            GridContextMenu menu,
            GridContextAction action
    ) {
        if (!adminMode) {
            setInteractionNoise(SMALL_INTERACTION_NOISE);
        }
        switch (action.kind()) {
            case MOVE_TO_INVENTORY -> moveGridItemToFirstAvailable(menu, GridOwner.INVENTORY, context);
            case MOVE_TO_CONTAINER -> moveGridItemToFirstAvailable(menu, GridOwner.CONTAINER, context);
            case DROP -> dropGridItem(menu.source(), menu.sourceIndex(), menu.item(), menu.definition());
            case UNEQUIP -> unequipGridItem(menu.sourceIndex(), menu.item());
            case EQUIP -> equipGridItem(menu.source(), menu.sourceIndex(), menu.item(), menu.definition());
            case USE -> useGridItem(menu.source(), menu.sourceIndex(), menu.item(), menu.definition());
        }
    }

    private boolean shiftTransferHoveredGridItem(SimulationContext context, int screenX, int screenY) {
        if (!isContainerOpen()) {
            return false;
        }
        DraggedGridItem item = findGridItemAt(context, screenX, screenY);
        if (item == null || item.source() == GridOwner.EQUIPMENT) {
            return false;
        }
        GridOwner target = item.source() == GridOwner.INVENTORY ? GridOwner.CONTAINER : GridOwner.INVENTORY;
        moveGridItemToFirstAvailable(new GridContextMenu(
                item.source(),
                item.sourceIndex(),
                item.item(),
                item.definition(),
                screenX,
                screenY,
                List.of()
        ), target, context);
        gridContextMenu = null;
        draggedGridItem = null;
        if (!adminMode) {
            setInteractionNoise(SMALL_INTERACTION_NOISE);
        }
        return true;
    }

    private boolean tryUseHoveredGridItem(SimulationContext context) {
        if (pendingOilUse != null || (!inventoryOpen && !isContainerOpen())) {
            return false;
        }
        DraggedGridItem item = findGridItemAt(context, mouseScreenX, mouseScreenY);
        if (item == null) {
            return false;
        }
        if (!canUseGridItem(item.definition())) {
            return true;
        }
        useGridItem(item.source(), item.sourceIndex(), item.item(), item.definition());
        return true;
    }

    private void useGridItem(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition
    ) {
        if (definition.isEquipment()) {
            addNotification("Equipment UI is not wired yet.", Color.WHITE);
            return;
        }
        DungeonItemDefinition itemDefinition = definition.itemDefinition();
        if (itemDefinition.category() == DungeonItemCategory.FOOD) {
            useFoodItem(source, sourceIndex, item, itemDefinition);
            return;
        }
        if ("lantern_oil".equals(itemDefinition.id())) {
            if (fillPrimaryLanternFromOil(source, sourceIndex, item)) {
                return;
            }
            if (source == GridOwner.EQUIPMENT) {
                addNotification("Equip a lantern in primary.", ERROR_PROMPT_COLOR);
                return;
            }
            startLanternOilMode(source, sourceIndex, item, itemDefinition);
            return;
        }
        switch (itemDefinition.id()) {
            case "bandage" -> useBandage(source, sourceIndex, item);
            case "antidote" -> useAntitoxin(source, sourceIndex, item);
            case "stamina_draught" -> useEnduranceDraught(source, sourceIndex, item);
            case "map_scrap", "note" -> activeDocument = readableDocumentView(itemDefinition, item.properties());
            case "crowbar", "knife", "spear", "rock" -> addNotification("Swung " + definition.name() + ".", Color.WHITE);
            default -> {
                // Reserved for item-specific use behavior.
            }
        }
    }

    private boolean tryUseEquippedHotkey(int keyCode) {
        if (inventoryOpen || isContainerOpen() || pendingOilUse != null || activeDocument != null) {
            return false;
        }
        if (keyCode == KeyEvent.VK_F) {
            useEquipmentSlot(EquipmentSlot.PRIMARY);
            return true;
        }
        int secondaryIndex = switch (keyCode) {
            case KeyEvent.VK_1 -> 0;
            case KeyEvent.VK_2 -> 1;
            case KeyEvent.VK_3 -> 2;
            case KeyEvent.VK_4 -> 3;
            case KeyEvent.VK_5 -> 4;
            case KeyEvent.VK_6 -> 5;
            default -> -1;
        };
        if (secondaryIndex >= 0) {
            if (secondaryIndex < equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance())) {
                useEquipmentSlot(EquipmentSlot.secondarySlot(secondaryIndex));
            }
            return true;
        }
        return false;
    }

    private void swapPrimaryWithFirstSecondary() {
        if (!equipmentState.primarySwapEnabled(DungeonEquipmentLibrary.instance())) {
            addNotification("No quick-swap gear equipped.", ERROR_PROMPT_COLOR);
            return;
        }
        EquipmentSlot secondarySlot = EquipmentSlot.SECONDARY_1;
        DungeonInventoryItem primary = equipmentState.get(EquipmentSlot.PRIMARY).orElse(null);
        DungeonInventoryItem secondary = equipmentState.get(secondarySlot).orElse(null);
        if (primary == null && secondary == null) {
            return;
        }
        DungeonCarryableDefinition primaryDefinition = primary == null
                ? null
                : DungeonCarryableLibrary.instance().find(primary.itemId()).orElse(null);
        DungeonCarryableDefinition secondaryDefinition = secondary == null
                ? null
                : DungeonCarryableLibrary.instance().find(secondary.itemId()).orElse(null);
        if (primaryDefinition != null && !canEquipInSlot(secondarySlot, primaryDefinition)) {
            addNotification("Primary item does not fit quick slot 1.", ERROR_PROMPT_COLOR);
            return;
        }
        if (secondaryDefinition != null && !canEquipInSlot(EquipmentSlot.PRIMARY, secondaryDefinition)) {
            addNotification("Quick item cannot be held.", ERROR_PROMPT_COLOR);
            return;
        }
        if (secondary == null) {
            equipmentState.remove(EquipmentSlot.PRIMARY);
        } else {
            equipmentState.set(EquipmentSlot.PRIMARY, equippedSlotCopy(secondary));
        }
        if (primary == null) {
            equipmentState.remove(secondarySlot);
        } else {
            equipmentState.set(secondarySlot, equippedSlotCopy(primary));
        }
    }

    private DungeonInventoryItem equippedSlotCopy(DungeonInventoryItem item) {
        return new DungeonInventoryItem(
                item.itemId(),
                0,
                0,
                item.quantity(),
                item.properties()
        );
    }

    private void useEquipmentSlot(EquipmentSlot slot) {
        DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
        if (item == null) {
            return;
        }
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
        if (definition == null) {
            return;
        }
        if (isToggleableEquippedLight(definition)) {
            toggleEquippedLantern(slot, item);
            return;
        }
        if (canUseGridItem(definition)) {
            useGridItem(GridOwner.EQUIPMENT, slot.ordinal(), item, definition);
            return;
        }
        addNotification("Swung " + definition.name() + ".", Color.WHITE);
    }

    private boolean isToggleableEquippedLight(DungeonCarryableDefinition definition) {
        return definition != null &&
                definition.isItem() &&
                definition.itemDefinition().category() == DungeonItemCategory.LIGHT &&
                definition.itemDefinition().defaultProperties().containsKey("is_on");
    }

    private void toggleEquippedLantern(EquipmentSlot slot, DungeonInventoryItem lantern) {
        DungeonItemDefinition definition = DungeonItemLibrary.find(lantern.itemId()).orElse(null);
        if (definition == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>(lantern.properties());
        double fuel = numericProperty(properties, "fuel_remaining",
                numericProperty(definition.defaultProperties(), "fuel_remaining", 0.0));
        if (fuel <= 0.0) {
            addNotification("This light is out of fuel.", ERROR_PROMPT_COLOR);
            return;
        }
        boolean isOn = properties.get("is_on") instanceof Boolean value && value;
        properties.put("is_on", !isOn);
        equipmentState.set(slot, new DungeonInventoryItem(
                lantern.itemId(),
                0,
                0,
                lantern.quantity(),
                Map.copyOf(properties)
        ));
    }

    private void equipGridItem(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition
    ) {
        if (item == null || definition == null) {
            return;
        }
        EquipmentSlot targetSlot = firstAvailableEquipSlot(definition);
        if (targetSlot == null) {
            addNotification("No equipment slot available.", ERROR_PROMPT_COLOR);
            return;
        }

        List<DungeonInventoryItem> sourceItems = new ArrayList<>(gridItems(source));
        int currentIndex = currentGridItemIndex(sourceItems, sourceIndex, item);
        if (currentIndex < 0) {
            return;
        }
        DungeonInventoryItem equippedItem = new DungeonInventoryItem(
                item.itemId(),
                0,
                0,
                item.quantity(),
                item.properties()
        );
        sourceItems.remove(currentIndex);
        if (!setGridItems(source, sourceItems)) {
            return;
        }
        DungeonInventoryItem previous = equipmentState.set(targetSlot, equippedItem);
        if (!syncInventoryCapacityToEquipment()) {
            equipmentState.set(targetSlot, previous);
            setGridItems(source, gridItemsWithRestoredItem(sourceItems, currentIndex, item));
            addNotification("Can't resize inventory.", ERROR_PROMPT_COLOR);
            return;
        }
        gridContextMenu = null;
    }

    private void unequipGridItem(int sourceIndex, DungeonInventoryItem item) {
        EquipmentSlot slot = equipmentSlotFromIndex(sourceIndex);
        if (slot == null || item == null || equipmentState.get(slot).isEmpty()) {
            return;
        }
        DungeonInventoryItem removed = equipmentState.remove(slot);
        if (!syncInventoryCapacityToEquipment()) {
            equipmentState.set(slot, removed);
            addNotification("Inventory is too full.", ERROR_PROMPT_COLOR);
            return;
        }
        if (inventory.addNextAvailable(item.itemId(), item.quantity(), item.properties())) {
            gridContextMenu = null;
        } else {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            DungeonPoint dropCell = loadedArea == null ? null : chooseDropCell(dropOrigin(GridOwner.EQUIPMENT));
            if (definition != null && dropCell != null) {
                dropInventoryItemIntoWorld(item, definition, dropCell);
                gridContextMenu = null;
            } else {
                equipmentState.set(slot, removed);
                syncInventoryCapacityToEquipment();
                addNotification("Your inventory is full.", ERROR_PROMPT_COLOR);
            }
        }
    }

    private List<DungeonInventoryItem> gridItemsWithRestoredItem(
            List<DungeonInventoryItem> items,
            int index,
            DungeonInventoryItem item
    ) {
        List<DungeonInventoryItem> restored = new ArrayList<>(items);
        restored.add(Math.max(0, Math.min(index, restored.size())), item);
        return restored;
    }

    private EquipmentSlot equipmentSlotFromIndex(int index) {
        EquipmentSlot[] slots = EquipmentSlot.values();
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    private EquipmentSlot firstAvailableEquipSlot(DungeonCarryableDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (definition.isEquipment()) {
            EquipmentSlot bodySlot = definition.equipmentDefinition().apparelSlot();
            return equipmentState.get(bodySlot).isEmpty() ? bodySlot : null;
        }
        if (equipmentState.get(EquipmentSlot.PRIMARY).isEmpty()) {
            return EquipmentSlot.PRIMARY;
        }
        int unlocked = equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance());
        for (int i = 0; i < unlocked; i++) {
            EquipmentSlot slot = EquipmentSlot.secondarySlot(i);
            if (equipmentState.get(slot).isEmpty() && canEquipInSlot(slot, definition)) {
                return slot;
            }
        }
        return null;
    }

    private boolean canEquipInSecondary(EquipmentSlot slot, DungeonCarryableDefinition definition) {
        DungeonItemSize size = definition == null ? null : definition.inventorySize();
        if (size == null) {
            return false;
        }
        EquipmentAllowance allowance = secondaryAllowance(slot);
        if (allowance == EquipmentAllowance.ANY) {
            return isHolsterLargeButNotHuge(definition);
        }
        if (size.width() > 1 || size.height() > 2) {
            return false;
        }
        if (!definition.isItem()) {
            return false;
        }
        DungeonItemDefinition itemDefinition = definition.itemDefinition();
        return switch (allowance) {
            case TINY -> size.width() == 1 && size.height() == 1 && isTinySecondaryItem(itemDefinition);
            case FOOD -> itemDefinition.category() == DungeonItemCategory.FOOD;
            case MEDICAL -> itemDefinition.category() == DungeonItemCategory.MEDICAL;
            case OIL -> "lantern_oil".equals(itemDefinition.id());
            case SMALL_CONSUMABLE -> isSmallConsumableSecondaryItem(itemDefinition);
            case SMALL_TOOL -> itemDefinition.category() == DungeonItemCategory.TOOL;
            case SMALL_NON_LARGE, BROAD_SMALL -> true;
            case KEYS_ONLY -> itemDefinition.category() == DungeonItemCategory.KEY;
            case NONE -> false;
            case ANY -> true;
        };
    }

    private EquipmentAllowance secondaryAllowance(EquipmentSlot slot) {
        if (slot == null || !slot.isSecondary()) {
            return EquipmentAllowance.NONE;
        }
        List<EquipmentAllowance> allowances = secondaryAllowances();
        int index = slot.secondaryIndex();
        if (index < 0 || index >= allowances.size()) {
            return EquipmentAllowance.NONE;
        }
        return allowances.get(index);
    }

    private List<EquipmentAllowance> secondaryAllowances() {
        List<EquipmentAllowance> allowances = new ArrayList<>();
        allowances.add(EquipmentAllowance.SMALL_CONSUMABLE);
        allowances.add(EquipmentAllowance.SMALL_CONSUMABLE);
        DungeonEquipmentLibrary library = DungeonEquipmentLibrary.instance();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            DungeonInventoryItem item = equipmentState.get(slot).orElse(null);
            if (item == null) {
                continue;
            }
            DungeonEquipmentDefinition definition = library.find(item.itemId()).orElse(null);
            if (definition == null) {
                continue;
            }
            int slots = (int) numericProperty(definition.defaultProperties(), "secondary_slots", 0.0);
            EquipmentAllowance allowance = equipmentAllowance(definition.defaultProperties());
            for (int i = 0; i < slots && allowances.size() < DungeonEquipmentState.MAX_SECONDARY_SLOTS; i++) {
                allowances.add(allowance);
            }
        }
        return List.copyOf(allowances);
    }

    private EquipmentAllowance equipmentAllowance(Map<String, Object> properties) {
        String allowance = propertyString(properties, "secondary_allowance").toUpperCase(Locale.US);
        if (allowance.isBlank()) {
            return EquipmentAllowance.NONE;
        }
        try {
            return EquipmentAllowance.valueOf(allowance);
        } catch (IllegalArgumentException ignored) {
            return EquipmentAllowance.NONE;
        }
    }

    private boolean isTinySecondaryItem(DungeonItemDefinition definition) {
        return definition.category() == DungeonItemCategory.KEY ||
                "lockpick".equals(definition.id()) ||
                "note".equals(definition.id()) ||
                "map_scrap".equals(definition.id());
    }

    private boolean isSmallConsumableSecondaryItem(DungeonItemDefinition definition) {
        return definition.category() == DungeonItemCategory.FOOD ||
                definition.category() == DungeonItemCategory.MEDICAL ||
                definition.category() == DungeonItemCategory.DOCUMENT ||
                "lantern_oil".equals(definition.id()) ||
                "flare".equals(definition.id());
    }

    private boolean isHolsterLargeButNotHuge(DungeonCarryableDefinition definition) {
        DungeonItemSize size = definition == null ? null : definition.inventorySize();
        if (size == null || definition.isEquipment()) {
            return false;
        }
        if ("floor_lantern".equals(definition.id())) {
            return true;
        }
        return size.width() <= 2 && size.height() <= 2;
    }

    private void useFoodItem(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonItemDefinition definition
    ) {
        double hunger = numericProperty(item.properties(), "hunger_amount",
                numericProperty(definition.defaultProperties(), "hunger_amount",
                        numericProperty(definition.defaultProperties(), "heal_amount", 0.0)));
        if (hunger <= 0.0) {
            return;
        }
        if (characterState.get(CharacterProperty.HUNGER) > 95.0) {
            addNotification("You are full.", ERROR_PROMPT_COLOR);
            return;
        }
        consumeOneGridItem(source, sourceIndex, item);
        characterState.addEffect(new ActiveCharacterEffect(
                "eating",
                Double.POSITIVE_INFINITY,
                hunger,
                CharacterEffectMode.ADD
        ));
        String effectId = propertyString(definition.defaultProperties(), "effect_id");
        if (!effectId.isBlank()) {
            double duration = "sick".equals(effectId)
                    ? randomRange(SICK_DURATION_MIN_SECONDS, SICK_DURATION_MAX_SECONDS)
                    : 20.0;
            characterState.addEffect(new ActiveCharacterEffect(effectId, duration, 1.0, CharacterEffectMode.ADD));
        }
    }

    private void useBandage(GridOwner source, int sourceIndex, DungeonInventoryItem item) {
        if (characterState.get(CharacterProperty.HEALTH) >= characterState.get(CharacterProperty.MAX_HEALTH)) {
            addNotification("You are already healthy.", ERROR_PROMPT_COLOR);
            return;
        }
        if (!consumeOneGridItem(source, sourceIndex, item)) {
            return;
        }
        characterState.addEffect(new ActiveCharacterEffect(
                "regeneration",
                15.0,
                1.0,
                CharacterEffectMode.ADD
        ));
    }

    private void useAntitoxin(GridOwner source, int sourceIndex, DungeonInventoryItem item) {
        if (!consumeOneGridItem(source, sourceIndex, item)) {
            return;
        }
        characterState.removeNeutralizableEffects(DungeonEffectLibrary.instance());
        characterState.addSanity(10.0);
        characterState.addEffect(new ActiveCharacterEffect(
                "neutralization",
                3.0,
                1.0,
                CharacterEffectMode.ADD
        ));
    }

    private void useEnduranceDraught(GridOwner source, int sourceIndex, DungeonInventoryItem item) {
        if (!consumeOneGridItem(source, sourceIndex, item)) {
            return;
        }
        double duration = randomRange(ENDURANCE_DURATION_MIN_SECONDS, ENDURANCE_DURATION_MAX_SECONDS);
        characterState.addEffect(new ActiveCharacterEffect(
                "elevated_stamina",
                duration,
                1.0,
                CharacterEffectMode.ADD
        ));
        characterState.addEffect(new ActiveCharacterEffect(
                "endurance_boost",
                duration,
                1.0,
                CharacterEffectMode.ADD
        ));
    }

    private double randomRange(double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return low + SEED_SOURCE.nextDouble() * (high - low);
    }

    private void startLanternOilMode(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonItemDefinition definition
    ) {
        pendingOilUse = new PendingOilUse(source, sourceIndex, item, definition);
        addNotification("Select lantern to fill.", Color.WHITE);
    }

    private boolean fillPrimaryLanternFromOil(GridOwner oilSource, int oilIndex, DungeonInventoryItem oilItem) {
        DungeonInventoryItem lantern = equipmentState.get(EquipmentSlot.PRIMARY).orElse(null);
        if (lantern == null || !"floor_lantern".equals(lantern.itemId())) {
            return false;
        }
        DungeonItemDefinition lanternDefinition = DungeonItemLibrary.find(lantern.itemId()).orElse(null);
        if (lanternDefinition == null) {
            return false;
        }
        double oilAmount = numericProperty(oilItem.properties(), "fuel_amount",
                numericProperty(DungeonItemLibrary.require("lantern_oil").defaultProperties(), "fuel_amount",
                        numericProperty(DungeonItemLibrary.require("lantern_oil").defaultProperties(),
                                "fuel_amount_min", 0.0)));
        if (oilAmount <= 0.0) {
            return false;
        }
        Map<String, Object> properties = new HashMap<>(lantern.properties());
        double currentFuel = numericProperty(properties, "fuel_remaining",
                numericProperty(lanternDefinition.defaultProperties(), "fuel_remaining", 0.0));
        double maxFuel = numericProperty(lanternDefinition.defaultProperties(), "burn_time", MAX_LIGHT_FUEL);
        properties.put("fuel_remaining", Math.min(maxFuel, currentFuel + oilAmount));
        properties.put("is_on", false);
        equipmentState.set(EquipmentSlot.PRIMARY, new DungeonInventoryItem(
                lantern.itemId(),
                0,
                0,
                lantern.quantity(),
                Map.copyOf(properties)
        ));
        consumeOneGridItem(oilSource, oilIndex, oilItem);
        return true;
    }

    private boolean consumeOneGridItem(GridOwner source, int sourceIndex, DungeonInventoryItem item) {
        if (source == GridOwner.EQUIPMENT) {
            EquipmentSlot slot = equipmentSlotFromIndex(sourceIndex);
            if (slot == null || item == null || equipmentState.get(slot).isEmpty()) {
                return false;
            }
            if (item.quantity() > 1) {
                equipmentState.set(slot, new DungeonInventoryItem(
                        item.itemId(),
                        0,
                        0,
                        item.quantity() - 1,
                        item.properties()
                ));
            } else {
                equipmentState.remove(slot);
            }
            return true;
        }
        List<DungeonInventoryItem> items = new ArrayList<>(gridItems(source));
        int currentIndex = currentGridItemIndex(items, sourceIndex, item);
        if (currentIndex < 0) {
            return false;
        }
        DungeonInventoryItem current = items.get(currentIndex);
        if (current.quantity() > 1) {
            items.set(currentIndex, new DungeonInventoryItem(
                    current.itemId(),
                    current.x(),
                    current.y(),
                    current.quantity() - 1,
                    current.properties()
            ));
        } else {
            items.remove(currentIndex);
        }
        return setGridItems(source, items);
    }

    private void moveGridItemToFirstAvailable(
            GridContextMenu menu,
            GridOwner targetOwner,
            SimulationContext context
    ) {
        if (menu.source() == targetOwner) {
            return;
        }
        ItemGridView targetView = gridViewForOwner(context, targetOwner);
        if (targetView == null) {
            addNotification("No target container is open.", ERROR_PROMPT_COLOR);
            return;
        }

        List<DungeonInventoryItem> sourceItems = new ArrayList<>(gridItems(menu.source()));
        int sourceIndex = currentGridItemIndex(sourceItems, menu.sourceIndex(), menu.item());
        if (sourceIndex < 0) {
            return;
        }
        DungeonInventoryItem sourceItem = sourceItems.remove(sourceIndex);
        List<DungeonInventoryItem> targetItems = new ArrayList<>(gridItems(targetOwner));
        if (!stackIntoGrid(targetItems, sourceItem, menu.definition())) {
            DungeonInventoryItem placed = firstAvailableGridPlacement(
                    targetItems,
                    sourceItem,
                    menu.definition(),
                    targetView
            );
            if (placed == null) {
                addNotification(targetOwner == GridOwner.INVENTORY
                        ? "Your inventory is full."
                        : "No room in container.", ERROR_PROMPT_COLOR);
                return;
            }
            targetItems.add(placed);
        }

        if (setGridItems(menu.source(), sourceItems)) {
            setGridItems(targetOwner, targetItems);
        }
    }

    private void dropGridItem(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition
    ) {
        if (loadedArea == null || item == null || definition == null) {
            return;
        }
        if (source == GridOwner.EQUIPMENT) {
            EquipmentSlot slot = equipmentSlotFromIndex(sourceIndex);
            if (slot == null || equipmentState.get(slot).isEmpty()) {
                return;
            }
            DropOrigin origin = dropOrigin(source);
            DungeonPoint dropCell = chooseDropCell(origin);
            if (dropCell == null) {
                addNotification("Can't drop item here.", ERROR_PROMPT_COLOR);
                return;
            }
            DungeonInventoryItem removed = equipmentState.remove(slot);
            if (!syncInventoryCapacityToEquipment()) {
                equipmentState.set(slot, removed);
                addNotification("Inventory is too full.", ERROR_PROMPT_COLOR);
                return;
            }
            dropInventoryItemIntoWorld(item, definition, dropCell);
            return;
        }
        List<DungeonInventoryItem> sourceItems = new ArrayList<>(gridItems(source));
        int currentIndex = currentGridItemIndex(sourceItems, sourceIndex, item);
        if (currentIndex < 0) {
            return;
        }
        DropOrigin origin = dropOrigin(source);
        DungeonPoint dropCell = chooseDropCell(origin);
        if (dropCell == null) {
            addNotification("Can't drop item here.", ERROR_PROMPT_COLOR);
            return;
        }

        sourceItems.remove(currentIndex);
        if (!setGridItems(source, sourceItems)) {
            return;
        }
        dropInventoryItemIntoWorld(item, definition, dropCell);
    }

    private void dropInventoryItemIntoWorld(
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition,
            DungeonPoint dropCell
    ) {
        DungeonItem dropped = new DungeonItem(item.itemId(), dropCell, DungeonDirection.NORTH);
        droppedWorldItems.add(dropped);
        Map<String, Object> properties = new HashMap<>(item.properties());
        if (definition.isItem() && definition.itemDefinition().category() == DungeonItemCategory.LIGHT) {
            properties.put("is_on", false);
        }
        properties.put("quantity", item.quantity());
        String key = placedItemStateKey(dropped);
        persistentItemStates.put(key, PersistentItemState.placedState(dropped, Map.copyOf(properties)));
        if (definition.isItem()) {
            initializeLightState(definition.itemDefinition(), dropped);
        }
    }

    private void dropHoveredGridItem(SimulationContext context) {
        if (mouseScreenX < 0 || mouseScreenY < 0 || (!inventoryOpen && !isContainerOpen())) {
            return;
        }
        DraggedGridItem item = findGridItemAt(context, mouseScreenX, mouseScreenY);
        if (item != null) {
            dropGridItem(item.source(), item.sourceIndex(), item.item(), item.definition());
            gridContextMenu = null;
        }
    }

    private DropOrigin dropOrigin(GridOwner source) {
        if (source == GridOwner.CONTAINER && openContainerItem != null) {
            return new DropOrigin(
                    openContainerItem.position(),
                    itemCellCenterX(openContainerItem),
                    itemCellCenterY(openContainerItem)
            );
        }
        int x = (int) Math.floor(playerX);
        int y = (int) Math.floor(playerY);
        return new DropOrigin(new DungeonPoint(x, y), playerX, playerY);
    }

    private DungeonPoint chooseDropCell(DropOrigin origin) {
        List<DungeonPoint> candidates = new ArrayList<>();
        for (int y = origin.centerCell().y() - 1; y <= origin.centerCell().y() + 1; y++) {
            for (int x = origin.centerCell().x() - 1; x <= origin.centerCell().x() + 1; x++) {
                candidates.add(new DungeonPoint(x, y));
            }
        }
        for (int i = candidates.size() - 1; i > 0; i--) {
            int swap = SEED_SOURCE.nextInt(i + 1);
            DungeonPoint temp = candidates.get(i);
            candidates.set(i, candidates.get(swap));
            candidates.set(swap, temp);
        }
        for (DungeonPoint candidate : candidates) {
            if (canDropAt(origin, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canDropAt(DropOrigin origin, DungeonPoint cell) {
        return isLoadedCell(cell) &&
                isOccupiedCell(cell) &&
                !hasWorldItemAt(cell) &&
                !cellBlockedByWall(cell) &&
                canReachDropCell(origin, cell);
    }

    private boolean isLoadedCell(DungeonPoint cell) {
        DungeonRect bounds = loadedArea.getLoadedBounds();
        return cell.x() >= bounds.minX() &&
                cell.x() + 1 <= bounds.maxX() &&
                cell.y() >= bounds.minY() &&
                cell.y() + 1 <= bounds.maxY();
    }

    private boolean isOccupiedCell(DungeonPoint cell) {
        double x = cell.x() + 0.5;
        double y = cell.y() + 0.5;
        DungeonRect area = new DungeonRect(cell.x(), cell.y(), cell.x() + 1, cell.y() + 1);
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(area)) {
            for (DungeonOccupiedArea occupied : placement.getWorldOccupiedAreas()) {
                if (occupiedAreaContains(occupied, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean occupiedAreaContains(DungeonOccupiedArea area, double x, double y) {
        List<DungeonPoint> points = area.getPoints();
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            DungeonPoint a = points.get(i);
            DungeonPoint b = points.get(j);
            boolean crosses = (a.y() > y) != (b.y() > y);
            if (crosses) {
                double xAtY = (double) (b.x() - a.x()) * (y - a.y()) / (double) (b.y() - a.y()) + a.x();
                if (x < xAtY) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private boolean hasWorldItemAt(DungeonPoint cell) {
        return hasWorldItemAt(cell, null);
    }

    private boolean hasWorldItemAt(DungeonPoint cell, DungeonItem ignoredItem) {
        DungeonRect area = new DungeonRect(cell.x(), cell.y(), cell.x() + 1, cell.y() + 1);
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(area)) {
            for (DungeonItem item : placement.getWorldItems()) {
                if (!item.equals(ignoredItem) && !isPersistentDeleted(item) && item.position().equals(cell)) {
                    return true;
                }
            }
        }
        for (DungeonItem item : droppedItemsIntersecting(area)) {
            if (!item.equals(ignoredItem) && !isPersistentDeleted(item) && item.position().equals(cell)) {
                return true;
            }
        }
        for (DungeonItem item : randomItemsIntersecting(area)) {
            if (!item.equals(ignoredItem) && !isPersistentDeleted(item) && item.position().equals(cell)) {
                return true;
            }
        }
        return false;
    }

    private boolean cellBlockedByWall(DungeonPoint cell) {
        double centerX = cell.x() + 0.5;
        double centerY = cell.y() + 0.5;
        DungeonRect bounds = new DungeonRect(cell.x(), cell.y(), cell.x() + 1, cell.y() + 1);
        for (DungeonLine wall : loadedArea.getWallsIntersecting(bounds)) {
            if (lineIntersectsRect(wall, centerX, centerY, 0.9, 0.9)) {
                return true;
            }
        }
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(bounds)) {
            if (lineIntersectsRect(wall, centerX, centerY, 0.9, 0.9)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReachDropCell(DropOrigin origin, DungeonPoint cell) {
        double targetX = cell.x() + 0.5;
        double targetY = cell.y() + 0.5;
        DungeonRect bounds = new DungeonRect(
                (int) Math.floor(Math.min(origin.x(), targetX)) - 1,
                (int) Math.floor(Math.min(origin.y(), targetY)) - 1,
                (int) Math.ceil(Math.max(origin.x(), targetX)) + 1,
                (int) Math.ceil(Math.max(origin.y(), targetY)) + 1
        );
        for (DungeonLine wall : loadedArea.getWallsIntersecting(bounds)) {
            if (segmentsIntersect(origin.x(), origin.y(), targetX, targetY,
                    wall.start().x(), wall.start().y(), wall.end().x(), wall.end().y())) {
                return false;
            }
        }
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(bounds)) {
            if (segmentsIntersect(origin.x(), origin.y(), targetX, targetY,
                    wall.start().x(), wall.start().y(), wall.end().x(), wall.end().y())) {
                return false;
            }
        }
        return true;
    }

    private boolean stackIntoGrid(
            List<DungeonInventoryItem> items,
            DungeonInventoryItem sourceItem,
            DungeonCarryableDefinition definition
    ) {
        int stackLimit = stackLimit(definition);
        if (stackLimit <= 1) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            DungeonInventoryItem existing = items.get(i);
            if (!existing.itemId().equals(sourceItem.itemId()) ||
                    !existing.properties().equals(sourceItem.properties()) ||
                    existing.quantity() + sourceItem.quantity() > stackLimit) {
                continue;
            }
            items.set(i, new DungeonInventoryItem(
                    existing.itemId(),
                    existing.x(),
                    existing.y(),
                    existing.quantity() + sourceItem.quantity(),
                    existing.properties()
            ));
            return true;
        }
        return false;
    }

    private boolean canStackItem(DungeonCarryableDefinition definition) {
        return stackLimit(definition) > 1;
    }

    private boolean tryStackOnGridItem(
            List<DungeonInventoryItem> items,
            DungeonInventoryItem sourceItem,
            DungeonCarryableDefinition sourceDefinition,
            int targetIndex
    ) {
        if (targetIndex < 0 || targetIndex >= items.size()) {
            return false;
        }
        DungeonInventoryItem target = items.get(targetIndex);
        if (!target.itemId().equals(sourceItem.itemId()) ||
                !target.properties().equals(sourceItem.properties()) ||
                !canStackItem(sourceDefinition)) {
            return false;
        }
        int stackLimit = stackLimit(sourceDefinition);
        if (target.quantity() + sourceItem.quantity() > stackLimit) {
            return false;
        }
        items.set(targetIndex, new DungeonInventoryItem(
                target.itemId(),
                target.x(),
                target.y(),
                target.quantity() + sourceItem.quantity(),
                target.properties()
        ));
        return true;
    }

    private int currentGridItemIndex(List<DungeonInventoryItem> items, int preferredIndex, DungeonInventoryItem item) {
        if (preferredIndex >= 0 && preferredIndex < items.size() && items.get(preferredIndex).equals(item)) {
            return preferredIndex;
        }
        return items.indexOf(item);
    }

    private DungeonInventoryItem firstAvailableGridPlacement(
            List<DungeonInventoryItem> existing,
            DungeonInventoryItem sourceItem,
            DungeonCarryableDefinition definition,
            ItemGridView targetView
    ) {
        DungeonItemSize size = definition.inventorySize();
        if (size == null) {
            return null;
        }
        int maxX = targetView.widthCells() - size.width();
        int maxY = targetView.heightCells() - size.height();
        if (maxX < 0 || maxY < 0) {
            return null;
        }
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                DungeonInventoryItem candidate = new DungeonInventoryItem(
                        sourceItem.itemId(),
                        x,
                        y,
                        sourceItem.quantity(),
                        sourceItem.properties()
                );
                if (canPlaceInGrid(existing, candidate, definition, targetView)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private DraggedGridItem findGridItemAt(SimulationContext context, int screenX, int screenY) {
        for (ItemGridView view : activeGridViews(context)) {
            if (view.owner() == GridOwner.EQUIPMENT) {
                if (screenX < view.x() || screenX >= view.x() + view.cellSize() ||
                        screenY < view.y() || screenY >= view.y() + view.cellSize()) {
                    continue;
                }
                DungeonInventoryItem item = equipmentState.get(view.equipmentSlot()).orElse(null);
                DungeonCarryableDefinition definition = item == null
                        ? null
                        : DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
                if (item != null && definition != null) {
                    return new DraggedGridItem(
                            GridOwner.EQUIPMENT,
                            view.equipmentSlot().ordinal(),
                            item,
                            definition,
                            screenX - view.x(),
                            screenY - view.y()
                    );
                }
                continue;
            }
            List<DungeonInventoryItem> items = gridItems(view.owner());
            for (int i = items.size() - 1; i >= 0; i--) {
                DungeonInventoryItem item = items.get(i);
                DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
                if (definition == null || definition.inventorySize() == null) {
                    continue;
                }
                DungeonItemSize size = definition.inventorySize();
                int itemX = view.x() + item.x() * view.cellSize();
                int itemY = view.y() + item.y() * view.cellSize();
                int itemWidth = size.width() * view.cellSize();
                int itemHeight = size.height() * view.cellSize();
                if (screenX >= itemX && screenX < itemX + itemWidth &&
                        screenY >= itemY && screenY < itemY + itemHeight) {
                    return new DraggedGridItem(
                            view.owner(),
                            i,
                            item,
                            definition,
                            screenX - itemX,
                            screenY - itemY
                    );
                }
            }
        }
        return null;
    }

    private void completeGridDrag(SimulationContext context, int screenX, int screenY) {
        ItemGridView targetView = null;
        for (ItemGridView view : activeGridViews(context)) {
            if (screenX >= view.x() && screenX < view.x() + view.widthCells() * view.cellSize() &&
                    screenY >= view.y() && screenY < view.y() + view.heightCells() * view.cellSize()) {
                targetView = view;
                break;
            }
        }
        if (targetView == null) {
            return;
        }

        if (targetView.owner() == GridOwner.EQUIPMENT) {
            completeDragToEquipmentSlot(context, targetView);
            return;
        }
        if (draggedGridItem.source() == GridOwner.EQUIPMENT) {
            completeDragFromEquipmentSlot(targetView);
            return;
        }

        DungeonItemSize draggedSize = draggedGridItem.definition().inventorySize();
        if (draggedSize == null) {
            return;
        }
        double draggedCenterX = screenX - draggedGridItem.offsetX() + draggedSize.width() * targetView.cellSize() / 2.0;
        double draggedCenterY = screenY - draggedGridItem.offsetY() + draggedSize.height() * targetView.cellSize() / 2.0;
        int targetItemIndex = gridItemIndexAt(targetView, (int) Math.round(draggedCenterX), (int) Math.round(draggedCenterY));
        int targetX = (int) Math.round((draggedCenterX - targetView.x()) / targetView.cellSize() - draggedSize.width() / 2.0);
        int targetY = (int) Math.round((draggedCenterY - targetView.y()) / targetView.cellSize() - draggedSize.height() / 2.0);
        targetX = Math.max(0, Math.min(targetView.widthCells() - draggedSize.width(), targetX));
        targetY = Math.max(0, Math.min(targetView.heightCells() - draggedSize.height(), targetY));
        DungeonInventoryItem moved = new DungeonInventoryItem(
                draggedGridItem.item().itemId(),
                targetX,
                targetY,
                draggedGridItem.item().quantity(),
                draggedGridItem.item().properties()
        );

        List<DungeonInventoryItem> sourceItems = new ArrayList<>(gridItems(draggedGridItem.source()));
        if (draggedGridItem.sourceIndex() < 0 || draggedGridItem.sourceIndex() >= sourceItems.size()) {
            return;
        }
        sourceItems.remove(draggedGridItem.sourceIndex());

        if (draggedGridItem.source() == targetView.owner()) {
            if (targetItemIndex >= 0 && targetItemIndex != draggedGridItem.sourceIndex()) {
                int adjustedTargetIndex = targetItemIndex;
                if (targetItemIndex > draggedGridItem.sourceIndex()) {
                    adjustedTargetIndex--;
                }
                DungeonInventoryItem targetItem = adjustedTargetIndex >= 0 && adjustedTargetIndex < sourceItems.size()
                        ? sourceItems.get(adjustedTargetIndex)
                        : null;
                if (targetItem != null && "key_ring".equals(targetItem.itemId())) {
                    if (!canStoreOnKeyring(draggedGridItem.item(), draggedGridItem.definition())) {
                        addNotification("That key cannot go on the key ring.", ERROR_PROMPT_COLOR);
                        return;
                    }
                    List<KeyringKeyEntry> keys = new ArrayList<>(keyringKeys(targetItem));
                    if (!appendKeyringKey(keys, draggedGridItem.item())) {
                        return;
                    }
                    sourceItems.set(adjustedTargetIndex, keyringWithKeys(targetItem, keys));
                    setGridItems(targetView.owner(), sourceItems);
                    setInteractionNoise(KEYRING_INTERACTION_NOISE);
                    return;
                }
                if (adjustedTargetIndex >= 0 && adjustedTargetIndex < sourceItems.size() &&
                        tryStackOnGridItem(sourceItems, draggedGridItem.item(), draggedGridItem.definition(), adjustedTargetIndex)) {
                    setGridItems(targetView.owner(), sourceItems);
                    return;
                }
                if (targetItem != null &&
                        targetItem.itemId().equals(draggedGridItem.item().itemId()) &&
                        !canStackItem(draggedGridItem.definition())) {
                    addNotification("This item cannot stack.", ERROR_PROMPT_COLOR);
                    return;
                }
            }
            if (!canPlaceInGrid(sourceItems, moved, draggedGridItem.definition(), targetView)) {
                addNotification("Can't place item there.", ERROR_PROMPT_COLOR);
                return;
            }
            sourceItems.add(moved);
            setGridItems(targetView.owner(), sourceItems);
            return;
        }

        List<DungeonInventoryItem> targetItems = new ArrayList<>(gridItems(targetView.owner()));
        if (targetItemIndex >= 0) {
            DungeonInventoryItem targetItem = targetItems.get(targetItemIndex);
            if ("key_ring".equals(targetItem.itemId())) {
                if (!canStoreOnKeyring(draggedGridItem.item(), draggedGridItem.definition())) {
                    addNotification("That key cannot go on the key ring.", ERROR_PROMPT_COLOR);
                    return;
                }
                List<KeyringKeyEntry> keys = new ArrayList<>(keyringKeys(targetItem));
                if (!appendKeyringKey(keys, draggedGridItem.item())) {
                    return;
                }
                targetItems.set(targetItemIndex, keyringWithKeys(targetItem, keys));
                if (setGridItems(draggedGridItem.source(), sourceItems)) {
                    setGridItems(targetView.owner(), targetItems);
                    setInteractionNoise(KEYRING_INTERACTION_NOISE);
                }
                return;
            }
            if (tryStackOnGridItem(targetItems, draggedGridItem.item(), draggedGridItem.definition(), targetItemIndex)) {
                if (setGridItems(draggedGridItem.source(), sourceItems)) {
                    setGridItems(targetView.owner(), targetItems);
                }
                return;
            }
            if (targetItem.itemId().equals(draggedGridItem.item().itemId()) && !canStackItem(draggedGridItem.definition())) {
                addNotification("This item cannot stack.", ERROR_PROMPT_COLOR);
                return;
            }
        }
        if (!canPlaceInGrid(targetItems, moved, draggedGridItem.definition(), targetView)) {
            addNotification("Can't place item there.", ERROR_PROMPT_COLOR);
            return;
        }
        targetItems.add(moved);
        if (setGridItems(draggedGridItem.source(), sourceItems)) {
            setGridItems(targetView.owner(), targetItems);
        }
    }

    private void completeDragToEquipmentSlot(SimulationContext context, ItemGridView targetView) {
        EquipmentSlot targetSlot = targetView.equipmentSlot();
        DungeonInventoryItem targetItem = targetSlot == null ? null : equipmentState.get(targetSlot).orElse(null);
        if (targetItem != null && "key_ring".equals(targetItem.itemId()) &&
                draggedGridItem.source() != GridOwner.EQUIPMENT &&
                canStoreOnKeyring(draggedGridItem.item(), draggedGridItem.definition())) {
            List<DungeonInventoryItem> sourceItems = new ArrayList<>(gridItems(draggedGridItem.source()));
            int sourceIndex = currentGridItemIndex(sourceItems, draggedGridItem.sourceIndex(), draggedGridItem.item());
            if (sourceIndex < 0) {
                return;
            }
            List<KeyringKeyEntry> keys = new ArrayList<>(keyringKeys(targetItem));
            if (!appendKeyringKey(keys, draggedGridItem.item())) {
                return;
            }
            sourceItems.remove(sourceIndex);
            if (setGridItems(draggedGridItem.source(), sourceItems)) {
                equipmentState.set(targetSlot, keyringWithKeys(targetItem, keys));
                setInteractionNoise(KEYRING_INTERACTION_NOISE);
            }
            return;
        }
        if (targetSlot == null || !canEquipInSlot(targetSlot, draggedGridItem.definition())) {
            addNotification("Can't equip item there.", ERROR_PROMPT_COLOR);
            return;
        }
        DungeonInventoryItem moved = new DungeonInventoryItem(
                draggedGridItem.item().itemId(),
                0,
                0,
                draggedGridItem.item().quantity(),
                draggedGridItem.item().properties()
        );

        if (draggedGridItem.source() == GridOwner.EQUIPMENT) {
            EquipmentSlot sourceSlot = equipmentSlotFromIndex(draggedGridItem.sourceIndex());
            if (sourceSlot == null || sourceSlot == targetSlot) {
                return;
            }
            DungeonInventoryItem sourceItem = equipmentState.get(sourceSlot).orElse(null);
            if (targetItem != null) {
                DungeonCarryableDefinition targetDefinition =
                        DungeonCarryableLibrary.instance().find(targetItem.itemId()).orElse(null);
                if (!canEquipInSlot(sourceSlot, targetDefinition)) {
                    addNotification("Can't swap those items.", ERROR_PROMPT_COLOR);
                    return;
                }
                equipmentState.set(sourceSlot, new DungeonInventoryItem(
                        targetItem.itemId(),
                        0,
                        0,
                        targetItem.quantity(),
                        targetItem.properties()
                ));
            } else {
                equipmentState.remove(sourceSlot);
            }
            equipmentState.set(targetSlot, moved);
            if (!syncInventoryCapacityToEquipment()) {
                equipmentState.set(sourceSlot, sourceItem);
                equipmentState.set(targetSlot, targetItem);
                addNotification("Inventory is too full.", ERROR_PROMPT_COLOR);
            }
            return;
        }

        List<DungeonInventoryItem> originalSourceItems = new ArrayList<>(gridItems(draggedGridItem.source()));
        List<DungeonInventoryItem> sourceItems = new ArrayList<>(originalSourceItems);
        int sourceIndex = currentGridItemIndex(sourceItems, draggedGridItem.sourceIndex(), draggedGridItem.item());
        if (sourceIndex < 0) {
            return;
        }
        sourceItems.remove(sourceIndex);
        if (targetItem != null) {
            DungeonCarryableDefinition targetDefinition =
                    DungeonCarryableLibrary.instance().find(targetItem.itemId()).orElse(null);
            ItemGridView sourceView = gridViewForOwner(context, draggedGridItem.source());
            DungeonInventoryItem replacement = new DungeonInventoryItem(
                    targetItem.itemId(),
                    draggedGridItem.item().x(),
                    draggedGridItem.item().y(),
                    targetItem.quantity(),
                    targetItem.properties()
            );
            if (sourceView == null || targetDefinition == null ||
                    !canPlaceInGrid(sourceItems, replacement, targetDefinition, sourceView)) {
                addNotification("Can't swap those items.", ERROR_PROMPT_COLOR);
                return;
            }
            sourceItems.add(replacement);
        }
        if (setGridItems(draggedGridItem.source(), sourceItems)) {
            DungeonInventoryItem previous = equipmentState.set(targetSlot, moved);
            if (!syncInventoryCapacityToEquipment()) {
                equipmentState.set(targetSlot, previous);
                setGridItems(draggedGridItem.source(), originalSourceItems);
                addNotification("Inventory is too full.", ERROR_PROMPT_COLOR);
            }
        }
    }

    private void completeDragFromEquipmentSlot(ItemGridView targetView) {
        EquipmentSlot sourceSlot = equipmentSlotFromIndex(draggedGridItem.sourceIndex());
        if (sourceSlot == null) {
            return;
        }
        List<DungeonInventoryItem> targetItems = new ArrayList<>(gridItems(targetView.owner()));
        DungeonItemSize draggedSize = draggedGridItem.definition().inventorySize();
        if (draggedSize == null) {
            return;
        }
        int targetX = Math.max(0, Math.min(
                targetView.widthCells() - draggedSize.width(),
                (int) Math.floor((double) (mouseScreenX - targetView.x()) / targetView.cellSize())
        ));
        int targetY = Math.max(0, Math.min(
                targetView.heightCells() - draggedSize.height(),
                (int) Math.floor((double) (mouseScreenY - targetView.y()) / targetView.cellSize())
        ));
        DungeonInventoryItem moved = new DungeonInventoryItem(
                draggedGridItem.item().itemId(),
                targetX,
                targetY,
                draggedGridItem.item().quantity(),
                draggedGridItem.item().properties()
        );
        if (!canPlaceInGrid(targetItems, moved, draggedGridItem.definition(), targetView)) {
            addNotification("Can't place item there.", ERROR_PROMPT_COLOR);
            return;
        }
        DungeonInventoryItem removed = equipmentState.remove(sourceSlot);
        if (!syncInventoryCapacityToEquipment()) {
            equipmentState.set(sourceSlot, removed);
            addNotification("Inventory is too full.", ERROR_PROMPT_COLOR);
            return;
        }
        targetItems.add(moved);
        if (!setGridItems(targetView.owner(), targetItems)) {
            equipmentState.set(sourceSlot, removed);
            syncInventoryCapacityToEquipment();
        }
    }

    private boolean canEquipInSlot(EquipmentSlot slot, DungeonCarryableDefinition definition) {
        if (slot == null || definition == null) {
            return false;
        }
        if (slot == EquipmentSlot.PRIMARY) {
            return true;
        }
        if (slot.isSecondary()) {
            if (slot.secondaryIndex() >= equipmentState.unlockedSecondarySlots(DungeonEquipmentLibrary.instance()) ||
                    definition.isEquipment()) {
                return false;
            }
            if ("floor_lantern".equals(definition.id())) {
                return slot == EquipmentSlot.SECONDARY_1 &&
                        equipmentState.secondaryLightEnabled(DungeonEquipmentLibrary.instance());
            }
            return canEquipInSecondary(slot, definition);
        }
        return definition.isEquipment() && definition.equipmentDefinition().apparelSlot() == slot;
    }

    private int gridItemIndexAt(ItemGridView view, int screenX, int screenY) {
        List<DungeonInventoryItem> items = gridItems(view.owner());
        for (int i = items.size() - 1; i >= 0; i--) {
            DungeonInventoryItem item = items.get(i);
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition == null || definition.inventorySize() == null) {
                continue;
            }
            DungeonItemSize size = definition.inventorySize();
            int itemX = view.x() + item.x() * view.cellSize();
            int itemY = view.y() + item.y() * view.cellSize();
            int itemWidth = size.width() * view.cellSize();
            int itemHeight = size.height() * view.cellSize();
            if (screenX >= itemX && screenX < itemX + itemWidth &&
                    screenY >= itemY && screenY < itemY + itemHeight) {
                return i;
            }
        }
        return -1;
    }

    private boolean canPlaceInGrid(
            List<DungeonInventoryItem> existing,
            DungeonInventoryItem candidate,
            DungeonCarryableDefinition candidateDefinition,
            ItemGridView view
    ) {
        DungeonItemSize candidateSize = candidateDefinition.inventorySize();
        if (candidateSize == null ||
                candidate.x() + candidateSize.width() > view.widthCells() ||
                candidate.y() + candidateSize.height() > view.heightCells()) {
            return false;
        }
        for (DungeonInventoryItem item : existing) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (definition != null &&
                    definition.inventorySize() != null &&
                    inventoryItemsOverlap(candidate, candidateSize, item, definition.inventorySize())) {
                return false;
            }
        }
        return true;
    }

    private List<DungeonInventoryItem> gridItems(GridOwner owner) {
        if (owner == GridOwner.INVENTORY) {
            return inventory.getItems();
        }
        if (owner == GridOwner.EQUIPMENT) {
            return equipmentState.equippedItemList();
        }
        if (!isContainerOpen()) {
            return List.of();
        }
        return ensureContainerState(openContainerItem, openContainerDefinition).contents();
    }

    private boolean setGridItems(GridOwner owner, List<DungeonInventoryItem> items) {
        if (owner == GridOwner.INVENTORY) {
            return inventory.replaceAll(items);
        }
        if (owner == GridOwner.EQUIPMENT) {
            return false;
        }
        if (!isContainerOpen()) {
            return false;
        }
        ContainerPersistentState state = ensureContainerState(openContainerItem, openContainerDefinition);
        containerPersistentStates.put(openContainerKey, state.withContents(items));
        return true;
    }

    private boolean replaceGridItem(
            GridOwner owner,
            int sourceIndex,
            DungeonInventoryItem expected,
            DungeonInventoryItem replacement
    ) {
        if (owner == GridOwner.EQUIPMENT) {
            EquipmentSlot slot = equipmentSlotFromIndex(sourceIndex);
            if (slot == null || equipmentState.get(slot).isEmpty()) {
                return false;
            }
            equipmentState.set(slot, replacement);
            return true;
        }
        List<DungeonInventoryItem> items = new ArrayList<>(gridItems(owner));
        int currentIndex = currentGridItemIndex(items, sourceIndex, expected);
        if (currentIndex < 0) {
            return false;
        }
        items.set(currentIndex, replacement);
        return setGridItems(owner, items);
    }

    private List<ItemGridView> activeGridViews(SimulationContext context) {
        List<ItemGridView> equipmentViews = equipmentSlotViews(context);
        if (isContainerOpen()) {
            ContainerPersistentState state = ensureContainerState(openContainerItem, openContainerDefinition);
            DungeonItemSize capacity = containerCapacity(state.properties());
            if (capacity == null) {
                return equipmentViews;
            }
            List<ItemGridView> views = new ArrayList<>(containerGridViews(context, capacity));
            views.addAll(equipmentViews);
            return List.copyOf(views);
        }
        if (inventoryOpen) {
            List<ItemGridView> views = new ArrayList<>();
            views.add(inventoryGridView(context));
            views.addAll(equipmentViews);
            return List.copyOf(views);
        }
        return List.of();
    }

    private ItemGridView gridViewForOwner(SimulationContext context, GridOwner owner) {
        for (ItemGridView view : activeGridViews(context)) {
            if (view.owner() == owner) {
                return view;
            }
        }
        return null;
    }

    private ItemGridView inventoryGridView(SimulationContext context) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(900, width - 80);
        int panelHeight = Math.min(860, height - 80);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        int gridMargin = 18;
        DungeonItemSize inventoryCapacity = currentInventoryCapacity();
        int cellSize = Math.max(12, Math.min(
                (panelWidth - 340) / inventoryCapacity.width(),
                (panelHeight - gridMargin * 2) / inventoryCapacity.height()
        ));
        int gridWidth = cellSize * inventoryCapacity.width();
        int gridHeight = cellSize * inventoryCapacity.height();
        int gridX = panelX + panelWidth - gridMargin - gridWidth;
        int gridY = panelY + panelHeight / 2 - gridHeight / 2;
        return new ItemGridView(GridOwner.INVENTORY, gridX, gridY, cellSize,
                inventoryCapacity.width(), inventoryCapacity.height());
    }

    private List<ItemGridView> containerGridViews(SimulationContext context, DungeonItemSize capacity) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int panelWidth = Math.min(920, width - 60);
        int panelHeight = Math.min(860, height - 80);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        DungeonItemSize inventoryCapacity = currentInventoryCapacity();
        int gap = 56;
        int titleHeight = 56;
        int cellSize = Math.max(10, Math.min(
                (panelWidth - gap - 56) / (inventoryCapacity.width() + capacity.width()),
                (panelHeight - titleHeight - 36) / Math.max(inventoryCapacity.height(), capacity.height())
        ));
        int inventoryGridWidth = cellSize * inventoryCapacity.width();
        int containerGridWidth = cellSize * capacity.width();
        int totalGridWidth = inventoryGridWidth + gap + containerGridWidth;
        int inventoryGridX = panelX + panelWidth / 2 - totalGridWidth / 2;
        int containerGridX = inventoryGridX + inventoryGridWidth + gap;
        int gridY = panelY + titleHeight;
        return List.of(
                new ItemGridView(GridOwner.INVENTORY, inventoryGridX, gridY, cellSize,
                        inventoryCapacity.width(), inventoryCapacity.height()),
                new ItemGridView(GridOwner.CONTAINER, containerGridX, gridY, cellSize,
                        capacity.width(), capacity.height())
        );
    }

    private DungeonItemSize currentInventoryCapacity() {
        return new DungeonItemSize(inventory.getWidth(), inventory.getHeight());
    }

    private boolean syncInventoryCapacityToEquipment() {
        DungeonItemSize capacity = equipmentState.currentInventorySize(DungeonEquipmentLibrary.instance());
        return inventory.resize(capacity.width(), capacity.height());
    }

    private String effectLabel(ActiveCharacterEffect effect) {
        if (hasHiddenTimer(effect)) {
            return String.format(
                    Locale.US,
                    "%s  %.2fx  ?",
                    effectDisplayName(effect),
                    effect.getStrength()
            );
        }
        if (hasVisibleTimer(effect)) {
            return String.format(
                    Locale.US,
                    "%s  %.2fx  %s",
                    effectDisplayName(effect),
                    effect.getStrength(),
                    effectTimeLabel(effect)
            );
        }
        return String.format(Locale.US, "%s  %.2fx", effectDisplayName(effect), effect.getStrength());
    }

    private String effectDisplayName(ActiveCharacterEffect effect) {
        return DungeonEffectLibrary.instance()
                .find(effect.getEffectId())
                .map(CharacterEffectDefinition::getDisplayName)
                .orElse(effect.getEffectId());
    }

    private boolean hasVisibleTimer(ActiveCharacterEffect effect) {
        return effect != null &&
                !Double.isInfinite(effect.getDurationRemaining()) &&
                !hasHiddenTimer(effect) &&
                !isAmbientEffect(effect.getEffectId());
    }

    private boolean hasHiddenTimer(ActiveCharacterEffect effect) {
        return effect != null &&
                DungeonEffectLibrary.instance()
                        .find(effect.getEffectId())
                        .map(CharacterEffectDefinition::hasHiddenDuration)
                        .orElse(false);
    }

    private String effectTimeLabel(ActiveCharacterEffect effect) {
        return String.format(Locale.US, "%.1fs", effect.getDurationRemaining());
    }

    private boolean isAmbientEffect(String effectId) {
        return "lit".equals(effectId) ||
                "darkness".equals(effectId) ||
                "running".equals(effectId) ||
                "starving".equals(effectId);
    }

    private void drawStatusBars(SimulationContext context, Graphics2D graphics) {
        int width = context.getConfig().getWidth();
        int height = context.getConfig().getHeight();
        int barHeight = 18;
        int y = height - barHeight;
        int segmentWidth = width / 4;

        drawStatusBar(
                graphics,
                0,
                y,
                segmentWidth,
                barHeight,
                "Health",
                characterState.get(CharacterProperty.HEALTH),
                Math.max(1.0, characterState.get(CharacterProperty.MAX_HEALTH)),
                new Color(190, 48, 55)
        );
        drawStatusBar(
                graphics,
                segmentWidth,
                y,
                segmentWidth,
                barHeight,
                "Sanity",
                characterState.get(CharacterProperty.SANITY),
                100.0,
                new Color(117, 80, 190)
        );
        drawStatusBar(
                graphics,
                segmentWidth * 2,
                y,
                segmentWidth,
                barHeight,
                "Hunger",
                characterState.get(CharacterProperty.HUNGER),
                100.0,
                new Color(202, 139, 45)
        );
        drawStatusBar(
                graphics,
                segmentWidth * 3,
                y,
                width - segmentWidth * 3,
                barHeight,
                "Stamina",
                characterState.get(CharacterProperty.STAMINA),
                Math.max(1.0, characterState.get(CharacterProperty.MAX_STAMINA)),
                new Color(58, 142, 198)
        );
        if (adminMode) {
            drawAdminNoiseValue(graphics, width, y);
        }
    }

    private void drawAdminNoiseValue(Graphics2D graphics, int width, int statusY) {
        graphics.setFont(STATUS_BAR_FONT);
        String text = "Noise " + Math.round(characterState.getNoiseLevel());
        FontMetrics metrics = graphics.getFontMetrics();
        int paddingX = 8;
        int paddingY = 4;
        int boxWidth = metrics.stringWidth(text) + paddingX * 2;
        int boxHeight = metrics.getHeight() + paddingY * 2;
        int x = width - boxWidth - 10;
        int y = statusY - boxHeight - 8;
        graphics.setColor(new Color(24, 24, 24, 220));
        graphics.fillRect(x, y, boxWidth, boxHeight);
        graphics.setColor(new Color(190, 220, 255));
        graphics.drawRect(x, y, boxWidth - 1, boxHeight - 1);
        graphics.drawString(text, x + paddingX, y + paddingY + metrics.getAscent());
    }

    private void drawStatusBar(
            Graphics2D graphics,
            int x,
            int y,
            int width,
            int height,
            String label,
            double value,
            double max,
            Color fill
    ) {
        double ratio = max <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / max));
        graphics.setColor(new Color(24, 24, 24, 225));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(fill);
        graphics.fillRect(x, y, (int) Math.round(width * ratio), height);
        graphics.setColor(Color.BLACK);
        graphics.drawRect(x, y, Math.max(0, width - 1), Math.max(0, height - 1));

        graphics.setFont(STATUS_BAR_FONT);
        String text = label + " " + Math.round(Math.max(0.0, value)) + "/" + Math.round(max);
        FontMetrics metrics = graphics.getFontMetrics();
        int textX = x + width / 2 - metrics.stringWidth(text) / 2;
        int textY = y + height / 2 + metrics.getAscent() / 2 - 2;
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX + 1, textY + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX, textY);
    }

    private void drawPlayer(Graphics2D graphics, ViewTransform transform) {
        java.awt.Stroke oldStroke = graphics.getStroke();
        int x1 = transform.worldToScreenX(playerX - PLAYER_SIZE / 2.0);
        int y1 = transform.worldToScreenY(playerY - PLAYER_SIZE / 2.0);
        int x2 = transform.worldToScreenX(playerX + PLAYER_SIZE / 2.0);
        int y2 = transform.worldToScreenY(playerY + PLAYER_SIZE / 2.0);
        graphics.setColor(new Color(158, 78, 255));
        int centerX = transform.worldToScreenX(playerX);
        int centerY = transform.worldToScreenY(playerY);
        int width = Math.max(1, Math.abs(x2 - x1));
        int height = Math.max(1, Math.abs(y2 - y1));
        graphics.fillOval(Math.min(x1, x2), Math.min(y1, y2), width, height);
        int arrowLength = Math.max(10, Math.max(width, height));
        int arrowEndX = centerX + (int) Math.round(facingX * arrowLength);
        int arrowEndY = centerY + (int) Math.round(facingY * arrowLength);
        graphics.setColor(new Color(218, 180, 255));
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(centerX, centerY, arrowEndX, arrowEndY);
        double leftX = -facingY;
        double leftY = facingX;
        int headBackX = arrowEndX - (int) Math.round(facingX * 6.0);
        int headBackY = arrowEndY - (int) Math.round(facingY * 6.0);
        graphics.drawLine(
                arrowEndX,
                arrowEndY,
                headBackX + (int) Math.round(leftX * 4.0),
                headBackY + (int) Math.round(leftY * 4.0)
        );
        graphics.drawLine(
                arrowEndX,
                arrowEndY,
                headBackX - (int) Math.round(leftX * 4.0),
                headBackY - (int) Math.round(leftY * 4.0)
        );
        graphics.setStroke(oldStroke);
    }

    private double pixelsPerBlock(SimulationContext context) {
        return context.getConfig().getWidth() / VISIBLE_BLOCKS_ACROSS;
    }

    private void loadVisibleArea(SimulationContext context) {
        DungeonRect view = visibleWorldArea(context);
        if (loadedArea != null && loadedArea.getLoadedBounds().contains(view)) {
            return;
        }
        DungeonRect loadBounds = chunkAlignedLoadBounds(view);
        loadedArea = generationService.loadArea(seed, generationConfig, loadBounds);
        synchronizeDroppedItemsWithLoadedArea();
        synchronizeRandomItemsWithLoadedArea();
        populateRandomContainers();
        populateRandomHazards();
        populateRandomMapDetails();
        populateRandomLooseItems();
        Logger.log(Logger.TAG.DEBUG, "MazeSimulationGame: loaded chunks bounds="
                + loadBounds.minX() + "," + loadBounds.minY() + " to "
                + loadBounds.maxX() + "," + loadBounds.maxY()
                + " placements=" + loadedArea.getPlacements().size()
                + " randomContainers=" + randomItemCountForGroup("container")
                + " randomHazards=" + randomItemCountForGroup("hazard")
                + " randomDetails=" + randomItemCountForGroup("detail")
                + " randomLoose=" + randomItemCountForGroup("loose"));
    }

    private int randomItemCountForGroup(String group) {
        if (group == null || group.isBlank()) {
            return 0;
        }
        String marker = ":" + group.trim() + ":";
        int count = 0;
        for (String key : randomWorldItemKeys.values()) {
            if (key != null && key.contains(marker)) {
                count++;
            }
        }
        return count;
    }

    private List<DungeonItem> droppedItemsIntersecting(DungeonRect area) {
        List<DungeonItem> items = new ArrayList<>();
        for (DungeonItem item : droppedWorldItems) {
            if (area.intersects(new DungeonRect(
                    item.position().x(),
                    item.position().y(),
                    item.position().x() + 1,
                    item.position().y() + 1
            ))) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private List<DungeonItem> randomItemsIntersecting(DungeonRect area) {
        List<DungeonItem> items = new ArrayList<>();
        for (DungeonItem item : randomWorldItems) {
            if (area.intersects(new DungeonRect(
                    item.position().x(),
                    item.position().y(),
                    item.position().x() + 1,
                    item.position().y() + 1
            ))) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private void addRandomWorldItem(String key, DungeonItem item) {
        addRandomWorldItem(key, item, Map.of());
    }

    private void addRandomWorldItem(String key, DungeonItem item, Map<String, Object> properties) {
        if (key == null || key.isBlank() || item == null || isPersistentDeletedByKey(key)) {
            return;
        }
        if (!randomWorldItems.contains(item)) {
            randomWorldItems.add(item);
        }
        randomWorldItemKeys.put(item, key.trim());
        randomWorldItemProperties.put(item, Map.copyOf(properties == null ? Map.of() : properties));
    }

    private boolean isPersistentDeletedByKey(String key) {
        PersistentItemState state = persistentItemStates.get(key);
        return state != null && state.deleted();
    }

    private void synchronizeDroppedItemsWithLoadedArea() {
        if (loadedArea == null) {
            return;
        }
        DungeonRect bounds = loadedArea.getLoadedBounds();
        for (int i = droppedWorldItems.size() - 1; i >= 0; i--) {
            DungeonItem item = droppedWorldItems.get(i);
            if (item.position().x() < bounds.minX() ||
                    item.position().x() + 1 > bounds.maxX() ||
                    item.position().y() < bounds.minY() ||
                    item.position().y() + 1 > bounds.maxY()) {
                droppedWorldItems.remove(i);
            }
        }
        for (PersistentItemState state : persistentItemStates.values()) {
            DungeonItem item = state.placedItem();
            if (item == null || state.deleted()) {
                continue;
            }
            if (item.position().x() >= bounds.minX() &&
                    item.position().x() + 1 <= bounds.maxX() &&
                    item.position().y() >= bounds.minY() &&
                    item.position().y() + 1 <= bounds.maxY() &&
                    !droppedWorldItems.contains(item)) {
                droppedWorldItems.add(item);
            }
        }
    }

    private void synchronizeRandomItemsWithLoadedArea() {
        if (loadedArea == null) {
            randomWorldItems.clear();
            randomWorldItemKeys.clear();
            randomWorldItemProperties.clear();
            return;
        }
        randomWorldItems.clear();
        randomWorldItemKeys.clear();
        randomWorldItemProperties.clear();
    }

    private void updateOwnershipAudit(double deltaSeconds) {
        ownershipAuditSeconds += Math.max(0.0, deltaSeconds);
        if (ownershipAuditSeconds < OWNERSHIP_AUDIT_INTERVAL_SECONDS) {
            return;
        }
        ownershipAuditSeconds = 0.0;
        auditItemOwnership();
    }

    private void auditItemOwnership() {
        List<String> warnings = new ArrayList<>();
        auditLoadedWorldItems(warnings);
        auditPersistentWorldState(warnings);
        auditGridItemReferences(warnings);
        if (!warnings.isEmpty()) {
            Logger.log(Logger.TAG.WARN, "MazeSimulationGame ownership audit found "
                    + warnings.size() + " issue(s): " + String.join(" | ", warnings));
        }
    }

    private void auditPersistentWorldState(List<String> warnings) {
        DungeonRect bounds = loadedArea == null ? null : loadedArea.getLoadedBounds();
        Set<String> activePlacedKeys = new HashSet<>();
        for (DungeonItem item : droppedWorldItems) {
            String key = placedItemStateKey(item);
            activePlacedKeys.add(key);
            PersistentItemState state = persistentItemStates.get(key);
            if (state == null || state.deleted() || state.placedItem() == null) {
                warnings.add("active placed item has no live persistent state " + key);
            }
            if (bounds != null && !cellWithinBounds(item.position(), bounds)) {
                warnings.add("active placed item outside loaded bounds " + key);
            }
        }
        if (bounds == null) {
            if (!droppedWorldItems.isEmpty()) {
                warnings.add("active placed item list is not empty without loaded bounds count=" + droppedWorldItems.size());
            }
            if (!randomWorldItems.isEmpty() || !randomWorldItemKeys.isEmpty()) {
                warnings.add("active random item state is not empty without loaded bounds count=" + randomWorldItems.size());
            }
            return;
        }
        for (DungeonItem item : randomWorldItems) {
            String key = randomWorldItemKeys.get(item);
            if (key == null || key.isBlank()) {
                warnings.add("active random item has no random key " + itemStateKey(item));
            }
            if (!randomWorldItemProperties.containsKey(item)) {
                warnings.add("active random item has no runtime properties " + itemStateKey(item));
            }
            if (!cellWithinBounds(item.position(), bounds)) {
                warnings.add("active random item outside loaded bounds " + itemStateKey(item));
            }
        }
        for (DungeonItem item : randomWorldItemProperties.keySet()) {
            if (!randomWorldItemKeys.containsKey(item)) {
                warnings.add("random item properties without active key " + itemStateKey(item));
            }
        }
        for (Map.Entry<String, PersistentItemState> entry : persistentItemStates.entrySet()) {
            PersistentItemState state = entry.getValue();
            DungeonItem item = state.placedItem();
            if (item == null || state.deleted()) {
                continue;
            }
            boolean inLoadedBounds = cellWithinBounds(item.position(), bounds);
            boolean active = activePlacedKeys.contains(entry.getKey());
            if (inLoadedBounds && !active) {
                warnings.add("placed persistent item in loaded bounds is not active " + entry.getKey());
            } else if (!inLoadedBounds && active) {
                warnings.add("placed persistent item outside loaded bounds is active " + entry.getKey());
            }
        }
    }

    private boolean cellWithinBounds(DungeonPoint cell, DungeonRect bounds) {
        return cell != null &&
                bounds != null &&
                cell.x() >= bounds.minX() &&
                cell.x() + 1 <= bounds.maxX() &&
                cell.y() >= bounds.minY() &&
                cell.y() + 1 <= bounds.maxY();
    }

    private void auditLoadedWorldItems(List<String> warnings) {
        Map<String, Integer> worldCounts = new HashMap<>();
        if (loadedArea != null) {
            for (DungeonPlacedArtifact placement : loadedArea.getPlacements()) {
                for (DungeonItem item : placement.getWorldItems()) {
                    if (!isPersistentDeleted(item)) {
                        incrementCount(worldCounts, "seed:" + itemStateKey(item));
                    }
                }
            }
        }
        for (DungeonItem item : droppedWorldItems) {
            incrementCount(worldCounts, "placed:" + itemStateKey(item));
        }
        for (DungeonItem item : randomWorldItems) {
            String key = randomWorldItemKeys.getOrDefault(item, itemStateKey(item));
            if (!isPersistentDeletedByKey(key)) {
                incrementCount(worldCounts, "random:" + key);
            }
        }
        for (Map.Entry<String, Integer> entry : worldCounts.entrySet()) {
            if (entry.getValue() > 1) {
                warnings.add("loaded world duplicate " + entry.getKey() + " count=" + entry.getValue());
            }
        }
    }

    private void auditGridItemReferences(List<String> warnings) {
        IdentityHashMap<DungeonInventoryItem, String> owners = new IdentityHashMap<>();
        auditGridItemsForOwner("inventory", inventory.getItems(), owners, warnings);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipmentState.get(slot).ifPresent(item ->
                    auditGridItemForOwner("equipment:" + slot.name().toLowerCase(), item, owners, warnings));
        }
        for (Map.Entry<String, ContainerPersistentState> entry : containerPersistentStates.entrySet()) {
            auditGridItemsForOwner("container:" + entry.getKey(), entry.getValue().contents(), owners, warnings);
        }
    }

    private void auditGridItemsForOwner(
            String owner,
            List<DungeonInventoryItem> items,
            IdentityHashMap<DungeonInventoryItem, String> owners,
            List<String> warnings
    ) {
        for (DungeonInventoryItem item : items) {
            auditGridItemForOwner(owner, item, owners, warnings);
        }
    }

    private void auditGridItemForOwner(
            String owner,
            DungeonInventoryItem item,
            IdentityHashMap<DungeonInventoryItem, String> owners,
            List<String> warnings
    ) {
        String previousOwner = owners.put(item, owner);
        if (previousOwner != null) {
            warnings.add("same inventory item object in " + previousOwner + " and " + owner
                    + " id=" + item.itemId());
        }
    }

    private void incrementCount(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private void centerCameraOnPlayer() {
        cameraX = playerX;
        cameraY = playerY;
    }

    private DungeonRect playerBounds(double centerX, double centerY) {
        double half = PLAYER_SIZE / 2.0;
        return new DungeonRect(
                (int) Math.floor(centerX - half - 1.0),
                (int) Math.floor(centerY - half - 1.0),
                (int) Math.ceil(centerX + half + 1.0),
                (int) Math.ceil(centerY + half + 1.0)
        );
    }

    private boolean lineIntersectsRect(DungeonLine line, double centerX, double centerY, double width, double height) {
        double minX = centerX - width / 2.0;
        double minY = centerY - height / 2.0;
        double maxX = centerX + width / 2.0;
        double maxY = centerY + height / 2.0;
        double x1 = line.start().x();
        double y1 = line.start().y();
        double x2 = line.end().x();
        double y2 = line.end().y();

        if (pointInRect(x1, y1, minX, minY, maxX, maxY) || pointInRect(x2, y2, minX, minY, maxX, maxY)) {
            return true;
        }
        return segmentsIntersect(x1, y1, x2, y2, minX, minY, maxX, minY) ||
                segmentsIntersect(x1, y1, x2, y2, maxX, minY, maxX, maxY) ||
                segmentsIntersect(x1, y1, x2, y2, maxX, maxY, minX, maxY) ||
                segmentsIntersect(x1, y1, x2, y2, minX, maxY, minX, minY);
    }

    private boolean lineIntersectsCircle(DungeonLine line, double centerX, double centerY, double radius) {
        double ax = line.start().x();
        double ay = line.start().y();
        double bx = line.end().x();
        double by = line.end().y();
        double abX = bx - ax;
        double abY = by - ay;
        double abLengthSquared = abX * abX + abY * abY;
        if (abLengthSquared <= 0.0) {
            return distance(centerX, centerY, ax, ay) <= radius;
        }
        double t = ((centerX - ax) * abX + (centerY - ay) * abY) / abLengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + abX * t;
        double closestY = ay + abY * t;
        return distance(centerX, centerY, closestX, closestY) <= radius;
    }

    private boolean pointInRect(double x, double y, double minX, double minY, double maxX, double maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    private boolean segmentsIntersect(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy,
            double dx,
            double dy
    ) {
        double d1 = direction(cx, cy, dx, dy, ax, ay);
        double d2 = direction(cx, cy, dx, dy, bx, by);
        double d3 = direction(ax, ay, bx, by, cx, cy);
        double d4 = direction(ax, ay, bx, by, dx, dy);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(double ax, double ay, double bx, double by, double cx, double cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }

    private DungeonRect chunkAlignedLoadBounds(DungeonRect view) {
        int chunkSize = generationConfig.getChunkSize();
        int minChunkX = Math.floorDiv(view.minX(), chunkSize) - 1;
        int minChunkY = Math.floorDiv(view.minY(), chunkSize) - 1;
        int maxChunkX = Math.floorDiv(view.maxX(), chunkSize) + 1;
        int maxChunkY = Math.floorDiv(view.maxY(), chunkSize) + 1;
        return new DungeonRect(
                minChunkX * chunkSize,
                minChunkY * chunkSize,
                (maxChunkX + 1) * chunkSize,
                (maxChunkY + 1) * chunkSize
        );
    }

    private static SimulationConfig defaultConfig() {
        return new SimulationConfig(
                "Dungeon Simulation",
                SCREEN_SIZE,
                SCREEN_SIZE,
                60,
                Color.BLACK,
                25L,
                true
        );
    }

    private record ViewTransform(double pixelsPerBlock, int originX, int originY) {
        static ViewTransform from(
                SimulationContext context,
                double cameraX,
                double cameraY,
                double pixelsPerBlock
        ) {
            int originX = (int) Math.round(context.getConfig().getWidth() / 2.0 - cameraX * pixelsPerBlock);
            int originY = (int) Math.round(context.getConfig().getHeight() / 2.0 - cameraY * pixelsPerBlock);
            return new ViewTransform(pixelsPerBlock, originX, originY);
        }

        int worldToScreenX(int worldX) {
            return (int) Math.round(originX + worldX * pixelsPerBlock);
        }

        int worldToScreenY(int worldY) {
            return (int) Math.round(originY + worldY * pixelsPerBlock);
        }

        int worldToScreenX(double worldX) {
            return (int) Math.round(originX + worldX * pixelsPerBlock);
        }

        int worldToScreenY(double worldY) {
            return (int) Math.round(originY + worldY * pixelsPerBlock);
        }

        double screenToWorldX(int screenX) {
            return (screenX - originX) / pixelsPerBlock;
        }

        double screenToWorldY(int screenY) {
            return (screenY - originY) / pixelsPerBlock;
        }
    }

    private record HoveredItem(DungeonItem item, DungeonItemDefinition definition, double distance) {}

    private record HoveredGridItem(DungeonInventoryItem item, DungeonCarryableDefinition definition) {}

    private record DraggedGridItem(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition,
            int offsetX,
            int offsetY
    ) {}

    private record ItemGridView(
            GridOwner owner,
            int x,
            int y,
            int cellSize,
            int widthCells,
            int heightCells,
            EquipmentSlot equipmentSlot
    ) {
        ItemGridView(
                GridOwner owner,
                int x,
                int y,
                int cellSize,
                int widthCells,
                int heightCells
        ) {
            this(owner, x, y, cellSize, widthCells, heightCells, null);
        }
    }

    private record EquipmentLayout(List<EquipmentSlotBox> boxes) {
        EquipmentLayout {
            boxes = List.copyOf(boxes == null ? List.of() : boxes);
        }
    }

    private record EquipmentSlotBox(EquipmentSlot slot, int x, int y, int size) {}

    private record GridContextMenu(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonCarryableDefinition definition,
            int x,
            int y,
            List<GridContextAction> actions
    ) {
        GridContextMenu {
            actions = List.copyOf(actions == null ? List.of() : actions);
        }
    }

    private record GridContextAction(String label, GridContextActionKind kind) {}

    private record TextSegment(String text, boolean bold) {}

    private record DropOrigin(DungeonPoint centerCell, double x, double y) {}

    private record PendingOilUse(
            GridOwner source,
            int sourceIndex,
            DungeonInventoryItem item,
            DungeonItemDefinition definition
    ) {}

    private record EquippedOil(EquipmentSlot slot, DungeonInventoryItem item, DungeonItemDefinition definition) {}

    private record PendingKeyPlacement(DungeonInventoryItem item) {}

    private record KeyringKeyEntry(String itemId, String keyId, Map<String, Object> properties) {
        KeyringKeyEntry {
            itemId = itemId == null || itemId.isBlank() ? "small_key" : itemId.trim();
            keyId = keyId == null ? "" : keyId.trim();
            properties = Map.copyOf(properties == null ? Map.of() : properties);
        }
    }

    private record ReadDocumentView(String title, List<String> paragraphs) {
        ReadDocumentView {
            title = title == null || title.isBlank() ? "Document" : title.trim();
            paragraphs = List.copyOf(paragraphs == null ? List.of() : paragraphs);
        }
    }

    private record PlayerHazardExposure(boolean toxic, boolean asphyxiation) {}

    private record RandomPlacementCell(
            DungeonPlacedArtifact placement,
            DungeonPoint cell,
            List<DungeonDirection> flatWallDirections
    ) {
        RandomPlacementCell {
            flatWallDirections = List.copyOf(flatWallDirections == null ? List.of() : flatWallDirections);
        }
    }

    private record InteractionTarget(DungeonItem item, DungeonItemDefinition definition, double distance) {}

    private record InteractionOption(InteractionButton button, String label, InteractionKind kind) {}

    private record InteractionAction(
            InteractionButton button,
            InteractionTarget target,
            String label,
            InteractionKind kind
    ) {}

    private enum InteractionButton {
        FLEXIBLE,
        PRIMARY,
        SECONDARY
    }

    private enum GridOwner {
        INVENTORY,
        CONTAINER,
        EQUIPMENT
    }

    private enum GridContextActionKind {
        MOVE_TO_INVENTORY,
        MOVE_TO_CONTAINER,
        DROP,
        UNEQUIP,
        EQUIP,
        USE
    }

    private enum InteractionKind {
        TOGGLE_LIGHT,
        PICK_UP,
        OPEN,
        PUSH,
        SEARCH,
        SAVE,
        MENU,
        TOGGLE,
        PULL,
        INSPECT,
        READ,
        FILL_LIGHT,
        LIGHT_BOMB,
        BREAK
    }

    private record Notification(String text, Color color, double remainingSeconds) {
        Notification withRemainingSeconds(double remainingSeconds) {
            return new Notification(text, color, remainingSeconds);
        }
    }

    private record PersistentItemState(boolean deleted, Map<String, Object> properties, DungeonItem placedItem) {
        PersistentItemState {
            properties = Map.copyOf(properties == null ? Map.of() : properties);
        }

        static PersistentItemState empty() {
            return new PersistentItemState(false, Map.of(), null);
        }

        static PersistentItemState deletedState() {
            return new PersistentItemState(true, Map.of(), null);
        }

        static PersistentItemState placedState(DungeonItem item, Map<String, Object> properties) {
            if (item == null) {
                throw new IllegalArgumentException("Placed persistent item cannot be null.");
            }
            return new PersistentItemState(false, properties, item);
        }

        PersistentItemState withDeleted(boolean deleted) {
            return new PersistentItemState(deleted, properties, placedItem);
        }

        PersistentItemState withProperties(Map<String, Object> properties) {
            return new PersistentItemState(deleted, properties, placedItem);
        }

        PersistentItemState withProperty(String key, Object value) {
            if (key == null || key.isBlank()) {
                return this;
            }
            Map<String, Object> updated = new HashMap<>(properties);
            if (value == null) {
                updated.remove(key.trim());
            } else {
                updated.put(key.trim(), value);
            }
            return withProperties(updated);
        }
    }

    private record ContainerPersistentState(
            DungeonPoint position,
            DungeonDirection direction,
            Map<String, Object> properties,
            List<DungeonInventoryItem> contents,
            boolean contentsGenerated
    ) {
        ContainerPersistentState {
            if (position == null) {
                throw new IllegalArgumentException("Container persistent position cannot be null.");
            }
            if (direction == null) {
                throw new IllegalArgumentException("Container persistent direction cannot be null.");
            }
            properties = Map.copyOf(properties == null ? Map.of() : properties);
            contents = List.copyOf(contents == null ? List.of() : contents);
        }

        static ContainerPersistentState empty(DungeonItem item) {
            return new ContainerPersistentState(
                    item.position(),
                    item.direction(),
                    Map.of(),
                    List.of(),
                    false
            );
        }

        ContainerPersistentState withContents(List<DungeonInventoryItem> contents) {
            return new ContainerPersistentState(position, direction, properties, contents, true);
        }

        ContainerPersistentState withProperties(Map<String, Object> properties) {
            return new ContainerPersistentState(position, direction, properties, contents, contentsGenerated);
        }

        ContainerPersistentState withPosition(DungeonPoint position, DungeonDirection direction) {
            return new ContainerPersistentState(position, direction, properties, contents, contentsGenerated);
        }
    }

    private record LightVisibility(DungeonItem item, double x, double y, double radius, double strength) {}

    private record ContainerLootEntry(
            DungeonItemDefinition itemDefinition,
            boolean equipmentTemplate,
            double weight
    ) {
        static ContainerLootEntry item(DungeonItemDefinition definition, double weight) {
            return new ContainerLootEntry(definition, false, Math.max(0.0, weight));
        }

        static ContainerLootEntry equipmentTemplate(double weight) {
            return new ContainerLootEntry(null, true, Math.max(0.0, weight));
        }
    }

    private record RandomContainerEntry(DungeonItemDefinition definition, double weight) {
        RandomContainerEntry {
            weight = Math.max(0.0, weight);
        }
    }

    private record RandomMapItemEntry(DungeonItemDefinition definition, double weight) {
        RandomMapItemEntry {
            weight = Math.max(0.0, weight);
        }
    }
}
