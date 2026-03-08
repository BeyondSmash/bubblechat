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
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HiddenMutedPage extends InteractiveCustomUIPage<HiddenMutedPage.PageData> {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    private String hideInput = "";
    private String muteInput = "";
    private String muteDurationValue = "3600000"; // default 1h in ms

    private static final long[] DURATION_MS = {
        5 * 60_000L,      // 5 min
        15 * 60_000L,     // 15 min
        30 * 60_000L,     // 30 min
        60 * 60_000L,     // 1 hour
        2 * 3_600_000L,   // 2 hours
        6 * 3_600_000L,   // 6 hours
        12 * 3_600_000L,  // 12 hours
        24 * 3_600_000L   // 24 hours
    };

    private static final String[] DURATION_LABELS = {
        "5 min", "15 min", "30 min", "1 hour", "2 hours", "6 hours", "12 hours", "24 hours"
    };

    public HiddenMutedPage(@Nonnull SpeechManager manager,
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
        cmd.append("HiddenMuted.ui");
        applySettings(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        if (data.hideInput != null) hideInput = data.hideInput;
        if (data.muteInput != null) muteInput = data.muteInput;

        // Duration dropdown change
        if (data.dropdownId != null && "MuteDuration".equals(data.dropdownId) && data.dropdownValue != null) {
            muteDurationValue = data.dropdownValue;
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
                case "Hide" -> {
                    if (hideInput != null && !hideInput.trim().isEmpty()) {
                        String name = hideInput.trim().toLowerCase();
                        prefs.hiddenPlayers.add(name);
                        prefs.mutedPlayers.remove(name); // can't be both
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                        hideInput = "";
                    }
                }
                case "Mute" -> {
                    if (muteInput != null && !muteInput.trim().isEmpty()) {
                        String name = muteInput.trim().toLowerCase();
                        long durationMs;
                        try {
                            durationMs = Long.parseLong(muteDurationValue);
                        } catch (NumberFormatException e) {
                            durationMs = 3_600_000L;
                        }
                        prefs.mutedPlayers.put(name, System.currentTimeMillis() + durationMs);
                        prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                        muteInput = "";
                    }
                }
            }
            refresh();
            return;
        }

        if (data.removeHidden != null) {
            prefs.hiddenPlayers.remove(data.removeHidden.toLowerCase());
            prefsStorage.saveAsync(playerUuid, manager.getScheduler());
            refresh();
            return;
        }

        if (data.removeMuted != null) {
            prefs.mutedPlayers.remove(data.removeMuted.toLowerCase());
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
        // Back button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);

        // Sync input fields with server state (clears after Hide/Mute)
        cmd.set("#HideInput.Value", hideInput);
        cmd.set("#MuteInput.Value", muteInput);

        // Hide section
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HideInput",
            new EventData().append("@HideInput", "#HideInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#HideButton",
            new EventData().append("Action", "Hide"), false);

        // Mute section
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MuteInput",
            new EventData().append("@MuteInput", "#MuteInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MuteButton",
            new EventData().append("Action", "Mute"), false);

        // Duration dropdown
        List<DropdownEntryInfo> durEntries = new ArrayList<>();
        for (int i = 0; i < DURATION_LABELS.length; i++) {
            durEntries.add(new DropdownEntryInfo(
                LocalizableString.fromString(DURATION_LABELS[i]),
                String.valueOf(DURATION_MS[i])));
        }
        cmd.set("#DurationDropdown.Entries", durEntries);
        cmd.set("#DurationDropdown.Value", muteDurationValue);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DurationDropdown",
            EventData.of("DropdownId", "MuteDuration").append("@DropdownValue", "#DurationDropdown.Value"), false);

        // Build lists
        buildHiddenList(cmd, evt);
        buildMutedList(cmd, evt);
    }

    private void buildHiddenList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        cmd.clear("#HiddenCards");
        cmd.appendInline("#HiddenContainer", "Group #HiddenCards { LayoutMode: Top; }");

        List<String> sorted = new ArrayList<>(prefs.hiddenPlayers);
        Collections.sort(sorted);

        if (sorted.isEmpty()) {
            cmd.appendInline("#HiddenCards",
                "Label { Text: \"No hidden players.\"; Style: (FontSize: 14, TextColor: #888888); Anchor: (Top: 6); }");
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            String name = sorted.get(i);
            cmd.append("#HiddenCards", "HiddenMutedEntry.ui");
            cmd.set("#HiddenCards[" + i + "] #EntryName.Text", name);
            cmd.set("#HiddenCards[" + i + "] #TimeLabel.Text", "");
            cmd.set("#HiddenCards[" + i + "] #RemoveButton.Text", "Unhide");

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#HiddenCards[" + i + "] #RemoveButton",
                new EventData().append("RemoveHidden", name), false);
        }
    }

    private void buildMutedList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

        cmd.clear("#MutedCards");
        cmd.appendInline("#MutedContainer", "Group #MutedCards { LayoutMode: Top; }");

        // Filter out expired mutes
        long now = System.currentTimeMillis();
        List<Map.Entry<String, Long>> active = new ArrayList<>();
        Iterator<Map.Entry<String, Long>> it = prefs.mutedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
            } else {
                active.add(entry);
            }
        }
        active.sort(Map.Entry.comparingByKey());

        if (active.isEmpty()) {
            cmd.appendInline("#MutedCards",
                "Label { Text: \"No muted players.\"; Style: (FontSize: 14, TextColor: #888888); Anchor: (Top: 6); }");
            return;
        }

        for (int i = 0; i < active.size(); i++) {
            Map.Entry<String, Long> entry = active.get(i);
            cmd.append("#MutedCards", "HiddenMutedEntry.ui");
            cmd.set("#MutedCards[" + i + "] #EntryName.Text", entry.getKey());
            cmd.set("#MutedCards[" + i + "] #TimeLabel.Text", formatTimeRemaining(entry.getValue() - now));
            cmd.set("#MutedCards[" + i + "] #RemoveButton.Text", "Unmute");

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#MutedCards[" + i + "] #RemoveButton",
                new EventData().append("RemoveMuted", entry.getKey()), false);
        }
    }

    private static String formatTimeRemaining(long ms) {
        if (ms <= 0) return "expired";
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static class PageData {
        @Nonnull
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
            .<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("@HideInput", Codec.STRING),
                (d, v) -> d.hideInput = v, d -> d.hideInput)
            .addField(new KeyedCodec<>("@MuteInput", Codec.STRING),
                (d, v) -> d.muteInput = v, d -> d.muteInput)
            .addField(new KeyedCodec<>("RemoveHidden", Codec.STRING),
                (d, v) -> d.removeHidden = v, d -> d.removeHidden)
            .addField(new KeyedCodec<>("RemoveMuted", Codec.STRING),
                (d, v) -> d.removeMuted = v, d -> d.removeMuted)
            .addField(new KeyedCodec<>("DropdownId", Codec.STRING),
                (d, v) -> d.dropdownId = v, d -> d.dropdownId)
            .addField(new KeyedCodec<>("@DropdownValue", Codec.STRING),
                (d, v) -> d.dropdownValue = v, d -> d.dropdownValue)
            .build();

        String action;
        String hideInput;
        String muteInput;
        String removeHidden;
        String removeMuted;
        String dropdownId;
        String dropdownValue;
    }
}
