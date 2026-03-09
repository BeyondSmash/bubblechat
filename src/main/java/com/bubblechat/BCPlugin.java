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
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BCPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PLUGIN_NAME = "BubbleChat";
    private static final String VERSION = "1.0.2";
    private static final long POLL_INTERVAL_MS = 20;

    private SpeechManager speechManager;
    private BubbleThemeStorage themeStorage;
    private PlayerBubblePrefsStorage prefsStorage;
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

        speechManager = new SpeechManager();
        speechManager.setThemeStorage(themeStorage);
        speechManager.setPrefsStorage(prefsStorage);

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
        getCommandRegistry().registerCommand(new BCCommand(speechManager, themeStorage, prefsStorage));
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
                    // Apply saved theme (color/light mode) on top of default spawners
                    speechManager.applyThemeToPlayer(playerUuid, playerRef);
                } catch (Exception e) {
                    LOGGER.atWarning().log("Error sending particle configs to %s: %s",
                        playerRef.getUsername(), e.getMessage());
                }
            }, 2, TimeUnit.SECONDS);
        });

        // Register chat event (PlayerChatEvent is IAsyncEvent<String>)
        // Wrap onChat in try-catch to prevent exceptions from failing the future
        // (a failed future can cause Hytale to suppress the chat message)
        getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> {
            return future.thenApply(event -> {
                if (!event.isCancelled()) {
                    try {
                        speechManager.onChat(event.getSender(), event.getContent());
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error in BubbleChat onChat handler");
                    }
                }
                return event;
            });
        });

        // Register disconnect cleanup
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            speechManager.removePlayer(event.getPlayerRef().getUuid());
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
