package com.bubblechat;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

public class SpeechState {

    private final UUID playerUuid;
    private final String speakerLowerName;
    private final String[] words;
    private final World world;
    private int currentWordIndex;
    private int playerNetworkId;
    private int virtualEntityNetId;
    private Vector3d lastPosition;
    private long generation;
    private long startTimeMs;
    private volatile int tileCount;
    private int pageStartIndex;
    private boolean continuationPage;
    private int currentPage;
    private int totalPages;
    private int lastEffectiveWordIndex; // for superpage cap truncation

    // Model height adjustment (for custom NPC models)
    private double heightAdjust = 0.0;

    // Cached viewer list for scheduler-thread particle sends (avoids world.execute latency)
    private volatile java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> cachedViewers;

    // Multi-line support
    private int lineCount = 1;
    private int line2NetId = -1;
    private int line3NetId = -1;
    private int pageIndicatorNetId = -1;
    private int line1EndWordIndex = Integer.MAX_VALUE;
    private int line2EndWordIndex = Integer.MAX_VALUE;

    public SpeechState(UUID playerUuid, String speakerLowerName, String[] words, long generation, World world) {
        this.playerUuid = playerUuid;
        this.speakerLowerName = speakerLowerName;
        this.words = words;
        this.world = world;
        this.currentWordIndex = 0;
        this.pageStartIndex = 0;
        this.playerNetworkId = -1;
        this.virtualEntityNetId = -1;
        this.generation = generation;
        this.startTimeMs = System.currentTimeMillis();
        this.currentPage = 1;
        this.totalPages = 1;
        this.lastEffectiveWordIndex = words.length - 1;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getSpeakerLowerName() { return speakerLowerName; }
    public World getWorld() { return world; }
    public String[] getWords() { return words; }
    public int getCurrentWordIndex() { return currentWordIndex; }
    public void setCurrentWordIndex(int idx) { this.currentWordIndex = idx; }
    public int getPlayerNetworkId() { return playerNetworkId; }
    public void setPlayerNetworkId(int id) { this.playerNetworkId = id; }
    public int getVirtualEntityNetId() { return virtualEntityNetId; }
    public void setVirtualEntityNetId(int id) { this.virtualEntityNetId = id; }
    public Vector3d getLastPosition() { return lastPosition; }
    public void setLastPosition(Vector3d pos) { this.lastPosition = pos; }
    public long getGeneration() { return generation; }
    public long getStartTimeMs() { return startTimeMs; }
    public int getTileCount() { return tileCount; }
    public void setTileCount(int count) { this.tileCount = count; }
    public int getPageStartIndex() { return pageStartIndex; }
    public void setPageStartIndex(int idx) { this.pageStartIndex = idx; }
    public boolean isContinuationPage() { return continuationPage; }
    public void setContinuationPage(boolean v) { this.continuationPage = v; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int page) { this.currentPage = page; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int pages) { this.totalPages = pages; }
    public int getLastEffectiveWordIndex() { return lastEffectiveWordIndex; }
    public void setLastEffectiveWordIndex(int idx) { this.lastEffectiveWordIndex = idx; }

    public double getHeightAdjust() { return heightAdjust; }
    public void setHeightAdjust(double adj) { this.heightAdjust = adj; }

    public int getLineCount() { return lineCount; }
    public void setLineCount(int n) { this.lineCount = n; }
    public int getLine2NetId() { return line2NetId; }
    public void setLine2NetId(int id) { this.line2NetId = id; }
    public int getLine3NetId() { return line3NetId; }
    public void setLine3NetId(int id) { this.line3NetId = id; }
    public int getPageIndicatorNetId() { return pageIndicatorNetId; }
    public void setPageIndicatorNetId(int id) { this.pageIndicatorNetId = id; }
    public int getLine1EndWordIndex() { return line1EndWordIndex; }
    public void setLine1EndWordIndex(int idx) { this.line1EndWordIndex = idx; }
    public int getLine2EndWordIndex() { return line2EndWordIndex; }
    public void setLine2EndWordIndex(int idx) { this.line2EndWordIndex = idx; }

    public java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> getCachedViewers() { return cachedViewers; }
    public void setCachedViewers(java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> viewers) { this.cachedViewers = viewers; }

    public String buildLine1Text() {
        int end = Math.min(currentWordIndex, line1EndWordIndex);
        StringBuilder sb = new StringBuilder();
        for (int i = pageStartIndex; i <= end && i < words.length; i++) {
            if (i > pageStartIndex) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public String buildLine2Text() {
        if (lineCount < 2 || currentWordIndex <= line1EndWordIndex) return "";
        int start = line1EndWordIndex + 1;
        int end = Math.min(currentWordIndex, line2EndWordIndex);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end && i < words.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public String buildLine3Text() {
        if (lineCount < 3 || currentWordIndex <= line2EndWordIndex) return "";
        int start = line2EndWordIndex + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= currentWordIndex && i < words.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public String getBubbleVariant() {
        return "BC_Bubble_" + tileCount;
    }

    public String buildDisplayText() {
        StringBuilder sb = new StringBuilder();
        for (int i = pageStartIndex; i <= currentWordIndex && i < words.length; i++) {
            if (i > pageStartIndex) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public boolean isComplete() {
        return currentWordIndex >= lastEffectiveWordIndex;
    }
}
