package com.bubblechat;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-viewer preferences — how THIS player sees OTHER players' bubbles.
 * Stored per-UUID in bubble_prefs/ directory.
 */
public class PlayerBubblePrefs {

    /** Per-player color overrides (lowercased player name → hex "#RRGGBB") */
    public Map<String, String> playerColors = new HashMap<>();

    /** Global color override — all players appear this color (null = use their own) */
    @Nullable
    public String globalColorOverride = null;

    /** Permanently hidden players (lowercased names) */
    public Set<String> hiddenPlayers = new HashSet<>();

    /** Temporarily muted players (lowercased name → expiry epoch ms) */
    public Map<String, Long> mutedPlayers = new HashMap<>();

    /** Max distance (blocks) to see bubbles. Default 20, max 200. */
    public int cullDistance = 20;

    /** Max simultaneous bubbles to render. Default 10, max 50. */
    public int maxBubbleCount = 10;

    /** Whether yell particle plays on ALL CAPS / ! words. Default true. */
    public boolean yellEnabled = true;

    /** Whether mouth viseme animations play during chat. Default true. */
    public boolean visemesEnabled = true;

    // --- Helpers ---

    public boolean isHidden(String lowerName) {
        return hiddenPlayers.contains(lowerName);
    }

    public boolean isMuted(String lowerName) {
        Long expiry = mutedPlayers.get(lowerName);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            mutedPlayers.remove(lowerName);
            return false;
        }
        return true;
    }

    /**
     * Resolve what color to use when this viewer sees the given speaker.
     * Priority: global override > per-player override > null (use speaker's own).
     */
    @Nullable
    public String resolveColorForSpeaker(String lowerName) {
        if (globalColorOverride != null) return globalColorOverride;
        return playerColors.get(lowerName);
    }

    public int getEffectiveCullDistance() {
        return Math.max(1, Math.min(cullDistance, 200));
    }

    public int getEffectiveMaxBubbleCount() {
        return Math.max(1, Math.min(maxBubbleCount, 50));
    }
}
