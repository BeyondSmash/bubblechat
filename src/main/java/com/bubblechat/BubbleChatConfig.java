package com.bubblechat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server-host configuration. Loaded from bubblechat-config.json in the plugin data directory.
 * If the file doesn't exist, a default one is created for the host to edit.
 */
public class BubbleChatConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FILE_NAME = "bubblechat-config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    // --- Configurable fields ---

    /** Default bubble tint color for all players who haven't chosen their own. Null = no tint (vanilla). */
    @Nullable
    public String defaultBubbleColor = null;

    /** Whether RP/private channels are enabled on this server. */
    public boolean rpChannelsEnabled = true;

    /** Whether Animalese voice sounds are enabled on this server. */
    public boolean animaleseEnabled = true;

    // --- Load/Save ---

    private transient Path configPath;

    public static BubbleChatConfig load(Path dataDir) {
        Path path = dataDir.resolve(FILE_NAME);
        BubbleChatConfig config;

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                if (json != null && !json.isBlank()) {
                    config = GSON.fromJson(json, BubbleChatConfig.class);
                    if (config == null) config = new BubbleChatConfig();
                    config.configPath = path;
                    LOGGER.atInfo().log("[BubbleChat] Server config loaded (defaultColor=%s, rpChannels=%s, animalese=%s)",
                        config.defaultBubbleColor, config.rpChannelsEnabled, config.animaleseEnabled);
                    // Re-save so any newly added fields appear in the JSON for the server owner
                    config.save();
                    return config;
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("[BubbleChat] Failed to read config: %s", e.getMessage());
            }

            // Try backup
            Path bak = path.resolveSibling(FILE_NAME + ".bak");
            if (Files.exists(bak)) {
                try {
                    String json = Files.readString(bak);
                    if (json != null && !json.isBlank()) {
                        config = GSON.fromJson(json, BubbleChatConfig.class);
                        if (config != null) {
                            config.configPath = path;
                            LOGGER.atWarning().log("[BubbleChat] Recovered server config from backup");
                            config.save();
                            return config;
                        }
                    }
                } catch (Exception e2) {
                    LOGGER.atWarning().log("[BubbleChat] Backup config also failed: %s", e2.getMessage());
                }
            }
        }

        // Create default config
        config = new BubbleChatConfig();
        config.configPath = path;
        config.save();
        LOGGER.atInfo().log("[BubbleChat] Created default bubblechat-config.json");
        return config;
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Path tmp = configPath.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, GSON.toJson(this));
            if (Files.exists(configPath)) {
                Files.copy(configPath, configPath.resolveSibling(FILE_NAME + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.atWarning().log("[BubbleChat] Failed to save config: %s", e.getMessage());
        }
    }
}
