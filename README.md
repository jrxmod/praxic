# ⚡ PRAXIC

> **Protection Runtime Analysis & eXploit Interception Core**

![Version](https://img.shields.io/badge/version-0.3.0-orange) ![Loader](https://img.shields.io/badge/loader-fabric-blue) ![Minecraft](https://img.shields.io/badge/minecraft-1.21.1-brightgreen) ![License](https://img.shields.io/badge/license-Apache--2.0-red) 

Keep your server clean. **PRAXIC** is a lightweight server-side anticheat that silently watches every player. No config bloat, no false flags — just clean, fast detection.

**Works completely server-side.** Your players install nothing. 🌐 **Website:** [jrxmod.netlify.app](https://jrxmod.netlify.app)

---

## 🛡️ What it catches

| Module | What it stops |
|---|---|
| ⚡ **FlyCheck** | Flying in survival without permission |
| 🏃 **SpeedCheck** | Moving faster than physically possible |
| 🪂 **NoFallCheck** | Taking zero fall damage from any height |
| 🗡️ **ReachCheck** | Hitting players/mobs from across the map |
| 🌀 **KillAuraCheck** | Attacking targets without looking or too fast |
| 🧱 **ScaffoldCheck** | Bridging at inhuman speed |
| 🧿 **AutoTotemCheck** | Re-equipping totems in milliseconds |
| 🎒 **InventoryCheck** | Moving items faster than any human can |

---

## ⚙️ Configurable Actions

Every check has three configurable responses in `praxic.json`:

- `warn` — alert the player and log the violation.
- `kick` — remove them from the server instantly.
- `ban` — permanent ban (uses vanilla ban list).

---

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/) for **1.21.1**
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `praxic-0.1.1.jar` into your server's `mods/` folder
4. Start the server — config generates at `config/praxic.json`
5. Use `/praxic reload` to apply changes without restarting.

---

## ⌨️ Commands

| Command | Description |
|---|---|
| `/praxic status` | See every module status at a glance |
| `/praxic check <player>` | Dig into a specific player's record |
| `/praxic violations` | Full server violation overview |
| `/praxic reset <player>` | Wipe someone's record clean |
| `/praxic reload` | Hot-reload your config instantly |

*All commands require OP level 2.*

---

## 📜 License

Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.

Copyright 2026 jrxmod.
