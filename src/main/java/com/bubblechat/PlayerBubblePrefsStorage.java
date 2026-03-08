package com.bubblechat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Per-UUID JSON file storage for PlayerBubblePrefs.
 * Files stored in {dataDir}/bubble_prefs/{uuid}.json
 */
public class PlayerBubblePrefsStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PREFS_DIR = "bubble_prefs";

    private final Path prefsDir;
    private final Map<UUID, PlayerBubblePrefs> cache = new ConcurrentHashMap<>();

    public PlayerBubblePrefsStorage(@Nonnull Path dataDir) {
        this.prefsDir = dataDir.resolve(PREFS_DIR);
    }

    @Nonnull
    public PlayerBubblePrefs getPrefs(@Nonnull UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    private PlayerBubblePrefs loadFromDisk(UUID uuid) {
        String fileName = uuid.toString() + ".json";
        Path file = prefsDir.resolve(fileName);

        PlayerBubblePrefs prefs = tryLoadJson(file);
        if (prefs != null) return prefs;

        // Try backup
        prefs = tryLoadJson(file.resolveSibling(fileName + ".bak"));
        if (prefs != null) {
            LOGGER.atWarning().log("[BubbleChat] Recovered prefs for %s from backup!", uuid);
            cache.put(uuid, prefs);
            save(uuid);
            return prefs;
        }

        return new PlayerBubblePrefs();
    }

    private PlayerBubblePrefs tryLoadJson(Path path) {
        try {
            if (!Files.exists(path)) return null;
            if (Files.size(path) == 0) return null;
            String json = Files.readString(path);
            if (json.isBlank()) return null;
            return GSON.fromJson(json, PlayerBubblePrefs.class);
        } catch (Exception e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to read %s: %s", path.getFileName(), e.getMessage());
            return null;
        }
    }

    public void save(@Nonnull UUID uuid) {
        PlayerBubblePrefs prefs = cache.get(uuid);
        if (prefs == null) return;
        String fileName = uuid.toString() + ".json";
        Path target = prefsDir.resolve(fileName);
        Path tempPath = target.resolveSibling(fileName + ".tmp");
        Path backupPath = target.resolveSibling(fileName + ".bak");
        try {
            Files.createDirectories(prefsDir);
            String json = GSON.toJson(prefs);
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
                LOGGER.atWarning().log("[BubbleChat] Failed to save prefs for %s: %s", uuid, e2.getMessage());
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to save prefs for %s: %s", uuid, e.getMessage());
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }

    public void saveAsync(@Nonnull UUID uuid, @Nonnull ScheduledExecutorService scheduler) {
        if (!scheduler.isShutdown()) {
            scheduler.execute(() -> save(uuid));
        } else {
            save(uuid);
        }
    }

    public void removeFromCache(@Nonnull UUID uuid) {
        cache.remove(uuid);
    }
}
