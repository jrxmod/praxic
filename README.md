<div align="center">

# ⚡ PRAXIC

**Protection Runtime Analysis & eXploit Interception Core**

Server-side anticheat for Fabric. Your players install nothing.

![Version](https://img.shields.io/badge/version-0.8.0-orange)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.1-brightgreen)
![Loader](https://img.shields.io/badge/loader-fabric-blue)
![License](https://img.shields.io/badge/license-Apache--2.0-red)

[Modrinth](https://modrinth.com/mod/praxic) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/praxic-anticheat) · [Website](https://jrxmod.netlify.app) · [Issues](https://github.com/jrxmod/praxic/issues)

</div>

---

## What it does

14 checks across movement, combat, world interaction, and client manipulation — powered by a physics-based prediction engine with lag compensation. Violations decay over time, thresholds adapt to ping, and actions are fully configurable per check.

## Checks

**Movement** — Fly · Speed · Jesus (water walk) · Y-Prediction (physics-based)
**Combat** — Reach · KillAura · Velocity (anti-knockback)
**World** — Scaffold · FastBreak · NoFall
**Client** — AutoClicker · AutoTotem · Inventory · Timer

## Actions

Every check supports: `warn` · `kick` · `ban` · `setback`
Configured per check in `config/praxic.json`.

## Commands

```
/praxic status                  — module overview
/praxic check <player>          — player violations
/praxic violations              — all players
/praxic stats                   — session statistics
/praxic reset <player>          — clear violations
/praxic reload                  — hot-reload config
/praxic whitelist add|remove|list — bypass checks
/praxic history <player>        — persistent log (works offline)
```

All commands require **OP level 2**.

## Integrations

- **Staff Alerts** — OP2+ notified in chat on every flag
- **Discord Webhook** — rich embeds to your channel
- **API** — `PraxicViolationEvent` for other mods (see [REVEX](https://github.com/jrxmod/revex))

## Install

1. [Fabric Loader](https://fabricmc.net/) + [Fabric API](https://modrinth.com/mod/fabric-api) for **1.21.1**
2. Drop the jar into `mods/`
3. Start → config generates at `config/praxic.json`

## License

Apache 2.0 — see [LICENSE](LICENSE). Copyright 2026 jrxmod.
