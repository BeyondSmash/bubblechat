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
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Per-NPC bubble color, keyed by HyCitizens {@code CitizenData.getId()}.
 * Stored as a single JSON file at {dataDir}/npc-colors.json with atomic, crash-safe save
 * (.tmp -> backup .bak -> ATOMIC_MOVE) and .bak recovery on load.
 */
public class NpcBubbleColorStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, NpcColor>>(){}.getType();

    /** One NPC's bubble color preference. */
    public static final class NpcColor {
        public String hex;          // "#RRGGBB"
        public boolean lightMode;
        public NpcColor() {}
        public NpcColor(String hex, boolean lightMode) { this.hex = hex; this.lightMode = lightMode; }
    }

    private final Path file;
    private final Path tmp;
    private final Path bak;
    private final Map<String, NpcColor> colors = new ConcurrentHashMap<>();

    public NpcBubbleColorStorage(@Nonnull Path dataDir) {
        this.file = dataDir.resolve("npc-colors.json");
        this.tmp = dataDir.resolve("npc-colors.json.tmp");
        this.bak = dataDir.resolve("npc-colors.json.bak");
        load();
    }

    private void load() {
        Map<String, NpcColor> loaded = tryLoad(file);
        if (loaded == null) {
            loaded = tryLoad(bak);
            if (loaded != null) LOGGER.atWarning().log("[BubbleChat] Recovered NPC colors from backup!");
        }
        if (loaded != null) colors.putAll(loaded);
    }

    private Map<String, NpcColor> tryLoad(Path p) {
        try {
            if (!Files.exists(p) || Files.size(p) == 0) return null;
            String json = Files.readString(p);
            if (json.isBlank()) return null;
            return GSON.fromJson(json, MAP_TYPE);
        } catch (Exception e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to read %s: %s", p.getFileName(), e.getMessage());
            return null;
        }
    }

    public NpcColor get(String npcId) { return npcId == null ? null : colors.get(npcId); }

    @Nonnull
    public Map<String, NpcColor> all() { return colors; }

    public void put(@Nonnull String npcId, @Nonnull String hex, boolean lightMode) {
        colors.put(npcId, new NpcColor(hex, lightMode));
    }

    public void remove(@Nonnull String npcId) { colors.remove(npcId); }

    public void saveAsync(@Nonnull ScheduledExecutorService scheduler) {
        if (!scheduler.isShutdown()) scheduler.execute(this::save);
        else save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(colors, MAP_TYPE);
            Files.writeString(tmp, json);
            if (Files.exists(file) && Files.size(file) > 0) {
                Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, file,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOGGER.atWarning().log("[BubbleChat] Failed to save NPC colors: %s", e2.getMessage());
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to save NPC colors: %s", e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}
