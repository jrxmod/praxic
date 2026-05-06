<div align="center">

# PRAXIC

**Protection Runtime Analysis & eXploit Interception Core**

Server-side anticheat mod for Minecraft Fabric 1.21.1.
No client installation required — works with vanilla, Fabric, and any other clients.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.9-white.svg)](https://fabricmc.net)

</div>

---

## Features

- **FlyCheck** — Detects illegal flight in survival mode
- **SpeedCheck** — Detects movement speed hacks with lag spike protection
- **NoFallCheck** — Detects fall damage cancellation exploits
- **ReachCheck** — Detects attacks beyond allowed distance
- **Configurable** — JSON config with per-check settings, thresholds and actions
- **Modular** — Enable or disable each check independently
- **Lightweight** — Minimal performance impact on server

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/user/jrxmod)
2. Place the `.jar` file into your server's `mods/` folder
3. Start the server — config will be generated at `config/praxic.json`

## Commands

All commands require **operator level 2** or higher.

| Command | Description |
|---------|-------------|
| `/praxic status` | Show status of all check modules |
| `/praxic check <player>` | Show violations for a specific player |
| `/praxic violations` | Show violations for all online players |
| `/praxic reload` | Reload configuration file |

## Configuration

The config file is located at `config/praxic.json` and is auto-generated on first launch. Each check module has its own toggle, violation threshold and action (`warn` or `kick`).

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.9+
- Fabric API 0.116.11+1.21.1+
- Java 21+

## License

Copyright 2026 jrxmod. Licensed under [Apache 2.0](LICENSE).
