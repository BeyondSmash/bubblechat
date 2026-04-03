package com.bubblechat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.world.SetPaused;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BCPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PLUGIN_NAME = "BubbleChat";
    private static final String VERSION = "2.0.0";
    private static final long POLL_INTERVAL_MS = 20;

    private SpeechManager speechManager;
    private BubbleThemeStorage themeStorage;
    private PlayerBubblePrefsStorage prefsStorage;
    private ChannelStorage channelStorage;
    private BubbleChatConfig serverConfig;
    private ScheduledExecutorService scheduler;

    public BCPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("=================================");
        LOGGER.atInfo().log("%s v%s initializing...", PLUGIN_NAME, VERSION);
        LOGGER.atInfo().log("=================================");

        themeStorage = new BubbleThemeStorage(getDataDirectory());
        themeStorage.load();

        prefsStorage = new PlayerBubblePrefsStorage(getDataDirectory());

        channelStorage = new ChannelStorage(getDataDirectory());
        channelStorage.load();

        serverConfig = BubbleChatConfig.load(getDataDirectory());

        speechManager = new SpeechManager();
        speechManager.setThemeStorage(themeStorage);
        speechManager.setPrefsStorage(prefsStorage);
        speechManager.setChannelStorage(channelStorage);
        speechManager.setServerConfig(serverConfig);

        BubbleChatAPI.init(speechManager);
    }

    @Override
    protected void start() {
        // Init composite renderer + build runtime-cloned particle packets
        speechManager.initResources();

        // Start scheduler (must be before event registration to avoid race condition)
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "BC-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        speechManager.setScheduler(scheduler);

        // Register command
        getCommandRegistry().registerCommand(new BCCommand(speechManager, themeStorage, prefsStorage, channelStorage, serverConfig));
        LOGGER.atInfo().log("Registered /bchat command");

        // Send custom particle configs to players on join + restore persisted settings
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID playerUuid = playerRef.getUuid();

            // Restore persisted selfVisible (default false for new players)
            PlayerBubbleTheme theme = themeStorage.getTheme(playerUuid);
            speechManager.setSelfVisible(playerUuid, theme.selfVisible);

            // Delay slightly so client is ready to receive packets
            scheduler.schedule(() -> {
                try {
                    speechManager.sendParticleConfigs(playerRef);
                    // NOTE: mouth registration moved to lazy flow (sendCombinedParticleConfigs)
                    // to avoid [AssetUpdate] stutter on login
                    // Apply saved theme (color/light mode) on top of default spawners
                    speechManager.applyThemeToPlayer(playerUuid, playerRef);
                } catch (Exception e) {
                    LOGGER.atWarning().log("Error sending particle configs to %s: %s",
                        playerRef.getUsername(), e.getMessage());
                }

                // Validate channel slots — verify PINs still exist, rejoin if needed
                try {
                    PlayerBubblePrefs prefs = prefsStorage.getPrefs(playerUuid);
                    boolean changed = false;
                    for (int i = 0; i < prefs.channelSlots.length; i++) {
                        String pin = prefs.channelSlots[i];
                        if (pin != null) {
                            RpChannel ch = channelStorage.getChannel(pin);
                            if (ch == null) {
                                // Channel no longer exists — clear slot
                                prefs.channelSlots[i] = null;
                                changed = true;
                            } else if (!ch.isMember(playerUuid)) {
                                // Re-add to channel (reconnect)
                                ch.members.add(playerUuid);
                            }
                        }
                    }
                    if (changed) {
                        // Reset activeSlot if current slot is now empty
                        if (prefs.activeSlot >= 0 && (prefs.activeSlot >= prefs.channelSlots.length ||
                            prefs.channelSlots[prefs.activeSlot] == null)) {
                            prefs.activeSlot = -1;
                        }
                        prefsStorage.saveAsync(playerUuid, scheduler);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("Error validating channel slots for %s: %s",
                        playerRef.getUsername(), e.getMessage());
                }
            }, 2, TimeUnit.SECONDS);
        });

        // Register chat event (PlayerChatEvent is IAsyncEvent<String>)
        // Handles RP channel prefixes (rp1/rp2/rp3/pbc), channel isolation, and confirmation flow
        getEventRegistry().registerAsyncGlobal(EventPriority.FIRST, PlayerChatEvent.class, future -> {
            return future.thenApply(event -> {
                if (event.isCancelled()) return event;
                try {
                    PlayerRef sender = event.getSender();
                    UUID uuid = sender.getUuid();
                    String message = event.getContent();
                    PlayerBubblePrefs prefs = prefsStorage.getPrefs(uuid);

                    // Parse prefix
                    String lowerMsg = message.toLowerCase();
                    String prefix = null;
                    String strippedMessage = message;
                    int targetSlot = -1;

                    if (!serverConfig.rpChannelsEnabled) {
                        // RP channels disabled — skip prefix parsing, treat as public chat
                        speechManager.onChat(sender, message);
                        return event;
                    }

                    if (lowerMsg.startsWith("rp1")) {
                        prefix = "rp"; targetSlot = 0;
                        strippedMessage = message.substring(3);
                        if (!strippedMessage.isEmpty() && strippedMessage.charAt(0) == ' ')
                            strippedMessage = strippedMessage.substring(1);
                    } else if (lowerMsg.startsWith("rp2")) {
                        prefix = "rp"; targetSlot = 1;
                        strippedMessage = message.substring(3);
                        if (!strippedMessage.isEmpty() && strippedMessage.charAt(0) == ' ')
                            strippedMessage = strippedMessage.substring(1);
                    } else if (lowerMsg.startsWith("rp3")) {
                        prefix = "rp"; targetSlot = 2;
                        strippedMessage = message.substring(3);
                        if (!strippedMessage.isEmpty() && strippedMessage.charAt(0) == ' ')
                            strippedMessage = strippedMessage.substring(1);
                    } else if (lowerMsg.startsWith("pbc")) {
                        prefix = "pbc";
                        strippedMessage = message.substring(3);
                        if (!strippedMessage.isEmpty() && strippedMessage.charAt(0) == ' ')
                            strippedMessage = strippedMessage.substring(1);
                    }

                    if (prefix != null && strippedMessage.isEmpty()) {
                        // Prefix with no message content — let vanilla handle as-is
                        speechManager.onChat(sender, message);
                        return event;
                    }

                    // Handle switch confirmation
                    if (prefix != null && prefs.switchConfirm) {
                        event.setCancelled(true);
                        // Queue message and show confirmation HUD
                        final String finalMsg = strippedMessage;
                        final String finalPrefix = prefix;
                        final int finalSlot = targetSlot;
                        // Must open UI on world thread
                        UUID worldUuid = sender.getWorldUuid();
                        if (worldUuid != null) {
                            com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid)
                                .execute(() -> speechManager.queueConfirmation(sender, finalPrefix, finalSlot, finalMsg));
                        }
                        return event;
                    }

                    // No confirmation needed — process immediately
                    if (prefix != null && prefix.equals("rp") && targetSlot >= 0) {
                        // Switch active slot
                        prefs.activeSlot = targetSlot;
                        prefsStorage.saveAsync(uuid, speechManager.getScheduler());
                    }

                    boolean forcePbc = "pbc".equals(prefix);
                    String activePin = forcePbc ? null : prefs.getActiveChannelPin();

                    if (activePin != null && channelStorage.isMember(activePin, uuid)) {
                        String finalChatMsg = (prefix != null) ? strippedMessage : message;
                        UUID worldUuid = sender.getWorldUuid();

                        if (prefs.dualVisibility) {
                            // Dual visibility: send to BOTH channel AND public
                            // Don't cancel vanilla — public players see normal chat text

                            // Send [RP] text to channel members synchronously (sendMessage is thread-safe)
                            Message rpMsg = Message.raw("[RP] " + sender.getUsername() + ": " + finalChatMsg);
                            Set<UUID> members = channelStorage.getMembers(activePin);
                            for (UUID memberUuid : members) {
                                if (memberUuid.equals(uuid)) {
                                    sender.sendMessage(rpMsg);
                                    continue;
                                }
                                for (PlayerRef p : com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) {
                                    if (p.getUuid().equals(memberUuid)) {
                                        p.sendMessage(rpMsg);
                                        break;
                                    }
                                }
                            }

                            // Bubble visible to everyone (channel + public) — no channel isolation
                            speechManager.onChat(sender, finalChatMsg, null, true);

                            // Remove channel members from vanilla targets — they already got [RP] text
                            event.getTargets().removeIf(target -> {
                                UUID tUuid = target.getUuid();
                                if (tUuid.equals(uuid)) return true; // sender already got [RP] self-text
                                return channelStorage.isMember(activePin, tUuid);
                            });
                        } else {
                            // Normal channel mode — cancel vanilla chat, send to channel only
                            event.setCancelled(true);
                            if (worldUuid != null) {
                                var world = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid);
                                if (world != null) {
                                    world.execute(() -> speechManager.onChannelChat(sender, finalChatMsg, activePin));
                                }
                            }
                        }
                    } else {
                        // Public mode — vanilla handles text, we add bubble
                        String chatMsg = (prefix != null) ? strippedMessage : message;
                        speechManager.onChat(sender, chatMsg);

                        // For public chat, hide from isolated channel members
                        event.getTargets().removeIf(target -> {
                            PlayerBubblePrefs tPrefs = prefsStorage.getPrefs(target.getUuid());
                            String tPin = tPrefs.getActiveChannelPin();
                            if (tPin != null && channelStorage.isMember(tPin, target.getUuid())) {
                                return !tPrefs.dualVisibility; // remove if NOT dual visibility
                            }
                            return false;
                        });
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Error in BubbleChat chat handler");
                }
                return event;
            });
        });

        // Register disconnect cleanup
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID disconnectUuid = event.getPlayerRef().getUuid();
            speechManager.removePlayer(disconnectUuid);
            channelStorage.removePlayerFromAll(disconnectUuid);
            channelStorage.saveAsync(scheduler);
        });

        // Clear active bubble chat when ThinkingBubble / busy-bubble triggers (UI opens)
        PacketAdapters.registerOutbound((PlayerPacketWatcher) (playerRef, packet) -> {
            if (packet.getId() == 216) { // SetPage
                SetPage setPage = (SetPage) packet;
                if (setPage.page != Page.None) {
                    speechManager.clearSpeech(playerRef.getUuid());
                }
            }
        });
        PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            if (packet.getId() == 158) { // SetPaused
                SetPaused setPaused = (SetPaused) packet;
                if (setPaused.paused) {
                    speechManager.clearSpeech(playerRef.getUuid());
                }
            }
        });

        // Poll loop for death detection + position following (20ms for smooth tracking)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                speechManager.pollAndCleanup();
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Error in BubbleChat poll loop");
            }
        }, 1000, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        LOGGER.atInfo().log("=================================");
        LOGGER.atInfo().log("%s v%s started!", PLUGIN_NAME, VERSION);
        LOGGER.atInfo().log("=================================");
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("%s shutting down...", PLUGIN_NAME);

        if (themeStorage != null) {
            themeStorage.save();
        }

        if (channelStorage != null) {
            channelStorage.save();
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        LOGGER.atInfo().log("%s shutdown complete", PLUGIN_NAME);
    }
}
