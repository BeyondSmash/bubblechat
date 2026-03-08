package com.bubblechat;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders composite bubble textures by stamping font atlas characters
 * onto pre-rendered bubble background variants.
 *
 * Font atlas: "Squares Bold Free" — 704×544, 15×15 grid, 46×36px cells, 220 chars, cyan #90F7FF.
 * Bubble variants: BC_Bubble_0 through BC_Bubble_14 (256–2048px wide, 384px tall).
 */
public class CompositeRenderer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Font atlas grid
    private static final int ATLAS_CELL_W = 46;
    private static final int ATLAS_CELL_H = 36;
    private static final int ATLAS_COLS = 15;

    // Rendering parameters (tunable)
    static final float CHAR_SCALE = 2.0f;
    static final int CHAR_PITCH = 32; // horizontal advance per character
    private static final int BODY_Y_START = 63;
    private static final int BODY_Y_END = 298;
    private static final int EDGE_INSET = 40;

    private static final int SCALED_CHAR_H = Math.round(ATLAS_CELL_H * CHAR_SCALE);
    private static final int SCALED_CHAR_W = Math.round(ATLAS_CELL_W * CHAR_SCALE);

    private BufferedImage fontAtlas;
    private final BufferedImage[] bubbleVariants;
    private final Map<Character, int[]> charMap = new HashMap<>();

    private static final int VARIANT_COUNT = 15;

    public CompositeRenderer() {
        this.bubbleVariants = new BufferedImage[VARIANT_COUNT];
    }

    /**
     * Load font atlas and all bubble variant PNGs from JAR classpath.
     * Call once at startup.
     */
    public boolean loadResources() {
        try {
            // Load font atlas
            try (InputStream is = getClass().getResourceAsStream("/Common/Particles/Textures/BC/BC_FontAtlas.png")) {
                if (is == null) {
                    LOGGER.atWarning().log("Font atlas not found in classpath");
                    return false;
                }
                fontAtlas = ImageIO.read(is);
                LOGGER.atInfo().log("Loaded font atlas: %dx%d", fontAtlas.getWidth(), fontAtlas.getHeight());
            }

            // Load bubble variants
            for (int i = 0; i < VARIANT_COUNT; i++) {
                String path = "/Common/Particles/Textures/BC/BC_Bubble_" + i + ".png";
                try (InputStream is = getClass().getResourceAsStream(path)) {
                    if (is == null) {
                        LOGGER.atWarning().log("Bubble variant %d not found: %s", i, path);
                        return false;
                    }
                    bubbleVariants[i] = ImageIO.read(is);
                }
            }
            LOGGER.atInfo().log("Loaded %d bubble variants", VARIANT_COUNT);

            // Build character lookup map
            buildCharMap();
            LOGGER.atInfo().log("Built character map with %d entries", charMap.size());

            return true;
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to load composite resources: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Render text onto a bubble variant background.
     * Returns a new BufferedImage with text baked in, or null on error.
     */
    public BufferedImage renderComposite(String text, int variantIndex) {
        if (fontAtlas == null || variantIndex < 0 || variantIndex >= VARIANT_COUNT) return null;
        BufferedImage bg = bubbleVariants[variantIndex];
        if (bg == null) return null;

        // Create copy of the background
        BufferedImage composite = new BufferedImage(bg.getWidth(), bg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        g.drawImage(bg, 0, 0, null);

        // Calculate text positioning
        int textWidth = text.length() * CHAR_PITCH;
        int availableWidth = bg.getWidth() - 2 * EDGE_INSET;
        int startX = EDGE_INSET + (availableWidth - textWidth) / 2;
        if (startX < EDGE_INSET) startX = EDGE_INSET; // clamp

        int bodyHeight = BODY_Y_END - BODY_Y_START;
        int startY = BODY_Y_START + (bodyHeight - SCALED_CHAR_H) / 2;

        // Stamp each character from the atlas
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int[] rc = charMap.get(c);
            if (rc == null) {
                // Unknown character — skip (space or unsupported)
                continue;
            }

            int srcX = rc[1] * ATLAS_CELL_W;
            int srcY = rc[0] * ATLAS_CELL_H;
            int destX = startX + i * CHAR_PITCH;
            int destY = startY;

            // Draw scaled character cell onto composite
            g.drawImage(fontAtlas,
                destX, destY, destX + SCALED_CHAR_W, destY + SCALED_CHAR_H,
                srcX, srcY, srcX + ATLAS_CELL_W, srcY + ATLAS_CELL_H,
                null);
        }

        g.dispose();
        return composite;
    }

    /**
     * Write composite image to disk as PNG.
     */
    public boolean writeComposite(BufferedImage img, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            ImageIO.write(img, "png", outputPath.toFile());
            return true;
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to write composite to %s: %s", outputPath, e.getMessage());
            return false;
        }
    }

    /**
     * Get the width/height of a bubble variant (for spawner frameSize).
     */
    public int getVariantWidth(int variantIndex) {
        if (variantIndex < 0 || variantIndex >= VARIANT_COUNT || bubbleVariants[variantIndex] == null) return 256;
        return bubbleVariants[variantIndex].getWidth();
    }

    public int getVariantHeight(int variantIndex) {
        if (variantIndex < 0 || variantIndex >= VARIANT_COUNT || bubbleVariants[variantIndex] == null) return 384;
        return bubbleVariants[variantIndex].getHeight();
    }

    public boolean isLoaded() {
        return fontAtlas != null;
    }

    /**
     * Build the character lookup map.
     * Maps char → {row, col} in the atlas grid.
     * Data sourced from Squares Bold Free_90f7ff.json.
     */
    private void buildCharMap() {
        // ASCII printable range (space through ~) — codepoints 32-126
        // Atlas layout: chars are sequentially placed left-to-right, top-to-bottom
        // Row 0: space ! " # $ % & ' ( ) * + , - .
        // Row 1: / 0 1 2 3 4 5 6 7 8 9 : ; < =
        // Row 2: > ? @ A B C D E F G H I J K L
        // Row 3: M N O P Q R S T U V W X Y Z [
        // Row 4: \ ] ^ _ ` a b c d e f g h i j
        // Row 5: k l m n o p q r s t u v w x y
        // Row 6: z { | } ~ ...extended chars

        int index = 0;
        // Codepoints 32-126 (standard ASCII printable)
        for (int cp = 32; cp <= 126; cp++) {
            int row = index / ATLAS_COLS;
            int col = index % ATLAS_COLS;
            charMap.put((char) cp, new int[]{row, col});
            index++;
        }

        // Extended characters from the atlas (codepoints 164+)
        // We only need ASCII for chat, but include a few extras
        int[] extendedCodepoints = {
            164, 166, 167, 169, 171, 172, 174, 176, 177, 181,  // ¤¦§©«¬®°±µ
            182, 183, 187,                                        // ¶·»
            // Cyrillic and other extended chars (1025-1169, 8211-8482)
            // Skip these for now — only ASCII needed for chat
        };
        for (int cp : extendedCodepoints) {
            int row = index / ATLAS_COLS;
            int col = index % ATLAS_COLS;
            charMap.put((char) cp, new int[]{row, col});
            index++;
        }
    }
}
