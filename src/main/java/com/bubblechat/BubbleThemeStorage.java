package com.bubblechat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;


public class BubbleThemeStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FILE_NAME = "bubble_themes.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerBubbleTheme>>() {}.getType();

    private Map<String, PlayerBubbleTheme> playerThemes = new HashMap<>();
    private final Path dataDir;

    public BubbleThemeStorage(@Nonnull Path dataDir) {
        this.dataDir = dataDir;
    }

    @Nonnull
    public PlayerBubbleTheme getTheme(@Nonnull UUID uuid) {
        String key = uuid.toString();
        PlayerBubbleTheme theme = playerThemes.get(key);
        if (theme == null) {
            theme = new PlayerBubbleTheme();
            playerThemes.put(key, theme);
        }
        return theme;
    }

    public void load() {
        Path path = dataDir.resolve(FILE_NAME);
        Map<String, PlayerBubbleTheme> loaded = tryLoadJsonMap(path);
        if (loaded != null) {
            playerThemes = loaded;
            LOGGER.atInfo().log("[BubbleChat] Loaded %d player themes", playerThemes.size());
        } else {
            // Try backup
            loaded = tryLoadJsonMap(path.resolveSibling(FILE_NAME + ".bak"));
            if (loaded != null) {
                playerThemes = loaded;
                LOGGER.atWarning().log("[BubbleChat] Recovered %d player themes from backup!", playerThemes.size());
                save();
            } else {
                playerThemes = new HashMap<>();
            }
        }
    }

    private Map<String, PlayerBubbleTheme> tryLoadJsonMap(Path path) {
        try {
            if (!Files.exists(path)) return null;
            if (Files.size(path) == 0) return null;
            String json = Files.readString(path);
            if (json.isBlank()) return null;
            return GSON.fromJson(json, MAP_TYPE);
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
            String json = GSON.toJson(playerThemes);
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
                LOGGER.atWarning().log("[BubbleChat] Failed to save themes: %s", e2.getMessage());
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to save themes: %s", e.getMessage());
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
