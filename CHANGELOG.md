# Changelog

All notable changes to PRAXIC will be documented in this file.

## Unreleased — false positive fixes

### Fixed

- **FastBreakCheck**: mining time calculation now matches vanilla 1.21.1 (tool speeds, Efficiency, Haste / Conduit Power, Mining Fatigue, water and mid-air penalties, 30/100 divisor). Blocks that break instantly in vanilla (shears on leaves, Efficiency V + Haste) are no longer flagged.
- **FastPlaceCheck**: only successful block placements are counted; non-block items such as fireworks are ignored. Default limit raised to the vanilla ceiling of 20 blocks/sec.
- **NoFallCheck**: respects the `fallDamage` gamerule and accounts for armor, Protection / Feather Falling, Resistance and sweet berry bushes. A flag requires less than half of the expected damage to have been dealt.
- **NoSlowCheck**: default limit raised to 0.30 blocks/tick — vanilla sprinting while eating (0.286) is no longer flagged.
- **FastBreakCheck**: correct-tool detection now mirrors vanilla `Player#hasCorrectToolForDrops` — blocks that do not require a tool for drops (leaves, dirt, grass, logs) use the 30 divisor even with bare hands, matching real server-side breaking speed.
- **ScaffoldCheck / TowerCheck**: only successful block placements are counted; scaffold default limit raised to 12 blocks/sec.
- **ElytraFlyCheck**: firework rocket use grants a 3-second grace period.
- **AutoClickerCheck**: added a flag cooldown to prevent flag spam.
- **KillAuraCheck**: burst counter resets after a flag.
- **VelocityCheck**: knockback into a wall no longer flags.
- **AutoTotemCheck**: item swaps without recent damage are not treated as totem consumption.
- **SprintCheck**: removed the blindness branch — vanilla does not cancel an ongoing sprint.
- **StepCheck**: piston lifts are exempt.
- **FlyCheck**: Jump Boost is exempt; natural falls no longer flag.

## 0.12.0 - Sentinel

### Added
- **New ElytraFlyCheck**: detects ElytraFly modules via horizontal speed + vertical glide anomaly, with buffer and ping compensation
- **New StepCheck**: detects instant step >0.75 blocks without jump, with slime/honey exemption
- **New GroundSpoofCheck**: detects clients spoofing onGround while airborne, key for fly/nofall bypasses
- **New TowerCheck**: detects automated tower building via vertical placement rate
- **New FastPlaceCheck**: detects block placement faster than vanilla allows (10 blocks/sec)
- **Dashboard metrics**: new `/api/metrics` endpoint with TPS/MSPT, TPS/MSPT display in stat bar
- **Dashboard actions**: Reset VL and Toggle Whitelist buttons in player detail (requires token if enabled)
- **Command suggestions**: `/praxic check`, `reset`, `whitelist`, `history`, `evidence` now autocomplete online player names via Brigadier suggestions
- **Config versioning**: `configVersion` field for future migrations

### Changed
- **EvidenceManager & HistoryManager**: file I/O now asynchronous on dedicated thread pools, eliminating main-thread lag spikes on flags
- **ServerGamePacketListenerMixin**: fixed race where PlayerData was fetched before server thread switch; now fetched inside `execute()` and captures onGround packet flag
- **ViolationManager**: staff/discord alert maps now cleaned on disconnect to prevent memory leak; added `cleanup(UUID)` method
- **ConfidenceEngine**: added weights for new checks (ElytraFly 0.22, Step 0.18, GroundSpoof 0.28, Tower 0.18, FastPlace 0.12)
- **CheckManager**: disconnect now also clears ViolationManager cooldowns; dead player handling resets new buffers (elytra, tower, fastplace, groundspoof)
- **SpeedCheck / NoSlowCheck**: ice detection expanded to 5 blocks (center + N/S/E/W below) to prevent false positives from ice momentum
- **NoFallCheck**: safe landing detection rewritten to use exact block checks and tags (BEDS, WOOL_CARPETS) instead of substring matching
- **VelocityCheck**: slime/honey bounce now preserves pending knockback check instead of cancelling
- **FastBreakCheck**: added correct-tool penalty (0.2x speed) for incorrect tool usage
- **TimerCheck**: added TPS guard — skips evaluation when server TPS < 17.0 to avoid false positives during lag
- **PlayerProfiler**: baseline now requires entropy and CPS samples in addition to speed, improving toggling detection reliability
- **GhostEntity**: armor stand now small, no baseplate, silent, not invulnerable (invulnerable could block attack packets)
- **DiscordWebhook**: payload now built with Gson to safely escape JSON, truncated details to 1024 chars
- **UpdateChecker**: JSON parsing rewritten from regex to Gson `JsonParser`
- **PraxicWebServer**: auth now URL-decodes token query param; thread pool increased to 4; added `/api/action/reset` and `/api/action/whitelist` endpoints
- **PraxicCommand**: status now shows 28 checks in 4 groups including new ones

### Fixed
- **JesusCheck**: double decrement of `jesusWaterGraceTicks` (was decremented in both CheckManager and JesusCheck) caused grace to expire twice as fast
- **JesusCheck**: lily pad detection now checks both foot and below positions
- **PlayerData**: `decayViolations` now uses entrySet to avoid potential concurrent modification issues
- **GhostEntityManager**: spawn position logic preserved but entity properties hardened
- **Dashboard**: token injection now safe for special characters via URL decoding

## 0.11.0 - Evidence & Protocol
### Added
- **New PhaseCheck**: detects sustained noclip / in-wall movement with movement buffering
- **New NoSlowCheck**: detects NoSlow modules while using slowed items (food, bow, shield, etc.)
- **New CriticalsCheck**: detects spoofed critical-hit packets during combat
- **New BadPacketsCheck**: protocol sanity check for impossible movement / rotation packets
- **New GhostTrapCheck identity**: ghost honeypot hits now flow through the normal violation, confidence, history and API pipeline
- **EvidenceManager**: persistent rich evidence packets in `config/praxic-evidence.json`
- **/praxic evidence [player]**: inspect recent global or per-player evidence packets
- **Dashboard incidents feed**: new `/api/incidents` endpoint and Incidents tab in the web dashboard
- **Dashboard evidence panel**: player detail page now shows evidence packets, ghost traps and state buffers
- **Configurable confidence policy**: warn/setback/kick/ban thresholds and `confidenceAutoBan`
- **Alert cooldowns**: configurable staff and Discord alert rate limits

### Changed
- **ViolationManager** now records rich evidence and rate-limits noisy staff/Discord alerts
- **ActionResolver** now caps confidence-based actions by each check's configured maximum action
- **GhostEntityManager** now respects config for enabled state, spawn chance, cooldown and lifetime
- **PraxicStats** now tracks top players in addition to top checks
- **README** updated for the expanded 23-check release

### Fixed
- **RotationCheck combat context**: attack packets now update `lastAttackTime`, so combat-only rotation checks can actually evaluate
- **Ghost honeypot hits** are no longer only logged; they now create actionable PRAXIC violations
- **Disconnect cleanup** now despawns active ghost traps for the player

## 0.10.0 - Foundation & Traps
### Added
- **GhostEntityManager**: new honeypot trap system that spawns invisible ArmorStand entities to detect KillAura/AimAssist
- **GhostEntity**: invisible ArmorStand-based entities (completely rewritten from previous mob-based version)
- **New cross-correlations** in ConfidenceEngine:
  - Rotation + Timing → ×1.4 multiplier
  - Movement + Anomaly → ×1.3 multiplier
- **Enhanced `/praxic check <player>`**:
  - Now shows Confidence + Anomaly scores
  - Analytics snapshot (Entropy, Max Snap, CPS, Speed)
  - Baseline status (READY / WARMING)
  - Active ghost trap count
- **Web Dashboard v0.10.0**:
  - Displays active ghost traps per player
  - Version updated to 0.10.0

### Changed
- `Praxic.java`: added `GhostEntityManager` singleton
- `ServerGamePacketListenerMixin`: ghost honeypot check runs before normal KillAura/Reach checks
- `gradle.properties`: version bumped to 0.10.0
- Dashboard API now exposes `ghostTraps` field

### Fixed
- GhostEntity completely rewritten — replaced visible aggressive mobs with invisible ArmorStands
- Commands registration fixed (moved back to `onInitialize`)

## 0.9.0 - Watchtower
### Added
- **Decision Engine — ConfidenceEngine**: per-player evidence score built from weighted check flags and cross-check correlation
- **Decision Engine — AnomalyScoreEngine**: accumulates sub-threshold baseline deviations to detect closet cheaters over time
- **Decision Engine — ActionResolver**: unified punishment gate based on confidence score, replaces per-check flat VL thresholds
- **PostKillSnapCheck**: new combat check — detects Kill Aura via yaw snap in the first ticks after a kill
- **Web Dashboard**: embedded admin panel at http://127.0.0.1:8765/ — live player list, per-player analytics, violation history, check status, player search, optional token auth

### Changed
- **ViolationManager**: punishments now routed through ActionResolver; staff alerts include confidence score
- **CheckManager**: tick pipeline extended to 15 steps — anomaly feed and confidence nudge after analytics
- **/praxic check**: now displays confidence and anomaly scores alongside VL
- **/praxic status**: shows WebDashboard row with URL when enabled
- **PraxicConfig**: new fields — postKillSnapCheckEnabled, postKillSnapMaxAngle, webDashboardPort, webDashboardToken

### Fixed
- **CheckManager**: death guard no longer resets behavioural analysers — baseline was destroyed on every death, blinding toggling detection for the rest of the session
- **TimingAnalyzer**: interval derivation logic fixed — single attack per tick was never recorded, leaving clickIntervalStdDev at -1.0 indefinitely

## 0.8.0 - Analysis Layer
### Added
- **Engine v2 — MovementAnalyzer**: tracks speed history, acceleration curve, strafe ratio and jump frequency per player
- **Engine v2 — PlayerProfiler**: builds a behavioural baseline over the first 5 minutes using Welford's online algorithm, then produces a deviation score for toggling detection
- **Engine v2 — PlayerAnalytics**: unified analytics bundle — all four profiles (rotation, timing, movement, baseline) in one object per tick

### Changed
- **CheckManager**: analysis pipeline consolidated into a single PlayerAnalytics object instead of separate maps per profile
- **PlayerData**: removed legacy Y-prediction fields (predictedVY, yPredictionActive, yPredictionGraceTicks) — fully replaced by PhysicsEngine

## 0.7.0 - Engine Foundation
### Added
- **Engine v2 Data Layer**: immutable per-tick player snapshot — all engine layers read from it
- **Engine v2 Physics Layer**: Y-prediction simulation as a standalone engine component
- **Engine v2 Analysis Layer**: rotation analyzer with Shannon entropy and post-kill snap detection
- **Engine v2 Analysis Layer**: timing analyzer with click and packet interval deviation

### Changed
- YPredictionCheck now reads from the physics engine instead of running its own simulation
- CheckManager tick pipeline extended: snapshot → physics → analysis → checks

## 0.6.0 - New Checks
### Added
- RotationCheck: detects AimAssist via suspicious rotation snaps during combat
- SprintCheck: detects sprinting under conditions vanilla prohibits (low hunger, Blindness)
- BoatFlyCheck: detects flying while riding a boat

### Changed
- FlyCheck: removed deprecated state mutation code (now fully managed by CheckManager)
- PlayerData: added joinGraceTicks — all checks skip the first 2 seconds after join
- PraxicViolationEvent: now cancellable (listeners return boolean)
  true = listener handles punishment, PRAXIC skips its own action

## 0.5.0 - Engine Update II
### Added
- **Movement State Machine** — centralized movement state (GROUND / JUMP / AIR / FALLING / WATER / CLIMB)
  All checks now read a single shared state instead of maintaining their own booleans
- **Y-Prediction Engine** — physics-based vertical movement check (`YPredictionCheck`)
  Simulates vanilla gravity (`vy = (vy - 0.08) * 0.98`) and compares predicted Y to actual Y
  Flags only when player is *above* prediction — catches fly and hover cheats
  Lag-compensated tolerance, resync on flag to prevent VL cascades
  Default action: `setback`
- `lastYaw` / `lastPitch` fields added to `PlayerData` (groundwork for RotationCheck in next release)

### Changed
- `/praxic status` now shows checks grouped by category: Movement / Combat / World / Client / System
- `/praxic status` now also shows `StaffAlerts` and `Discord` state
- Kick / ban / warn messages now show human-readable reasons instead of internal check names
  (e.g. "Flying is not allowed on this server." instead of "FlyCheck")
- `waterExitTicks` and `jesusWaterGraceTicks` are now managed centrally by `CheckManager`
  instead of being updated inside individual checks — order of execution no longer matters

### Fixed
- `airTicks` could increment while in water or on a climbable in edge cases — now strictly tied to airborne states

## 0.4.1 — Patch
### Fixed
- JesusCheck: false positives when falling into water or exiting water.
  Added independent grace timer (15 ticks), wasInWater transition guard,
  and downward movement check (dy < -0.01).

## 0.4.0 — Engine Update
### Added
- VL Decay: violation levels decrease by 1 every 5 seconds without new flags.
- Setback: new action type — teleports player back to last safe ground position instead of kicking.
- Lag Compensation: dynamic check thresholds based on player ping, capped at 500ms to prevent spoofing.
- JesusCheck: detects walking on water surface. Accounts for Frost Walker, lily pads, water exit grace period and knockback.
- VelocityCheck: detects knockback cancellation by measuring horizontal displacement after taking damage.
- /praxic whitelist add/remove/list — exclude players from all checks. Persistent across restarts.
- /praxic history <player> — view last 10 violation entries per player. Persistent, works for offline players.

### Fixed
- player.latency replaced with player.connection.latency() — correct Fabric API method.

## 0.3.0 — Integrations & API
### Added
- Update Checker: notifies OP2+ players on join if a newer version is available on Modrinth.
- Discord Webhook: sends violation alerts to a Discord channel (configurable, disabled by default).
- OnViolation API: Fabric event for other mods to listen to PRAXIC violation events.
- Stats: /praxic stats command showing total flags, top checks and top players this session.

## 0.2.0 — New Detection Modules
### Added
- AutoClickerCheck: detects abnormal CPS (>20) during combat using a 1-second sliding window.
- TimerCheck: detects client-side game speed manipulation using a 5-second sliding window.
- FastBreakCheck: detects breaking blocks faster than physically possible.
  Accounts for block hardness, tool speed, Haste and Mining Fatigue effects.
- GitHub Issue templates: Bug Report and Feature Request.

## 0.1.2 — Stability & Staff Alerts
### Added
- Staff Alerts: notify online operators (OP level 2+) when a player is flagged.
- Advanced FlyCheck: added detection for illegal vertical ascent (flying up).
- Config: added `enableStaffAlerts` toggle.

### Fixed
- FlyCheck: fixed false positives when climbing ladders, vines, and scaffolding.
- FlyCheck: added grace period for water-to-land transitions.
- SpeedCheck: added movement buffer to prevent flagging on single-tick sprint-jump spikes.
- NoFallCheck: added support for Absorption hearts (Golden Apples).
- ReachCheck: increased survival threshold to 4.5 to accommodate mob hitboxes.

## 0.1.1 — Hotfix update
### Changed
- ReachCheck threshold adjusted to reduce false positives on mobs.
- /praxic status now shows all modules.

## 0.1.0 — Initial release
### Added
- FlyCheck, SpeedCheck, NoFallCheck, ReachCheck, KillAuraCheck.
- ScaffoldCheck, AutoTotemCheck, InventoryCheck.
- warn/kick/ban actions per check.
- /praxic commands: status, check, violations, reset, reload.
- Logging to logs/praxic.log.
