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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BubbleThemePage extends InteractiveCustomUIPage<BubbleThemePage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    private static final int MAX_HISTORY = 50;

    // Undo/redo history
    private final java.util.Deque<SettingsSnapshot> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<SettingsSnapshot> redoStack = new java.util.ArrayDeque<>();

    public BubbleThemePage(@Nonnull SpeechManager manager,
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
        // Undo stack starts empty — pushUndo() is called before each user change
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("BubbleTheme.ui");
        applySettings(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        if (data.action != null) {
            switch (data.action) {
                case "Undo" -> {
                    applyUndo();
                    refresh();
                    return;
                }
                case "Redo" -> {
                    applyRedo();
                    refresh();
                    return;
                }
                case "Back" -> {
                    close();
                    return;
                }
                case "Reset" -> {
                    pushUndo();
                    theme.lightMode = false;
                    theme.tintColorHex = null;
                    theme.selfVisible = false;
                    manager.setSelfVisible(playerUuid, false);
                    prefs.cullDistance = 20;
                    prefs.maxBubbleCount = 10;
                    prefs.visemesEnabled = true;
                    prefs.yellEnabled = true;
                    themeStorage.saveAsync(manager.getScheduler());
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    manager.applyThemeToPlayer(playerUuid, playerRef);
                    refresh();
                    return;
                }
                case "LightOn" -> {
                    pushUndo();
                    theme.lightMode = true;
                    // Auto-clamp color for light mode contrast
                    String lightHex = theme.tintColorHex != null ? theme.tintColorHex : "#C6C6C6";
                    theme.tintColorHex = clampColorForMode(lightHex, true);
                    themeStorage.saveAsync(manager.getScheduler());
                    manager.applyThemeToPlayer(playerUuid, playerRef);
                }
                case "DarkOn" -> {
                    pushUndo();
                    theme.lightMode = false;
                    themeStorage.saveAsync(manager.getScheduler());
                    manager.applyThemeToPlayer(playerUuid, playerRef);
                }
                case "SelfOn" -> {
                    pushUndo();
                    theme.selfVisible = true;
                    manager.setSelfVisible(playerUuid, true);
                    themeStorage.saveAsync(manager.getScheduler());
                }
                case "SelfOff" -> {
                    pushUndo();
                    theme.selfVisible = false;
                    manager.setSelfVisible(playerUuid, false);
                    themeStorage.saveAsync(manager.getScheduler());
                }
                case "DefaultColor" -> {
                    pushUndo();
                    theme.tintColorHex = null;
                    themeStorage.saveAsync(manager.getScheduler());
                    manager.applyThemeToPlayer(playerUuid, playerRef);
                }
                case "VisOn" -> {
                    pushUndo();
                    prefs.visemesEnabled = true;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "VisOff" -> {
                    pushUndo();
                    prefs.visemesEnabled = false;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "YellOn" -> {
                    pushUndo();
                    prefs.yellEnabled = true;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "YellOff" -> {
                    pushUndo();
                    prefs.yellEnabled = false;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefaultCull" -> {
                    pushUndo();
                    prefs.cullDistance = 20;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefaultMax" -> {
                    pushUndo();
                    prefs.maxBubbleCount = 10;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "GoPlayerColors" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        PlayerColorsPage page = new PlayerColorsPage(manager, themeStorage, prefsStorage, playerRef, playerUuid);
                        player.getPageManager().openCustomPage(ref, store, page);
                    }
                    return;
                }
                case "GoHiddenMuted" -> {
                    Player player2 = store.getComponent(ref, Player.getComponentType());
                    if (player2 != null) {
                        HiddenMutedPage page = new HiddenMutedPage(manager, themeStorage, prefsStorage, playerRef, playerUuid);
                        player2.getPageManager().openCustomPage(ref, store, page);
                    }
                    return;
                }
            }
            refresh();
            return;
        }

        // Dropdown changes
        if (data.dropdownId != null && data.dropdownValue != null) {
            pushUndo();
            try {
                switch (data.dropdownId) {
                    case "Color" -> {
                        String hex = data.dropdownValue;
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        if (hex.length() > 7) hex = hex.substring(0, 7);
                        // Contrast accommodation
                        hex = clampColorForMode(hex, theme.lightMode);
                        theme.tintColorHex = hex;
                        themeStorage.saveAsync(manager.getScheduler());
                        manager.applyThemeToPlayer(playerUuid, playerRef);
                    }
                    case "CullDist" -> {
                        int val = Integer.parseInt(data.dropdownValue);
                        prefs.cullDistance = Math.max(10, Math.min(200, val));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                    case "MaxBubble" -> {
                        int val = Integer.parseInt(data.dropdownValue);
                        prefs.maxBubbleCount = Math.max(1, Math.min(50, val));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                }
            } catch (NumberFormatException ignored) {}
            refresh();
        }
    }

    // --- Undo/Redo ---

    private record SettingsSnapshot(boolean lightMode, String tintColorHex,
                                     boolean selfVisible, int cullDistance, int maxBubbleCount,
                                     boolean visemesEnabled, boolean yellEnabled) {}

    private SettingsSnapshot takeSnapshot() {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);
        return new SettingsSnapshot(theme.lightMode, theme.tintColorHex,
                theme.selfVisible, prefs.cullDistance, prefs.maxBubbleCount,
                prefs.visemesEnabled, prefs.yellEnabled);
    }

    private void restoreSnapshot(SettingsSnapshot s) {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);
        theme.lightMode = s.lightMode();
        theme.tintColorHex = s.tintColorHex();
        theme.selfVisible = s.selfVisible();
        manager.setSelfVisible(playerUuid, s.selfVisible());
        prefs.cullDistance = s.cullDistance();
        prefs.maxBubbleCount = s.maxBubbleCount();
        prefs.visemesEnabled = s.visemesEnabled();
        prefs.yellEnabled = s.yellEnabled();
        themeStorage.saveAsync(manager.getScheduler());
        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
        manager.applyThemeToPlayer(playerUuid, playerRef);
    }

    private void pushUndo() {
        undoStack.push(takeSnapshot());
        if (undoStack.size() > MAX_HISTORY) ((java.util.ArrayDeque<?>) undoStack).removeLast();
        redoStack.clear();
    }

    private void applyUndo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(takeSnapshot());
        restoreSnapshot(undoStack.pop());
    }

    private void applyRedo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(takeSnapshot());
        restoreSnapshot(redoStack.pop());
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        applyValues(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    /**
     * Register all event bindings — called ONCE in build().
     * Never called from refresh() to avoid exponential binding accumulation.
     */
    private void registerEvents(@Nonnull UIEventBuilder evt) {
        // Color picker
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ColorPicker",
            EventData.of("DropdownId", "Color").append("@DropdownValue", "#ColorPicker.Value"), false);

        // Default color
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultColorBtn",
            new EventData().append("Action", "DefaultColor"), false);

        // Cull Distance dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CullDistDropdown",
            EventData.of("DropdownId", "CullDist").append("@DropdownValue", "#CullDistDropdown.Value"), false);

        // Max Bubbles dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MaxBubbleDropdown",
            EventData.of("DropdownId", "MaxBubble").append("@DropdownValue", "#MaxBubbleDropdown.Value"), false);

        // Toggle pairs (each has 4 bindings: active on/off, dim on/off)
        registerToggleEvents(evt, "#LightOnA", "#LightOnD", "#DarkOnA", "#DarkOnD", "LightOn", "DarkOn");
        registerToggleEvents(evt, "#SelfOnA", "#SelfOnD", "#SelfOffA", "#SelfOffD", "SelfOn", "SelfOff");
        registerToggleEvents(evt, "#VisOnA", "#VisOnD", "#VisOffA", "#VisOffD", "VisOn", "VisOff");
        registerToggleEvents(evt, "#YellOnA", "#YellOnD", "#YellOffA", "#YellOffD", "YellOn", "YellOff");

        // Default buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultCullBtn",
            new EventData().append("Action", "DefaultCull"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultMaxBtn",
            new EventData().append("Action", "DefaultMax"), false);

        // Nav buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavPlayerColors",
            new EventData().append("Action", "GoPlayerColors"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavHiddenMuted",
            new EventData().append("Action", "GoHiddenMuted"), false);

        // Undo/Redo/Back/Reset
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UndoButton",
            new EventData().append("Action", "Undo"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RedoButton",
            new EventData().append("Action", "Redo"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
            new EventData().append("Action", "Reset"), false);
    }

    private void registerToggleEvents(UIEventBuilder evt,
                                       String onActiveId, String onDimId,
                                       String offActiveId, String offDimId,
                                       String onAction, String offAction) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, onActiveId,
            new EventData().append("Action", onAction), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, onDimId,
            new EventData().append("Action", onAction), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, offActiveId,
            new EventData().append("Action", offAction), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, offDimId,
            new EventData().append("Action", offAction), false);
    }

    private void applySettings(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        applyValues(cmd);
        registerEvents(evt);
    }

    /**
     * Update all UI values — safe to call from refresh() without duplicating event bindings.
     */
    private void applyValues(@Nonnull UICommandBuilder cmd) {
        PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        // Mode toggle visibility
        setToggleVisibility(cmd, "#LightOnA", "#LightOnD", "#DarkOnA", "#DarkOnD", theme.lightMode);

        // Self-visible toggle visibility
        setToggleVisibility(cmd, "#SelfOnA", "#SelfOnD", "#SelfOffA", "#SelfOffD", theme.selfVisible);

        // Color picker — mode-dependent default
        String defaultHex = theme.lightMode ? "#F0F0F5" : "#FFFFFF";
        String currentHex = theme.tintColorHex != null ? theme.tintColorHex : defaultHex;
        if (!currentHex.startsWith("#")) currentHex = "#" + currentHex;
        cmd.set("#ColorPicker.Value", currentHex);

        // Preview swatch — always match the color picker value
        cmd.set("#PreviewSwatch.Background.Color", currentHex + "FF");

        // Bubble texture preview (toggle light/dark, tint via Background.Color multiply)
        cmd.set("#BubblePreviewDark.Visible", !theme.lightMode);
        cmd.set("#BubblePreviewLight.Visible", theme.lightMode);
        String tintColor = currentHex + "FF";
        cmd.set("#BubblePreviewDark.Background.Color", tintColor);
        cmd.set("#BubblePreviewLight.Background.Color", tintColor);

        // HSV / RGB / Hex display
        int r = 255, g = 255, b = 255;
        try {
            java.awt.Color c = java.awt.Color.decode(currentHex);
            r = c.getRed(); g = c.getGreen(); b = c.getBlue();
        } catch (NumberFormatException ignored) {}
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        cmd.set("#HueField.Value", String.format("%3d", Math.round(hsb[0] * 360)));
        cmd.set("#SatField.Value", String.format("%3d", Math.round(hsb[1] * 100)));
        cmd.set("#ValField.Value", String.format("%3d", Math.round(hsb[2] * 100)));
        cmd.set("#RedField.Value", String.format("%3d", r));
        cmd.set("#GreenField.Value", String.format("%3d", g));
        cmd.set("#BlueField.Value", String.format("%3d", b));
        cmd.set("#HexField.Value", currentHex.toUpperCase());

        // Contrast indicator (light mode only)
        if (theme.lightMode) {
            double lum = relativeLuminance(new java.awt.Color(r, g, b));
            double ratio = (1.05) / (lum + 0.05);
            if (lum <= MAX_BG_LUMINANCE) {
                String color;
                if (ratio >= 4.5) color = "#44cc44";
                else if (ratio >= 3.0) color = "#88aa44";
                else if (ratio >= 2.5) color = "#ccaa44";
                else color = "#cc8844";
                cmd.set("#ContrastLabel.Style.TextColor", color);
                cmd.set("#ContrastLabel.Text", String.format("%.1f:1", ratio));
            } else {
                cmd.set("#ContrastLabel.Style.TextColor", "#cc4444");
                cmd.set("#ContrastLabel.Text", "CLAMPED");
            }
            cmd.set("#ContrastLabel.Visible", true);
        } else {
            cmd.set("#ContrastLabel.Visible", false);
        }

        // Cull Distance dropdown
        List<DropdownEntryInfo> cullEntries = new ArrayList<>();
        int[] cullPresets = {10, 20, 30, 40, 50, 75, 100, 150, 200};
        for (int c : cullPresets) {
            cullEntries.add(new DropdownEntryInfo(LocalizableString.fromString(c + " M"), String.valueOf(c)));
        }
        cmd.set("#CullDistDropdown.Entries", cullEntries);
        cmd.set("#CullDistDropdown.Value", String.valueOf(prefs.cullDistance));

        // Max Bubbles dropdown
        List<DropdownEntryInfo> maxEntries = new ArrayList<>();
        int[] maxPresets = {1, 3, 5, 10, 15, 20, 30, 50};
        for (int m : maxPresets) {
            maxEntries.add(new DropdownEntryInfo(LocalizableString.fromString(String.valueOf(m)), String.valueOf(m)));
        }
        cmd.set("#MaxBubbleDropdown.Entries", maxEntries);
        cmd.set("#MaxBubbleDropdown.Value", String.valueOf(prefs.maxBubbleCount));

        // Visemes toggle visibility
        setToggleVisibility(cmd, "#VisOnA", "#VisOnD", "#VisOffA", "#VisOffD", prefs.visemesEnabled);

        // Yell toggle visibility
        setToggleVisibility(cmd, "#YellOnA", "#YellOnD", "#YellOffA", "#YellOffD", prefs.yellEnabled);

        // Undo + Redo
        cmd.set("#UndoButton.Visible", !undoStack.isEmpty());
        cmd.set("#RedoButton.Visible", !redoStack.isEmpty());
    }

    private void setToggleVisibility(UICommandBuilder cmd,
                                       String onActiveId, String onDimId,
                                       String offActiveId, String offDimId, boolean isOn) {
        cmd.set(onActiveId + ".Visible", isOn);
        cmd.set(onDimId + ".Visible", !isOn);
        cmd.set(offActiveId + ".Visible", !isOn);
        cmd.set(offDimId + ".Visible", isOn);
    }

    // Contrast ratio threshold (2:1 — lenient, allows brighter backgrounds)
    private static final double CONTRAST_MIN_RATIO = 2.0;
    private static final double MAX_BG_LUMINANCE = (1.05 / CONTRAST_MIN_RATIO) - 0.05;

    /**
     * Contrast accommodation.
     * Dark mode: Val minimum 15% (frame outline must stay distinct from dark body).
     * Light mode: darkens background until white text meets 2:1 contrast.
     */
    private static String clampColorForMode(String hex, boolean lightMode) {
        try {
            java.awt.Color c = java.awt.Color.decode(hex);
            float[] hsb = java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float h = hsb[0], s = hsb[1], v = hsb[2];
            boolean changed = false;
            if (!lightMode) {
                // Dark mode: val can't be lower than 15%
                if (v < 0.15f) { v = 0.15f; changed = true; }
            } else {
                // Light mode: ensure white text meets WCAG 4.5:1 contrast
                if (relativeLuminance(c) > MAX_BG_LUMINANCE) {
                    // Binary search for max V that keeps luminance within threshold
                    float lo = 0, hi = v;
                    for (int i = 0; i < 20; i++) {
                        float mid = (lo + hi) / 2;
                        int rgb = java.awt.Color.HSBtoRGB(h, s, mid) & 0xFFFFFF;
                        if (relativeLuminance(new java.awt.Color(rgb)) > MAX_BG_LUMINANCE) {
                            hi = mid;
                        } else {
                            lo = mid;
                        }
                    }
                    v = lo;
                    changed = true;
                }
            }
            if (changed) {
                int rgb = java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
                return String.format("#%06x", rgb);
            }
        } catch (NumberFormatException ignored) {}
        return hex;
    }

    private static double relativeLuminance(java.awt.Color c) {
        double r = linearize(c.getRed() / 255.0);
        double g = linearize(c.getGreen() / 255.0);
        double b = linearize(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
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
            .build();

        String action;
        String dropdownId;
        String dropdownValue;
    }
}
