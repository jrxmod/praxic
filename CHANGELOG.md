# Changelog

All notable changes to PRAXIC will be documented in this file.

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
