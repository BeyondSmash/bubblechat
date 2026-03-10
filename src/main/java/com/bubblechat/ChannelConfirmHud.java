package com.bubblechat;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ChannelConfirmHud extends InteractiveCustomUIPage<ChannelConfirmHud.PageData> {

    private final SpeechManager manager;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final ChannelStorage channelStorage;
    private final PlayerRef playerRef;
    private final UUID playerUuid;
    private final String prefix;       // "rp" or "pbc"
    private final int targetSlot;      // 0-2 for rp, -1 for pbc
    private final String queuedMessage;
    private final String confirmText;

    public ChannelConfirmHud(@Nonnull SpeechManager manager,
                             @Nonnull PlayerBubblePrefsStorage prefsStorage,
                             @Nonnull ChannelStorage channelStorage,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull UUID playerUuid,
                             @Nonnull String prefix,
                             int targetSlot,
                             @Nonnull String queuedMessage,
                             @Nonnull String confirmText) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.manager = manager;
        this.prefsStorage = prefsStorage;
        this.channelStorage = channelStorage;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
        this.prefix = prefix;
        this.targetSlot = targetSlot;
        this.queuedMessage = queuedMessage;
        this.confirmText = confirmText;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("BubbleConfirm.ui");
        cmd.set("#ConfirmMsg.Text", confirmText);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SendBtn",
            new EventData().append("Action", "Send"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn",
            new EventData().append("Action", "Cancel"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        if ("Send".equals(data.action)) {
            close();
            PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);

            if ("pbc".equals(prefix)) {
                // Public broadcast — use normal onChat
                manager.onChat(playerRef, queuedMessage);
                // Also send as vanilla chat to all players (manual since we cancelled the event)
                Message chatMsg = Message.raw(playerRef.getUsername() + ": " + queuedMessage);
                for (PlayerRef p : Universe.get().getPlayers()) {
                    p.sendMessage(chatMsg);
                }
            } else {
                // RP channel — switch slot and send to channel
                if (targetSlot >= 0) {
                    prefs.activeSlot = targetSlot;
                    prefsStorage.saveAsync(playerUuid, manager.getScheduler());
                }
                String activePin = prefs.getActiveChannelPin();
                if (activePin != null && channelStorage.isMember(activePin, playerUuid)) {
                    manager.onChannelChat(playerRef, queuedMessage, activePin);
                } else {
                    // Not in a channel for this slot — send as public
                    manager.onChat(playerRef, queuedMessage);
                }
            }
        } else if ("Cancel".equals(data.action)) {
            close();
            // Message dropped — do nothing
        }
    }

    public static class PageData {
        @Nonnull
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
            .<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .build();

        String action;
    }
}
