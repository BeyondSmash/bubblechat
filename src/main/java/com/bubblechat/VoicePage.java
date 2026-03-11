package com.bubblechat;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VoicePage extends InteractiveCustomUIPage<VoicePage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;
    private String cachedPreviewText = "";
    private ScheduledFuture<?> previewResetTask;

    private static final String[] VOICE_NAMES = {
        "Voice 1 - Bright", "Voice 2 - Warm", "Voice 3 - Deep",
        "Voice 4 - Soft", "Voice 5 - Sharp", "Voice 6 - Mellow",
        "Voice 7 - Raspy", "Voice 8 - Squeaky"
    };

    private static final float[] PITCH_VALUES = {
        0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.7f, 2.0f
    };

    private static final float[] VOL_VALUES = {
        0f, 0.06f, 0.21f, 0.42f, 0.84f, 1.26f, 2.1f, 3.15f, 4.2f
    };

    private static final int[] CULL_PRESETS = {1, 2, 3, 5, 10, 15, 20, 30};

    public VoicePage(@Nonnull SpeechManager manager,
                     @Nonnull BubbleThemeStorage themeStorage,
                     @Nonnull PlayerBubblePrefsStorage prefsStorage,
                     @Nonnull PlayerRef playerRef,
                     @Nonnull UUID playerUuid) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.manager = manager;
        this.themeStorage = themeStorage;
        this.prefsStorage = prefsStorage;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("BubbleVoice.ui");
        applyValues(cmd);
        registerEvents(evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        if (data.action != null) {
            switch (data.action) {
                case "Back" -> {
                    if (previewResetTask != null) previewResetTask.cancel(false);
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                            new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "AnimaleseOn" -> {
                    theme.animalese = true;
                    themeStorage.saveAsync(manager.getScheduler());
                }
                case "AnimaleseOff" -> {
                    theme.animalese = false;
                    themeStorage.saveAsync(manager.getScheduler());
                }
                case "GoPlayerColors" -> {
                    if (previewResetTask != null) previewResetTask.cancel(false);
                    Player p2 = store.getComponent(ref, Player.getComponentType());
                    if (p2 != null) {
                        p2.getPageManager().openCustomPage(ref, store,
                            new PlayerColorsPage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "GoChannels" -> {
                    if (previewResetTask != null) previewResetTask.cancel(false);
                    Player p3 = store.getComponent(ref, Player.getComponentType());
                    if (p3 != null) {
                        p3.getPageManager().openCustomPage(ref, store,
                            new ChannelsPage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "GoHiddenMuted" -> {
                    if (previewResetTask != null) previewResetTask.cancel(false);
                    Player p4 = store.getComponent(ref, Player.getComponentType());
                    if (p4 != null) {
                        p4.getPageManager().openCustomPage(ref, store,
                            new HiddenMutedPage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "Reset" -> {
                    theme.animalese = true;
                    theme.voiceType = 5;
                    theme.pitch = 1.0f;
                    themeStorage.saveAsync(manager.getScheduler());
                    prefs.animaleseVolume = 2.1f;
                    prefs.animaleseCullDistance = 5;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "UpdateText" -> {
                    if (data.previewText != null) cachedPreviewText = data.previewText;
                    return;
                }
                case "Preview" -> {
                    if (!cachedPreviewText.isBlank()) {
                        World world = getPlayerWorld();
                        if (world != null) {
                            long dur = manager.playAnimalesePreview(playerUuid, playerRef, cachedPreviewText, world);
                            showPlayingState(dur, world);
                        }
                    }
                    return;
                }
                case "PreviewHello" -> {
                    World w = getPlayerWorld();
                    if (w != null) {
                        long dur = manager.playAnimalesePreview(playerUuid, playerRef, "Hello there!", w);
                        showPlayingState(dur, w);
                    }
                    return;
                }
                case "PreviewNice" -> {
                    World w2 = getPlayerWorld();
                    if (w2 != null) {
                        long dur = manager.playAnimalesePreview(playerUuid, playerRef, "Nice to meet you!", w2);
                        showPlayingState(dur, w2);
                    }
                    return;
                }
                case "PreviewLong" -> {
                    World w3 = getPlayerWorld();
                    if (w3 != null) {
                        long dur = manager.playAnimalesePreview(playerUuid, playerRef, "How are you doing today?", w3);
                        showPlayingState(dur, w3);
                    }
                    return;
                }
            }
        }

        // Dropdown handlers
        if (data.dropdownId != null && data.dropdownValue != null) {
            try {
                switch (data.dropdownId) {
                    case "Voice" -> {
                        int v = Integer.parseInt(data.dropdownValue);
                        theme.voiceType = Math.max(0, Math.min(7, v));
                        themeStorage.saveAsync(manager.getScheduler());
                    }
                    case "Pitch" -> {
                        float p = Float.parseFloat(data.dropdownValue);
                        theme.pitch = Math.max(0.5f, Math.min(2.0f, p));
                        themeStorage.saveAsync(manager.getScheduler());
                    }
                    case "Volume" -> {
                        float vol = Float.parseFloat(data.dropdownValue);
                        prefs.animaleseVolume = Math.max(0f, Math.min(4.2f, vol));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                    case "Cull" -> {
                        int c = Integer.parseInt(data.dropdownValue);
                        prefs.animaleseCullDistance = Math.max(1, Math.min(30, c));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        refresh();
    }

    private void showPlayingState(long durationMs, World world) {
        if (durationMs <= 0) return;
        if (previewResetTask != null) previewResetTask.cancel(false);

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PreviewBtn.Text", "Playing...");
        sendUpdate(cmd, new UIEventBuilder(), false);

        previewResetTask = manager.getScheduler().schedule(() -> {
            world.execute(() -> {
                UICommandBuilder reset = new UICommandBuilder();
                reset.set("#PreviewBtn.Text", "Preview");
                sendUpdate(reset, new UIEventBuilder(), false);
            });
        }, durationMs, TimeUnit.MILLISECONDS);
    }

    @javax.annotation.Nullable
    private World getPlayerWorld() {
        UUID worldUuid = playerRef.getWorldUuid();
        return worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
    }

    /** Refresh values only — events are registered once in build(). */
    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        applyValues(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    /** Set current UI values (called from build + refresh). */
    private void applyValues(@Nonnull UICommandBuilder cmd) {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        // Animalese toggle
        cmd.set("#AnimaleseOn.Visible", theme.animalese);
        cmd.set("#AnimaleseOff.Visible", !theme.animalese);

        // Voice type dropdown
        List<DropdownEntryInfo> voiceEntries = new ArrayList<>();
        for (int i = 0; i < VOICE_NAMES.length; i++) {
            voiceEntries.add(new DropdownEntryInfo(LocalizableString.fromString(VOICE_NAMES[i]), String.valueOf(i)));
        }
        cmd.set("#VoiceDropdown.Entries", voiceEntries);
        cmd.set("#VoiceDropdown.Value", String.valueOf(theme.voiceType));

        // Pitch dropdown
        List<DropdownEntryInfo> pitchEntries = new ArrayList<>();
        for (float pv : PITCH_VALUES) {
            String label = String.format("%.1f", pv);
            pitchEntries.add(new DropdownEntryInfo(LocalizableString.fromString(label), label));
        }
        cmd.set("#PitchDropdown.Entries", pitchEntries);
        cmd.set("#PitchDropdown.Value", String.format("%.1f", theme.pitch));

        // Volume dropdown (viewer-side)
        List<DropdownEntryInfo> volEntries = new ArrayList<>();
        for (float vv : VOL_VALUES) {
            String label = vv == 0f ? "Muted" : String.format("%.0f%%", vv / 4.2f * 100f);
            volEntries.add(new DropdownEntryInfo(LocalizableString.fromString(label), String.format("%.2f", vv)));
        }
        cmd.set("#VolumeDropdown.Entries", volEntries);
        cmd.set("#VolumeDropdown.Value", String.format("%.2f", snapToNearest(prefs.animaleseVolume, VOL_VALUES)));

        // Audio Range dropdown (viewer-side)
        List<DropdownEntryInfo> cullEntries = new ArrayList<>();
        for (int cp : CULL_PRESETS) {
            cullEntries.add(new DropdownEntryInfo(LocalizableString.fromString(cp + " blocks"), String.valueOf(cp)));
        }
        cmd.set("#CullDropdown.Entries", cullEntries);
        cmd.set("#CullDropdown.Value", String.valueOf(prefs.animaleseCullDistance));
    }

    private static float snapToNearest(float value, float[] presets) {
        float best = presets[0];
        float bestDist = Math.abs(value - best);
        for (int i = 1; i < presets.length; i++) {
            float dist = Math.abs(value - presets[i]);
            if (dist < bestDist) { best = presets[i]; bestDist = dist; }
        }
        return best;
    }

    /** Register event bindings once in build() — never re-sent on refresh. */
    private void registerEvents(@Nonnull UIEventBuilder evt) {
        // Animalese toggle
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AnimaleseOn",
            new EventData().append("Action", "AnimaleseOff"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AnimaleseOff",
            new EventData().append("Action", "AnimaleseOn"), false);

        // Dropdown value-changed
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VoiceDropdown",
            EventData.of("DropdownId", "Voice").append("@DropdownValue", "#VoiceDropdown.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PitchDropdown",
            EventData.of("DropdownId", "Pitch").append("@DropdownValue", "#PitchDropdown.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VolumeDropdown",
            EventData.of("DropdownId", "Volume").append("@DropdownValue", "#VolumeDropdown.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CullDropdown",
            EventData.of("DropdownId", "Cull").append("@DropdownValue", "#CullDropdown.Value"), false);

        // Back + Reset buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
            new EventData().append("Action", "Reset"), false);

        // Navigation buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavPlayerColors",
            new EventData().append("Action", "GoPlayerColors"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavChannels",
            new EventData().append("Action", "GoChannels"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavHiddenMuted",
            new EventData().append("Action", "GoHiddenMuted"), false);

        // Preview: track text field changes server-side, preview button uses cached text
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PreviewInput",
            EventData.of("Action", "UpdateText").append("@PreviewText", "#PreviewInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PreviewBtn",
            new EventData().append("Action", "Preview"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PreviewHello",
            new EventData().append("Action", "PreviewHello"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PreviewNice",
            new EventData().append("Action", "PreviewNice"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PreviewLong",
            new EventData().append("Action", "PreviewLong"), false);
    }

    public static class PageData {
        @Nonnull
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
            .<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("DropdownId", Codec.STRING),
                (d, v) -> d.dropdownId = v, d -> d.dropdownId)
            .addField(new KeyedCodec<>("@DropdownValue", Codec.STRING),
                (d, v) -> d.dropdownValue = v, d -> d.dropdownValue)
            .addField(new KeyedCodec<>("@PreviewText", Codec.STRING),
                (d, v) -> d.previewText = v, d -> d.previewText)
            .build();

        String action;
        String dropdownId;
        String dropdownValue;
        String previewText;
    }
}
