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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Page 2 of /bchat npc — rich color editor for one NPC: ColorPicker + bubble PNG preview,
 * HSV/RGB/Hex readouts, WCAG contrast clamping, light/dark, and save. Mirrors the player
 * BubbleThemePage color section.
 */
public class NpcColorEditorPage extends InteractiveCustomUIPage<NpcColorEditorPage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final NpcBubbleColorStorage npcColors;
    private final PlayerRef playerRef;
    private final UUID playerUuid;
    private final String npcId;
    private final String npcName;

    private String currentPickerHex;
    private boolean lightMode = false;

    public NpcColorEditorPage(@Nonnull SpeechManager manager,
                              @Nonnull BubbleThemeStorage themeStorage,
                              @Nonnull PlayerBubblePrefsStorage prefsStorage,
                              @Nonnull NpcBubbleColorStorage npcColors,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nonnull String npcId,
                              @Nonnull String npcName) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.manager = manager;
        this.themeStorage = themeStorage;
        this.prefsStorage = prefsStorage;
        this.npcColors = npcColors;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
        this.npcId = npcId;
        this.npcName = npcName;

        NpcBubbleColorStorage.NpcColor c = npcColors.get(npcId);
        if (c != null) {
            lightMode = c.lightMode;
            currentPickerHex = (c.hex != null) ? c.hex : defaultHex();
        } else {
            currentPickerHex = defaultHex();
        }
    }

    private String defaultHex() {
        return lightMode ? "#F0F0F5" : "#FFFFFF";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("NpcColorEditor.ui");
        cmd.set("#NpcName.Text", npcName);
        applyValues(cmd);
        registerEvents(evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        if (data.dropdownId != null && "Color".equals(data.dropdownId) && data.dropdownValue != null) {
            String hex = data.dropdownValue;
            if (!hex.startsWith("#")) hex = "#" + hex;
            if (hex.length() > 7) hex = hex.substring(0, 7);
            currentPickerHex = clampColorForMode(hex, lightMode);
            refresh();
            return;
        }

        if (data.action != null) {
            switch (data.action) {
                case "Light" -> { lightMode = true; currentPickerHex = clampColorForMode(currentPickerHex, true); refresh(); }
                case "Dark" -> { lightMode = false; currentPickerHex = clampColorForMode(currentPickerHex, false); refresh(); }
                case "Default" -> {
                    manager.clearNpcColor(npcId);
                    openList(ref, store);
                }
                case "Save" -> {
                    manager.applyNpcColor(npcId, currentPickerHex, lightMode);
                    openList(ref, store);
                }
                case "Back" -> openList(ref, store);
            }
        }
    }

    private void openList(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store,
                new NpcColorsListPage(manager, themeStorage, prefsStorage, npcColors, playerRef, playerUuid));
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        applyValues(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void registerEvents(@Nonnull UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ColorPicker",
            EventData.of("DropdownId", "Color").append("@DropdownValue", "#ColorPicker.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultColorBtn",
            new EventData().append("Action", "Default"), false);
        // Light/Dark toggle pairs (active + dim variants share the same action)
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LightOnA",
            new EventData().append("Action", "Light"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LightOnD",
            new EventData().append("Action", "Light"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DarkOnA",
            new EventData().append("Action", "Dark"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DarkOnD",
            new EventData().append("Action", "Dark"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
            new EventData().append("Action", "Save"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);
    }

    private void applyValues(@Nonnull UICommandBuilder cmd) {
        String currentHex = currentPickerHex.startsWith("#") ? currentPickerHex : "#" + currentPickerHex;

        cmd.set("#ColorPicker.Value", currentHex);
        cmd.set("#PreviewSwatch.Background.Color", currentHex + "FF");
        cmd.set("#PreviewText.Text", npcName);

        cmd.set("#BubblePreviewDark.Visible", !lightMode);
        cmd.set("#BubblePreviewLight.Visible", lightMode);
        String tintColor = currentHex + "FF";
        cmd.set("#BubblePreviewDark.Background.Color", tintColor);
        cmd.set("#BubblePreviewLight.Background.Color", tintColor);

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

        if (lightMode) {
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

        setToggleVisibility(cmd, "#LightOnA", "#LightOnD", "#DarkOnA", "#DarkOnD", lightMode);
    }

    private void setToggleVisibility(UICommandBuilder cmd,
                                     String onActiveId, String onDimId,
                                     String offActiveId, String offDimId, boolean isOn) {
        cmd.set(onActiveId + ".Visible", isOn);
        cmd.set(onDimId + ".Visible", !isOn);
        cmd.set(offActiveId + ".Visible", !isOn);
        cmd.set(offDimId + ".Visible", isOn);
    }

    // ---- WCAG contrast clamping (ported from BubbleThemePage) ----
    private static final double CONTRAST_MIN_RATIO = 2.0;
    private static final double MAX_BG_LUMINANCE = (1.05 / CONTRAST_MIN_RATIO) - 0.05;

    /**
     * Dark mode: Val minimum 15% (outline must stay distinct from the dark body).
     * Light mode: darken the background until white text meets the contrast threshold.
     */
    private static String clampColorForMode(String hex, boolean lightMode) {
        try {
            java.awt.Color c = java.awt.Color.decode(hex);
            float[] hsb = java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float h = hsb[0], s = hsb[1], v = hsb[2];
            boolean changed = false;
            if (!lightMode) {
                if (v < 0.15f) { v = 0.15f; changed = true; }
            } else {
                if (relativeLuminance(c) > MAX_BG_LUMINANCE) {
                    float lo = 0, hi = v;
                    for (int i = 0; i < 20; i++) {
                        float mid = (lo + hi) / 2;
                        int rgb = java.awt.Color.HSBtoRGB(h, s, mid) & 0xFFFFFF;
                        if (relativeLuminance(new java.awt.Color(rgb)) > MAX_BG_LUMINANCE) hi = mid;
                        else lo = mid;
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
