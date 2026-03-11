[![Discord](https://img.shields.io/discord/1466767155893633158?label=Join-Discord&logo=discord&logoColor=E1B154&labelColor=23272a&color=E1B154&style=for-the-badge)](https://discord.gg/h3zWEH3rzk)

***

# BubbleChat

Smooth, animated speech bubbles above players when they chat. Word-by-word reveal, auto-sizing, multi-line wrapping, page indicators, mouth animations, custom colors, private RP channels, and a full per-player settings GUI.

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

### Animalese Voice

A charming Animal Crossing-inspired voice synthesis system that plays alongside speech bubbles:

*   **Phoneme-mapped audio** — each letter triggers a pitched instrument note based on its phoneme group, creating a unique "voice" per message
*   **8 voice types** — randomly assigned per player session, each with a distinct pitch range
*   **Per-player volume control** — adjustable from 0% to 200% (default 100%)
*   **Proximity-based falloff** — volume decreases naturally with distance from the speaker
*   **Per-player mute** — mute individual players' animalese audio without hiding their bubbles
*   **Global toggle** — enable or disable animalese entirely from the Voice Settings page

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

### RP Channels

Private roleplay channels for in-character chat that only channel members can see:

*   **PIN-based channels** — create or join channels with a shared PIN code
*   **3 channel slots** — save up to 3 channels and switch between them
*   **Chat prefixes** — type `rp1`, `rp2`, or `rp3` before your message to send to that channel, or `pbc` to force public
*   **Dual visibility** — optionally show your bubble to everyone (public + channel) while sending \[RP\] text to channel members
*   **Switch confirmation** — optional confirmation prompt before switching channels via prefix
*   **Per-channel bubble colors** — distinct tint colors for each channel slot, customizable per viewer
*   **RP cull distance** — separate cull distance for channel bubbles (default 10M)
*   **Yell ranges** — separate range settings for yell bubble visibility (default 50M) and yell particle visibility (default 75M)

### Visibility Control

*   **Enable/Disable toggle** — turn speech bubbles on or off entirely
*   **Self-visible toggle** — show or hide your own speech bubble
*   **Hide players** — permanently hide bubbles from specific players
*   **Mute players** — temporarily mute with preset durations (5 min to 24 hours, auto-expires)
*   **Animalese mute** — mute specific players' voice audio while still seeing their bubbles
*   **Cull distance** — adjustable from 10M to 200M (default 20M)
*   **Max bubble count** — limit simultaneous bubbles from 1 to 50 (default 10)

***

## Settings GUI

Open with `/bchat` for the full settings page:

*   **Enable** — On/Off toggle for speech bubbles
*   **Mode** — Light/Dark toggle
*   **Self Visible** — On/Off toggle
*   **Bubble Color** — Color picker with live preview swatch, HSV/RGB/Hex readouts, and Default button
*   **Cull Distance** — Dropdown (10-200M)
*   **Max Bubbles** — Dropdown (1-50)
*   **Expressions** — On/Off toggle for mouth animations
*   **Player Colors** — Sub-page for global and per-player color overrides
*   **Channels** — Sub-page for RP channel management, dual visibility, cull distances, and yell ranges
*   **Hidden & Muted** — Sub-page for managing hidden, muted, and animalese-muted players
*   **Voice** — Sub-page for animalese toggle, volume, and preview
*   **Reset All** — Restore all settings to defaults
*   **Undo / Redo** — Full action history (up to 50 steps)

***

## Commands

| Command                                             | Description                         |
| --------------------------------------------------- | ----------------------------------- |
| <code>/bchat</code>                                 | Open the settings GUI               |
| <code>/bchat toggle</code>                          | Toggle speech bubbles on/off        |
| <code>/bchat self on\|off</code>                    | Show/hide your own bubble           |
| <code>/bchat clear</code>                           | Dismiss your current speech bubble  |
| <code>/bchat theme</code>                           | Open the settings GUI               |
| <code>/bchat theme light\|dark</code>               | Set bubble mode via command         |
| <code>/bchat theme color #RRGGBB\|reset</code>     | Set bubble tint color via command   |
| <code>/bchat pc</code>                              | Open Player Colors page             |
| <code>/bchat ch</code>                              | Open Channels page                  |
| <code>/bchat hm</code>                              | Open Hidden & Muted page            |
| <code>/bchat vc</code>                              | Open Voice Settings page            |
| <code>/bchat vc on\|off</code>                      | Toggle animalese on/off             |
| <code>/bchat status</code>                          | Show current settings               |
| <code>/bchat help</code>                            | Show command list                   |

### Chat Prefixes

| Prefix                    | Description                                          |
| ------------------------- | ---------------------------------------------------- |
| <code>rp1 message</code>  | Send to channel in slot 1                            |
| <code>rp2 message</code>  | Send to channel in slot 2                            |
| <code>rp3 message</code>  | Send to channel in slot 3                            |
| <code>pbc message</code>  | Force public chat (bypass active channel)            |

***

## Compatibility

*   **BusyBubble** — Full bidirectional priority: speech bubbles clear any active thinking bubble on send, and thinking bubbles won't spawn while a speech bubble is active. When a UI opens, any active speech bubble is also cleared.

***

## Installation

1.  Drop `BubbleChat-1.5.0.jar` into your `Mods` folder
2.  All player settings are per-player via the in-game GUI — no setup required

### Server Configuration (Optional)

On first run, BubbleChat creates a `bubblechat-config.json` in the plugin data directory (`Saves/<WorldName>/plugindata/BeyondSmash/BubbleChat/`). Server hosts can edit this to customize server-wide defaults:

```json
{
  "defaultBubbleColor": null,
  "rpChannelsEnabled": true,
  "animaleseEnabled": true
}
```

| Setting              | Description                                                                                       |
| -------------------- | ------------------------------------------------------------------------------------------------- |
| `defaultBubbleColor` | Default bubble tint for players who haven't chosen their own (`"#RRGGBB"` or `null` for no tint) |
| `rpChannelsEnabled`  | Enable or disable RP/private channels server-wide                                                 |
| `animaleseEnabled`   | Enable or disable animalese voice sounds server-wide                                              |

***

## Known Issues

### `[AssetUpdate]` messages on connect

When joining a server with BubbleChat, you may see 2 `[AssetUpdate]` log messages in chat:

```
[AssetUpdate] ParticleSpawners: Starting AddOrUpdate
[AssetUpdate] ParticleSystems: Starting AddOrUpdate
```

**This is a Hytale client behavior, not a bug.** The client logs these messages whenever a plugin sends custom particle definitions after the initial load phase. BubbleChat needs to send its custom bubble particle configs to your client so the speech bubbles can render — there is no way to suppress these messages from the server side.

We've already minimized this from 11 messages down to 2 by batching all particle configs into a single packet. The remaining 2 messages are the minimum achievable without Hytale client changes. This will likely be resolved in a future Hytale update as the game matures past Early Access.

### Head rotation animation

Currently, BubbleChat cannot add a subtle "talking" head bob animation while a player speaks. Hytale's animation system uses a Face slot that completely overrides camera-driven head rotation — there is no additive blending mode to layer a small animation on top. This will be revisited if Hytale adds animation blending support in a future update.

### Voice chat proximity indicator

There is no server-side API to detect whether a player is in a Hytale voice chat. The native voice system is handled entirely by the C# client. A "voice active" indicator above players would require client-side API access that doesn't currently exist.

### Font customization

BubbleChat's text is rendered using the vanilla Nameplate element (the same system that displays player usernames above their heads). This means the font, text color, and text styling are controlled entirely by Hytale's built-in nameplate renderer — there is currently no server-side API to change these properties.

My hope is that Hypixel adds more support for Nameplate customization (text color, font changes, styling, etc.) in a future update. Once that API exists, I'll update BubbleChat to support those features. Additionally, the text currently uses a virtual entity positioned by the server, whereas the bubble background uses entity-attached particles that track the player at client frame rate. Ideally the text could also be entity-attached for the smoothest possible tracking, but that isn't available for nameplates yet.

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
*   **WalnutOwl** — Testing & feedback
*   **Joey475574** — Testing & feedback
*   **AZB** — Testing & feedback