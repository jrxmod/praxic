# ⚡ PRAXIC

> **Protection Runtime Analysis & eXploit Interception Core**

![Version](https://img.shields.io/badge/version-0.4.0-orange) ![Loader](https://img.shields.io/badge/loader-fabric-blue) ![Minecraft](https://img.shields.io/badge/minecraft-1.21.1-brightgreen) ![License](https://img.shields.io/badge/license-Apache--2.0-red)

Keep your server clean. **PRAXIC** is a lightweight server-side anticheat that silently watches every player. No config bloat, no false flags — just clean, fast detection.

**Works completely server-side.** Your players install nothing.

🌐 **Website:** [jrxmod.netlify.app](https://jrxmod.netlify.app) · 📦 **Modrinth:** [modrinth.com/mod/praxic](https://modrinth.com/mod/praxic) · 🔥 **CurseForge:** [curseforge.com/minecraft/mc-mods/praxic-anticheat](https://www.curseforge.com/minecraft/mc-mods/praxic-anticheat)

---

## 🛡️ What it catches

### Movement & Combat
| Module | What it stops |
|---|---|
| ⚡ **FlyCheck** | Flying in survival without permission |
| 🏃 **SpeedCheck** | Moving faster than physically possible |
| 🪂 **NoFallCheck** | Taking zero fall damage from any height |
| 🗡️ **ReachCheck** | Hitting players/mobs from impossible distance |
| 🌀 **KillAuraCheck** | Attacking targets without looking or too fast |
| 🌊 **JesusCheck** | Walking on water surfaces |
| 💥 **VelocityCheck** | Ignoring knockback during combat |

### Building & Items
| Module | What it stops |
|---|---|
| 🧱 **ScaffoldCheck** | Bridging at inhuman speed |
| 🧿 **AutoTotemCheck** | Re-equipping totems in milliseconds |
| 🎒 **InventoryCheck** | Moving items faster than any human can |

### Advanced Detection
| Module | What it stops |
|---|---|
| 🖱️ **AutoClickerCheck** | Abnormal click speed during combat (>20 CPS) |
| ⏱️ **TimerCheck** | Client-side game speed manipulation |
| ⛏️ **FastBreakCheck** | Breaking blocks faster than physically possible |

---

## 🧠 Smart Engine

- **VL Decay** — violations fade over time. Clean players recover naturally.
- **Lag Compensation** — thresholds adapt to player ping. Capped at 500ms.
- **Setback** — teleport back instead of kicking. Less disruptive, same protection.

---

## ⚙️ Configurable Actions

Every check has four configurable responses in `config/praxic.json`:

- `warn` — alert the player and log the violation
- `kick` — remove them from the server instantly
- `ban` — permanent ban (uses vanilla ban list)
- `setback` — teleport back to last safe position

---

## 🔔 Integrations

- **Staff Alerts** — OP2+ notified in chat on every flag
- **Discord Webhook** — rich embeds to your Discord channel
- **Update Checker** — notifies on join if a new version is available
- **API** — `PraxicViolationEvent` for other mods to listen to

---

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/) for **1.21.1**
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `praxic-x.x.x.jar` into your server's `mods/` folder
4. Start the server — config generates at `config/praxic.json`
5. Use `/praxic reload` to apply config changes without restarting

---

## ⌨️ Commands

| Command | Description |
|---|---|
| `/praxic status` | See every module at a glance |
| `/praxic stats` | Session stats — flags, top checks, top players |
| `/praxic check <player>` | Dig into a specific player's record |
| `/praxic violations` | Full server violation overview |
| `/praxic reset <player>` | Wipe someone's record clean |
| `/praxic reload` | Hot-reload config instantly |
| `/praxic whitelist add/remove/list` | Exclude trusted players from all checks |
| `/praxic history <player>` | Persistent violation log (works offline) |

*All commands require OP level 2.*

---

## 📜 License

Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.

Copyright 2026 jrxmod.
