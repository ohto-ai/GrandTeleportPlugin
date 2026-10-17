# GrandTeleport

Cinematic teleport transitions for Paper 1.20.6 servers, with an optional Fabric client mod.

Instead of snapping a player to their destination, GrandTeleport intercepts the teleport, lifts the camera above the world, travels across the map, and descends at the destination — with configurable sounds, particles, titles, and player freezing. The server plugin works on its own; players who install the companion Fabric mod get a smooth, client-side camera animation.

## Features

- Intercepts `/tp` / `/teleport` and all `PlayerTeleportEvent` teleports, including TPA, warp, home, spawn, back, and other plugin teleports
- Camera transition phases: pull up, hold, travel, push down
- Optional Fabric client mod for smooth camera control
- Server-side fallback for players without the mod (can be enabled/disabled)
- Configurable timings, peak altitude, sounds, particles, titles, and movement freezing
- Command whitelist / blacklist filtering
- Global and per-player toggles
- Admin status, reload, and test commands

## Requirements

| Component | Requirement |
|---|---|
| Server | Paper 1.20.6, Java 21 |
| Client mod (optional) | Minecraft 1.20.5+, Fabric Loader, Fabric API, Java 21 |

## Build

### Plugin

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/GrandTeleportPlugin-<version>.jar`.

### Client mod

The client mod is a separate Gradle project:

```bash
cd grand-teleport-client
./gradlew build
```

The mod JAR is written to `grand-teleport-client/build/libs/`.

## Installation

1. Put the plugin JAR into the server's `plugins/` directory.
2. (Optional) Put the client mod JAR into each player's `mods/` directory.
3. Start the server once to generate `plugins/GrandTeleportPlugin/config.yml`.
4. Edit the configuration and restart, or use `/gtp reload`.

## Commands

Main command: `/grandtp` (alias `/gtp`).

| Command | Description | Permission |
|---|---|---|
| `/gtp on` | Enable effects globally | `grandteleport.admin` |
| `/gtp off` | Disable effects globally | `grandteleport.admin` |
| `/gtp default <on\|off>` | Set the default for new players | `grandteleport.admin` |
| `/gtp status [player]` | Show global / player status | `grandteleport.admin` |
| `/gtp reload` | Reload the configuration | `grandteleport.admin` |
| `/gtp toggle <player>` | Toggle effects for one player | `grandteleport.admin` |
| `/gtp clear <player>` | Reset a player to the default | `grandteleport.admin` |
| `/gtp test mod <command>` | Test a camera transition | `grandteleport.admin` |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `grandteleport.use` | `true` | Receive cinematic teleport effects |
| `grandteleport.self` | `true` | Toggle own effects with `/gtp toggle` |
| `grandteleport.admin` | `op` | Admin commands |
| `grandteleport.bypass` | `false` | Skip all cinematic effects |

## Configuration

The generated `config.yml` is heavily commented. Key options include:

- `enabled` / `default_enabled` — global and default toggles
- `mod.enabled` — enable the optional client-mod channel
- `commands` — command patterns to intercept (`coordinates` or `passthrough`)
- `filter_mode` / `filter_patterns` — `whitelist`, `blacklist`, or `none`
- `fallback.enabled` — server-side animation for clients without the mod
- `camera.*` — peak altitude and phase durations (in ticks)
- `sounds.*`, `particles.*`, `titles.*` — audiovisual effects
- `freeze.*` — block movement, damage, and interaction during a transition

## How it works

The plugin registers plugin-messaging channels (`grandteleport:*`) and performs a handshake with the optional client mod. When a teleport is intercepted, the server computes a camera path and either drives the client mod or uses the server-side fallback. The actual teleport is deferred until the camera transition reaches the destination.

## Project layout

```text
src/main/java/com/ohtoai/grandteleport/   Paper plugin
grand-teleport-client/                    Fabric client mod
  src/main/java/dev/ohtoai/grandteleportclient/
```

## License

No root license file is currently included. The companion client mod declares MIT in its `fabric.mod.json`.
