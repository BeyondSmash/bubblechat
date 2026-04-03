package com.bubblechat;

import com.hypixel.hytale.protocol.Color;

import javax.annotation.Nullable;

public class PlayerBubbleTheme {

    public boolean lightMode = false;

    @Nullable
    public String tintColorHex = null;

    public boolean selfVisible = false;

    /** When true, all words appear at once instead of word-by-word. Default false. */
    public boolean instantText = false;

    // Animalese voice settings (speaker-side)
    public boolean animalese = true;       // on by default
    public int voiceType = 5;              // 0-7 (Voice 6 - Mellow)
    public float pitch = 1.0f;            // UI pitch 0.5-2.0 (actual = stored * 1.5)

    /**
     * Parse tintColorHex into a protocol Color for ModelParticle override.
     * Returns null if no custom color is set (use spawner default).
     * Accepts "#RRGGBB" or "RRGGBB" format.
     */
    @Nullable
    public Color toProtocolColor() {
        String raw = tintColorHex;
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            if (hex.length() != 6) return null;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color((byte) r, (byte) g, (byte) b);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
