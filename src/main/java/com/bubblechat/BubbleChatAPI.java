package com.bubblechat;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
/**
 * Public API for other plugins to trigger BubbleChat speech bubbles.
 *
 * <p>Usage from another plugin:</p>
 * <pre>
 *   // Add to your manifest.json Dependencies:
 *   //   "BeyondSmash:BubbleChat": "*"
 *
 *   BubbleChatAPI.showBubble(playerRef, "Hello world!");
 *   BubbleChatAPI.clearBubble(playerRef);
 * </pre>
 */
public final class BubbleChatAPI {

    private static SpeechManager manager;

    private BubbleChatAPI() {}

    static void init(@Nonnull SpeechManager speechManager) {
        manager = speechManager;
    }

    /**
     * Show a speech bubble above a player with the default color.
     * Replaces any existing bubble on this player.
     *
     * @param playerRef the player to show the bubble above
     * @param text      the message text
     * @return true if the bubble was triggered, false if BubbleChat is not ready
     */
    public static boolean showBubble(@Nonnull PlayerRef playerRef, @Nonnull String text) {
        if (manager == null) return false;
        manager.onChat(playerRef, text);
        return true;
    }

    /**
     * Clear/dismiss the active speech bubble for a player.
     *
     * @param playerRef the player whose bubble to clear
     * @return true if cleared, false if BubbleChat is not ready
     */
    public static boolean clearBubble(@Nonnull PlayerRef playerRef) {
        if (manager == null) return false;
        manager.clearSpeech(playerRef.getUuid());
        return true;
    }

    /**
     * Check if BubbleChat is initialized and ready to accept API calls.
     */
    public static boolean isReady() {
        return manager != null;
    }

    /**
     * Check if a player has speech bubbles enabled (not disabled via /bchat toggle).
     *
     * @param playerRef the player to check
     * @return true if enabled, false if disabled or BubbleChat is not ready
     */
    public static boolean isEnabled(@Nonnull PlayerRef playerRef) {
        if (manager == null) return false;
        return manager.isEnabled(playerRef.getUuid());
    }

    /**
     * Check if a player currently has an active speech bubble.
     *
     * @param playerRef the player to check
     * @return true if they have an active bubble
     */
    public static boolean hasActiveBubble(@Nonnull PlayerRef playerRef) {
        if (manager == null) return false;
        return manager.hasActiveBubble(playerRef.getUuid());
    }

    /**
     * Check if a player currently has an active speech bubble (by UUID).
     * Used by BusyBubble via reflection to avoid spawning while a speech bubble is active.
     */
    public static boolean hasActiveBubble(@Nonnull java.util.UUID playerUuid) {
        if (manager == null) return false;
        return manager.hasActiveBubble(playerUuid);
    }
}
