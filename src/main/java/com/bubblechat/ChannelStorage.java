package com.bubblechat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages RP channels keyed by 4-digit PIN.
 * Persisted as a single JSON file with atomic save pattern.
 */
public class ChannelStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "rp_channels.json";

    private final Path dataDir;
    private final Map<String, RpChannel> channels = new ConcurrentHashMap<>();

    /** Wrapper for Gson serialization. */
    private static class ChannelsFile {
        Map<String, RpChannel> channels;

        ChannelsFile() {
            this.channels = new ConcurrentHashMap<>();
        }

        ChannelsFile(Map<String, RpChannel> channels) {
            this.channels = channels;
        }
    }

    public ChannelStorage(@Nonnull Path dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Create a new RP channel with a unique 4-digit PIN.
     * The host is automatically added as the first member.
     */
    @Nonnull
    public RpChannel createChannel(@Nonnull UUID hostUuid) {
        String pin = generateUniquePin();
        RpChannel channel = new RpChannel(pin, hostUuid);
        channels.put(pin, channel);
        return channel;
    }

    /** Look up a channel by PIN. Returns null if not found. */
    @Nullable
    public RpChannel getChannel(@Nonnull String pin) {
        return channels.get(pin);
    }

    /**
     * Add a player to the given channel.
     * @return true if the player was added (channel exists), false if channel not found.
     */
    public boolean joinChannel(@Nonnull String pin, @Nonnull UUID playerUuid) {
        RpChannel channel = channels.get(pin);
        if (channel == null) return false;
        channel.members.add(playerUuid);
        return true;
    }

    /**
     * Remove a player from the given channel.
     * If the channel becomes empty after removal, it is deleted.
     */
    public void leaveChannel(@Nonnull String pin, @Nonnull UUID playerUuid) {
        RpChannel channel = channels.get(pin);
        if (channel == null) return;
        channel.members.remove(playerUuid);
        if (channel.isEmpty()) {
            channels.remove(pin);
        }
    }

    /** Return an unmodifiable view of the channel's members, or empty set if not found. */
    @Nonnull
    public Set<UUID> getMembers(@Nonnull String pin) {
        RpChannel channel = channels.get(pin);
        if (channel == null) return Collections.emptySet();
        return Collections.unmodifiableSet(channel.members);
    }

    /** Check if a player is a member of the given channel. */
    public boolean isMember(@Nonnull String pin, @Nonnull UUID playerUuid) {
        RpChannel channel = channels.get(pin);
        return channel != null && channel.isMember(playerUuid);
    }

    /** Remove a player from ALL channels (used on disconnect). Cleans up empty channels. */
    public void removePlayerFromAll(@Nonnull UUID playerUuid) {
        channels.values().removeIf(channel -> {
            channel.members.remove(playerUuid);
            return channel.isEmpty();
        });
    }

    /** Generate a unique random 4-digit PIN that doesn't collide with existing channels. */
    private String generateUniquePin() {
        String pin;
        int attempts = 0;
        do {
            pin = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            attempts++;
            if (attempts > 1000) {
                // Extremely unlikely, but prevent infinite loop
                LOGGER.atWarning().log("[BubbleChat] PIN collision after 1000 attempts, using timestamp suffix");
                pin = String.format("%04d", System.currentTimeMillis() % 10000);
                break;
            }
        } while (channels.containsKey(pin));
        return pin;
    }

    /** Gson deserializes Set<UUID> as HashSet — replace with ConcurrentHashMap-backed sets. */
    private void fixMembersSets() {
        for (RpChannel ch : channels.values()) {
            Set<UUID> concurrent = ConcurrentHashMap.newKeySet();
            concurrent.addAll(ch.members);
            ch.members = concurrent;
        }
    }

    // --- Persistence (atomic save pattern) ---

    public void load() {
        Path path = dataDir.resolve(FILE_NAME);
        ChannelsFile loaded = tryLoadJson(path);
        if (loaded != null && loaded.channels != null) {
            channels.clear();
            channels.putAll(loaded.channels);
            fixMembersSets();
            LOGGER.atInfo().log("[BubbleChat] Loaded %d RP channels", channels.size());
        } else {
            // Try backup
            loaded = tryLoadJson(path.resolveSibling(FILE_NAME + ".bak"));
            if (loaded != null && loaded.channels != null) {
                channels.clear();
                channels.putAll(loaded.channels);
                fixMembersSets();
                LOGGER.atWarning().log("[BubbleChat] Recovered %d RP channels from backup!", channels.size());
                save();
            }
        }
    }

    @Nullable
    private ChannelsFile tryLoadJson(Path path) {
        try {
            if (!Files.exists(path)) return null;
            if (Files.size(path) == 0) return null;
            String json = Files.readString(path);
            if (json.isBlank()) return null;
            return GSON.fromJson(json, ChannelsFile.class);
        } catch (Exception e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to read %s: %s", path.getFileName(), e.getMessage());
            return null;
        }
    }

    public void save() {
        Path target = dataDir.resolve(FILE_NAME);
        Path tempPath = target.resolveSibling(FILE_NAME + ".tmp");
        Path backupPath = target.resolveSibling(FILE_NAME + ".bak");
        try {
            Files.createDirectories(target.getParent());
            ChannelsFile file = new ChannelsFile(channels);
            String json = GSON.toJson(file);
            Files.writeString(tempPath, json);
            if (Files.exists(target) && Files.size(target) > 0) {
                Files.copy(target, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempPath, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOGGER.atWarning().log("[BubbleChat] Failed to save channels: %s", e2.getMessage());
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to save channels: %s", e.getMessage());
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }

    public void saveAsync(@Nonnull ScheduledExecutorService scheduler) {
        if (!scheduler.isShutdown()) {
            scheduler.execute(this::save);
        } else {
            save();
        }
    }
}
