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

    // --- Channel fields ---

    /** RP channel PINs per slot (null = empty slot). Max 3 slots. */
    public String[] channelSlots = new String[3];

    /** Currently active channel slot index (0-2). -1 = no active channel (public). */
    public int activeSlot = -1;

    /** When true, also see public bubbles while in a private channel. Default false. */
    public boolean dualVisibility = false;

    /** When true, show confirmation UI before switching channels via prefix. Default true. */
    public boolean switchConfirm = true;

    /** Max distance (blocks) to see bubbles in RP channels. Default 10. */
    public int rpCullDistance = 10;

    /** Channel bubble tint colors (per-viewer). Keys: "public", "rp1", "rp2", "rp3". */
    public Map<String, String> channelColors = new HashMap<>(Map.of(
        "rp1", "#2d3d5a",
        "rp2", "#3d2d5a",
        "rp3", "#2d5a3d"
    ));

    /** Max distance for yell bubble visibility. Default 50. */
    public int yellBubbleRange = 50;

    /** Max distance for yell particle visibility. Default 75. */
    public int yellParticleRange = 75;

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

    public int getEffectiveRpCullDistance() {
        return Math.max(1, Math.min(rpCullDistance, 200));
    }

    public int getEffectiveYellBubbleRange() {
        return Math.max(1, Math.min(yellBubbleRange, 200));
    }

    public int getEffectiveYellParticleRange() {
        return Math.max(1, Math.min(yellParticleRange, 200));
    }

    @Nullable
    public String getActiveChannelPin() {
        if (activeSlot < 0 || activeSlot >= channelSlots.length) return null;
        return channelSlots[activeSlot];
    }

    public int findAvailableSlot() {
        for (int i = 0; i < channelSlots.length; i++) {
            if (channelSlots[i] == null) return i;
        }
        return -1;
    }

    @Nullable
    public String getChannelColor(String channelKey) {
        return channelColors.get(channelKey);
    }
}
