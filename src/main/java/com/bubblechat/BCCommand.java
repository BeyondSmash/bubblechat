package com.bubblechat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.UUID;

public class BCCommand extends AbstractPlayerCommand {

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final ChannelStorage channelStorage;
    private final BubbleChatConfig serverConfig;

    public BCCommand(SpeechManager manager, BubbleThemeStorage themeStorage,
                     PlayerBubblePrefsStorage prefsStorage, ChannelStorage channelStorage,
                     BubbleChatConfig serverConfig) {
        super("bchat", "Toggle speech bubbles");
        setPermissionGroup(null);
        setAllowsExtraArguments(true);
        this.manager = manager;
        this.themeStorage = themeStorage;
        this.prefsStorage = prefsStorage;
        this.channelStorage = channelStorage;
        this.serverConfig = serverConfig;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = context.getInputString();
        String trimmed = (input == null) ? "" : input.trim();

        String cmdPrefix = "bchat";
        if (trimmed.toLowerCase().startsWith(cmdPrefix)) {
            trimmed = trimmed.substring(cmdPrefix.length()).trim();
        }

        UUID uuid = playerRef.getUuid();

        if (trimmed.equalsIgnoreCase("help") || trimmed.equals("--help")) {
            sendHelp(context);
            return;
        }

        if (trimmed.toLowerCase().startsWith("self")) {
            handleSelf(context, trimmed, uuid);
            return;
        }

        if (trimmed.equalsIgnoreCase("clear")) {
            manager.clearSpeech(uuid);
            context.sendMessage(Message.raw("Speech bubble cleared."));
            return;
        }

        if (trimmed.toLowerCase().startsWith("theme")) {
            handleTheme(context, trimmed, uuid, playerRef, store, ref);
            return;
        }

        // Direct page shortcuts
        if (trimmed.equalsIgnoreCase("pc")) {
            openPage(store, ref, playerRef, "pc");
            return;
        }
        if (trimmed.equalsIgnoreCase("ch")) {
            if (serverConfig != null && !serverConfig.rpChannelsEnabled) {
                context.sendMessage(Message.raw("RP channels are disabled on this server."));
                return;
            }
            openPage(store, ref, playerRef, "ch");
            return;
        }
        if (trimmed.equalsIgnoreCase("hm")) {
            openPage(store, ref, playerRef, "hm");
            return;
        }
        if (trimmed.equalsIgnoreCase("vc")) {
            openPage(store, ref, playerRef, "vc");
            return;
        }
        if (trimmed.toLowerCase().startsWith("vc ")) {
            String vcArg = trimmed.substring(3).trim();
            if (vcArg.equalsIgnoreCase("on") || vcArg.equalsIgnoreCase("off")) {
                PlayerBubbleTheme theme = themeStorage.getTheme(uuid);
                theme.animalese = vcArg.equalsIgnoreCase("on");
                themeStorage.saveAsync(manager.getScheduler());
                context.sendMessage(Message.raw("Animalese " + (theme.animalese ? "enabled" : "disabled") + "."));
            } else {
                context.sendMessage(Message.raw("Usage: /bchat vc on|off"));
            }
            return;
        }


        if (trimmed.equalsIgnoreCase("status")) {
            boolean enabled = manager.isEnabled(uuid);
            boolean selfVisible = manager.isSelfVisible(uuid);
            context.sendMessage(Message.raw(
                "BubbleChat: " + (enabled ? "enabled" : "disabled") +
                " | Self-visible: " + (selfVisible ? "on" : "off") +
                " | Active bubbles: " + manager.getActiveCount()
            ));
            return;
        }

        // Default: open settings GUI
        if (trimmed.isEmpty()) {
            openPage(store, ref, playerRef, "main");
            return;
        }


        if (trimmed.equalsIgnoreCase("toggle")) {
            boolean nowEnabled = manager.togglePlayer(uuid);
            context.sendMessage(Message.raw(
                "BubbleChat " + (nowEnabled ? "enabled" : "disabled") + " for you."
            ));
        } else {
            context.sendMessage(Message.raw("Unknown subcommand: " + trimmed));
            context.sendMessage(Message.raw("Use /bchat help for usage."));
        }
    }

    private void handleSelf(@Nonnull CommandContext context, @Nonnull String trimmed, UUID uuid) {
        String arg = trimmed.length() > 4 ? trimmed.substring(4).trim() : "";

        if (arg.equalsIgnoreCase("on")) {
            manager.setSelfVisible(uuid, true);
            context.sendMessage(Message.raw("Speech bubbles now visible to yourself."));
        } else if (arg.equalsIgnoreCase("off")) {
            manager.setSelfVisible(uuid, false);
            context.sendMessage(Message.raw("Speech bubbles hidden from yourself."));
        } else {
            boolean current = manager.isSelfVisible(uuid);
            context.sendMessage(Message.raw("Self-visible: " + (current ? "on" : "off")));
            context.sendMessage(Message.raw("Usage: /bchat self on|off"));
        }
    }

    private void handleTheme(@Nonnull CommandContext context, @Nonnull String trimmed,
                             UUID uuid, @Nonnull PlayerRef playerRef,
                             @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        String arg = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";

        if (arg.isEmpty()) {
            // Open GUI
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store,
                    new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, uuid));
            }
            return;
        }

        PlayerBubbleTheme theme = themeStorage.getTheme(uuid);

        if (arg.equalsIgnoreCase("light")) {
            theme.lightMode = true;
            themeStorage.saveAsync(manager.getScheduler());
            manager.applyThemeToPlayer(uuid, playerRef);
            context.sendMessage(Message.raw("Bubble theme set to Light mode."));
        } else if (arg.equalsIgnoreCase("dark")) {
            theme.lightMode = false;
            themeStorage.saveAsync(manager.getScheduler());
            manager.applyThemeToPlayer(uuid, playerRef);
            context.sendMessage(Message.raw("Bubble theme set to Dark mode."));
        } else if (arg.toLowerCase().startsWith("color")) {
            String colorArg = arg.length() > 5 ? arg.substring(5).trim() : "";
            if (colorArg.isEmpty()) {
                String current = theme.tintColorHex != null ? theme.tintColorHex : "default";
                context.sendMessage(Message.raw("Current tint color: " + current));
                context.sendMessage(Message.raw("Usage: /bchat theme color <#RRGGBB|reset>"));
                return;
            }
            if (colorArg.equalsIgnoreCase("reset")) {
                theme.tintColorHex = null;
                themeStorage.saveAsync(manager.getScheduler());
                manager.applyThemeToPlayer(uuid, playerRef);
                context.sendMessage(Message.raw("Bubble tint color reset to default."));
            } else {
                String hex = colorArg.startsWith("#") ? colorArg : "#" + colorArg;
                if (hex.length() != 7 || !hex.substring(1).matches("[0-9a-fA-F]{6}")) {
                    context.sendMessage(Message.raw("Invalid color format. Use #RRGGBB (e.g. #3366FF)"));
                    return;
                }
                theme.tintColorHex = hex;
                themeStorage.saveAsync(manager.getScheduler());
                manager.applyThemeToPlayer(uuid, playerRef);
                context.sendMessage(Message.raw("Bubble tint color set to " + hex));
            }
        } else {
            context.sendMessage(Message.raw("Usage: /bchat theme light|dark"));
            context.sendMessage(Message.raw("Usage: /bchat theme color <#RRGGBB|reset>"));
        }
    }

    private void openPage(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef, @Nonnull String page) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        UUID uuid = playerRef.getUuid();
        switch (page) {
            case "main" -> player.getPageManager().openCustomPage(ref, store,
                new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, uuid));
            case "pc" -> player.getPageManager().openCustomPage(ref, store,
                new PlayerColorsPage(manager, themeStorage, prefsStorage, playerRef, uuid));
            case "ch" -> player.getPageManager().openCustomPage(ref, store,
                new ChannelsPage(manager, themeStorage, prefsStorage, playerRef, uuid));
            case "hm" -> player.getPageManager().openCustomPage(ref, store,
                new HiddenMutedPage(manager, themeStorage, prefsStorage, playerRef, uuid));
            case "vc" -> player.getPageManager().openCustomPage(ref, store,
                new VoicePage(manager, themeStorage, prefsStorage, playerRef, uuid));
        }
    }

    private void sendHelp(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("--- BubbleChat Help ---"));
        context.sendMessage(Message.raw("/bchat - Open bubble settings"));
        context.sendMessage(Message.raw("/bchat pc - Player colors"));
        context.sendMessage(Message.raw("/bchat ch - Channels"));
        context.sendMessage(Message.raw("/bchat hm - Hidden & muted"));
        context.sendMessage(Message.raw("/bchat vc - Voice settings"));
        context.sendMessage(Message.raw("/bchat vc on|off - Toggle animalese"));
        context.sendMessage(Message.raw("/bchat toggle - Toggle on/off"));
        context.sendMessage(Message.raw("/bchat self on|off - Show/hide own bubble"));
        context.sendMessage(Message.raw("/bchat clear - Dismiss current bubble"));
        context.sendMessage(Message.raw("/bchat theme light|dark - Set mode"));
        context.sendMessage(Message.raw("/bchat theme color <#RRGGBB|reset> - Set tint"));
        context.sendMessage(Message.raw("/bchat status - Show settings"));
    }
}
