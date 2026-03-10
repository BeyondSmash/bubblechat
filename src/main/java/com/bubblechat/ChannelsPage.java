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

public class ChannelsPage extends InteractiveCustomUIPage<ChannelsPage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    // Preset color cycle for channel color editing
    private static final String[] COLOR_PRESETS = {
        "#2d3d5a", "#3d2d5a", "#2d5a3d", "#5a2d2d", "#5a4a2d",
        "#2d5a5a", "#4a2d5a", "#3d5a2d", "#5a2d4a", "#333333"
    };

    private static final int[] RP_CULL_PRESETS = {5, 10, 15, 20, 30, 50};
    private static final int[] YELL_RANGE_PRESETS = {30, 50, 75, 100, 150, 200};

    public ChannelsPage(@Nonnull SpeechManager manager,
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
        cmd.append("BubbleChannels.ui");
        applySettings(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);
        ChannelStorage channelStorage = manager.getChannelStorage();

        if (data.action != null) {
            switch (data.action) {
                case "Back" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                            new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "GoPlayerColors" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                            new PlayerColorsPage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "GoChannels" -> {
                    // Already on this page — just refresh
                    refresh();
                    return;
                }
                case "GoHiddenMuted" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                            new HiddenMutedPage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                    return;
                }
                case "Host" -> {
                    if (channelStorage != null) {
                        int slot = prefs.findAvailableSlot();
                        if (slot >= 0) {
                            RpChannel channel = channelStorage.createChannel(playerUuid);
                            prefs.channelSlots[slot] = channel.pin;
                            prefs.activeSlot = slot;
                            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                            channelStorage.saveAsync(manager.getScheduler());
                        }
                    }
                }
                case "Join" -> {
                    if (channelStorage != null && data.textInput != null && !data.textInput.trim().isEmpty()) {
                        String pin = data.textInput.trim();
                        // Skip if player already has this PIN in a slot
                        boolean alreadyInChannel = false;
                        for (String s : prefs.channelSlots) {
                            if (pin.equals(s)) { alreadyInChannel = true; break; }
                        }
                        if (alreadyInChannel) break;
                        int slot = prefs.findAvailableSlot();
                        if (slot < 0) break; // All 3 slots occupied
                        if (channelStorage.joinChannel(pin, playerUuid)) {
                            prefs.channelSlots[slot] = pin;
                            prefs.activeSlot = slot;
                            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                            channelStorage.saveAsync(manager.getScheduler());
                        }
                    }
                }
                case "Leave" -> {
                    if (channelStorage != null) {
                        String activePin = prefs.getActiveChannelPin();
                        if (activePin != null) {
                            channelStorage.leaveChannel(activePin, playerUuid);
                            prefs.channelSlots[prefs.activeSlot] = null;
                            // Auto-select first remaining occupied slot, or -1 if none
                            prefs.activeSlot = -1;
                            for (int i = 0; i < prefs.channelSlots.length; i++) {
                                if (prefs.channelSlots[i] != null) {
                                    prefs.activeSlot = i;
                                    break;
                                }
                            }
                            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                            channelStorage.saveAsync(manager.getScheduler());
                        }
                    }
                }
                case "DualVisOn" -> {
                    prefs.dualVisibility = true;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DualVisOff" -> {
                    prefs.dualVisibility = false;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "SwitchConfOn" -> {
                    prefs.switchConfirm = true;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "SwitchConfOff" -> {
                    prefs.switchConfirm = false;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "YellOn" -> {
                    prefs.yellEnabled = true;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "YellOff" -> {
                    prefs.yellEnabled = false;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefaultRpCull" -> {
                    prefs.rpCullDistance = 10;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefaultYellBubble" -> {
                    prefs.yellBubbleRange = 50;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefaultYellParticle" -> {
                    prefs.yellParticleRange = 75;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefPublic" -> {
                    prefs.channelColors.remove("public");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefRp1" -> {
                    prefs.channelColors.put("rp1", "#2d3d5a");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefRp2" -> {
                    prefs.channelColors.put("rp2", "#3d2d5a");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "DefRp3" -> {
                    prefs.channelColors.put("rp3", "#2d5a3d");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "EditPublic" -> {
                    cycleColor(prefs, "public", "#333333");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "EditRp1" -> {
                    cycleColor(prefs, "rp1", "#2d3d5a");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "EditRp2" -> {
                    cycleColor(prefs, "rp2", "#3d2d5a");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                case "EditRp3" -> {
                    cycleColor(prefs, "rp3", "#2d5a3d");
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
            }
            refresh();
            return;
        }

        // Dropdown changes
        if (data.dropdownId != null && data.dropdownValue != null) {
            try {
                switch (data.dropdownId) {
                    case "Slot" -> {
                        int slot = Integer.parseInt(data.dropdownValue);
                        if (slot >= 0 && slot < prefs.channelSlots.length) {
                            prefs.activeSlot = slot;
                            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                        }
                    }
                    case "RpCull" -> {
                        int val = Integer.parseInt(data.dropdownValue);
                        prefs.rpCullDistance = Math.max(5, Math.min(200, val));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                    case "YellBubble" -> {
                        int val = Integer.parseInt(data.dropdownValue);
                        prefs.yellBubbleRange = Math.max(10, Math.min(200, val));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                    case "YellParticle" -> {
                        int val = Integer.parseInt(data.dropdownValue);
                        prefs.yellParticleRange = Math.max(10, Math.min(200, val));
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                    }
                }
            } catch (NumberFormatException ignored) {}
            refresh();
        }
    }

    /**
     * Cycle through preset colors for a given channel key.
     */
    private void cycleColor(PlayerBubblePrefs prefs, String key, String defaultColor) {
        String current = prefs.channelColors.getOrDefault(key, defaultColor);
        int nextIdx = 0;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i].equals(current)) {
                nextIdx = (i + 1) % COLOR_PRESETS.length;
                break;
            }
        }
        prefs.channelColors.put(key, COLOR_PRESETS[nextIdx]);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        applyValues(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void applySettings(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        applyValues(cmd);
        registerEvents(evt);
    }

    /**
     * Register all event bindings -- called ONCE in build().
     * Never called from refresh() to avoid exponential binding accumulation.
     */
    private void registerEvents(@Nonnull UIEventBuilder evt) {
        // Back button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);

        // Nav buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavPlayerColors",
            new EventData().append("Action", "GoPlayerColors"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavChannels",
            new EventData().append("Action", "GoChannels"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NavHiddenMuted",
            new EventData().append("Action", "GoHiddenMuted"), false);

        // Slot dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SlotDropdown",
            EventData.of("DropdownId", "Slot").append("@DropdownValue", "#SlotDropdown.Value"), false);

        // Leave button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveBtn",
            new EventData().append("Action", "Leave"), false);

        // Host button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#HostBtn",
            new EventData().append("Action", "Host"), false);

        // PIN input field sync
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PinInput",
            new EventData().append("@TextInput", "#PinInput.Value"), false);

        // Join button (includes the PIN text input value)
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#JoinBtn",
            EventData.of("Action", "Join").append("@TextInput", "#PinInput.Value"), false);

        // Toggle pairs
        registerToggleEvents(evt, "#DualVisOnA", "#DualVisOnD", "#DualVisOffA", "#DualVisOffD", "DualVisOn", "DualVisOff");
        registerToggleEvents(evt, "#SwitchConfOnA", "#SwitchConfOnD", "#SwitchConfOffA", "#SwitchConfOffD", "SwitchConfOn", "SwitchConfOff");
        registerToggleEvents(evt, "#YellOnA", "#YellOnD", "#YellOffA", "#YellOffD", "YellOn", "YellOff");

        // RP Cull Distance dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RpCullDropdown",
            EventData.of("DropdownId", "RpCull").append("@DropdownValue", "#RpCullDropdown.Value"), false);

        // Yell Bubble Range dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#YellBubbleDropdown",
            EventData.of("DropdownId", "YellBubble").append("@DropdownValue", "#YellBubbleDropdown.Value"), false);

        // Yell Particle Range dropdown
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#YellParticleDropdown",
            EventData.of("DropdownId", "YellParticle").append("@DropdownValue", "#YellParticleDropdown.Value"), false);

        // Default buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultRpCullBtn",
            new EventData().append("Action", "DefaultRpCull"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultYellBubbleBtn",
            new EventData().append("Action", "DefaultYellBubble"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultYellParticleBtn",
            new EventData().append("Action", "DefaultYellParticle"), false);

        // Channel color edit buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditPublicBtn",
            new EventData().append("Action", "EditPublic"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditRp1Btn",
            new EventData().append("Action", "EditRp1"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditRp2Btn",
            new EventData().append("Action", "EditRp2"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditRp3Btn",
            new EventData().append("Action", "EditRp3"), false);

        // Channel color default buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefPublicBtn",
            new EventData().append("Action", "DefPublic"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefRp1Btn",
            new EventData().append("Action", "DefRp1"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefRp2Btn",
            new EventData().append("Action", "DefRp2"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DefRp3Btn",
            new EventData().append("Action", "DefRp3"), false);
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

    /**
     * Update all UI values -- safe to call from refresh() without duplicating event bindings.
     */
    private void applyValues(@Nonnull UICommandBuilder cmd) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);
        ChannelStorage channelStorage = manager.getChannelStorage();

        // --- Slot dropdown ---
        List<DropdownEntryInfo> slotEntries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String pin = prefs.channelSlots[i];
            String label = "Slot " + (i + 1) + (pin != null ? " (PIN: " + pin + ")" : " (Empty)");
            slotEntries.add(new DropdownEntryInfo(LocalizableString.fromString(label), String.valueOf(i)));
        }
        cmd.set("#SlotDropdown.Entries", slotEntries);
        cmd.set("#SlotDropdown.Value", String.valueOf(Math.max(0, prefs.activeSlot)));

        // --- Status label ---
        String activePin = prefs.getActiveChannelPin();
        if (activePin != null && channelStorage != null) {
            RpChannel ch = channelStorage.getChannel(activePin);
            if (ch != null) {
                int memberCount = ch.members.size();
                boolean isHost = playerUuid.equals(ch.hostUuid);
                cmd.set("#StatusLabel.Text", "Connected (PIN: " + activePin + ") - " + memberCount + " members" + (isHost ? " [Host]" : ""));
            } else {
                cmd.set("#StatusLabel.Text", "Channel expired");
            }
        } else {
            cmd.set("#StatusLabel.Text", "Not in a channel");
        }

        // --- PIN display ---
        if (activePin != null) {
            cmd.set("#PinLabel.Text", "PIN: " + activePin);
        } else {
            cmd.set("#PinLabel.Text", "PIN: ----");
        }

        // --- Toggles ---
        setToggleVisibility(cmd, "#DualVisOnA", "#DualVisOnD", "#DualVisOffA", "#DualVisOffD", prefs.dualVisibility);
        setToggleVisibility(cmd, "#SwitchConfOnA", "#SwitchConfOnD", "#SwitchConfOffA", "#SwitchConfOffD", prefs.switchConfirm);
        setToggleVisibility(cmd, "#YellOnA", "#YellOnD", "#YellOffA", "#YellOffD", prefs.yellEnabled);

        // --- RP Cull Distance dropdown ---
        List<DropdownEntryInfo> rpCullEntries = new ArrayList<>();
        for (int c : RP_CULL_PRESETS) {
            rpCullEntries.add(new DropdownEntryInfo(LocalizableString.fromString(c + " M"), String.valueOf(c)));
        }
        cmd.set("#RpCullDropdown.Entries", rpCullEntries);
        cmd.set("#RpCullDropdown.Value", String.valueOf(prefs.rpCullDistance));

        // --- Yell Bubble Range dropdown ---
        List<DropdownEntryInfo> yellBubbleEntries = new ArrayList<>();
        for (int y : YELL_RANGE_PRESETS) {
            yellBubbleEntries.add(new DropdownEntryInfo(LocalizableString.fromString(y + " M"), String.valueOf(y)));
        }
        cmd.set("#YellBubbleDropdown.Entries", yellBubbleEntries);
        cmd.set("#YellBubbleDropdown.Value", String.valueOf(prefs.yellBubbleRange));

        // --- Yell Particle Range dropdown ---
        List<DropdownEntryInfo> yellParticleEntries = new ArrayList<>();
        for (int y : YELL_RANGE_PRESETS) {
            yellParticleEntries.add(new DropdownEntryInfo(LocalizableString.fromString(y + " M"), String.valueOf(y)));
        }
        cmd.set("#YellParticleDropdown.Entries", yellParticleEntries);
        cmd.set("#YellParticleDropdown.Value", String.valueOf(prefs.yellParticleRange));

        // --- Channel color swatches ---
        String publicColor = prefs.channelColors.getOrDefault("public", "#333333");
        cmd.set("#PublicSwatch.Background.Color", publicColor + "FF");
        cmd.set("#Rp1Swatch.Background.Color", prefs.channelColors.getOrDefault("rp1", "#2d3d5a") + "FF");
        cmd.set("#Rp2Swatch.Background.Color", prefs.channelColors.getOrDefault("rp2", "#3d2d5a") + "FF");
        cmd.set("#Rp3Swatch.Background.Color", prefs.channelColors.getOrDefault("rp3", "#2d5a3d") + "FF");
    }

    private void setToggleVisibility(UICommandBuilder cmd,
                                       String onActiveId, String onDimId,
                                       String offActiveId, String offDimId, boolean isOn) {
        cmd.set(onActiveId + ".Visible", isOn);
        cmd.set(onDimId + ".Visible", !isOn);
        cmd.set(offActiveId + ".Visible", !isOn);
        cmd.set(offDimId + ".Visible", isOn);
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
            .addField(new KeyedCodec<>("@TextInput", Codec.STRING),
                (d, v) -> d.textInput = v, d -> d.textInput)
            .build();

        String action;
        String dropdownId;
        String dropdownValue;
        String textInput;
    }
}
