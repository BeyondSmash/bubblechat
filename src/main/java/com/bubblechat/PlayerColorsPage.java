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
import java.util.*;

public class PlayerColorsPage extends InteractiveCustomUIPage<PlayerColorsPage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    private String inputField = "";
    private String currentPickerHex = "#FF0000";

    public PlayerColorsPage(@Nonnull SpeechManager manager,
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
        cmd.append("PlayerColors.ui");
        applySettings(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        if (data.input != null) {
            inputField = data.input;
        }

        // Color picker change
        if (data.dropdownId != null && "Color".equals(data.dropdownId) && data.dropdownValue != null) {
            String hex = data.dropdownValue;
            if (!hex.startsWith("#")) hex = "#" + hex;
            if (hex.length() > 7) hex = hex.substring(0, 7);
            currentPickerHex = hex;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#HexField.Value", hex.toUpperCase());
            sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }

        if (data.action != null) {
            switch (data.action) {
                case "Back" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        BubbleThemePage page = new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, playerUuid);
                        player.getPageManager().openCustomPage(ref, store, page);
                    }
                    return;
                }
                case "SetGlobal" -> {
                    prefs.globalColorOverride = currentPickerHex;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    manager.registerOverrideColor(currentPickerHex);
                }
                case "ClearGlobal" -> {
                    prefs.globalColorOverride = null;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "Add" -> {
                    if (inputField != null && !inputField.trim().isEmpty()) {
                        String name = inputField.trim().toLowerCase();
                        prefs.playerColors.put(name, currentPickerHex);
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                        manager.registerOverrideColor(currentPickerHex);
                        inputField = "";
                    }
                }
            }
            refresh();
            return;
        }

        if (data.editAction != null) {
            prefs.playerColors.put(data.editAction.toLowerCase(), currentPickerHex);
            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
            manager.registerOverrideColor(currentPickerHex);
            refresh();
            return;
        }

        if (data.removeAction != null) {
            prefs.playerColors.remove(data.removeAction.toLowerCase());
            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
            refresh();
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        applySettings(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void applySettings(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        // Color picker
        cmd.set("#ColorPicker.Value", currentPickerHex);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ColorPicker",
            EventData.of("DropdownId", "Color").append("@DropdownValue", "#ColorPicker.Value"), false);

        // Hex display
        cmd.set("#HexField.Value", currentPickerHex.toUpperCase());

        // Global override swatch — show color or "None"
        if (prefs.globalColorOverride != null) {
            cmd.set("#GlobalSwatch.Background.Color", prefs.globalColorOverride + "FF");
            cmd.set("#GlobalSwatch.Visible", true);
            cmd.set("#GlobalNone.Visible", false);
        } else {
            cmd.set("#GlobalSwatch.Visible", false);
            cmd.set("#GlobalNone.Visible", true);
        }

        // Sync input field with server state (clears after Add)
        cmd.set("#NameInput.Value", inputField);

        // Input field + buttons
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NameInput",
            new EventData().append("@Input", "#NameInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AddButton",
            new EventData().append("Action", "Add"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetGlobalBtn",
            new EventData().append("Action", "SetGlobal"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearGlobalBtn",
            new EventData().append("Action", "ClearGlobal"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);

        buildList(cmd, evt);
    }

    private void buildList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        cmd.clear("#ListCards");
        cmd.appendInline("#ListContainer", "Group #ListCards { LayoutMode: Top; }");

        if (prefs.playerColors.isEmpty()) {
            cmd.appendInline("#ListCards",
                "Label { Text: \"No per-player colors set.\"; Style: (FontSize: 14, TextColor: #888888); Anchor: (Top: 10); }");
            return;
        }

        List<Map.Entry<String, String>> sorted = new ArrayList<>(prefs.playerColors.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, String> entry = sorted.get(i);
            cmd.append("#ListCards", "PlayerColorEntry.ui");
            cmd.set("#ListCards[" + i + "] #EntryName.Text", entry.getKey());
            cmd.set("#ListCards[" + i + "] #ColorSwatch.Background.Color", entry.getValue() + "FF");
            cmd.set("#ListCards[" + i + "] #HexLabel.Text", entry.getValue().toUpperCase());

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#ListCards[" + i + "] #EditButton",
                new EventData().append("EditAction", entry.getKey()), false);

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#ListCards[" + i + "] #RemoveButton",
                new EventData().append("RemoveAction", entry.getKey()), false);
        }
    }

    public static class PageData {
        @Nonnull
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
            .<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("@Input", Codec.STRING),
                (d, v) -> d.input = v, d -> d.input)
            .addField(new KeyedCodec<>("EditAction", Codec.STRING),
                (d, v) -> d.editAction = v, d -> d.editAction)
            .addField(new KeyedCodec<>("RemoveAction", Codec.STRING),
                (d, v) -> d.removeAction = v, d -> d.removeAction)
            .addField(new KeyedCodec<>("DropdownId", Codec.STRING),
                (d, v) -> d.dropdownId = v, d -> d.dropdownId)
            .addField(new KeyedCodec<>("@DropdownValue", Codec.STRING),
                (d, v) -> d.dropdownValue = v, d -> d.dropdownValue)
            .build();

        String action;
        String input;
        String editAction;
        String removeAction;
        String dropdownId;
        String dropdownValue;
    }
}
