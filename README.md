<div align="center">

# ⚡ PRAXIC

**Protection Runtime Analysis & eXploit Interception Core**

Server-side anticheat for Fabric. Your players install nothing.

![Version](https://img.shields.io/badge/version-0.14.0-orange)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.1-brightgreen)
![Loader](https://img.shields.io/badge/loader-fabric-blue)
![License](https://img.shields.io/badge/license-Apache--2.0-red)

[Modrinth](https://modrinth.com/mod/praxic) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/praxic-anticheat) · [Website](https://jrxmod.netlify.app) · [Issues](https://github.com/jrxmod/praxic/issues)

</div>

---

## What it does

29 checks across movement, combat, world interaction, protocol sanity, and client manipulation — powered by a physics-based prediction engine with lag compensation, confidence scoring, behavioural baselines, and evidence packets. Violations decay over time, thresholds adapt to ping, and actions are configurable per check.

## Checks

- **Movement** — Fly · Speed · Phase · NoSlow · Jesus (water walk) · Sprint · BoatFly · ElytraFly · Step · GroundSpoof · Teleport (Blink) · Y-Prediction (physics-based)
- **Combat** — Reach (incl. through-wall raycast) · KillAura · GhostTrap honeypots · Criticals · Velocity (anti-knockback) · Rotation · PostKillSnap
- **World** — Scaffold · Tower · FastBreak · FastPlace · NoFall
- **Client / Protocol** — AutoClicker · AutoTotem · Inventory · Timer · BadPackets

## Actions

Every check supports: `flag` · `warn` · `freeze` · `setback` · `kick` · `ban`
Configured per check in `config/praxic.json`. Confidence scoring chooses the action and each check's configured action acts as a maximum cap.

## Commands

```
/praxic status                  — module overview
/praxic check <player>          — player violations with confidence bar
/praxic violations              — all players
/praxic stats                   — session statistics
/praxic perf                    — server & anticheat performance monitor
/praxic debug <player>          — record 30s of tick data for investigation
/praxic tp <player>             — teleport to last flag location
/praxic reset <player>          — clear violations
/praxic reload                  — hot-reload config
/praxic whitelist add|remove|list — bypass checks
/praxic history <player>        — persistent log (works offline)
/praxic evidence [player]       — rich evidence packets for review
/praxic evidence clear <player> — clear stored evidence
```

All commands require **OP level 2**.

## Integrations

- **Staff Alerts** — OP2+ notified in chat on every flag, with cooldown protection
- **Discord Webhook** — rich embeds to your channel
- **Evidence Store** — compact review packets in `config/praxic-evidence.json`
- **Web Dashboard** — local admin UI at `http://127.0.0.1:8765/`
- **API** — `PraxicViolationEvent` for other mods (see [REVEX](https://github.com/jrxmod/revex))

## Install

1. [Fabric Loader](https://fabricmc.net/) + [Fabric API](https://modrinth.com/mod/fabric-api) for **1.21.1**
2. Drop the jar into `mods/`
3. Start → config generates at `config/praxic.json`

## License

Apache 2.0 — see [LICENSE](LICENSE). Copyright 2026 jrxmod.
