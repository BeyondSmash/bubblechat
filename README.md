[![Discord](https://img.shields.io/discord/1466767155893633158?label=Join-Discord&logo=discord&logoColor=E1B154&labelColor=23272a&color=E1B154&style=for-the-badge)](https://discord.gg/h3zWEH3rzk)

***

# BubbleChat

Smooth, animated speech bubbles above players when they chat. Word-by-word reveal, auto-sizing, multi-line wrapping, page indicators, mouth animations, custom colors, and a full per-player settings GUI.

***

## Features

### Speech Bubbles

*   **Particle-based bubbles** that follow the player smoothly at client frame rate via entity-attached particles
*   **Word-by-word reveal** animation with natural pacing and punctuation pauses
*   **Auto-sizing** across 15 width tiers — bubbles grow and shrink to fit the message
*   **Multi-line wrapping** (1-line, 2-line, and 3-line bubbles) with weighted character width for proper visual fit
*   **Page system** for long messages — splits into up to 3 pages with dot indicators showing current position
*   **Smooth fade-out** animation when the bubble disappears

### Mouth Animations (Visemes)

Custom phoneme-based lip-sync animations play on the player model while the bubble is active. Each word triggers a mouth shape based on its starting letter, mapped to 16 distinct mouth positions:

![Viseme Mapping Table](https://i.imgur.com/eTEPvEk.png)

![Mouth Sprite Sheet](https://i.imgur.com/6jfOvC8.png)

The mouth animation system uses a custom sprite sheet with 16 phoneme positions (Rest, A, O, E/I/U/Y, S/T/D, N, L, R, F/V, H, W, J, K/G, B, M, P) animated via `blockyanim` files. Each position is registered as its own animation group so transitions between mouth shapes are smooth and immediate.

### Yell Effect

When a message contains ALL CAPS words (2+ letters) or words ending with `!`, a scream particle effect plays alongside the bubble for emphasis.

### Appearance

*   **Dark and Light mode** bubble backgrounds with per-player toggle
*   **Custom bubble colors** via a full color picker with live HSV/RGB/Hex readouts
*   **Contrast protection** (light mode) — automatically clamps overly-bright colors so white text stays readable, with a WCAG contrast indicator
*   **Dark mode brightness constraint** — colors darkened to minimum 15% brightness to distinguish from the bubble frame outline

### Player Color Overrides

Control what colors you see on other players' bubbles, independent of their own settings:

*   **All Players override** — force every player's bubble to one color
*   **Per-player overrides** — set specific colors for individual players
*   **Priority order**: All Players override > Per-player override > Speaker's own color

### Visibility Control

*   **Self-visible toggle** — show or hide your own speech bubble
*   **Hide players** — permanently hide bubbles from specific players
*   **Mute players** — temporarily mute with preset durations (5 min to 24 hours, auto-expires)
*   **Cull distance** — adjustable from 10M to 200M (default 20M)
*   **Max bubble count** — limit simultaneous bubbles from 1 to 50 (default 10)

***

## Settings GUI

Open with `/bchat theme` for the full settings page:

*   **Mode** — Light/Dark toggle
*   **Self Visible** — On/Off toggle
*   **Bubble Color** — Color picker with live preview swatch, HSV/RGB/Hex readouts, and Default button
*   **Cull Distance** — Dropdown (10-200M)
*   **Max Bubbles** — Dropdown (1-50)
*   **Expressions** — On/Off toggle for mouth animations
*   **Yell Effect** — On/Off toggle
*   **Player Colors** — Sub-page for global and per-player color overrides
*   **Hidden / Muted** — Sub-page for managing hidden and muted players
*   **Reset All** — Restore all settings to defaults
*   **Undo / Redo** — Full action history (up to 50 steps)

***

## Commands

| Command                                                  |Description                        |
| -------------------------------------------------------- |---------------------------------- |
| <code>/bchat</code>                                      |Toggle speech bubbles on/off       |
| <code>/bchat self on|off</code>                          |Show/hide your own bubble          |
| <code>/bchat clear</code>                                |Dismiss your current speech bubble |
| <code>/bchat theme</code>                                |Open the settings GUI              |
| <code>/bchat theme light|dark</code>                     |Set bubble mode via command        |
| <code>/bchat theme color &amp;amp;amp;lt;#RRGGBB|reset&amp;amp;amp;gt;</code> |Set bubble tint color via command  |
| <code>/bchat status</code>                               |Show current settings              |
| <code>/bchat help</code>                                 |Show command list                  |

***

## Compatibility

*   **BusyBubble** — Speech bubbles automatically clear when a thinking/busy UI opens, preventing visual overlap.

***

## Installation

1.  Drop `BubbleChat-1.0.1.jar` into your `Mods` folder
2.  No configuration needed — all settings are per-player via the in-game GUI

***

## API — Plugin Integration

Other plugins can trigger speech bubbles programmatically using the BubbleChat API.

**Your manifest.json:**
```json
"Dependencies": { "BeyondSmash:BubbleChat": "*" }
```

**Usage:**
```java
import com.bubblechat.BubbleChatAPI;

// Show a speech bubble above a player
BubbleChatAPI.showBubble(playerRef, "Hello world!");

// Clear/dismiss a player's active bubble
BubbleChatAPI.clearBubble(playerRef);

// Check if BubbleChat is loaded and ready
BubbleChatAPI.isReady();

// Check if a player has bubbles enabled
BubbleChatAPI.isEnabled(playerRef);

// Check if a player currently has an active bubble
BubbleChatAPI.hasActiveBubble(playerRef);
```

Bubbles triggered via the API use the speaker's own theme settings (color, light/dark mode) and respect all viewer-side preferences (color overrides, mute, hide, cull distance).

***

## Credits

*   **EQNOX** — Testing & feedback
*   **BannerWolf** — Testing & feedback
*   **sai3rina** — Testing & feedback
*   **zenkuro** — Testing & feedback
*   **Matt\_97** — Testing & feedback
