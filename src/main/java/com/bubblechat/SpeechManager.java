package com.bubblechat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.IntangibleUpdate;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.ModelUpdate;
import com.hypixel.hytale.protocol.NameplateUpdate;
import com.hypixel.hytale.protocol.ParticleAnimationFrame;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.protocol.RangeVector2f;
import com.hypixel.hytale.protocol.Size;
import com.hypixel.hytale.protocol.TransformUpdate;
import com.hypixel.hytale.protocol.EmitShape;
import com.hypixel.hytale.protocol.FXRenderMode;
import com.hypixel.hytale.protocol.ParticleRotationInfluence;
import com.hypixel.hytale.protocol.ParticleScaleRatioConstraint;
import com.hypixel.hytale.protocol.ParticleUVOption;
import com.hypixel.hytale.protocol.SoftParticle;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateParticleSpawners;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.protocol.packets.assets.UpdateParticleSystems;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.ItemPlayerAnimations;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemPlayerAnimations;

import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SpeechManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double PARTICLE_Y_OFFSET_DEFAULT = 3.70;
    private static final double BUBBLE_Y_OFFSET = 2.78;
    // Default player model bounding box top (box.max.y). Used to compute height adjustment for custom NPC models.
    private static final double DEFAULT_MODEL_TOP = 1.9;
    private static final long WORD_REVEAL_MS = 300;
    private static final long HOLD_AFTER_COMPLETE_MS = 3000;
    private static final int MAX_LINE_CHARS = 40;
    private static final int MAX_WORD_CHARS = 25;
    private static final long PAGE_PAUSE_MS = 1000;    // pause after last word before page flip
    private static final long PAGE_START_DELAY_MS = 750; // delay at start of new page (bubble shrinks first)
    private static final int MAX_SUPERPAGES = 3;
    private static final double PAGE_INDICATOR_Y_OFFSET_1L = -0.200; // 1-liner page indicator Y offset
    private static final double PAGE_INDICATOR_Y_OFFSET_2L = -0.450; // 2-liner page indicator Y offset
    private static final double PAGE_INDICATOR_Y_OFFSET_3L = -0.540; // 3-liner page indicator Y offset

    // Per-line-count Y adjustment (shifts both text + particle together)
    // Tuned so triangle pointer sits at consistent height across all line counts
    private static final double BUBBLE_Y_ADJUST_1L = -0.600;
    private static final double BUBBLE_Y_ADJUST_2L = -0.320;
    private static final double BUBBLE_Y_ADJUST_3L = -0.260;

    // Multi-line stacked nameplate positions
    private static final double LINE_GAP = 0.3;
    private static final double LINE1_NUDGE = -0.056;  // 7px down from equidistant
    private static final double LINE2_NUDGE = -0.016;  // 2px down from equidistant
    // 3-liner nudges (separate from 2-liner for independent calibration)
    private static final double LINE1_NUDGE_3L = -0.104;  // base(-0.056) + 6px down
    private static final double LINE2_NUDGE_3L = -0.032;  // 2px down
    private static final double LINE3_NUDGE_3L = 0.040;   // 1px up

    // Default virtual entity view range (used if no prefs available)
    private static final double VIEW_RANGE = 40.0;
    private static final double VIEW_RANGE_SQ = VIEW_RANGE * VIEW_RANGE;

    // 20ms overlap (320ms life, 300ms respawn)
    private static final float PARTICLE_LIFESPAN = 0.32f;
    private static final long BUBBLE_PARTICLE_RESPAWN_MS = 300;

    // Fade-out: 500ms particle that goes opacity 1→0 + shrinks to 0
    private static final float FADE_LIFESPAN = 0.5f;
    private static final long FADE_DURATION_MS = 500;

    // ---- Pre-rendered bubble variants (64px half-width steps, 15 variants) ----
    private static final int VARIANT_COUNT = 15;
    private static final String[][] VARIANT_TEXTURES = {
        {"BC_Bubble_0",  "Particles/Textures/BC/BC_Bubble_0.png",  "256",  "384"},
        {"BC_Bubble_1",  "Particles/Textures/BC/BC_Bubble_1.png",  "384",  "384"},
        {"BC_Bubble_2",  "Particles/Textures/BC/BC_Bubble_2.png",  "512",  "384"},
        {"BC_Bubble_3",  "Particles/Textures/BC/BC_Bubble_3.png",  "640",  "384"},
        {"BC_Bubble_4",  "Particles/Textures/BC/BC_Bubble_4.png",  "768",  "384"},
        {"BC_Bubble_5",  "Particles/Textures/BC/BC_Bubble_5.png",  "896",  "384"},
        {"BC_Bubble_6",  "Particles/Textures/BC/BC_Bubble_6.png",  "1024", "384"},
        {"BC_Bubble_7",  "Particles/Textures/BC/BC_Bubble_7.png",  "1152", "384"},
        {"BC_Bubble_8",  "Particles/Textures/BC/BC_Bubble_8.png",  "1280", "384"},
        {"BC_Bubble_9",  "Particles/Textures/BC/BC_Bubble_9.png",  "1408", "384"},
        {"BC_Bubble_10", "Particles/Textures/BC/BC_Bubble_10.png", "1536", "384"},
        {"BC_Bubble_11", "Particles/Textures/BC/BC_Bubble_11.png", "1664", "384"},
        {"BC_Bubble_12", "Particles/Textures/BC/BC_Bubble_12.png", "1792", "384"},
        {"BC_Bubble_13", "Particles/Textures/BC/BC_Bubble_13.png", "1920", "384"},
        {"BC_Bubble_14", "Particles/Textures/BC/BC_Bubble_14.png", "2048", "384"},
    };

    // ---- 2-liner bubble variants (same 15 widths, 512px tall) ----
    private static final String[][] VARIANT_2LINER_TEXTURES = {
        {"BC_2Liner_0",  "Particles/Textures/BC/BC_2Liner_0.png",  "256",  "512"},
        {"BC_2Liner_1",  "Particles/Textures/BC/BC_2Liner_1.png",  "384",  "512"},
        {"BC_2Liner_2",  "Particles/Textures/BC/BC_2Liner_2.png",  "512",  "512"},
        {"BC_2Liner_3",  "Particles/Textures/BC/BC_2Liner_3.png",  "640",  "512"},
        {"BC_2Liner_4",  "Particles/Textures/BC/BC_2Liner_4.png",  "768",  "512"},
        {"BC_2Liner_5",  "Particles/Textures/BC/BC_2Liner_5.png",  "896",  "512"},
        {"BC_2Liner_6",  "Particles/Textures/BC/BC_2Liner_6.png",  "1024", "512"},
        {"BC_2Liner_7",  "Particles/Textures/BC/BC_2Liner_7.png",  "1152", "512"},
        {"BC_2Liner_8",  "Particles/Textures/BC/BC_2Liner_8.png",  "1280", "512"},
        {"BC_2Liner_9",  "Particles/Textures/BC/BC_2Liner_9.png",  "1408", "512"},
        {"BC_2Liner_10", "Particles/Textures/BC/BC_2Liner_10.png", "1536", "512"},
        {"BC_2Liner_11", "Particles/Textures/BC/BC_2Liner_11.png", "1664", "512"},
        {"BC_2Liner_12", "Particles/Textures/BC/BC_2Liner_12.png", "1792", "512"},
        {"BC_2Liner_13", "Particles/Textures/BC/BC_2Liner_13.png", "1920", "512"},
        {"BC_2Liner_14", "Particles/Textures/BC/BC_2Liner_14.png", "2048", "512"},
    };

    // ---- 3-liner bubble variants (same 15 widths, 640px tall) ----
    private static final String[][] VARIANT_3LINER_TEXTURES = {
        {"BC_3Liner_0",  "Particles/Textures/BC/BC_3Liner_0.png",  "256",  "640"},
        {"BC_3Liner_1",  "Particles/Textures/BC/BC_3Liner_1.png",  "384",  "640"},
        {"BC_3Liner_2",  "Particles/Textures/BC/BC_3Liner_2.png",  "512",  "640"},
        {"BC_3Liner_3",  "Particles/Textures/BC/BC_3Liner_3.png",  "640",  "640"},
        {"BC_3Liner_4",  "Particles/Textures/BC/BC_3Liner_4.png",  "768",  "640"},
        {"BC_3Liner_5",  "Particles/Textures/BC/BC_3Liner_5.png",  "896",  "640"},
        {"BC_3Liner_6",  "Particles/Textures/BC/BC_3Liner_6.png",  "1024", "640"},
        {"BC_3Liner_7",  "Particles/Textures/BC/BC_3Liner_7.png",  "1152", "640"},
        {"BC_3Liner_8",  "Particles/Textures/BC/BC_3Liner_8.png",  "1280", "640"},
        {"BC_3Liner_9",  "Particles/Textures/BC/BC_3Liner_9.png",  "1408", "640"},
        {"BC_3Liner_10", "Particles/Textures/BC/BC_3Liner_10.png", "1536", "640"},
        {"BC_3Liner_11", "Particles/Textures/BC/BC_3Liner_11.png", "1664", "640"},
        {"BC_3Liner_12", "Particles/Textures/BC/BC_3Liner_12.png", "1792", "640"},
        {"BC_3Liner_13", "Particles/Textures/BC/BC_3Liner_13.png", "1920", "640"},
        {"BC_3Liner_14", "Particles/Textures/BC/BC_3Liner_14.png", "2048", "640"},
    };

    // ---- Light bubble variants (same widths, light base #E0E0E0) ----
    private static final String[][] VARIANT_TEXTURES_LIGHT = {
        {"BC_Bubble_Light_0",  "Particles/Textures/BC/BC_Bubble_Light_0.png",  "256",  "384"},
        {"BC_Bubble_Light_1",  "Particles/Textures/BC/BC_Bubble_Light_1.png",  "384",  "384"},
        {"BC_Bubble_Light_2",  "Particles/Textures/BC/BC_Bubble_Light_2.png",  "512",  "384"},
        {"BC_Bubble_Light_3",  "Particles/Textures/BC/BC_Bubble_Light_3.png",  "640",  "384"},
        {"BC_Bubble_Light_4",  "Particles/Textures/BC/BC_Bubble_Light_4.png",  "768",  "384"},
        {"BC_Bubble_Light_5",  "Particles/Textures/BC/BC_Bubble_Light_5.png",  "896",  "384"},
        {"BC_Bubble_Light_6",  "Particles/Textures/BC/BC_Bubble_Light_6.png",  "1024", "384"},
        {"BC_Bubble_Light_7",  "Particles/Textures/BC/BC_Bubble_Light_7.png",  "1152", "384"},
        {"BC_Bubble_Light_8",  "Particles/Textures/BC/BC_Bubble_Light_8.png",  "1280", "384"},
        {"BC_Bubble_Light_9",  "Particles/Textures/BC/BC_Bubble_Light_9.png",  "1408", "384"},
        {"BC_Bubble_Light_10", "Particles/Textures/BC/BC_Bubble_Light_10.png", "1536", "384"},
        {"BC_Bubble_Light_11", "Particles/Textures/BC/BC_Bubble_Light_11.png", "1664", "384"},
        {"BC_Bubble_Light_12", "Particles/Textures/BC/BC_Bubble_Light_12.png", "1792", "384"},
        {"BC_Bubble_Light_13", "Particles/Textures/BC/BC_Bubble_Light_13.png", "1920", "384"},
        {"BC_Bubble_Light_14", "Particles/Textures/BC/BC_Bubble_Light_14.png", "2048", "384"},
    };

    private static final String[][] VARIANT_2LINER_LIGHT_TEXTURES = {
        {"BC_2Liner_Light_0",  "Particles/Textures/BC/BC_2Liner_Light_0.png",  "256",  "512"},
        {"BC_2Liner_Light_1",  "Particles/Textures/BC/BC_2Liner_Light_1.png",  "384",  "512"},
        {"BC_2Liner_Light_2",  "Particles/Textures/BC/BC_2Liner_Light_2.png",  "512",  "512"},
        {"BC_2Liner_Light_3",  "Particles/Textures/BC/BC_2Liner_Light_3.png",  "640",  "512"},
        {"BC_2Liner_Light_4",  "Particles/Textures/BC/BC_2Liner_Light_4.png",  "768",  "512"},
        {"BC_2Liner_Light_5",  "Particles/Textures/BC/BC_2Liner_Light_5.png",  "896",  "512"},
        {"BC_2Liner_Light_6",  "Particles/Textures/BC/BC_2Liner_Light_6.png",  "1024", "512"},
        {"BC_2Liner_Light_7",  "Particles/Textures/BC/BC_2Liner_Light_7.png",  "1152", "512"},
        {"BC_2Liner_Light_8",  "Particles/Textures/BC/BC_2Liner_Light_8.png",  "1280", "512"},
        {"BC_2Liner_Light_9",  "Particles/Textures/BC/BC_2Liner_Light_9.png",  "1408", "512"},
        {"BC_2Liner_Light_10", "Particles/Textures/BC/BC_2Liner_Light_10.png", "1536", "512"},
        {"BC_2Liner_Light_11", "Particles/Textures/BC/BC_2Liner_Light_11.png", "1664", "512"},
        {"BC_2Liner_Light_12", "Particles/Textures/BC/BC_2Liner_Light_12.png", "1792", "512"},
        {"BC_2Liner_Light_13", "Particles/Textures/BC/BC_2Liner_Light_13.png", "1920", "512"},
        {"BC_2Liner_Light_14", "Particles/Textures/BC/BC_2Liner_Light_14.png", "2048", "512"},
    };

    private static final String[][] VARIANT_3LINER_LIGHT_TEXTURES = {
        {"BC_3Liner_Light_0",  "Particles/Textures/BC/BC_3Liner_Light_0.png",  "256",  "640"},
        {"BC_3Liner_Light_1",  "Particles/Textures/BC/BC_3Liner_Light_1.png",  "384",  "640"},
        {"BC_3Liner_Light_2",  "Particles/Textures/BC/BC_3Liner_Light_2.png",  "512",  "640"},
        {"BC_3Liner_Light_3",  "Particles/Textures/BC/BC_3Liner_Light_3.png",  "640",  "640"},
        {"BC_3Liner_Light_4",  "Particles/Textures/BC/BC_3Liner_Light_4.png",  "768",  "640"},
        {"BC_3Liner_Light_5",  "Particles/Textures/BC/BC_3Liner_Light_5.png",  "896",  "640"},
        {"BC_3Liner_Light_6",  "Particles/Textures/BC/BC_3Liner_Light_6.png",  "1024", "640"},
        {"BC_3Liner_Light_7",  "Particles/Textures/BC/BC_3Liner_Light_7.png",  "1152", "640"},
        {"BC_3Liner_Light_8",  "Particles/Textures/BC/BC_3Liner_Light_8.png",  "1280", "640"},
        {"BC_3Liner_Light_9",  "Particles/Textures/BC/BC_3Liner_Light_9.png",  "1408", "640"},
        {"BC_3Liner_Light_10", "Particles/Textures/BC/BC_3Liner_Light_10.png", "1536", "640"},
        {"BC_3Liner_Light_11", "Particles/Textures/BC/BC_3Liner_Light_11.png", "1664", "640"},
        {"BC_3Liner_Light_12", "Particles/Textures/BC/BC_3Liner_Light_12.png", "1792", "640"},
        {"BC_3Liner_Light_13", "Particles/Textures/BC/BC_3Liner_Light_13.png", "1920", "640"},
        {"BC_3Liner_Light_14", "Particles/Textures/BC/BC_3Liner_Light_14.png", "2048", "640"},
    };

    private static final float BUBBLE_SCALE_Y = 0.20f;
    private static final float BUBBLE_SCALE_X = 0.25f;
    private static double particleYOffset = 3.165;
    private static final int MAX_TILES = 14;
    private static final float MAX_WEIGHTED_WIDTH = 35.0f;

    private final Map<UUID, SpeechState> activeSpeech = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generationCounters = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingReveals = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> pendingParticleLoops = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastParticleSendTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<ScheduledFuture<?>>> pendingMouthAnims = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> selfVisiblePlayers = ConcurrentHashMap.newKeySet();
    // Cached model for virtual entity floating text
    private com.hypixel.hytale.protocol.Model cachedFloatingTextModel;

    private UpdateParticleSpawners cachedSpawnerUpdate;
    private UpdateParticleSystems cachedSystemUpdate;
    private UpdateParticleSpawners cachedYellSpawnerUpdate;
    private UpdateParticleSystems cachedYellSystemUpdate;
    private ScheduledExecutorService scheduler;

    // Per-speaker custom-colored spawner packets (one-time registration per speaker session)
    private final Map<UUID, UpdateParticleSpawners> customColorSpawnerPackets = new ConcurrentHashMap<>();
    private final Map<UUID, UpdateParticleSystems> customColorSystemPackets = new ConcurrentHashMap<>();
    private final Map<UUID, String> customColorPrefixes = new ConcurrentHashMap<>();
    private final Map<UUID, String> registeredCustomColorHex = new ConcurrentHashMap<>();

    // Viewer override color spawner registration (keyed by hex, e.g. "#FF0000")
    private final Map<String, String> overrideColorPrefixes = new ConcurrentHashMap<>(); // hex → prefix
    private final Map<String, UpdateParticleSpawners> overrideSpawnerPackets = new ConcurrentHashMap<>();
    private final Map<String, UpdateParticleSystems> overrideSystemPackets = new ConcurrentHashMap<>();
    private BubbleThemeStorage themeStorage;
    private PlayerBubblePrefsStorage prefsStorage;
    private ChannelStorage channelStorage;
    private BubbleChatConfig serverConfig;

    /** Cached sound event indices: [voiceType 0-7][letter 0-25]. 0 = not found. */
    private final int[][] animaleseSoundIndices = new int[8][26];

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void setThemeStorage(BubbleThemeStorage themeStorage) {
        this.themeStorage = themeStorage;
    }

    public void setPrefsStorage(PlayerBubblePrefsStorage prefsStorage) {
        this.prefsStorage = prefsStorage;
    }

    public void setChannelStorage(ChannelStorage channelStorage) {
        this.channelStorage = channelStorage;
    }

    public ChannelStorage getChannelStorage() {
        return channelStorage;
    }

    public void setServerConfig(BubbleChatConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public BubbleChatConfig getServerConfig() {
        return serverConfig;
    }

    public void initResources() {
        // Cache Mouse model for virtual entity floating text
        try {
            ModelAsset mouseAsset = ModelAsset.getAssetMap().getAsset("Mouse");
            if (mouseAsset != null) {
                Model mouseModel = Model.createScaledModel(mouseAsset, 0.001f);
                cachedFloatingTextModel = mouseModel.toPacket();
                cachedFloatingTextModel.hitbox = null;
                LOGGER.atInfo().log("Cached floating text model");
            } else {
                LOGGER.atWarning().log("Mouse model not found");
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to cache model: %s", e.getMessage());
        }

        // Build runtime-cloned particle system (JSON spawners silently fail on client)
        buildParticlePackets();

        // Cache Animalese sound event indices
        int loaded = 0;
        for (int v = 0; v < 8; v++) {
            for (int c = 0; c < 26; c++) {
                char letter = (char) ('A' + c);
                int idx = TempAssetIdUtil.getSoundEventIndex("BC_Voice" + (v + 1) + "_" + letter);
                animaleseSoundIndices[v][c] = idx;
                if (idx != 0) loaded++;
            }
        }
        LOGGER.atInfo().log("[BubbleChat] Animalese: cached %d/208 sound indices", loaded);
    }

    /**
     * Build all bubble particle spawners and systems from scratch (no template dependency).
     * Creates width variants (BC_Bubble_0 through BC_Bubble_14) for 1/2/3-liner, dark+light.
     * All variants are sent to clients at connect time.
     */
    private void buildParticlePackets() {
        try {
            Map<String, com.hypixel.hytale.protocol.ParticleSpawner> spawnersMap = buildSpawnerMap();
            Map<String, com.hypixel.hytale.protocol.ParticleSystem> systemsMap = buildSystemMap();

            cachedSpawnerUpdate = new UpdateParticleSpawners(UpdateType.AddOrUpdate, spawnersMap, null);
            cachedSystemUpdate = new UpdateParticleSystems(UpdateType.AddOrUpdate, systemsMap, null);

            LOGGER.atInfo().log("Built %d bubble variant spawners (1/2/3-liner, dark+light) from scratch", VARIANT_COUNT * 6);

            // Clone Hedera_Scream → BC_Yell (reflection pattern — doesn't modify original)
            ParticleSystem hederaSystemAsset = ParticleSystem.getAssetMap().getAsset("Hedera_Scream");
            if (hederaSystemAsset != null) {
                com.hypixel.hytale.protocol.ParticleSystem hederaPacket = hederaSystemAsset.toPacket();

                // Build spawner lookup for yell cloning
                var spawnerAssetMap = com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner.getAssetMap();

                // Clone each spawner referenced by the system, prefix with "BC_Yell_"
                Map<String, com.hypixel.hytale.protocol.ParticleSpawner> yellSpawners = new HashMap<>();
                String[] yellSpawnerIds = null;
                if (hederaPacket.spawners != null) {
                    yellSpawnerIds = new String[hederaPacket.spawners.length];
                    for (int i = 0; i < hederaPacket.spawners.length; i++) {
                        String origId = hederaPacket.spawners[i].spawnerId;
                        String clonedId = "BC_Yell_" + origId;
                        yellSpawnerIds[i] = clonedId;

                        var origAsset = spawnerAssetMap.getAsset(origId);
                        if (origAsset != null) {
                            var cloned = origAsset.toPacket().clone();
                            cloned.id = clonedId;
                            yellSpawners.put(clonedId, cloned);
                        }
                    }
                }

                // Build BC_Yell system referencing cloned spawner IDs
                com.hypixel.hytale.protocol.ParticleSpawnerGroup[] yellGroups = null;
                if (hederaPacket.spawners != null && yellSpawnerIds != null) {
                    yellGroups = new com.hypixel.hytale.protocol.ParticleSpawnerGroup[hederaPacket.spawners.length];
                    for (int i = 0; i < hederaPacket.spawners.length; i++) {
                        var grp = hederaPacket.spawners[i].clone();
                        grp.spawnerId = yellSpawnerIds[i];
                        yellGroups[i] = grp;
                    }
                }

                com.hypixel.hytale.protocol.ParticleSystem yellSystem = new com.hypixel.hytale.protocol.ParticleSystem(
                    "BC_Yell", yellGroups, hederaPacket.lifeSpan, hederaPacket.cullDistance, hederaPacket.boundingRadius, false);

                Map<String, com.hypixel.hytale.protocol.ParticleSystem> yellSystemMap = new HashMap<>();
                yellSystemMap.put("BC_Yell", yellSystem);

                cachedYellSpawnerUpdate = new UpdateParticleSpawners(UpdateType.AddOrUpdate, yellSpawners, null);
                cachedYellSystemUpdate = new UpdateParticleSystems(UpdateType.AddOrUpdate, yellSystemMap, null);

                LOGGER.atInfo().log("Cloned Hedera_Scream → BC_Yell (%d spawners)", yellSpawners.size());
            } else {
                LOGGER.atWarning().log("Hedera_Scream not found — yell particle disabled");
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to build particle packets: %s", e.getMessage());
        }
    }

    private static final Color DARK_BG = new Color((byte) 25, (byte) 25, (byte) 35);
    private static final Color LIGHT_BG = new Color((byte) 240, (byte) 240, (byte) 245);
    private static final Color WHITE = new Color((byte) 255, (byte) 255, (byte) 255);

    private Map<String, com.hypixel.hytale.protocol.ParticleSpawner> buildSpawnerMap() {
        float twoLinerScaleY = BUBBLE_SCALE_Y * (512f / 384f);
        float threeLinerScaleY = BUBBLE_SCALE_Y * (640f / 384f);

        Map<String, com.hypixel.hytale.protocol.ParticleSpawner> spawnersMap = new HashMap<>();

        // Dark variants (default tint = DARK_BG)
        addSpawnerVariants(VARIANT_TEXTURES, DARK_BG, BUBBLE_SCALE_Y, PARTICLE_LIFESPAN, 1.0f, spawnersMap);
        addSpawnerVariants(VARIANT_2LINER_TEXTURES, DARK_BG, twoLinerScaleY, PARTICLE_LIFESPAN, 1.0f, spawnersMap);
        addSpawnerVariants(VARIANT_3LINER_TEXTURES, DARK_BG, threeLinerScaleY, PARTICLE_LIFESPAN, 1.0f, spawnersMap);

        // Light variants (default tint = LIGHT_BG)
        addSpawnerVariants(VARIANT_TEXTURES_LIGHT, LIGHT_BG, BUBBLE_SCALE_Y, PARTICLE_LIFESPAN, 1.0f, spawnersMap);
        addSpawnerVariants(VARIANT_2LINER_LIGHT_TEXTURES, LIGHT_BG, twoLinerScaleY, PARTICLE_LIFESPAN, 1.0f, spawnersMap);
        addSpawnerVariants(VARIANT_3LINER_LIGHT_TEXTURES, LIGHT_BG, threeLinerScaleY, PARTICLE_LIFESPAN, 1.0f, spawnersMap);

        // Fade-out variants (opacity 1→0 over FADE_LIFESPAN)
        addSpawnerVariants(VARIANT_TEXTURES, DARK_BG, BUBBLE_SCALE_Y, FADE_LIFESPAN, 0f, spawnersMap);
        addSpawnerVariants(VARIANT_2LINER_TEXTURES, DARK_BG, twoLinerScaleY, FADE_LIFESPAN, 0f, spawnersMap);
        addSpawnerVariants(VARIANT_3LINER_TEXTURES, DARK_BG, threeLinerScaleY, FADE_LIFESPAN, 0f, spawnersMap);
        addSpawnerVariants(VARIANT_TEXTURES_LIGHT, LIGHT_BG, BUBBLE_SCALE_Y, FADE_LIFESPAN, 0f, spawnersMap);
        addSpawnerVariants(VARIANT_2LINER_LIGHT_TEXTURES, LIGHT_BG, twoLinerScaleY, FADE_LIFESPAN, 0f, spawnersMap);
        addSpawnerVariants(VARIANT_3LINER_LIGHT_TEXTURES, LIGHT_BG, threeLinerScaleY, FADE_LIFESPAN, 0f, spawnersMap);

        return spawnersMap;
    }

    /**
     * Create a bubble ParticleSpawner from scratch — no template dependency.
     * All fields are explicitly set to eliminate inherited jitter from random vanilla spawners.
     * @param endOpacity 1.0f for normal, 0f for fade-out
     */
    private static com.hypixel.hytale.protocol.ParticleSpawner createBubbleSpawner(
            String name, String texturePath, int texW, int texH,
            Color tint, float scaleY, float lifeSpan, float endOpacity) {
        Rangef sx = new Rangef(BUBBLE_SCALE_X, BUBBLE_SCALE_X);
        Rangef sy = new Rangef(scaleY, scaleY);
        RangeVector2f scale = new RangeVector2f(sx, sy);

        var frames = new Int2ObjectOpenHashMap<ParticleAnimationFrame>();
        frames.put(0,   new ParticleAnimationFrame(null, scale, null, tint, 1.0f));
        frames.put(100, new ParticleAnimationFrame(null, scale, null, tint, endOpacity));
        ParticleAnimationFrame initFrame = new ParticleAnimationFrame(null, scale, null, tint, 1.0f);

        com.hypixel.hytale.protocol.Particle particle = new com.hypixel.hytale.protocol.Particle(
            texturePath,
            new Size(texW, texH),
            ParticleUVOption.None,
            ParticleScaleRatioConstraint.None,
            SoftParticle.Disable,
            0f,      // softParticlesFadeFactor
            false,   // useSpriteBlending
            initFrame,
            null,    // collisionAnimationFrame
            frames
        );

        return new com.hypixel.hytale.protocol.ParticleSpawner(
            name,
            particle,
            EmitShape.Sphere,                      // shape (with null emitOffset = point)
            null,                                   // emitOffset
            0f,                                     // cameraOffset
            false,                                  // useEmitDirection
            lifeSpan,
            null,                                   // spawnRate (burst mode)
            true,                                   // spawnBurst
            null,                                   // waveDelay
            null,                                   // totalParticles
            1,                                      // maxConcurrentParticles
            null,                                   // initialVelocity
            0f,                                     // velocityStretchMultiplier
            ParticleRotationInfluence.Billboard,    // face camera
            false,                                  // particleRotateWithSpawner
            false,                                  // isLowRes
            0f,                                     // trailSpawnerPositionMultiplier
            0f,                                     // trailSpawnerRotationMultiplier
            null,                                   // particleCollision
            FXRenderMode.BlendLinear,
            0f,                                     // lightInfluence
            false,                                  // linearFiltering
            new Rangef(lifeSpan, lifeSpan),         // particleLifeSpan
            null,                                   // uvMotion
            null,                                   // attractors
            null                                    // intersectionHighlight
        );
    }

    /** Create a bubble ParticleSystem from scratch — no template dependency. */
    private static com.hypixel.hytale.protocol.ParticleSystem createBubbleSystem(String name, float lifeSpan) {
        com.hypixel.hytale.protocol.ParticleSpawnerGroup group =
            new com.hypixel.hytale.protocol.ParticleSpawnerGroup(
                name,   // spawnerId
                null,   // positionOffset
                null,   // rotationOffset
                false,  // fixedRotation
                0f,     // startDelay
                null,   // spawnRate
                null,   // waveDelay
                1,      // totalSpawners
                1,      // maxConcurrent
                null,   // initialVelocity
                null,   // emitOffset
                null,   // lifeSpan
                null    // attractors
            );
        return new com.hypixel.hytale.protocol.ParticleSystem(
            name, new com.hypixel.hytale.protocol.ParticleSpawnerGroup[]{group},
            lifeSpan, 1000.0f, 1000.0f, false);
    }

    /**
     * Add spawner variants for a set of textures.
     * @param endOpacity 1.0f for normal sustain, 0f for fade-out
     */
    private void addSpawnerVariants(
            String[][] textures, Color tint, float scaleY, float lifeSpan, float endOpacity,
            Map<String, com.hypixel.hytale.protocol.ParticleSpawner> out) {
        boolean isFade = endOpacity < 1.0f;
        for (String[] variant : textures) {
            String name = isFade ? variant[0] + "_Fade" : variant[0];
            out.put(name, createBubbleSpawner(name, variant[1],
                Integer.parseInt(variant[2]), Integer.parseInt(variant[3]),
                tint, scaleY, lifeSpan, endOpacity));
        }
    }

    private Map<String, com.hypixel.hytale.protocol.ParticleSystem> buildSystemMap() {
        Map<String, com.hypixel.hytale.protocol.ParticleSystem> systemsMap = new HashMap<>();

        for (String[][] textures : new String[][][]{
                VARIANT_TEXTURES, VARIANT_2LINER_TEXTURES, VARIANT_3LINER_TEXTURES,
                VARIANT_TEXTURES_LIGHT, VARIANT_2LINER_LIGHT_TEXTURES, VARIANT_3LINER_LIGHT_TEXTURES}) {
            for (String[] variant : textures) {
                String name = variant[0];
                systemsMap.put(name, createBubbleSystem(name, PARTICLE_LIFESPAN));
                String fadeName = name + "_Fade";
                systemsMap.put(fadeName, createBubbleSystem(fadeName, FADE_LIFESPAN));
            }
        }

        return systemsMap;
    }

    private double getPageIndicatorYOffset(int lineCount) {
        if (lineCount >= 3) return PAGE_INDICATOR_Y_OFFSET_3L;
        if (lineCount >= 2) return PAGE_INDICATOR_Y_OFFSET_2L;
        return PAGE_INDICATOR_Y_OFFSET_1L;
    }

    private double getBubbleYAdjust(int lineCount) {
        if (lineCount >= 3) return BUBBLE_Y_ADJUST_3L;
        if (lineCount >= 2) return BUBBLE_Y_ADJUST_2L;
        return BUBBLE_Y_ADJUST_1L;
    }

    /**
     * Apply a player's theme color to their active bubble.
     * No broadcasting needed — color is passed via ModelParticle.color at spawn time.
     */
    public void applyThemeToPlayer(@Nonnull UUID uuid, @Nonnull PlayerRef playerRef) {
        // Mark custom color as stale so onChat() will re-register with new tint
        // DO NOT call registerSpeakerCustomColor here — it sends UpdateParticleSpawners
        // to ALL players (25-44ms client cost), causing visible world particle refresh.
        // Color/mode changes take effect on next chat message.
        if (themeStorage != null) {
            Color customTint = resolveEffectiveTint(uuid);
            if (customTint != null) {
                // Mark stale so onChat re-registers with new color
                registeredCustomColorHex.remove(uuid);
            } else {
                // Cleared custom color — remove per-speaker spawners
                customColorPrefixes.remove(uuid);
                customColorSpawnerPackets.remove(uuid);
                customColorSystemPackets.remove(uuid);
                registeredCustomColorHex.remove(uuid);
            }
        }

        // If this player has an active speech bubble, immediately respawn with new theme
        // (resolveSpawnerName handles light/dark dynamically, no re-registration needed)
        SpeechState s = activeSpeech.get(uuid);
        if (s != null && s.getPlayerNetworkId() >= 0) {
            World w = s.getWorld();
            if (w != null) {
                w.execute(() -> {
                    try {
                        sendBubbleParticle(uuid, s.getPlayerNetworkId(), s.getTileCount(), s.getLineCount());
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * Send our custom particle configs to a player.
     * Must be called on player join before any particle spawns.
     */
    public void sendParticleConfigs(@Nonnull PlayerRef playerRef) {
        // Register any override colors from this player's saved prefs
        if (prefsStorage != null) {
            PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerRef.getUuid());
            if (prefs.globalColorOverride != null) registerOverrideColor(prefs.globalColorOverride);
            for (String hex : prefs.playerColors.values()) {
                registerOverrideColor(hex);
            }
            // Register channel colors so spawners exist for channel tint
            for (String hex : prefs.channelColors.values()) {
                registerOverrideColor(hex);
            }
        }

        try {
            if (playerRef.getPacketHandler() == null) return;

            // Batch ALL spawners and systems into two combined packets to minimize client log spam
            Map<String, com.hypixel.hytale.protocol.ParticleSpawner> allSpawners = new HashMap<>();
            Map<String, com.hypixel.hytale.protocol.ParticleSystem> allSystems = new HashMap<>();

            // Collect base bubble spawners
            if (cachedSpawnerUpdate != null && cachedSpawnerUpdate.particleSpawners != null)
                allSpawners.putAll(cachedSpawnerUpdate.particleSpawners);
            if (cachedSystemUpdate != null && cachedSystemUpdate.particleSystems != null)
                allSystems.putAll(cachedSystemUpdate.particleSystems);

            // Collect yell spawners
            if (cachedYellSpawnerUpdate != null && cachedYellSpawnerUpdate.particleSpawners != null)
                allSpawners.putAll(cachedYellSpawnerUpdate.particleSpawners);
            if (cachedYellSystemUpdate != null && cachedYellSystemUpdate.particleSystems != null)
                allSystems.putAll(cachedYellSystemUpdate.particleSystems);

            // Collect per-speaker custom-colored spawners
            for (var pkt : customColorSpawnerPackets.values()) {
                if (pkt.particleSpawners != null) allSpawners.putAll(pkt.particleSpawners);
            }
            for (var pkt : customColorSystemPackets.values()) {
                if (pkt.particleSystems != null) allSystems.putAll(pkt.particleSystems);
            }

            // Collect viewer-override color spawners
            for (var pkt : overrideSpawnerPackets.values()) {
                if (pkt.particleSpawners != null) allSpawners.putAll(pkt.particleSpawners);
            }
            for (var pkt : overrideSystemPackets.values()) {
                if (pkt.particleSystems != null) allSystems.putAll(pkt.particleSystems);
            }

            // Send as two batched packets (1 spawner + 1 system = 2 client log lines)
            if (!allSpawners.isEmpty()) {
                playerRef.getPacketHandler().writeNoCache(
                    new UpdateParticleSpawners(UpdateType.AddOrUpdate, allSpawners, null));
            }
            if (!allSystems.isEmpty()) {
                playerRef.getPacketHandler().writeNoCache(
                    new UpdateParticleSystems(UpdateType.AddOrUpdate, allSystems, null));
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to send particle configs to %s: %s",
                playerRef.getUsername(), e.getMessage());
        }
    }

    /**
     * Register per-speaker custom-colored spawner variants (one-time per speaker session).
     * Creates cloned spawners with the speaker's custom tint color and sends to all connected players.
     * Uses prefix "BCP{netId}_" to avoid name collisions.
     */
    private void registerSpeakerCustomColor(UUID speakerUuid, int speakerNetId, Color tint) {
        String prefix = "BCP" + speakerNetId;
        float twoLinerScaleY = BUBBLE_SCALE_Y * (512f / 384f);
        float threeLinerScaleY = BUBBLE_SCALE_Y * (640f / 384f);

        Map<String, com.hypixel.hytale.protocol.ParticleSpawner> spawners = new HashMap<>();
        Map<String, com.hypixel.hytale.protocol.ParticleSystem> systems = new HashMap<>();

        // All 6 texture arrays × 2 animations (hold + fade) = 12 spawner sets
        String[][][] allTextures = {
            VARIANT_TEXTURES, VARIANT_2LINER_TEXTURES, VARIANT_3LINER_TEXTURES,
            VARIANT_TEXTURES_LIGHT, VARIANT_2LINER_LIGHT_TEXTURES, VARIANT_3LINER_LIGHT_TEXTURES
        };
        float[] scaleYValues = {
            BUBBLE_SCALE_Y, twoLinerScaleY, threeLinerScaleY,
            BUBBLE_SCALE_Y, twoLinerScaleY, threeLinerScaleY
        };

        for (int i = 0; i < allTextures.length; i++) {
            for (String[] variant : allTextures[i]) {
                String baseName = variant[0]; // e.g. "BC_Bubble_5"
                String customName = prefix + "_" + baseName.substring(3); // e.g. "BCP5_Bubble_5"
                String fadeName = customName + "_Fade";
                String texturePath = variant[1];
                int texW = Integer.parseInt(variant[2]);
                int texH = Integer.parseInt(variant[3]);

                // Hold spawner + system
                spawners.put(customName, createBubbleSpawner(customName, texturePath,
                    texW, texH, tint, scaleYValues[i], PARTICLE_LIFESPAN, 1.0f));
                systems.put(customName, createBubbleSystem(customName, PARTICLE_LIFESPAN));

                // Fade spawner + system
                spawners.put(fadeName, createBubbleSpawner(fadeName, texturePath,
                    texW, texH, tint, scaleYValues[i], FADE_LIFESPAN, 0f));
                systems.put(fadeName, createBubbleSystem(fadeName, FADE_LIFESPAN));
            }
        }

        UpdateParticleSpawners spawnerPacket = new UpdateParticleSpawners(UpdateType.AddOrUpdate, spawners, null);
        UpdateParticleSystems systemPacket = new UpdateParticleSystems(UpdateType.AddOrUpdate, systems, null);

        customColorSpawnerPackets.put(speakerUuid, spawnerPacket);
        customColorSystemPackets.put(speakerUuid, systemPacket);
        customColorPrefixes.put(speakerUuid, prefix);

        // Track which hex was registered so we detect color changes in onChat
        String effectiveHex = resolveEffectiveHex(speakerUuid);
        if (effectiveHex != null) registeredCustomColorHex.put(speakerUuid, effectiveHex);

        // Send to all connected players
        for (PlayerRef p : Universe.get().getPlayers()) {
            try {
                if (p.getPacketHandler() != null) {
                    p.getPacketHandler().writeNoCache(spawnerPacket);
                    p.getPacketHandler().writeNoCache(systemPacket);
                }
            } catch (Exception ignored) {}
        }

        LOGGER.atInfo().log("Registered custom color spawners for speaker %s (prefix=%s, color=%s)",
            speakerUuid, prefix, tint);
    }

    /**
     * Register override color spawners for viewer-side color overrides (per-player or global).
     * Keyed by hex string. Only registers once per unique hex color.
     * Called from PlayerColorsPage when viewer sets/edits a color override.
     */
    public void registerOverrideColor(String hex) {
        registerOverrideColor(hex, false);
    }

    /**
     * Register override color spawners. If broadcast=true, sends to all connected players immediately
     * (used when a color changes mid-session from settings pages). If broadcast=false, only stores
     * the packets for later batched delivery via sendParticleConfigs.
     */
    public void registerOverrideColor(String hex, boolean broadcast) {
        if (hex == null) return;
        String normalized = hex.startsWith("#") ? hex : "#" + hex;
        if (overrideColorPrefixes.containsKey(normalized)) return; // already registered

        Color tint = parseHexColor(normalized);
        if (tint == null) return;

        String hexClean = normalized.substring(1); // strip #
        String prefix = "BCO_" + hexClean;
        float twoLinerScaleY = BUBBLE_SCALE_Y * (512f / 384f);
        float threeLinerScaleY = BUBBLE_SCALE_Y * (640f / 384f);

        Map<String, com.hypixel.hytale.protocol.ParticleSpawner> spawners = new HashMap<>();
        Map<String, com.hypixel.hytale.protocol.ParticleSystem> systems = new HashMap<>();

        String[][][] allTextures = {
            VARIANT_TEXTURES, VARIANT_2LINER_TEXTURES, VARIANT_3LINER_TEXTURES,
            VARIANT_TEXTURES_LIGHT, VARIANT_2LINER_LIGHT_TEXTURES, VARIANT_3LINER_LIGHT_TEXTURES
        };
        float[] scaleYValues = {
            BUBBLE_SCALE_Y, twoLinerScaleY, threeLinerScaleY,
            BUBBLE_SCALE_Y, twoLinerScaleY, threeLinerScaleY
        };

        for (int i = 0; i < allTextures.length; i++) {
            for (String[] variant : allTextures[i]) {
                String baseName = variant[0];
                String customName = prefix + "_" + baseName.substring(3);
                String fadeName = customName + "_Fade";
                String texturePath = variant[1];
                int texW = Integer.parseInt(variant[2]);
                int texH = Integer.parseInt(variant[3]);

                spawners.put(customName, createBubbleSpawner(customName, texturePath,
                    texW, texH, tint, scaleYValues[i], PARTICLE_LIFESPAN, 1.0f));
                systems.put(customName, createBubbleSystem(customName, PARTICLE_LIFESPAN));

                spawners.put(fadeName, createBubbleSpawner(fadeName, texturePath,
                    texW, texH, tint, scaleYValues[i], FADE_LIFESPAN, 0f));
                systems.put(fadeName, createBubbleSystem(fadeName, FADE_LIFESPAN));
            }
        }

        UpdateParticleSpawners spawnerPacket = new UpdateParticleSpawners(UpdateType.AddOrUpdate, spawners, null);
        UpdateParticleSystems systemPacket = new UpdateParticleSystems(UpdateType.AddOrUpdate, systems, null);

        overrideColorPrefixes.put(normalized, prefix);
        overrideSpawnerPackets.put(normalized, spawnerPacket);
        overrideSystemPackets.put(normalized, systemPacket);

        // Only broadcast mid-session (settings page changes); on-connect is handled by batched sendParticleConfigs
        if (broadcast) {
            for (PlayerRef p : Universe.get().getPlayers()) {
                try {
                    if (p.getPacketHandler() != null) {
                        p.getPacketHandler().writeNoCache(spawnerPacket);
                        p.getPacketHandler().writeNoCache(systemPacket);
                    }
                } catch (Exception ignored) {}
            }
        }

        LOGGER.atInfo().log("Registered override color spawners (hex=%s, prefix=%s)", normalized, prefix);
    }

    /**
     * Resolve spawner name for a specific viewer watching a specific speaker.
     * Priority: viewer global override > viewer per-player override > channel color > speaker's own custom > default.
     */
    private String resolveSpawnerNameForViewer(UUID viewerUuid, UUID speakerUuid,
                                                String speakerLowerName, int lineCount, int tileCount) {
        PlayerBubbleTheme speakerTheme = themeStorage != null ? themeStorage.getTheme(speakerUuid) : new PlayerBubbleTheme();
        boolean light = speakerTheme.lightMode;

        // Check viewer override (global or per-player — highest priority)
        String overridePrefix = null;
        if (prefsStorage != null && speakerLowerName != null) {
            PlayerBubblePrefs viewerPrefs = prefsStorage.getPrefs(viewerUuid);
            String overrideHex = viewerPrefs.resolveColorForSpeaker(speakerLowerName);
            if (overrideHex != null) {
                String normalized = overrideHex.startsWith("#") ? overrideHex : "#" + overrideHex;
                overridePrefix = overrideColorPrefixes.get(normalized);
            }
        }

        if (overridePrefix != null) {
            String lineType = lineCount >= 3
                ? (light ? "3Liner_Light_" : "3Liner_")
                : lineCount >= 2
                    ? (light ? "2Liner_Light_" : "2Liner_")
                    : (light ? "Bubble_Light_" : "Bubble_");
            return overridePrefix + "_" + lineType + tileCount;
        }

        // Channel color override — if speaker is in an RP channel, viewer sees channel's tint
        SpeechState state = activeSpeech.get(speakerUuid);
        if (state != null && state.getChannelPin() != null && prefsStorage != null) {
            PlayerBubblePrefs viewerPrefs = prefsStorage.getPrefs(viewerUuid);
            String channelKey = getChannelKeyForViewer(viewerPrefs, state.getChannelPin());
            if (channelKey != null) {
                String channelHex = viewerPrefs.getChannelColor(channelKey);
                if (channelHex != null) {
                    String normalized = channelHex.startsWith("#") ? channelHex : "#" + channelHex;
                    String channelPrefix = overrideColorPrefixes.get(normalized);
                    if (channelPrefix != null) {
                        String lineType = lineCount >= 3
                            ? (light ? "3Liner_Light_" : "3Liner_")
                            : lineCount >= 2
                                ? (light ? "2Liner_Light_" : "2Liner_")
                                : (light ? "Bubble_Light_" : "Bubble_");
                        return channelPrefix + "_" + lineType + tileCount;
                    }
                }
            }
        }

        // Fall back to speaker's own custom color or default
        return resolveSpawnerName(speakerUuid, lineCount, tileCount);
    }

    /**
     * Get the channel color key (rp1/rp2/rp3) for a viewer seeing a message from a specific channel.
     * Returns null if the viewer is not in this channel.
     */
    @Nullable
    private String getChannelKeyForViewer(PlayerBubblePrefs viewerPrefs, String channelPin) {
        for (int i = 0; i < viewerPrefs.channelSlots.length; i++) {
            if (channelPin.equals(viewerPrefs.channelSlots[i])) {
                return "rp" + (i + 1);
            }
        }
        return null;
    }

    static String[] tokenizeMessage(String message) {
        String[] allWords = message.trim().split("\\s+");
        if (allWords.length == 0) return new String[0];
        for (int i = 0; i < allWords.length; i++) {
            if (allWords[i].length() > MAX_WORD_CHARS) {
                allWords[i] = allWords[i].substring(0, MAX_WORD_CHARS - 3) + "...";
            }
            // Also truncate by weighted width so wide chars (W, M) don't overflow the bubble
            if (getWeightedLength(allWords[i]) > MAX_WEIGHTED_WIDTH) {
                String w = allWords[i];
                int end = w.length();
                while (end > 1 && getWeightedLength(w.substring(0, end - 3) + "...") > MAX_WEIGHTED_WIDTH) {
                    end--;
                }
                allWords[i] = w.substring(0, Math.max(1, end - 3)) + "...";
            }
        }
        return allWords;
    }

    private static int findPageEndIndex(String[] words, int pageStart, int maxChars) {
        int lineLen = 0;
        for (int i = pageStart; i < words.length; i++) {
            int newLen = lineLen + (lineLen > 0 ? 1 : 0) + words[i].length();
            if (newLen > maxChars && i > pageStart) {
                return i - 1;
            }
            lineLen = newLen;
        }
        return words.length - 1;
    }

    // Find the last word index where cumulative weighted width stays <= maxWeight
    private static int findWeightSplitIndex(String[] words, int start, float maxWeight) {
        float weight = 0;
        for (int i = start; i < words.length; i++) {
            float wordWeight = getWeightedLength(words[i]);
            float newWeight = weight + (i > start ? 0.5f : 0) + wordWeight;
            if (newWeight > maxWeight && i > start) {
                return i - 1;
            }
            weight = newWeight;
        }
        return words.length - 1;
    }

    /**
     * Simulate multi-line superpage planning. Returns [totalSuperpages, lastWordIndex].
     * Caps at MAX_SUPERPAGES.
     */
    private static int[] planSuperpages(String[] words, int startFrom) {
        int pages = 0;
        int start = startFrom;
        int lastWordIdx = words.length - 1;

        while (start < words.length && pages < MAX_SUPERPAGES) {
            pages++;

            // Plan line 1 (constrain by both char count and weight)
            int line1End = Math.min(
                findPageEndIndex(words, start, MAX_LINE_CHARS),
                findWeightSplitIndex(words, start, MAX_WEIGHTED_WIDTH)
            );
            if (line1End >= words.length - 1) { lastWordIdx = words.length - 1; break; }

            // Plan line 2
            int line2End = Math.min(
                findPageEndIndex(words, line1End + 1, MAX_LINE_CHARS),
                findWeightSplitIndex(words, line1End + 1, MAX_WEIGHTED_WIDTH)
            );
            if (line2End >= words.length - 1) { lastWordIdx = words.length - 1; break; }

            // Plan line 3
            int line3End = Math.min(
                findPageEndIndex(words, line2End + 1, MAX_LINE_CHARS),
                findWeightSplitIndex(words, line2End + 1, MAX_WEIGHTED_WIDTH)
            );

            lastWordIdx = line3End;
            start = line3End + 1;
        }

        return new int[]{pages, lastWordIdx};
    }

    private static final String[] SUPERSCRIPT_DIGITS = {"\u2070", "\u00B9", "\u00B2", "\u00B3"};

    private static String superscriptPage(int page) {
        if (page >= 0 && page < SUPERSCRIPT_DIGITS.length) return SUPERSCRIPT_DIGITS[page];
        return String.valueOf(page);
    }

    private static float getWeightedLength(String text) {
        float total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'W' || c == 'M') {
                total += 2.0f;
            } else if (c == 'w' || c == 'm') {
                total += 1.5f;
            } else if (c == ' ') {
                total += 0.5f;
            } else if (c == 'i' || c == 'l' || c == '1' || c == '!' || c == '|' || c == '\'' || c == '.' || c == ',' || c == ':' || c == ';') {
                total += 0.5f;
            } else if (c == 't' || c == 'f' || c == 'r' || c == 'j' || c == '(' || c == ')' || c == '[' || c == ']') {
                total += 0.65f;
            } else if (c == 's' || c == 'e' || c == 'c' || c == 'a' || c == 'z') {
                total += 0.85f;
            } else if (c >= 'A' && c <= 'Z') {
                total += 1.2f;
            } else {
                total += 1.0f;
            }
        }
        return total;
    }

    public boolean togglePlayer(UUID uuid) {
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            return true;
        } else {
            disabledPlayers.add(uuid);
            clearSpeech(uuid);
            return false;
        }
    }

    public boolean isEnabled(UUID uuid) {
        return !disabledPlayers.contains(uuid);
    }

    public void enablePlayer(UUID uuid) {
        disabledPlayers.remove(uuid);
    }

    public void disablePlayer(UUID uuid) {
        disabledPlayers.add(uuid);
        clearSpeech(uuid);
    }

    public void setSelfVisible(UUID uuid, boolean visible) {
        if (visible) {
            selfVisiblePlayers.add(uuid);
        } else {
            selfVisiblePlayers.remove(uuid);
            // If player has active speech, despawn text entities from their screen
            SpeechState state = activeSpeech.get(uuid);
            if (state != null) {
                List<Integer> despawnIds = new ArrayList<>();
                int netId = state.getVirtualEntityNetId();
                int l2 = state.getLine2NetId();
                int l3 = state.getLine3NetId();
                int pi = state.getPageIndicatorNetId();
                if (netId >= 0) despawnIds.add(netId);
                if (l2 >= 0) despawnIds.add(l2);
                if (l3 >= 0) despawnIds.add(l3);
                if (pi >= 0) despawnIds.add(pi);
                if (!despawnIds.isEmpty()) {
                    PlayerRef selfRef = findPlayerRef(uuid);
                    if (selfRef != null && selfRef.getPacketHandler() != null) {
                        int[] ids = despawnIds.stream().mapToInt(Integer::intValue).toArray();
                        selfRef.getPacketHandler().writeNoCache(new EntityUpdates(ids, null));
                    }
                }
            }
        }
    }

    public boolean isSelfVisible(UUID uuid) {
        return selfVisiblePlayers.contains(uuid);
    }

    // ---- Get viewers within range ----
    private List<PlayerRef> getViewers(UUID speakerUuid, Vector3d speakerPos) {
        List<PlayerRef> viewers = new ArrayList<>();
        boolean includeSelf = selfVisiblePlayers.contains(speakerUuid);

        // Look up speaker's lowercased name for hidden/muted checks
        String speakerLowerName = null;
        SpeechState speakerState = activeSpeech.get(speakerUuid);
        if (speakerState != null) {
            speakerLowerName = speakerState.getSpeakerLowerName();
        }

        // Pre-lookup speaker's world UUID to skip cross-world viewers (store.getComponent asserts same thread)
        PlayerRef speakerRef = findPlayerRef(speakerUuid);
        UUID speakerWorldUuid = speakerRef != null ? speakerRef.getWorldUuid() : null;

        for (PlayerRef p : Universe.get().getPlayers()) {
            UUID pUuid = p.getUuid();
            boolean isSelf = pUuid.equals(speakerUuid);

            if (!includeSelf && isSelf) continue;

            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;

            // Skip viewers in different world instances (e.g. dungeon portals)
            if (speakerWorldUuid != null && !speakerWorldUuid.equals(p.getWorldUuid())) {
                continue;
            }

            // Cache viewer prefs once per viewer (used for hidden/muted, channel, cull, max bubbles)
            PlayerBubblePrefs viewerPrefs = (prefsStorage != null && !isSelf)
                    ? prefsStorage.getPrefs(pUuid) : null;

            // Check viewer's hidden/muted preferences
            if (viewerPrefs != null && speakerLowerName != null) {
                if (viewerPrefs.isHidden(speakerLowerName)) continue;
                if (viewerPrefs.isMuted(speakerLowerName)) continue;
            }

            // Channel isolation: if speaker is in an RP channel, only show to members
            // Skip isolation entirely for dual-visibility bubbles (visible to all)
            if (speakerState != null && speakerState.isDualVisibilityBubble()) {
                // No filtering — bubble visible to everyone in range
            } else if (speakerState != null && speakerState.getChannelPin() != null && channelStorage != null) {
                String pin = speakerState.getChannelPin();
                if (!channelStorage.isMember(pin, pUuid)) {
                    continue;
                }
            } else if (viewerPrefs != null && channelStorage != null) {
                // Public chat — hide bubble from isolated RP channel members without dual visibility
                String vPin = viewerPrefs.getActiveChannelPin();
                if (vPin != null && channelStorage.isMember(vPin, pUuid) && !viewerPrefs.dualVisibility) {
                    continue;
                }
            }

            if (!isSelf && speakerPos != null) {
                double maxRangeSq = VIEW_RANGE_SQ;
                if (viewerPrefs != null) {
                    boolean isYell = speakerState != null && speakerState.isYellMessage();
                    if (speakerState != null && speakerState.getChannelPin() != null) {
                        // RP channel — use viewer's RP cull distance (or yell range)
                        int range = isYell ? viewerPrefs.getEffectiveYellBubbleRange()
                                           : viewerPrefs.getEffectiveRpCullDistance();
                        maxRangeSq = (double) range * range;
                    } else {
                        // Public — use viewer's normal cull distance (or yell range)
                        int range = isYell ? viewerPrefs.getEffectiveYellBubbleRange()
                                           : viewerPrefs.getEffectiveCullDistance();
                        maxRangeSq = (double) range * range;
                    }
                }

                try {
                    Store<EntityStore> viewerStore = ref.getStore();
                    TransformComponent vtc = viewerStore.getComponent(ref, TransformComponent.getComponentType());
                    if (vtc != null) {
                        Vector3d vp = vtc.getPosition();
                        double dx = vp.getX() - speakerPos.getX();
                        double dz = vp.getZ() - speakerPos.getZ();
                        if (dx * dx + dz * dz > maxRangeSq) continue;
                    }
                } catch (IllegalStateException e) {
                    // Cross-world thread mismatch (viewer moved worlds during iteration) — skip
                    continue;
                }
            }

            // Check viewer's max bubble count
            if (viewerPrefs != null) {
                int maxBubbles = viewerPrefs.getEffectiveMaxBubbleCount();
                if (activeSpeech.size() > maxBubbles) {
                    // Count how many active speakers are closer than this one
                    int closerCount = countCloserSpeakers(p, speakerUuid, speakerPos);
                    if (closerCount >= maxBubbles) continue;
                }
            }

            viewers.add(p);
        }
        return viewers;
    }

    /**
     * Count how many active speakers (other than the given speaker) are closer to the viewer.
     * Used for max bubble count priority — closer speakers get priority.
     */
    private int countCloserSpeakers(PlayerRef viewer, UUID speakerUuid, Vector3d speakerPos) {
        if (speakerPos == null) return 0;

        Ref<EntityStore> viewerRef = viewer.getReference();
        if (viewerRef == null || !viewerRef.isValid()) return 0;
        Store<EntityStore> store = viewerRef.getStore();
        TransformComponent tc = store.getComponent(viewerRef, TransformComponent.getComponentType());
        if (tc == null) return 0;
        Vector3d viewerPos = tc.getPosition();

        double speakerDistSq = distSqXZ(viewerPos, speakerPos);
        int count = 0;

        for (Map.Entry<UUID, SpeechState> entry : activeSpeech.entrySet()) {
            UUID otherUuid = entry.getKey();
            if (otherUuid.equals(speakerUuid) || otherUuid.equals(viewer.getUuid())) continue;

            Vector3d otherPos = entry.getValue().getLastPosition();
            if (otherPos == null) continue;
            if (distSqXZ(viewerPos, otherPos) < speakerDistSq) {
                count++;
            }
        }
        return count;
    }

    private static double distSqXZ(Vector3d a, Vector3d b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // ---- BusyBubble integration (optional, via reflection) ----
    private static java.lang.reflect.Method busyBubbleClearMethod;
    private static boolean busyBubbleChecked = false;

    private static void clearBusyBubble(UUID uuid) {
        if (!busyBubbleChecked) {
            busyBubbleChecked = true;
            try {
                Class<?> api = Class.forName("com.thinkingbubble.BusyBubbleAPI");
                busyBubbleClearMethod = api.getMethod("clearBubble", UUID.class);
            } catch (Exception ignored) {
                // BusyBubble not installed
            }
        }
        if (busyBubbleClearMethod != null) {
            try {
                busyBubbleClearMethod.invoke(null, uuid);
            } catch (Exception ignored) {}
        }
    }

    // ---- onChannelChat: entry point for RP channel chat ----
    /**
     * Handle chat in an RP channel.
     * Sends formatted text message to channel members within range, then triggers bubble.
     */
    public void onChannelChat(@Nonnull PlayerRef sender, @Nonnull String message, @Nonnull String channelPin) {
        onChannelChat(sender, message, channelPin, true);
    }

    /**
     * Handle chat in an RP channel.
     * Sends formatted text message to channel members within range.
     * @param triggerBubble if true, also triggers a channel-restricted bubble via onChat(channelPin).
     *                      Set false when dual-visibility sender handles the bubble separately.
     */
    public void onChannelChat(@Nonnull PlayerRef sender, @Nonnull String message,
                               @Nonnull String channelPin, boolean triggerBubble) {
        UUID uuid = sender.getUuid();
        if (disabledPlayers.contains(uuid)) return;
        if (channelStorage == null) return;

        Set<UUID> members = channelStorage.getMembers(channelPin);
        if (members.isEmpty()) return;

        Ref<EntityStore> senderRef = sender.getReference();
        if (senderRef == null || !senderRef.isValid()) return;

        // Get sender position
        Store<EntityStore> store = senderRef.getStore();
        TransformComponent tc = store.getComponent(senderRef, TransformComponent.getComponentType());
        Vector3d senderPos = tc != null ? tc.getPosition() : null;

        // Determine if yell
        boolean isYell = isYellMessage(message);

        // Format chat message with [RP] prefix
        Message chatMsg = Message.raw("[RP] " + sender.getUsername() + ": " + message);

        // Send text to members in range (using each receiver's own prefs for range)
        for (UUID memberUuid : members) {
            if (memberUuid.equals(uuid)) {
                // Always send text to self
                sender.sendMessage(chatMsg);
                continue;
            }

            PlayerRef memberRef = findPlayerRef(memberUuid);
            if (memberRef == null) continue;

            // Range check using receiver's prefs
            if (senderPos != null) {
                Ref<EntityStore> mRef = memberRef.getReference();
                if (mRef == null || !mRef.isValid()) continue;
                Store<EntityStore> mStore = mRef.getStore();
                TransformComponent mtc = mStore.getComponent(mRef, TransformComponent.getComponentType());
                if (mtc != null) {
                    PlayerBubblePrefs memberPrefs = prefsStorage.getPrefs(memberUuid);
                    int range = isYell ? memberPrefs.getEffectiveYellBubbleRange()
                                       : memberPrefs.getEffectiveRpCullDistance();
                    double maxRangeSq = (double) range * range;
                    Vector3d mPos = mtc.getPosition();
                    double dx = mPos.getX() - senderPos.getX();
                    double dz = mPos.getZ() - senderPos.getZ();
                    if (dx * dx + dz * dz > maxRangeSq) continue;
                }
            }

            memberRef.sendMessage(chatMsg);
        }

        if (triggerBubble) {
            // Trigger channel-restricted bubble
            onChat(sender, message, channelPin);
        }
    }

    // ---- queueConfirmation: show confirm HUD before channel switch ----
    public void queueConfirmation(@Nonnull PlayerRef sender, @Nonnull String prefix,
                                   int targetSlot, @Nonnull String message) {
        UUID uuid = sender.getUuid();
        Ref<EntityStore> ref = sender.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        com.hypixel.hytale.server.core.entity.entities.Player player =
            store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null) return;

        String confirmText;
        if ("pbc".equals(prefix)) {
            confirmText = "Send to public chat? Everyone will see this.";
        } else {
            int slotNum = targetSlot + 1;
            confirmText = "Switch to RP Channel " + slotNum + " before sending?";
        }

        ChannelConfirmHud hud = new ChannelConfirmHud(
            this, prefsStorage, channelStorage, sender, uuid,
            prefix, targetSlot, message, confirmText);
        player.getPageManager().openCustomPage(ref, store, hud);
    }

    // ---- onChat: entry point from chat event ----
    public void onChat(@Nonnull PlayerRef sender, @Nonnull String message) {
        onChat(sender, message, null, false);
    }

    public void onChat(@Nonnull PlayerRef sender, @Nonnull String message, @Nullable String channelPin) {
        onChat(sender, message, channelPin, false);
    }

    public void onChat(@Nonnull PlayerRef sender, @Nonnull String message,
                        @Nullable String channelPin, boolean dualVisibility) {
        UUID uuid = sender.getUuid();

        if (disabledPlayers.contains(uuid)) return;

        String[] words = tokenizeMessage(message);
        if (words.length == 0) return;

        // Clear BusyBubble thinking bubble if present (optional dependency via reflection)
        clearBusyBubble(uuid);

        Ref<EntityStore> senderRef = sender.getReference();
        if (senderRef == null || !senderRef.isValid()) return;

        UUID worldUuid = sender.getWorldUuid();
        if (worldUuid == null) return;
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) return;

        clearSpeech(uuid);

        long gen = generationCounters.merge(uuid, 1L, Long::sum);

        String speakerLowerName = sender.getUsername().toLowerCase();
        SpeechState state = new SpeechState(uuid, speakerLowerName, words, gen, world);
        state.setYellMessage(isYellMessage(message));
        if (channelPin != null) state.setChannelPin(channelPin);
        if (dualVisibility) state.setDualVisibilityBubble(true);

        // Plan superpages (capped at MAX_SUPERPAGES)
        int[] plan = planSuperpages(words, 0);
        int totalSuperpages = plan[0];
        int lastEffective = plan[1];
        state.setTotalPages(totalSuperpages);
        state.setLastEffectiveWordIndex(lastEffective);

        // Plan first superpage line layout (constrain by both char count and weight)
        int line1End = findPageEndIndex(words, 0, MAX_LINE_CHARS);
        int line1WeightEnd = findWeightSplitIndex(words, 0, MAX_WEIGHTED_WIDTH);
        line1End = Math.min(line1End, line1WeightEnd);
        boolean needsLine2 = line1End < lastEffective;

        if (needsLine2) {
            int line2End = findPageEndIndex(words, line1End + 1, MAX_LINE_CHARS);
            int line2WeightEnd = findWeightSplitIndex(words, line1End + 1, MAX_WEIGHTED_WIDTH);
            line2End = Math.min(line2End, line2WeightEnd);
            boolean needsLine3 = line2End < lastEffective;

            if (needsLine3) {
                state.setLineCount(3);
                state.setLine1EndWordIndex(line1End);
                state.setLine2EndWordIndex(line2End);
            } else {
                state.setLineCount(2);
                state.setLine1EndWordIndex(line1End);
                state.setLine2EndWordIndex(lastEffective);
            }
        } else {
            state.setLineCount(1);
            state.setLine1EndWordIndex(lastEffective);
            state.setLine2EndWordIndex(lastEffective);
        }

        // Look ahead one word for initial tile count so first word has padding
        String initSizeText = words[0];
        if (words.length > 1 && state.getLine1EndWordIndex() >= 1) {
            initSizeText = words[0] + " " + words[1];
        }
        state.setTileCount(selectTileCount(getWeightedLength(initSizeText)));
        activeSpeech.put(uuid, state);

        world.execute(() -> {
            if (!senderRef.isValid()) {
                activeSpeech.remove(uuid);
                return;
            }

            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            Store<EntityStore> store = senderRef.getStore();
            TransformComponent tc = store.getComponent(senderRef, TransformComponent.getComponentType());
            if (tc == null) {
                activeSpeech.remove(uuid);
                return;
            }
            Vector3d pos = tc.getPosition();
            state.setLastPosition(pos);

            // Get the player's real network ID for entity-attached particles
            if (state.getPlayerNetworkId() < 0) {
                NetworkId networkIdComp = store.getComponent(senderRef, NetworkId.getComponentType());
                if (networkIdComp != null) {
                    state.setPlayerNetworkId(networkIdComp.getId());
                }
            }

            // Compute height adjustment for custom NPC models (only taller-than-default)
            BoundingBox bb = store.getComponent(senderRef, BoundingBox.getComponentType());
            if (bb != null) {
                Box box = bb.getBoundingBox();
                double modelTop = box.max.y;
                double ha = modelTop - DEFAULT_MODEL_TOP;
                state.setHeightAdjust(ha > 0 ? ha : 0.0);
            }

            state.setCurrentWordIndex(0);

            // Register per-speaker custom color spawners (one-time, or re-register if color changed)
            if (themeStorage != null && state.getPlayerNetworkId() >= 0) {
                Color customTint = resolveEffectiveTint(uuid);
                if (customTint != null) {
                    String currentHex = resolveEffectiveHex(uuid);
                    String registeredHex = registeredCustomColorHex.get(uuid);
                    boolean needsRegister = !customColorPrefixes.containsKey(uuid)
                            || registeredHex == null
                            || !registeredHex.equals(currentHex);
                    if (needsRegister) {
                        registerSpeakerCustomColor(uuid, state.getPlayerNetworkId(), customTint);
                    }
                } else {
                    // No custom color — clear any stale per-speaker spawners
                    customColorPrefixes.remove(uuid);
                    customColorSpawnerPackets.remove(uuid);
                    customColorSystemPackets.remove(uuid);
                    registeredCustomColorHex.remove(uuid);
                }
            }

            if (state.getLineCount() >= 3) {
                // 3-line: spawn 3 stacked nameplates + 3-liner bubble
                int netId1 = world.getEntityStore().takeNextNetworkId();
                int netId2 = world.getEntityStore().takeNextNetworkId();
                int netId3 = world.getEntityStore().takeNextNetworkId();
                state.setVirtualEntityNetId(netId1);
                state.setLine2NetId(netId2);
                state.setLine3NetId(netId3);

                double ha = state.getHeightAdjust();
                double baseY = pos.getY() + BUBBLE_Y_OFFSET + ha + getBubbleYAdjust(3);
                Vector3d pos1 = new Vector3d(pos.getX(), baseY + LINE_GAP + LINE1_NUDGE_3L, pos.getZ());
                Vector3d pos2 = new Vector3d(pos.getX(), baseY + LINE2_NUDGE_3L, pos.getZ());
                Vector3d pos3 = new Vector3d(pos.getX(), baseY - LINE_GAP + LINE3_NUDGE_3L, pos.getZ());

                spawnVirtualEntity(netId1, pos1, words[0], uuid, pos, store);
                spawnVirtualEntity(netId2, pos2, "", uuid, pos, store);
                spawnVirtualEntity(netId3, pos3, "", uuid, pos, store);

                int playerNetId = state.getPlayerNetworkId();
                if (playerNetId >= 0) {
                    try { sendBubbleParticle(uuid, playerNetId, state.getTileCount(), 3); }
                    catch (Exception e) { LOGGER.at(java.util.logging.Level.WARNING).log("Initial bubble send error: " + e.getMessage()); }
                }
            } else if (state.getLineCount() >= 2) {
                // 2-line: spawn 2 stacked nameplates + 2-liner bubble
                int netId1 = world.getEntityStore().takeNextNetworkId();
                int netId2 = world.getEntityStore().takeNextNetworkId();
                state.setVirtualEntityNetId(netId1);
                state.setLine2NetId(netId2);

                double ha = state.getHeightAdjust();
                double baseY = pos.getY() + BUBBLE_Y_OFFSET + ha + getBubbleYAdjust(2);
                Vector3d pos1 = new Vector3d(pos.getX(), baseY + LINE_GAP / 2.0 + LINE1_NUDGE, pos.getZ());
                Vector3d pos2 = new Vector3d(pos.getX(), baseY - LINE_GAP / 2.0 + LINE2_NUDGE, pos.getZ());

                spawnVirtualEntity(netId1, pos1, words[0], uuid, pos, store);
                spawnVirtualEntity(netId2, pos2, "", uuid, pos, store);

                int playerNetId = state.getPlayerNetworkId();
                if (playerNetId >= 0) {
                    try { sendBubbleParticle(uuid, playerNetId, state.getTileCount(), 2); }
                    catch (Exception e) { LOGGER.at(java.util.logging.Level.WARNING).log("Initial bubble send error: " + e.getMessage()); }
                }
            } else {
                // Single-line: existing behavior
                int netId = world.getEntityStore().takeNextNetworkId();
                state.setVirtualEntityNetId(netId);

                String initialText = words[0];
                Vector3d bubblePos = new Vector3d(pos.getX(), pos.getY() + BUBBLE_Y_OFFSET + state.getHeightAdjust() + getBubbleYAdjust(1), pos.getZ());
                spawnVirtualEntity(netId, bubblePos, initialText, uuid, pos, store);

                int playerNetId = state.getPlayerNetworkId();
                if (playerNetId >= 0) {
                    try { sendBubbleParticle(uuid, playerNetId, state.getTileCount()); }
                    catch (Exception e) { LOGGER.at(java.util.logging.Level.WARNING).log("Initial bubble send error: " + e.getMessage()); }
                }
            }

            // Spawn page indicator if multiple superpages
            if (state.getTotalPages() > 1) {
                int piNetId = world.getEntityStore().takeNextNetworkId();
                state.setPageIndicatorNetId(piNetId);
                int lc = state.getLineCount();
                Vector3d piPos = new Vector3d(pos.getX(), pos.getY() + BUBBLE_Y_OFFSET + state.getHeightAdjust() + getBubbleYAdjust(lc) + getPageIndicatorYOffset(lc), pos.getZ());
                spawnVirtualEntity(piNetId, piPos, superscriptPage(state.getCurrentPage()), uuid, pos, store);
            }

            scheduleParticleLoop(uuid, gen);

            // Start mouth animation: register BC_Talk + play first word's mouth
            registerMouthAnims(uuid, pos);
            broadcastMouthForWord(uuid, words[0]);
            broadcastAnimaleseForWord(uuid, words[0]);
            if (isYellWord(words[0])) sendYellParticle(uuid);

            if (words.length > 1 && !state.isComplete()) {
                scheduleNextWord(uuid, gen, world);
            } else {
                scheduleHoldAndDespawn(uuid, gen, world);
            }
        });
    }

    // ---- Virtual entity spawn ----
    @SuppressWarnings("removal")
    private void spawnVirtualEntity(int netId, Vector3d position, String text,
                                     UUID speakerUuid, Vector3d speakerPos,
                                     Store<EntityStore> store) {
        IntangibleUpdate intangibleUpdate = new IntangibleUpdate();
        NameplateUpdate nameplateUpdate = new NameplateUpdate(text);

        ModelUpdate modelUpdate;
        if (cachedFloatingTextModel != null) {
            modelUpdate = new ModelUpdate(cachedFloatingTextModel, 0.001f);
        } else {
            com.hypixel.hytale.protocol.Model fallbackModel = new com.hypixel.hytale.protocol.Model();
            fallbackModel.scale = 0.001f;
            modelUpdate = new ModelUpdate(fallbackModel, 0.001f);
        }

        ModelTransform mt = new ModelTransform();
        mt.position = PositionUtil.toPositionPacket(position);
        mt.bodyOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        mt.lookOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        TransformUpdate transformUpdate = new TransformUpdate(mt);

        EntityUpdate entityUpdate = new EntityUpdate(netId, null,
                new ComponentUpdate[]{intangibleUpdate, nameplateUpdate, modelUpdate, transformUpdate});
        EntityUpdates spawnPacket = new EntityUpdates(null, new EntityUpdate[]{entityUpdate});

        for (PlayerRef viewer : getViewers(speakerUuid, speakerPos)) {
            viewer.getPacketHandler().writeNoCache(spawnPacket);
        }
    }

    // ---- Update nameplate text ----
    @SuppressWarnings("removal")
    private void updateNameplate(int netId, String text, UUID speakerUuid, Vector3d speakerPos) {
        NameplateUpdate nameplateUpdate = new NameplateUpdate(text);
        EntityUpdate entityUpdate = new EntityUpdate(netId, null,
                new ComponentUpdate[]{nameplateUpdate});
        EntityUpdates updatePacket = new EntityUpdates(null, new EntityUpdate[]{entityUpdate});

        for (PlayerRef viewer : getViewers(speakerUuid, speakerPos)) {
            viewer.getPacketHandler().writeNoCache(updatePacket);
        }
    }

    // ---- Update virtual entity position (follow player) ----
    @SuppressWarnings("removal")
    private void updateVirtualEntityPosition(int netId, Vector3d playerPos, UUID speakerUuid, double yAdjust) {
        Vector3d bubblePos = new Vector3d(playerPos.getX(), playerPos.getY() + BUBBLE_Y_OFFSET + yAdjust, playerPos.getZ());
        ModelTransform mt = new ModelTransform();
        mt.position = PositionUtil.toPositionPacket(bubblePos);
        mt.bodyOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        mt.lookOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        TransformUpdate transformUpdate = new TransformUpdate(mt);

        EntityUpdate entityUpdate = new EntityUpdate(netId, null,
                new ComponentUpdate[]{transformUpdate});
        EntityUpdates updatePacket = new EntityUpdates(null, new EntityUpdate[]{entityUpdate});

        for (PlayerRef viewer : getViewers(speakerUuid, playerPos)) {
            viewer.getPacketHandler().writeNoCache(updatePacket);
        }
    }

    // ---- Send raw position update (no Y offset added) ----
    private void sendPositionUpdate(int netId, Vector3d position, UUID speakerUuid, Vector3d speakerPos) {
        ModelTransform mt = new ModelTransform();
        mt.position = PositionUtil.toPositionPacket(position);
        mt.bodyOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        mt.lookOrientation = PositionUtil.toDirectionPacket(new Vector3f(0, 0, 0));
        TransformUpdate transformUpdate = new TransformUpdate(mt);

        EntityUpdate entityUpdate = new EntityUpdate(netId, null,
                new ComponentUpdate[]{transformUpdate});
        EntityUpdates updatePacket = new EntityUpdates(null, new EntityUpdate[]{entityUpdate});

        for (PlayerRef viewer : getViewers(speakerUuid, speakerPos)) {
            viewer.getPacketHandler().writeNoCache(updatePacket);
        }
    }

    private static final long PUNCTUATION_PAUSE_MS = 800;

    /** Check if a word ends a sentence or is a dash interruption, warranting an extra pause. */
    private static boolean isPauseWord(String word) {
        if (word == null || word.isEmpty()) return false;
        // Dash interruptions: word is entirely dashes, or ends with one or more dashes
        if (word.matches("-+")) return true;
        // Sentence-ending punctuation (ignoring trailing quotes/parens)
        String stripped = word.replaceAll("[\"')\\]]+$", "");
        if (stripped.isEmpty()) return false;
        char last = stripped.charAt(stripped.length() - 1);
        return last == '.' || last == '?' || last == '!';
    }

    // ---- Word reveal scheduling ----
    private void scheduleNextWord(UUID uuid, long gen, World world) {
        // Check if the just-revealed word warrants an extra pause
        SpeechState cur = activeSpeech.get(uuid);
        long delay = WORD_REVEAL_MS;
        if (cur != null) {
            int idx = cur.getCurrentWordIndex();
            String[] words = cur.getWords();
            if (idx >= 0 && idx < words.length && isPauseWord(words[idx])) {
                delay = WORD_REVEAL_MS + PUNCTUATION_PAUSE_MS;
            }
        }

        long finalDelay = delay;
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            SpeechState state = activeSpeech.get(uuid);
            if (state == null || state.getGeneration() != gen) return;

            world.execute(() -> {
                Long cg = generationCounters.get(uuid);
                if (cg == null || cg.longValue() != gen) return;

                SpeechState s = activeSpeech.get(uuid);
                if (s == null || s.getGeneration() != gen) return;

                int nextIdx = s.getCurrentWordIndex() + 1;
                if (nextIdx >= s.getWords().length) {
                    scheduleHoldAndDespawn(uuid, gen, world);
                    return;
                }

                if (s.getLineCount() >= 2) {
                    handleMultilineWordReveal(uuid, gen, world, s, nextIdx);
                } else {
                    handleSingleLineWordReveal(uuid, gen, world, s, nextIdx);
                }
            });
        }, finalDelay, TimeUnit.MILLISECONDS);

        pendingReveals.put(uuid, future);
    }

    // ---- Single-line word reveal (uses superpage transitions when line is full) ----
    private void handleSingleLineWordReveal(UUID uuid, long gen, World world, SpeechState s, int nextIdx) {
        int pageEnd = findPageEndIndex(s.getWords(), s.getPageStartIndex(), MAX_LINE_CHARS);
        // Also check weight limit
        int weightEnd = findWeightSplitIndex(s.getWords(), s.getPageStartIndex(), MAX_WEIGHTED_WIDTH);
        pageEnd = Math.min(pageEnd, weightEnd);

        if (nextIdx > pageEnd && nextIdx <= s.getLastEffectiveWordIndex()) {
            // Line full but more words to show — transition to next superpage
            scheduleSuperpageTransition(uuid, gen, world, nextIdx);
            return;
        }

        s.setCurrentWordIndex(nextIdx);
        broadcastMouthForWord(uuid, s.getWords()[nextIdx]);
        broadcastAnimaleseForWord(uuid, s.getWords()[nextIdx]);
        if (isYellWord(s.getWords()[nextIdx])) sendYellParticle(uuid);
        String displayText = s.buildDisplayText();

        // Look ahead: size bubble for the NEXT word too, so it's already wide enough
        // when that word appears. The regular particle loop picks up the new tile count.
        String sizeText = displayText;
        if (nextIdx + 1 < s.getWords().length && nextIdx < pageEnd) {
            sizeText = displayText + " " + s.getWords()[nextIdx + 1];
        }
        int newTileCount = selectTileCount(getWeightedLength(sizeText));
        if (newTileCount != s.getTileCount()) {
            s.setTileCount(newTileCount);
        }

        int netId = s.getVirtualEntityNetId();
        Vector3d lastPos = s.getLastPosition();
        if (netId >= 0 && lastPos != null) {
            updateNameplate(netId, displayText, uuid, lastPos);
        }

        if (!s.isComplete()) {
            scheduleNextWord(uuid, gen, world);
        } else {
            scheduleHoldAndDespawn(uuid, gen, world);
        }
    }

    // ---- Multi-line word reveal (2-line or 3-line superpage behavior) ----
    private void handleMultilineWordReveal(UUID uuid, long gen, World world, SpeechState s, int nextIdx) {
        String[] words = s.getWords();
        int line1End = s.getLine1EndWordIndex();
        int line2End = s.getLine2EndWordIndex();
        int lineCount = s.getLineCount();

        // Calculate superpage end (end of last line in this superpage, constrained by weight)
        int superpageEnd;
        if (lineCount >= 3) {
            if (line2End < words.length - 1) {
                superpageEnd = Math.min(
                    findPageEndIndex(words, line2End + 1, MAX_LINE_CHARS),
                    findWeightSplitIndex(words, line2End + 1, MAX_WEIGHTED_WIDTH)
                );
            } else {
                superpageEnd = line2End;
            }
        } else {
            if (line1End < words.length - 1) {
                superpageEnd = Math.min(
                    findPageEndIndex(words, line1End + 1, MAX_LINE_CHARS),
                    findWeightSplitIndex(words, line1End + 1, MAX_WEIGHTED_WIDTH)
                );
            } else {
                superpageEnd = line1End;
            }
        }

        if (nextIdx > superpageEnd && nextIdx <= s.getLastEffectiveWordIndex()) {
            // Superpage full — transition to next superpage
            scheduleSuperpageTransition(uuid, gen, world, nextIdx);
            return;
        }

        s.setCurrentWordIndex(nextIdx);
        broadcastMouthForWord(uuid, s.getWords()[nextIdx]);
        broadcastAnimaleseForWord(uuid, s.getWords()[nextIdx]);
        if (isYellWord(s.getWords()[nextIdx])) sendYellParticle(uuid);
        boolean onLine1 = nextIdx <= line1End;
        boolean onLine2 = !onLine1 && nextIdx <= line2End;
        Vector3d lastPos = s.getLastPosition();

        if (onLine1) {
            // Still filling Line 1 — grow bubble width
            String line1Text = s.buildLine1Text();

            // Look ahead: size bubble for the NEXT word too, so text doesn't clip the edge
            String sizeText = line1Text;
            if (nextIdx < line1End && nextIdx + 1 < words.length) {
                sizeText = line1Text + " " + words[nextIdx + 1];
            }
            int newTileCount = selectTileCount(getWeightedLength(sizeText));
            if (newTileCount != s.getTileCount()) {
                s.setTileCount(newTileCount);
            }

            int netId = s.getVirtualEntityNetId();
            if (netId >= 0 && lastPos != null) {
                updateNameplate(netId, line1Text, uuid, lastPos);
            }
        } else if (onLine2) {
            // Filling Line 2
            String line2Text = s.buildLine2Text();

            String sizeText = line2Text;
            if (nextIdx < line2End && nextIdx + 1 < words.length) {
                sizeText = line2Text + " " + words[nextIdx + 1];
            }
            int newTileCount = selectTileCount(getWeightedLength(sizeText));
            if (newTileCount > s.getTileCount()) {
                s.setTileCount(newTileCount);
            }
            int netId2 = s.getLine2NetId();
            if (netId2 >= 0 && lastPos != null) {
                updateNameplate(netId2, line2Text, uuid, lastPos);
            }
        } else {
            // Filling Line 3 (only in 3-liner mode)
            String line3Text = s.buildLine3Text();

            String sizeText = line3Text;
            if (nextIdx + 1 < words.length && nextIdx < superpageEnd) {
                sizeText = line3Text + " " + words[nextIdx + 1];
            }
            int newTileCount = selectTileCount(getWeightedLength(sizeText));
            if (newTileCount > s.getTileCount()) {
                s.setTileCount(newTileCount);
            }
            int netId3 = s.getLine3NetId();
            if (netId3 >= 0 && lastPos != null) {
                updateNameplate(netId3, line3Text, uuid, lastPos);
            }
        }

        if (!s.isComplete()) {
            scheduleNextWord(uuid, gen, world);
        } else {
            scheduleHoldAndDespawn(uuid, gen, world);
        }
    }

    /**
     * Cancel the current particle loop, spawn the new variant immediately,
     * and start a fresh loop. Immediate send is fine because the new bubble
     * is always >= the old size (fully covers it, no visible overlap).
     */
    private void restartParticleLoop(UUID uuid, long gen, SpeechState s) {
        ScheduledFuture<?> pf = pendingParticleLoops.remove(uuid);
        if (pf != null) pf.cancel(false);

        int playerNetId = s.getPlayerNetworkId();
        if (playerNetId >= 0) {
            sendBubbleParticle(uuid, playerNetId, s.getTileCount(), s.getLineCount());
        }
        scheduleParticleLoop(uuid, gen);
    }

    // ---- Superpage transition: despawn old nameplates, plan next superpage, spawn new ----
    private void scheduleSuperpageTransition(UUID uuid, long gen, World world, int nextStart) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            SpeechState state = activeSpeech.get(uuid);
            if (state == null || state.getGeneration() != gen) return;

            world.execute(() -> {
                Long cg = generationCounters.get(uuid);
                if (cg == null || cg.longValue() != gen) return;

                SpeechState s = activeSpeech.get(uuid);
                if (s == null || s.getGeneration() != gen) return;

                Vector3d lastPos = s.getLastPosition();

                // Despawn old nameplates + page indicator
                int oldNet1 = s.getVirtualEntityNetId();
                int oldNet2 = s.getLine2NetId();
                int oldNet3 = s.getLine3NetId();
                int oldPi = s.getPageIndicatorNetId();
                if (lastPos != null) {
                    List<Integer> despawnIds = new ArrayList<>();
                    if (oldNet1 >= 0) despawnIds.add(oldNet1);
                    if (oldNet2 >= 0) despawnIds.add(oldNet2);
                    if (oldNet3 >= 0) despawnIds.add(oldNet3);
                    if (oldPi >= 0) despawnIds.add(oldPi);
                    if (!despawnIds.isEmpty()) {
                        int[] ids = despawnIds.stream().mapToInt(Integer::intValue).toArray();
                        EntityUpdates despawnPacket = new EntityUpdates(ids, null);
                        for (PlayerRef viewer : getViewers(uuid, lastPos)) {
                            viewer.getPacketHandler().writeNoCache(despawnPacket);
                        }
                    }
                }

                // Plan next superpage (constrain by both char count and weight)
                String[] words = s.getWords();
                int lastEffective = s.getLastEffectiveWordIndex();
                int line1End = findPageEndIndex(words, nextStart, MAX_LINE_CHARS);
                int line1WeightEnd = findWeightSplitIndex(words, nextStart, MAX_WEIGHTED_WIDTH);
                line1End = Math.min(line1End, line1WeightEnd);
                if (line1End > lastEffective) line1End = lastEffective;
                boolean needsLine2 = line1End < lastEffective;

                int lineCount;
                int line2End;

                if (needsLine2) {
                    line2End = findPageEndIndex(words, line1End + 1, MAX_LINE_CHARS);
                    int line2WeightEnd = findWeightSplitIndex(words, line1End + 1, MAX_WEIGHTED_WIDTH);
                    line2End = Math.min(line2End, line2WeightEnd);
                    if (line2End > lastEffective) line2End = lastEffective;
                    boolean needsLine3 = line2End < lastEffective;
                    lineCount = needsLine3 ? 3 : 2;
                } else {
                    line2End = lastEffective;
                    lineCount = 1;
                }

                s.setPageStartIndex(nextStart);
                s.setCurrentWordIndex(nextStart);
                s.setLineCount(lineCount);
                s.setLine1EndWordIndex(line1End);
                s.setLine2EndWordIndex(line2End);
                s.setContinuationPage(true);
                s.setCurrentPage(s.getCurrentPage() + 1);

                // Allocate new net IDs and spawn nameplates
                PlayerRef playerRef = findPlayerRef(uuid);
                if (playerRef == null || lastPos == null) return;

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;
                Store<EntityStore> store = ref.getStore();

                // Get fresh position
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                Vector3d pos = tc != null ? tc.getPosition() : lastPos;
                s.setLastPosition(pos);

                int newNet1 = world.getEntityStore().takeNextNetworkId();
                s.setVirtualEntityNetId(newNet1);
                String initText = words[nextStart];

                if (lineCount >= 3) {
                    int newNet2 = world.getEntityStore().takeNextNetworkId();
                    int newNet3 = world.getEntityStore().takeNextNetworkId();
                    s.setLine2NetId(newNet2);
                    s.setLine3NetId(newNet3);

                    double baseY = pos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(3);
                    Vector3d pos1 = new Vector3d(pos.getX(), baseY + LINE_GAP + LINE1_NUDGE_3L, pos.getZ());
                    Vector3d pos2 = new Vector3d(pos.getX(), baseY + LINE2_NUDGE_3L, pos.getZ());
                    Vector3d pos3 = new Vector3d(pos.getX(), baseY - LINE_GAP + LINE3_NUDGE_3L, pos.getZ());

                    spawnVirtualEntity(newNet1, pos1, initText, uuid, pos, store);
                    spawnVirtualEntity(newNet2, pos2, "", uuid, pos, store);
                    spawnVirtualEntity(newNet3, pos3, "", uuid, pos, store);
                } else if (lineCount >= 2) {
                    int newNet2 = world.getEntityStore().takeNextNetworkId();
                    s.setLine2NetId(newNet2);
                    s.setLine3NetId(-1);

                    double baseY = pos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(2);
                    Vector3d pos1 = new Vector3d(pos.getX(), baseY + LINE_GAP / 2.0 + LINE1_NUDGE, pos.getZ());
                    Vector3d pos2 = new Vector3d(pos.getX(), baseY - LINE_GAP / 2.0 + LINE2_NUDGE, pos.getZ());

                    spawnVirtualEntity(newNet1, pos1, initText, uuid, pos, store);
                    spawnVirtualEntity(newNet2, pos2, "", uuid, pos, store);
                } else {
                    s.setLine2NetId(-1);
                    s.setLine3NetId(-1);
                    Vector3d bubblePos = new Vector3d(pos.getX(), pos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(1), pos.getZ());
                    spawnVirtualEntity(newNet1, bubblePos, initText, uuid, pos, store);
                }

                // Spawn page indicator for new superpage
                if (s.getTotalPages() > 1) {
                    int piNetId = world.getEntityStore().takeNextNetworkId();
                    s.setPageIndicatorNetId(piNetId);
                    int lc = s.getLineCount();
                    Vector3d piPos = new Vector3d(pos.getX(), pos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(lc) + getPageIndicatorYOffset(lc), pos.getZ());
                    spawnVirtualEntity(piNetId, piPos, superscriptPage(s.getCurrentPage()), uuid, pos, store);
                }

                // Look ahead to pre-size for next word on new page
                String spSizeText = initText;
                int spIdx = s.getCurrentWordIndex();
                if (spIdx + 1 < s.getWords().length) {
                    spSizeText = initText + " " + s.getWords()[spIdx + 1];
                }
                s.setTileCount(selectTileCount(getWeightedLength(spSizeText)));
                restartParticleLoop(uuid, gen, s);
                broadcastMouthForWord(uuid, initText);
                broadcastAnimaleseForWord(uuid, initText);
                if (isYellWord(initText)) sendYellParticle(uuid);

                // Delay before starting word reveal on new superpage
                scheduleSuperpageStartReveal(uuid, gen, world);
            });
        }, PAGE_PAUSE_MS, TimeUnit.MILLISECONDS);

        pendingReveals.put(uuid, future);
    }

    // ---- Superpage start delay: show first word then begin reveal ----
    private void scheduleSuperpageStartReveal(UUID uuid, long gen, World world) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            SpeechState state = activeSpeech.get(uuid);
            if (state == null || state.getGeneration() != gen) return;

            world.execute(() -> {
                Long cg = generationCounters.get(uuid);
                if (cg == null || cg.longValue() != gen) return;

                SpeechState s = activeSpeech.get(uuid);
                if (s == null || s.getGeneration() != gen) return;

                // First word is already shown (set in scheduleSuperpageTransition)
                // Look ahead to pre-size for next word
                String line1Text = s.buildLine1Text();
                String sizeText = line1Text;
                int idx = s.getCurrentWordIndex();
                if (idx + 1 < s.getWords().length) {
                    sizeText = line1Text + " " + s.getWords()[idx + 1];
                }
                int newTileCount = selectTileCount(getWeightedLength(sizeText));
                if (newTileCount != s.getTileCount()) {
                    s.setTileCount(newTileCount);
                }

                int netId = s.getVirtualEntityNetId();
                Vector3d lastPos = s.getLastPosition();
                if (netId >= 0 && lastPos != null) {
                    updateNameplate(netId, line1Text, uuid, lastPos);
                }

                if (!s.isComplete()) {
                    scheduleNextWord(uuid, gen, world);
                } else {
                    scheduleHoldAndDespawn(uuid, gen, world);
                }
            });
        }, PAGE_START_DELAY_MS, TimeUnit.MILLISECONDS);

        pendingReveals.put(uuid, future);
    }

    // ---- Hold after complete, fade out, then despawn ----
    private void scheduleHoldAndDespawn(UUID uuid, long gen, World world) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            // Stop the regular particle loop, send fade particle, despawn text immediately
            SpeechState s = activeSpeech.get(uuid);
            if (s != null && s.getGeneration() == gen) {
                ScheduledFuture<?> pf = pendingParticleLoops.remove(uuid);
                if (pf != null) pf.cancel(false);

                // Close mouth when done talking
                broadcastMouthRest(uuid);

                int playerNetId = s.getPlayerNetworkId();
                if (playerNetId >= 0) {
                    // getViewers() accesses components — must run on world thread
                    world.execute(() -> {
                        try {
                            // Verify speaker hasn't moved worlds since chat started
                            PlayerRef fr = findPlayerRef(uuid);
                            if (fr != null) {
                                Ref<EntityStore> cr = fr.getReference();
                                if (cr != null && cr.isValid()
                                        && cr.getStore().getExternalData().getWorld() != world) {
                                    return; // Stale world — skip fade
                                }
                            }
                            sendFadeParticle(uuid, playerNetId, s.getTileCount(), s.getLineCount());
                        } catch (Exception e) {
                            LOGGER.at(java.util.logging.Level.WARNING).log("Fade particle error: " + e.getMessage());
                        }
                    });
                }
            }

            // Despawn nameplates 100ms after fade starts
            scheduler.schedule(() -> {
                Long cg = generationCounters.get(uuid);
                if (cg == null || cg.longValue() != gen) return;
                world.execute(() -> performDespawn(uuid, gen));
            }, 100, TimeUnit.MILLISECONDS);
        }, HOLD_AFTER_COMPLETE_MS, TimeUnit.MILLISECONDS);

        pendingReveals.put(uuid, future);
    }

    // ---- Perform the actual despawn ----
    private void performDespawn(UUID uuid, long gen) {
        Long currentGen = generationCounters.get(uuid);
        if (currentGen == null || currentGen != gen) return;

        SpeechState state = activeSpeech.get(uuid);
        if (state == null || state.getGeneration() != gen) return;

        // Reset mouth to Default before removing state
        int playerNetId = state.getPlayerNetworkId();
        Vector3d lastPos = state.getLastPosition();
        broadcastMouthReset(uuid, playerNetId, lastPos);

        // Collect viewers BEFORE removing from activeSpeech — getViewers() needs
        // the SpeechState to apply correct channel isolation for despawn targeting
        int[] despawnIds = collectDespawnIds(state);
        List<PlayerRef> viewers = despawnIds.length > 0 ? getViewers(uuid, null) : List.of();

        activeSpeech.remove(uuid);
        pendingReveals.remove(uuid);
        lastParticleSendTime.remove(uuid);
        ScheduledFuture<?> pf = pendingParticleLoops.remove(uuid);
        if (pf != null) pf.cancel(false);

        // Despawn virtual entity/entities + page indicator
        // Send to ALL players (no cull distance) to ensure cleanup reaches viewers who may have moved away
        if (despawnIds.length > 0) {
            EntityUpdates despawnPacket = new EntityUpdates(despawnIds, null);
            for (PlayerRef viewer : viewers) {
                viewer.getPacketHandler().writeNoCache(despawnPacket);
            }
        }
    }

    /** Collect virtual entity + page indicator network IDs for despawn into a compact int[]. */
    private static int[] collectDespawnIds(SpeechState state) {
        int netId = state.getVirtualEntityNetId();
        int line2NetId = state.getLine2NetId();
        int line3NetId = state.getLine3NetId();
        int piNetId = state.getPageIndicatorNetId();
        int count = 0;
        if (netId >= 0) count++;
        if (line2NetId >= 0) count++;
        if (line3NetId >= 0) count++;
        if (piNetId >= 0) count++;
        if (count == 0) return new int[0];
        int[] ids = new int[count];
        int idx = 0;
        if (netId >= 0) ids[idx++] = netId;
        if (line2NetId >= 0) ids[idx++] = line2NetId;
        if (line3NetId >= 0) ids[idx++] = line3NetId;
        if (piNetId >= 0) ids[idx++] = piNetId;
        return ids;
    }

    // ---- Select tile count using linear scaling + weighted width ----
    private int selectTileCount(float charWeight) {
        float ratio = Math.min(charWeight / MAX_WEIGHTED_WIDTH, 1.0f);
        int tiles = Math.max(0, Math.round(ratio * MAX_TILES));
        return tiles;
    }

    // ---- Send entity-attached particle (thread-safe, no world.execute needed) ----
    private void sendBubbleParticle(UUID speakerUuid, int entityNetId, int tileCount) {
        sendBubbleParticle(speakerUuid, entityNetId, tileCount, 1);
    }

    private void sendBubbleParticle(UUID speakerUuid, int entityNetId, int tileCount, int lineCount) {
        SpeechState ss = activeSpeech.get(speakerUuid);
        double ha = ss != null ? ss.getHeightAdjust() : 0.0;
        float adjustedY = (float) (particleYOffset + getBubbleYAdjust(lineCount) + ha);
        com.hypixel.hytale.protocol.Vector3f offset = new com.hypixel.hytale.protocol.Vector3f(0f, adjustedY, 0f);
        lastParticleSendTime.put(speakerUuid, System.currentTimeMillis());

        String speakerLowerName = ss != null ? ss.getSpeakerLowerName() : null;
        String defaultSpawner = resolveSpawnerName(speakerUuid, lineCount, tileCount);

        Vector3d speakerPos = ss != null ? ss.getLastPosition() : null;
        List<PlayerRef> viewers = getViewers(speakerUuid, speakerPos);
        if (ss != null) ss.setCachedViewers(viewers);

        // Cache packets by spawner name to avoid rebuilding for each viewer with the same color
        Map<String, SpawnModelParticles> packetCache = new HashMap<>();

        for (PlayerRef viewer : viewers) {
            try {
                if (viewer.getPacketHandler() == null) continue;
                String spawner = resolveSpawnerNameForViewer(viewer.getUuid(), speakerUuid, speakerLowerName, lineCount, tileCount);
                SpawnModelParticles packet = packetCache.computeIfAbsent(spawner,
                    name -> buildParticlePacket(name, entityNetId, null, offset));
                viewer.getPacketHandler().writeNoCache(packet);
            } catch (Exception ignored) {}
        }
    }

    /** Resolve the spawner name: per-speaker custom prefix if custom color active, else default dark/light. */
    private String resolveSpawnerName(UUID speakerUuid, int lineCount, int tileCount) {
        PlayerBubbleTheme theme = themeStorage != null ? themeStorage.getTheme(speakerUuid) : new PlayerBubbleTheme();
        boolean light = theme.lightMode;

        String customPrefix = customColorPrefixes.get(speakerUuid);
        if (customPrefix != null) {
            // Custom color — still respect light/dark for correct outline texture
            String lineType = lineCount >= 3
                ? (light ? "3Liner_Light_" : "3Liner_")
                : lineCount >= 2
                    ? (light ? "2Liner_Light_" : "2Liner_")
                    : (light ? "Bubble_Light_" : "Bubble_");
            return customPrefix + "_" + lineType + tileCount;
        }
        // Default dark/light
        String prefix = lineCount >= 3
            ? (light ? "BC_3Liner_Light_" : "BC_3Liner_")
            : lineCount >= 2
                ? (light ? "BC_2Liner_Light_" : "BC_2Liner_")
                : (light ? "BC_Bubble_Light_" : "BC_Bubble_");
        return prefix + tileCount;
    }

    private void sendFadeParticle(UUID speakerUuid, int entityNetId, int tileCount, int lineCount) {
        SpeechState ss = activeSpeech.get(speakerUuid);
        double ha = ss != null ? ss.getHeightAdjust() : 0.0;
        float adjustedY = (float) (particleYOffset + getBubbleYAdjust(lineCount) + ha);
        com.hypixel.hytale.protocol.Vector3f offset = new com.hypixel.hytale.protocol.Vector3f(0f, adjustedY, 0f);

        String speakerLowerName = ss != null ? ss.getSpeakerLowerName() : null;
        Map<String, SpawnModelParticles> packetCache = new HashMap<>();

        Vector3d speakerPos = ss != null ? ss.getLastPosition() : null;
        for (PlayerRef viewer : getViewers(speakerUuid, speakerPos)) {
            try {
                if (viewer.getPacketHandler() == null) continue;
                String spawner = resolveSpawnerNameForViewer(viewer.getUuid(), speakerUuid, speakerLowerName, lineCount, tileCount) + "_Fade";
                SpawnModelParticles packet = packetCache.computeIfAbsent(spawner,
                    name -> buildParticlePacket(name, entityNetId, null, offset));
                viewer.getPacketHandler().writeNoCache(packet);
            } catch (Exception ignored) {}
        }
    }

    private SpawnModelParticles buildParticlePacket(String systemId, int entityNetId, Color color,
                                                     com.hypixel.hytale.protocol.Vector3f offset) {
        ModelParticle particle = new ModelParticle(
            systemId, 1.0f, color, EntityPart.Self, null, offset, null, false
        );
        return new SpawnModelParticles(entityNetId, new ModelParticle[]{particle});
    }

    @Nullable
    /**
     * Resolve effective bubble tint: player's custom color, or server default if set.
     * Returns null if neither is configured (use vanilla spawner).
     */
    private Color resolveEffectiveTint(UUID uuid) {
        if (themeStorage == null) return null;
        PlayerBubbleTheme theme = themeStorage.getTheme(uuid);
        Color tint = theme.toProtocolColor();
        if (tint == null && serverConfig != null && serverConfig.defaultBubbleColor != null) {
            return parseHexColor(serverConfig.defaultBubbleColor);
        }
        return tint;
    }

    private String resolveEffectiveHex(UUID uuid) {
        if (themeStorage == null) return null;
        PlayerBubbleTheme theme = themeStorage.getTheme(uuid);
        if (theme.tintColorHex != null) return theme.tintColorHex;
        if (serverConfig != null) return serverConfig.defaultBubbleColor;
        return null;
    }

    private static Color parseHexColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() != 6) return null;
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new Color((byte) r, (byte) g, (byte) b);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Particle re-spawn loop ----
    private void scheduleParticleLoop(UUID uuid, long gen) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Long currentGen = generationCounters.get(uuid);
            if (currentGen == null || currentGen != gen) return;

            SpeechState s = activeSpeech.get(uuid);
            if (s == null || s.getGeneration() != gen) return;

            int playerNetId = s.getPlayerNetworkId();
            if (playerNetId < 0) return;

            // Send particle immediately using cached viewers (no world.execute latency)
            List<PlayerRef> viewers = s.getCachedViewers();
            if (viewers != null && !viewers.isEmpty()) {
                try {
                    double ha = s.getHeightAdjust();
                    float adjustedY = (float) (particleYOffset + getBubbleYAdjust(s.getLineCount()) + ha);
                    var offset = new com.hypixel.hytale.protocol.Vector3f(0f, adjustedY, 0f);
                    String speakerLowerName = s.getSpeakerLowerName();
                    Map<String, SpawnModelParticles> packetCache = new HashMap<>();
                    lastParticleSendTime.put(uuid, System.currentTimeMillis());
                    for (PlayerRef viewer : viewers) {
                        try {
                            if (viewer.getPacketHandler() == null) continue;
                            String spawner = resolveSpawnerNameForViewer(viewer.getUuid(), uuid, speakerLowerName, s.getLineCount(), s.getTileCount());
                            SpawnModelParticles packet = packetCache.computeIfAbsent(spawner,
                                name -> buildParticlePacket(name, playerNetId, null, offset));
                            viewer.getPacketHandler().writeNoCache(packet);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    LOGGER.at(java.util.logging.Level.WARNING).log("Particle loop error: " + e.getMessage());
                }
            }

            // Refresh cached viewer list on world thread for next cycle
            World w = s.getWorld();
            if (w != null) {
                w.execute(() -> {
                    try {
                        s.setCachedViewers(getViewers(uuid, s.getLastPosition()));
                    } catch (Exception ignored) {}
                });
            }

            scheduleParticleLoop(uuid, gen);
        }, BUBBLE_PARTICLE_RESPAWN_MS, TimeUnit.MILLISECONDS);

        pendingParticleLoops.put(uuid, future);
    }

    @Nullable
    private PlayerRef findPlayerRef(UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUuid().equals(uuid)) return p;
        }
        return null;
    }

    // ---- Poll loop: cleanup disconnected/dead players + update virtual entity position ----
    public void pollAndCleanup() {
        if (activeSpeech.isEmpty()) return;

        for (Map.Entry<UUID, SpeechState> entry : activeSpeech.entrySet()) {
            UUID uuid = entry.getKey();

            PlayerRef playerRef = findPlayerRef(uuid);
            if (playerRef == null) {
                clearSpeech(uuid);
                continue;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                clearSpeech(uuid);
                continue;
            }

            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();

            // Entity component reads MUST run on world thread
            world.execute(() -> {
                if (!ref.isValid()) return;

                SpeechState s = activeSpeech.get(uuid);
                if (s == null) return;

                // Verify speaker hasn't moved to a different world instance (e.g. dungeon portal)
                // since we captured the world reference. If they did, this lambda is running on the
                // wrong thread — getViewers() would access instance stores from the default thread.
                PlayerRef freshRef = findPlayerRef(uuid);
                if (freshRef == null) { clearSpeech(uuid); return; }
                Ref<EntityStore> currentRef = freshRef.getReference();
                if (currentRef == null || !currentRef.isValid()) { clearSpeech(uuid); return; }
                if (currentRef.getStore().getExternalData().getWorld() != world) {
                    clearSpeech(uuid);
                    return;
                }

                DeathComponent deathComp = store.getComponent(ref, DeathComponent.getComponentType());
                if (deathComp != null) {
                    clearSpeech(uuid);
                    return;
                }

                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    Vector3d currentPos = tc.getPosition();
                    int netId = s.getVirtualEntityNetId();
                    if (netId >= 0) {
                        if (s.getLineCount() >= 3) {
                            // 3-line: update all 3 nameplate positions
                            double baseY = currentPos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(3);
                            Vector3d pos1 = new Vector3d(currentPos.getX(), baseY + LINE_GAP + LINE1_NUDGE_3L, currentPos.getZ());
                            sendPositionUpdate(netId, pos1, uuid, currentPos);
                            int line2NetId = s.getLine2NetId();
                            if (line2NetId >= 0) {
                                Vector3d pos2 = new Vector3d(currentPos.getX(), baseY + LINE2_NUDGE_3L, currentPos.getZ());
                                sendPositionUpdate(line2NetId, pos2, uuid, currentPos);
                            }
                            int line3NetId = s.getLine3NetId();
                            if (line3NetId >= 0) {
                                Vector3d pos3 = new Vector3d(currentPos.getX(), baseY - LINE_GAP + LINE3_NUDGE_3L, currentPos.getZ());
                                sendPositionUpdate(line3NetId, pos3, uuid, currentPos);
                            }
                        } else if (s.getLineCount() >= 2) {
                            // 2-line: update both nameplate positions
                            double baseY = currentPos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(2);
                            Vector3d pos1 = new Vector3d(currentPos.getX(), baseY + LINE_GAP / 2.0 + LINE1_NUDGE, currentPos.getZ());
                            sendPositionUpdate(netId, pos1, uuid, currentPos);
                            int line2NetId = s.getLine2NetId();
                            if (line2NetId >= 0) {
                                Vector3d pos2 = new Vector3d(currentPos.getX(), baseY - LINE_GAP / 2.0 + LINE2_NUDGE, currentPos.getZ());
                                sendPositionUpdate(line2NetId, pos2, uuid, currentPos);
                            }
                        } else {
                            updateVirtualEntityPosition(netId, currentPos, uuid, s.getHeightAdjust() + getBubbleYAdjust(1));
                        }
                    }
                    // Update page indicator position
                    int piNetId = s.getPageIndicatorNetId();
                    if (piNetId >= 0) {
                        int lc = s.getLineCount();
                        Vector3d piPos = new Vector3d(currentPos.getX(), currentPos.getY() + BUBBLE_Y_OFFSET + s.getHeightAdjust() + getBubbleYAdjust(lc) + getPageIndicatorYOffset(lc), currentPos.getZ());
                        sendPositionUpdate(piNetId, piPos, uuid, currentPos);
                    }
                    s.setLastPosition(currentPos);
                }
            });
        }
    }

    public void clearSpeech(UUID uuid) {
        SpeechState state = activeSpeech.get(uuid);

        // Build despawnIds + collect viewers BEFORE removing from activeSpeech —
        // getViewers() needs the SpeechState for correct channel isolation targeting
        int[] despawnIds = null;
        List<PlayerRef> viewers = List.of();
        if (state != null) {
            despawnIds = collectDespawnIds(state);
            if (despawnIds.length > 0) {
                viewers = getViewers(uuid, null);
            }
        }

        activeSpeech.remove(uuid);
        ScheduledFuture<?> rf = pendingReveals.remove(uuid);
        if (rf != null) rf.cancel(false);
        ScheduledFuture<?> pf = pendingParticleLoops.remove(uuid);
        if (pf != null) pf.cancel(false);
        lastParticleSendTime.remove(uuid);

        if (state != null) {
            // Reset mouth animation
            int playerNetId = state.getPlayerNetworkId();
            Vector3d lastPos = state.getLastPosition();
            broadcastMouthReset(uuid, playerNetId, lastPos);

            // Send despawn to all viewers (no cull distance) to ensure cleanup
            if (despawnIds != null && despawnIds.length > 0) {
                EntityUpdates despawnPacket = new EntityUpdates(despawnIds, null);
                for (PlayerRef viewer : viewers) {
                    viewer.getPacketHandler().writeNoCache(despawnPacket);
                }
            }
        }
    }

    public void removePlayer(UUID uuid) {
        clearSpeech(uuid);
        disabledPlayers.remove(uuid);
        selfVisiblePlayers.remove(uuid);
    }

    public int getActiveCount() {
        return activeSpeech.size();
    }

    public boolean hasActiveBubble(UUID uuid) {
        return activeSpeech.containsKey(uuid);
    }

    // Custom BubbleChat mouth animations — 16 phoneme-based mouth positions
    private static final String ANIM_PREFIX = "Characters/Animations/BubbleChat/";

    // All mouth animation file paths (key = animation key used in PlayAnimation)
    private static final String[][] MOUTH_ANIMS = {
        {"BC_Rest", ANIM_PREFIX + "BC_Rest.blockyanim"},       // (0,0)   — rest/default
        {"BC_A",    ANIM_PREFIX + "BC_A.blockyanim"},           // (200,0) — A
        {"BC_O",    ANIM_PREFIX + "BC_O.blockyanim"},           // (160,30)— O
        {"BC_EIUY", ANIM_PREFIX + "BC_EIUY.blockyanim"},       // (140,0) — E,I,U,Y
        {"BC_STD",  ANIM_PREFIX + "BC_STD.blockyanim"},         // (120,20)— S,T,D
        {"BC_N",    ANIM_PREFIX + "BC_N.blockyanim"},           // (180,0) — N
        {"BC_L",    ANIM_PREFIX + "BC_L.blockyanim"},           // (180,10)— L
        {"BC_R",    ANIM_PREFIX + "BC_R.blockyanim"},           // (140,30)— R
        {"BC_FV",   ANIM_PREFIX + "BC_FV.blockyanim"},          // (100,0) — F,V
        {"BC_H",    ANIM_PREFIX + "BC_H.blockyanim"},           // (140,20)— H
        {"BC_W",    ANIM_PREFIX + "BC_W.blockyanim"},           // (220,30)— W
        {"BC_J",    ANIM_PREFIX + "BC_J.blockyanim"},           // (120,30)— J
        {"BC_KG",   ANIM_PREFIX + "BC_KG.blockyanim"},          // (100,10)— K,G
        {"BC_B",    ANIM_PREFIX + "BC_B.blockyanim"},           // (20,0)  — B
        {"BC_M",    ANIM_PREFIX + "BC_M.blockyanim"},           // (20,10) — M
        {"BC_P",    ANIM_PREFIX + "BC_P.blockyanim"},           // (20,20) — P
    };

    // Letter → mouth animation key mapping
    private static final Map<Character, String> LETTER_TO_MOUTH = new HashMap<>();
    static {
        LETTER_TO_MOUTH.put('A', "BC_A");
        LETTER_TO_MOUTH.put('B', "BC_B");
        LETTER_TO_MOUTH.put('C', "BC_STD");  // C often sounds like S or K
        LETTER_TO_MOUTH.put('D', "BC_STD");
        LETTER_TO_MOUTH.put('E', "BC_EIUY");
        LETTER_TO_MOUTH.put('F', "BC_FV");
        LETTER_TO_MOUTH.put('G', "BC_KG");
        LETTER_TO_MOUTH.put('H', "BC_H");
        LETTER_TO_MOUTH.put('I', "BC_EIUY");
        LETTER_TO_MOUTH.put('J', "BC_J");
        LETTER_TO_MOUTH.put('K', "BC_KG");
        LETTER_TO_MOUTH.put('L', "BC_L");
        LETTER_TO_MOUTH.put('M', "BC_M");
        LETTER_TO_MOUTH.put('N', "BC_N");
        LETTER_TO_MOUTH.put('O', "BC_O");
        LETTER_TO_MOUTH.put('P', "BC_P");
        LETTER_TO_MOUTH.put('Q', "BC_KG");   // Q sounds like K
        LETTER_TO_MOUTH.put('R', "BC_R");
        LETTER_TO_MOUTH.put('S', "BC_STD");
        LETTER_TO_MOUTH.put('T', "BC_STD");
        LETTER_TO_MOUTH.put('U', "BC_EIUY");
        LETTER_TO_MOUTH.put('V', "BC_FV");
        LETTER_TO_MOUTH.put('W', "BC_W");
        LETTER_TO_MOUTH.put('X', "BC_KG");   // X sounds like KS
        LETTER_TO_MOUTH.put('Y', "BC_EIUY");
        LETTER_TO_MOUTH.put('Z', "BC_STD");  // Z sounds like S
    }

    /** Build the UpdateItemPlayerAnimations packet with all 16 mouth states. Cached after first call.
     *  Each mouth position is its own ItemPlayerAnimations group so that PlayAnimation with a different
     *  itemAnimationsId properly transitions on the client (same itemAnimationsId gets ignored if already playing). */
    private volatile UpdateItemPlayerAnimations cachedMouthRegisterPacket;

    private UpdateItemPlayerAnimations getMouthRegisterPacket() {
        if (cachedMouthRegisterPacket != null) return cachedMouthRegisterPacket;
        Map<String, ItemPlayerAnimations> animMap = new HashMap<>();
        for (String[] entry : MOUTH_ANIMS) {
            String key = entry[0];
            String path = entry[1];
            Map<String, ItemAnimation> singleAnim = new HashMap<>();
            singleAnim.put(key, new ItemAnimation(
                path, null, null, null, null, false, 1.0f, 0.05f, true, false
            ));
            ItemPlayerAnimations posAnims = new ItemPlayerAnimations(
                key, singleAnim, null, null, null, false
            );
            animMap.put(key, posAnims);
        }
        cachedMouthRegisterPacket = new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, animMap);
        return cachedMouthRegisterPacket;
    }

    /** Register BC_Talk mouth animations with all viewers of the speaker. */
    // ---- Yell particle (BC_Yell — cloned Hedera_Scream) ----

    private static boolean isYellWord(String word) {
        String letters = word.replaceAll("[^a-zA-Z]", "");
        if (letters.length() >= 2 && letters.equals(letters.toUpperCase())) return true;
        return word.endsWith("!");
    }

    /** Check if any word in the message is a yell word (ALL CAPS 2+ letters or ends with !). */
    private static boolean isYellMessage(String message) {
        for (String w : message.split("\\s+")) {
            if (isYellWord(w)) return true;
        }
        return false;
    }

    private void sendYellParticle(UUID speakerUuid) {
        PlayerRef playerRef = findPlayerRef(speakerUuid);
        if (playerRef == null) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRot = store.getComponent(ref, HeadRotation.getComponentType());
        if (tc == null || headRot == null) return;

        Vector3d pos = tc.getPosition();
        Vector3d dir = headRot.getDirection();
        double px = pos.getX() + dir.getX() * 0.6;
        double py = pos.getY() + 1.5;
        double pz = pos.getZ() + dir.getZ() * 0.6;

        SpawnParticleSystem packet = new SpawnParticleSystem(
            "BC_Yell", new Position(px, py, pz), null, 0.08f, null);

        // Check if speaker is in a channel
        SpeechState state = activeSpeech.get(speakerUuid);
        String channelPin = state != null ? state.getChannelPin() : null;

        // Skip viewers in different world instances (store.getComponent asserts same thread)
        UUID speakerWorldUuid = playerRef.getWorldUuid();

        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer.getUuid().equals(speakerUuid) && !selfVisiblePlayers.contains(speakerUuid)) continue;

            // Skip viewers in different world instances (e.g. dungeon portals)
            if (speakerWorldUuid != null && !speakerWorldUuid.equals(viewer.getWorldUuid())) continue;

            // Channel isolation
            if (channelPin != null && channelStorage != null) {
                if (!channelStorage.isMember(channelPin, viewer.getUuid())) continue;
            }

            // Cache prefs once per viewer (used for yell preference + range check)
            if (prefsStorage != null) {
                PlayerBubblePrefs viewerPrefs = prefsStorage.getPrefs(viewer.getUuid());
                if (!viewerPrefs.yellEnabled) continue;

                // Yell particle range check
                int yellPartRange = viewerPrefs.getEffectiveYellParticleRange();
                Ref<EntityStore> vRef = viewer.getReference();
                if (vRef != null && vRef.isValid()) {
                    Store<EntityStore> vStore = vRef.getStore();
                    TransformComponent vtc = vStore.getComponent(vRef, TransformComponent.getComponentType());
                    if (vtc != null) {
                        Vector3d vPos = vtc.getPosition();
                        double dx = vPos.getX() - pos.getX();
                        double dz = vPos.getZ() - pos.getZ();
                        if (dx * dx + dz * dz > (double) yellPartRange * yellPartRange) continue;
                    }
                }
            }

            viewer.getPacketHandler().writeNoCache(packet);
        }
    }

    // ---- Animalese Sound Playback ----

    /**
     * Play an animalese preview to a single player (for the voice settings test button).
     * Uses the player's current voice settings but plays at a fixed audible volume.
     * Must be called from the world thread.
     */
    /** Plays animalese preview to the player. Returns total duration in ms. */
    public long playAnimalesePreview(UUID playerUuid, PlayerRef playerRef, String text,
                                      World world) {
        PlayerBubbleTheme theme = themeStorage != null ? themeStorage.getTheme(playerUuid) : null;
        if (theme == null) return 0;

        int voiceType = Math.max(0, Math.min(7, theme.voiceType));
        float basePitch = Math.max(0.5f, Math.min(2.0f, theme.pitch)) * 1.5f;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return 0;
        Store<EntityStore> store = ref.getStore();
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) return 0;
        Vector3d pos = tc.getPosition();

        // Tokenize into words, cap at 30 chars total for preview
        String capped = text.length() > 30 ? text.substring(0, 30) : text;
        String[] words = capped.split("\\s+");

        // Use player's own volume setting (with 0.1x rescale), min 0.05 so preview is always audible
        PlayerBubblePrefs prefs = prefsStorage != null ? prefsStorage.getPrefs(playerUuid) : null;
        float previewVol = prefs != null ? Math.max(0.05f, prefs.animaleseVolume * 0.1f) : 0.15f;
        long wordDelay = 0;
        long maxDelay = 0;

        for (String word : words) {
            String letters = word.replaceAll("[^a-zA-Z]", "");
            if (letters.isEmpty()) { wordDelay += WORD_REVEAL_MS; continue; }

            boolean yellWord = word.endsWith("!");
            float wordPitch = yellWord ? basePitch * 1.2f : basePitch;
            long intervalMs = Math.max(50, Math.min(80, WORD_REVEAL_MS / Math.max(letters.length(), 1)));

            for (int i = 0; i < letters.length(); i++) {
                char ch = letters.charAt(i);
                int letterIdx = Character.toLowerCase(ch) - 'a';
                if (letterIdx < 0 || letterIdx > 25) continue;
                int soundIdx = animaleseSoundIndices[voiceType][letterIdx];
                if (soundIdx == 0) continue;

                float pitch = wordPitch * (0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
                if (Character.isUpperCase(ch)) pitch *= 1.2f;

                long delay = wordDelay + (long) i * intervalMs;
                if (delay > maxDelay) maxDelay = delay;
                final float fPitch = pitch;
                final int fSoundIdx = soundIdx;

                if (delay == 0) {
                    try {
                        SoundUtil.playSoundEvent3dToPlayer(ref, fSoundIdx, SoundCategory.SFX,
                            pos.getX(), pos.getY(), pos.getZ(), previewVol, fPitch, store);
                    } catch (Exception ignored) {}
                } else {
                    scheduler.schedule(() -> {
                        world.execute(() -> {
                            Ref<EntityStore> r = playerRef.getReference();
                            if (r == null || !r.isValid()) return;
                            Store<EntityStore> s = r.getStore();
                            TransformComponent t = s.getComponent(r, TransformComponent.getComponentType());
                            Vector3d p = t != null ? t.getPosition() : pos;
                            try {
                                SoundUtil.playSoundEvent3dToPlayer(r, fSoundIdx, SoundCategory.SFX,
                                    p.getX(), p.getY(), p.getZ(), previewVol, fPitch, s);
                            } catch (Exception ignored) {}
                        });
                    }, delay, TimeUnit.MILLISECONDS);
                }
            }
            wordDelay += WORD_REVEAL_MS;
        }
        // Add a small buffer for the last sound to finish playing
        return maxDelay + 150;
    }

    /**
     * Play Animalese sound for a word — fires per-character 3D sounds to nearby viewers.
     * Piggybacks on the same timing as mouth animations (50-80ms per letter).
     */
    private void broadcastAnimaleseForWord(UUID speakerUuid, String word) {
        SpeechState state = activeSpeech.get(speakerUuid);
        if (state == null) return;
        long gen = state.getGeneration();
        World world = state.getWorld();

        // Check server-wide animalese toggle
        BubbleChatConfig cfg = getServerConfig();
        if (cfg != null && !cfg.animaleseEnabled) return;

        // Check speaker has animalese enabled
        PlayerBubbleTheme theme = themeStorage != null ? themeStorage.getTheme(speakerUuid) : null;
        if (theme == null || !theme.animalese) return;

        int voiceType = Math.max(0, Math.min(7, theme.voiceType));
        float basePitch = Math.max(0.5f, Math.min(2.0f, theme.pitch)) * 1.5f;

        // Strip non-alpha characters
        String letters = word.replaceAll("[^a-zA-Z]", "");
        if (letters.isEmpty()) return;

        // Yell words (ends with !) get the same pitch boost as all-caps
        boolean yellWord = word.endsWith("!");

        // Get speaker position for 3D audio
        Vector3d speakerPos = state.getLastPosition();
        if (speakerPos == null) return;

        // Calculate per-letter interval (same formula as mouth anims)
        long intervalMs = Math.max(50, Math.min(80, WORD_REVEAL_MS / Math.max(letters.length(), 1)));

        // If yell word, boost the base pitch for all letters in this word
        float wordPitch = yellWord ? basePitch * 1.2f : basePitch;

        String speakerUuidStr = speakerUuid.toString();
        String speakerLowerName = state.getSpeakerLowerName();

        for (int i = 0; i < letters.length(); i++) {
            final char ch = letters.charAt(i);

            if (i == 0) {
                playAnimaleseLetter(speakerUuid, speakerUuidStr, speakerLowerName, speakerPos, voiceType, wordPitch, ch);
            } else {
                long delay = (long) i * intervalMs;
                scheduler.schedule(() -> {
                    Long cg = generationCounters.get(speakerUuid);
                    if (cg == null || cg.longValue() != gen) return;
                    // Must run on world thread — playAnimaleseLetter uses store.getComponent
                    // for cull distance checks, which asserts world thread
                    world.execute(() -> {
                        Long cg2 = generationCounters.get(speakerUuid);
                        if (cg2 == null || cg2.longValue() != gen) return;
                        SpeechState s = activeSpeech.get(speakerUuid);
                        Vector3d pos = s != null ? s.getLastPosition() : speakerPos;
                        playAnimaleseLetter(speakerUuid, speakerUuidStr, speakerLowerName, pos, voiceType, wordPitch, ch);
                    });
                }, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Play a single Animalese letter sound to all qualifying viewers.
     */
    private void playAnimaleseLetter(UUID speakerUuid, String speakerUuidStr, String speakerLowerName,
                                      Vector3d pos, int voiceType, float basePitch, char ch) {
        int letterIdx = Character.toLowerCase(ch) - 'a';
        if (letterIdx < 0 || letterIdx > 25) return;
        int soundIdx = animaleseSoundIndices[voiceType][letterIdx];
        if (soundIdx == 0) return;

        // Pitch: base +/- 10%, uppercase gets +20% bump
        float pitch = basePitch * (0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        if (Character.isUpperCase(ch)) {
            pitch *= 1.2f;
        }

        for (PlayerRef viewer : getViewers(speakerUuid, pos)) {
            UUID viewerUuid = viewer.getUuid();
            // Speaker doesn't hear own animalese — use Voice Settings preview instead
            if (viewerUuid.equals(speakerUuid)) continue;

            PlayerBubblePrefs viewerPrefs = prefsStorage != null ? prefsStorage.getPrefs(viewerUuid) : null;
            if (viewerPrefs == null) continue;

            if (viewerPrefs.isAnimaleseMuted(speakerLowerName)) continue;
            float vol = viewerPrefs.getAnimaleseVolumeForSpeaker(speakerUuidStr) * 0.1f;
            if (vol <= 0f) continue;

            // Animalese cull distance check
            int cullDist = viewerPrefs.getEffectiveAnimaleseCullDistance();
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) continue;

            Store<EntityStore> viewerStore = viewerRef.getStore();
            TransformComponent vtc = viewerStore.getComponent(viewerRef, TransformComponent.getComponentType());
            if (vtc != null && pos != null) {
                double maxRangeSq = (double) cullDist * cullDist;
                if (distSqXZ(vtc.getPosition(), pos) > maxRangeSq) continue;
            }

            try {
                SoundUtil.playSoundEvent3dToPlayer(viewerRef, soundIdx, SoundCategory.SFX,
                    pos.getX(), pos.getY(), pos.getZ(), vol, pitch, viewerStore);
            } catch (Exception e) {
                // Silently ignore — viewer may have disconnected
            }
        }
    }

    // ---- Mouth Animation System ----

    private void registerMouthAnims(UUID speakerUuid, Vector3d speakerPos) {
        UpdateItemPlayerAnimations packet = getMouthRegisterPacket();
        for (PlayerRef viewer : getViewers(speakerUuid, speakerPos)) {
            if (prefsStorage != null && !prefsStorage.getPrefs(viewer.getUuid()).visemesEnabled) continue;
            viewer.getPacketHandler().writeNoCache(packet);
        }
    }

    /** Broadcast mouth animations cycling through each letter of the word to all viewers. */
    private void broadcastMouthForWord(UUID speakerUuid, String word) {
        SpeechState state = activeSpeech.get(speakerUuid);
        if (state == null) return;
        int netId = state.getPlayerNetworkId();
        if (netId < 0) return;
        long gen = state.getGeneration();

        // Cancel any pending letter animations from the previous word
        cancelPendingMouthAnims(speakerUuid);

        // Brief rest between words (close mouth during the space)
        broadcastMouthKey(speakerUuid, netId, "BC_Rest");
        long restGap = 60; // ms of closed mouth between words

        // Strip non-alpha characters for mouth animation
        String letters = word.replaceAll("[^a-zA-Z]", "");
        if (letters.isEmpty()) return;

        // Build list of unique consecutive mouth keys
        List<String> keys = new ArrayList<>();
        String lastKey = null;
        for (int i = 0; i < letters.length(); i++) {
            String key = getMouthKeyForChar(letters.charAt(i));
            if (!key.equals(lastKey)) {
                keys.add(key);
                lastKey = key;
            }
        }

        // Calculate per-letter interval: fit within WORD_REVEAL_MS, min 50ms, max 80ms
        long intervalMs = Math.max(50, Math.min(80, WORD_REVEAL_MS / Math.max(keys.size(), 1)));

        // Schedule each letter's mouth position (offset by restGap for the between-word pause)
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            long delay = restGap + i * intervalMs;
            ScheduledFuture<?> f = scheduler.schedule(() -> {
                Long cg = generationCounters.get(speakerUuid);
                if (cg == null || cg.longValue() != gen) return;
                broadcastMouthKey(speakerUuid, netId, key);
            }, delay, TimeUnit.MILLISECONDS);
            futures.add(f);
        }

        // Close mouth after all letters complete (cancelled by next word if not the last)
        // Use full letter count so repeated letters (e.g. "rrrrr") hold the expression
        long restDelay = restGap + letters.length() * intervalMs;
        ScheduledFuture<?> restFuture = scheduler.schedule(() -> {
            Long cg = generationCounters.get(speakerUuid);
            if (cg == null || cg.longValue() != gen) return;
            broadcastMouthKey(speakerUuid, netId, "BC_Rest");
        }, restDelay, TimeUnit.MILLISECONDS);
        futures.add(restFuture);

        pendingMouthAnims.put(speakerUuid, futures);
    }

    /** Send a single mouth key to all viewers.
     *  Each key is its own itemAnimationsId so the client properly transitions between mouth shapes. */
    private void broadcastMouthKey(UUID speakerUuid, int netId, String key) {
        SpeechState state = activeSpeech.get(speakerUuid);
        if (state == null) return;
        PlayAnimation packet = new PlayAnimation(netId, key, key, AnimationSlot.Action);
        for (PlayerRef viewer : getViewers(speakerUuid, null)) {
            if (prefsStorage != null && !prefsStorage.getPrefs(viewer.getUuid()).visemesEnabled) continue;
            viewer.getPacketHandler().writeNoCache(packet);
        }
    }

    /** Cancel any pending per-letter mouth animation futures. */
    private void cancelPendingMouthAnims(UUID speakerUuid) {
        List<ScheduledFuture<?>> futures = pendingMouthAnims.remove(speakerUuid);
        if (futures != null) {
            for (ScheduledFuture<?> f : futures) {
                f.cancel(false);
            }
        }
    }

    /** Get mouth key for a single character. */
    private static String getMouthKeyForChar(char c) {
        return LETTER_TO_MOUTH.getOrDefault(Character.toUpperCase(c), "BC_Rest");
    }

    /** Broadcast mouth rest position to all players (no cull distance for reliable cleanup). */
    private void broadcastMouthRest(UUID speakerUuid) {
        cancelPendingMouthAnims(speakerUuid);
        SpeechState state = activeSpeech.get(speakerUuid);
        if (state == null) return;
        int netId = state.getPlayerNetworkId();
        if (netId < 0) return;
        PlayAnimation packet = new PlayAnimation(netId, "BC_Rest", "BC_Rest", AnimationSlot.Action);
        for (PlayerRef viewer : getViewers(speakerUuid, null)) {
            viewer.getPacketHandler().writeNoCache(packet);
        }
    }

    /** Reset mouth animation to Default (clears face expression). Uses provided state data for post-removal.
     *  Sends to ALL players (no cull distance) to ensure cleanup reaches viewers who may have moved away. */
    private void broadcastMouthReset(UUID speakerUuid, int playerNetId, Vector3d lastPos) {
        cancelPendingMouthAnims(speakerUuid);
        if (playerNetId < 0) return;
        PlayAnimation reset = new PlayAnimation(playerNetId, "Default", null, AnimationSlot.Action);
        for (PlayerRef viewer : getViewers(speakerUuid, null)) {
            viewer.getPacketHandler().writeNoCache(reset);
        }
    }

}
