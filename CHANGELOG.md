# Changelog

All notable changes to PRAXIC will be documented in this file.

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
