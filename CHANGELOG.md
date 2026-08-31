# Changelog

All notable changes to PRAXIC will be documented in this file.

## 0.16.0 - Impact

### Added
- **Packet mitigation**: illegal move, reach, air-place and Wind Charge packets are cancelled on the server thread before vanilla applies them. Movement setback rubberbands the client to `lastSafe`. Skips Impulse windows, join grace, creative, spectator and whitelist. A 2-packet buffer fires before the first rubberband. Timer, AutoClicker, Inventory and AimAssist are never cancelled this way. Toggle: `enableMitigation`.
- **MaceSmashCheck**: flags a mace attack that claims smash damage while hovering (airborne, not falling, `fallDistance` below vanilla 1.5). A real smash records an Impulse MACE window. Jump-peak hits are not flagged.
- **WindChargeAbuseCheck**: cancels a Wind Charge use while the vanilla 10-tick cooldown is active; flags a burst of charges used as hover fuel.

### Changed
- Config schema v5 (`enableMitigation`, mace and wind-charge check fields); v7 migrates leftover Timer 55 pkt/s to 32.
- `/praxic status` and the dashboard list 35 checks.
- Movement packet handlers no longer defer via `server.execute()`; cancellation must happen on the calling server thread.

### Fixed
- **TeleportCheck** reseeds its packet baseline on elytra / passenger / Impulse skips, so a long elytra flight no longer flags `Moved 8.8 blocks in one packet`. Blink is no longer treated as a connection stall.
- **JesusCheck** no longer requires `!isInWater` (water-walk keeps the AABB in water). Flags onGround water-walk with no solid floor, after a short buffer.
- **BoatFlyCheck** ignores spoofed `onGround` / `isInWater` and looks at real liquid/solid support. Hover threshold 12 ticks.
- **SpeedCheck** flags against a sprint-jump cap (~0.72 b/t, attribute-scaled) instead of the 1.3 teleport ceiling, so Speed / BunnyHop modules are visible.
- **FlyCheck** records and can cancel `flying=true` ability packets; creative-style Fly was previously only kicked by vanilla (`Flying is not enabled`) with PRAXIC VL=0.
- **FastBreakCheck** needs two consecutive impossible breaks before a flag (Nuker still trips; a single 0 ms packet does not).
- **TeleportCheck** compares each move packet to the tick-start origin. Blink dumps many small packets in one tick (vanilla `moved too quickly`) which consecutive-packet distance never saw.
- **FlyCheck** uses collision support instead of `onGround` / airTicks, so Flight with anti-kick is flagged in ~1.5 s instead of waiting for vanilla `floating too long`.
- **JesusCheck** matches the in-liquid `vy=0.11` hop and onGround spoof above water, including lava.
- **BoatFlyCheck** only treats the hull AABB as supported; water one block below is not support. Hover threshold 8 ticks.
- **SpeedCheck** grounded cap 0.50 b/t so ground speed cheats (cap ~0.66) are visible.
- **JesusCheck** packet path requires an `onGround=true` spoof while not in water; surface swimming and water-exit are ignored. Water-exit grace restored.
- **BoatFlyCheck** ignores duplicate vehicle packets and real freefall (`dy < -0.08`); hull fluid is sampled at the boat Y, so landing on water is not a hover.
- **AutoTotemCheck** timestamps vanilla totem pops and flags inventory clicks within 150 ms (instant re-equip). Tick polling missed same-tick re-equip.
- **NoFallCheck** tracks peak Y vs current Y and only treats the block at the feet as landed. StatusOnly `onGround` packets no longer zero the fall, and `onGround=false` movement packets no longer cancel the spoof counter.
- **AutoTotemCheck** flags offhand/totem inventory clicks within 400 ms of damage or a totem pop (slot 45, carried totem, F-swap).
- **GroundSpoofCheck** uses packet onGround plus foot collision (including StatusOnly). Tick airTicks / server onGround are ignored.
- **VehicleFlyCheck** reads vehicle-move packets like BoatFly.
- **StepCheck** flags a single packet climb above 0.75 (vanilla jump is ~0.42). Vertical 10-block steps are not TeleportCheck.
- **NoSlowCheck** treats use-item speed above ~0.16 or sprint-while-using as NoSlowdown (the 0.30 cap was full sprint).
- **ScaffoldCheck** uses the placed block (clicked face + direction), not the clicked neighbour.
- **GroundSpoofCheck** ignores StatusOnly packets (NoFall / standing still). Standing on the block below is not a spoof.
- **TimerCheck** compares packet count to elapsed seconds, not a fixed 5s quota (x2.0 was always under 55*5).
- **NoSlowCheck** requires an active use-item on the ground; sprint after eating and eating while falling are ignored.
- **NoFallCheck** does not treat onGround packets as spoof when water is within 4 blocks below (vanilla water landings).
- **TimerCheck** treats leftover `timerMaxPacketsPerSecond` 55 as 32 (schema v7). Evaluates after 2s of walking. Timer x2.0 is extra position packets (~40/s vs vanilla ~20/s); standing still does not send them.
- **TimerCheck** no longer uses packets/sec. Timer x2.0 delivers two position packets in the same server tick with the same millisecond, so the 5s average stayed at ~20. Flags a 50ms-per-packet balance (client ahead ~600ms) or 10 ticks in a row with 2+ position packets.
- **ScaffoldCheck** no longer flags vanilla run-and-place. Holding right-click while walking is the same rate as automated bridging. Requires under-foot places while last tick's pitch was not looking down (look spoof for the click).
- **ScaffoldCheck** requires a rotation-only look packet in the 250ms before an under-foot place (look-then-click). Vanilla walking sends PosRot, not a bare Rot. Extra Rot packets are not Timer.
- **TimerCheck** dropped the TPS-below-17 skip (a local server often sits there and the check never ran). Position packets only (look-only Rot from ScaffoldWalk is not Timer). Also flags ~9+ blocks of XZ in 20 ticks (vanilla sprint ~5.6, Timer x2 ~11).
- **TimerCheck** grounded window: 6.6 blocks over 20 on-ground ticks (vanilla sprint 5.6). Jump ticks are ignored so sprint-jump is not Timer. Walking with Timer x2.0 (~8.6) is visible without a sprint.
- **ScaffoldCheck** no longer uses per-tick `prevX` (place packets often arrive before movement, so dx was 0). Flags 3 near-feet places in 2s whose player XZ moved. Automated scaffold may place up to 2 blocks out.

## 0.15.0 - Impulse

### Added
- **Impulse engine**: short-lived exemptions for vanilla knockback that is not gravity-only: damage, explosions, wind charges, riptide, and mace smash. Movement checks skip while an impulse is active.
- **XZ friction prediction** in PhysicsEngine (ground 0.6, air 0.91, water 0.8). Completes the placeholder left since 0.7.0.
- **AimAssistCheck**: flags abnormally low yaw entropy during combat while the player is still rotating.
- **VehicleFlyCheck**: hovering horses, pigs, striders and minecarts (off rails). BoatFlyCheck is unchanged.
- **FastUseCheck**: eat/drink finishing in far fewer ticks than vanilla (32).
- **AirPlaceCheck**: placements against air or beyond block interaction range.
- Ghost honeypot **v2**: marker ArmorStand, no collision, at most one per player, spawn packets hidden from other players via ChunkMap tracker filter.

### Fixed
- `/praxic tp` now changes dimension to the world stored in the evidence packet.
- `/praxic whitelist add|remove` works for offline players via the profile cache.
- `TimerCheck` ignored `timerMaxPacketsPerSecond` and used a hardcoded window; it now reads config (default 55 pkt/s) and uses CheckManager TPS instead of per-packet reflection.
- `ReachCheck` now uses `entityInteractionRange()` so attribute modifiers are respected.
- `SpeedCheck` scales with the live `MOVEMENT_SPEED` attribute and skips soul sand / soul soil.
- `JesusCheck` no longer flags bubble columns.
- `FlyCheck` / `YPredictionCheck` / `StepCheck` / `GroundSpoofCheck` / `TeleportCheck` skip during riptide and Impulse windows (wind charge, mace, explosions).
- Debug recordings leaked if the player disconnected mid-capture.
- Web dashboard thread pool was not shut down on server stop.
- Ghost ArmorStands were broadcast to every player and had full collision.

### Changed
- Config schema v4. Stale `timerMaxPacketsPerSecond: 24` is migrated to 55.
- `/praxic status` and the dashboard list 33 checks.
- Discord embeds distinguish freeze, setback and warn actions.

## 0.14.0 - Polish

### Added
- **Clickable staff alerts** — player names in flag notifications are now clickable to open a full inspection and show a tooltip with context on hover. One click replaces manual `/praxic check` lookups.
- **Performance monitor** (`/praxic perf`) — shows server TPS, MSPT and how much processing time the anticheat itself consumes per tick and per player. Instantly see whether the server or the anticheat is the bottleneck.
- **Debug recording** (`/praxic debug <player>`) — captures 30 seconds of tick-by-tick data for a player and saves it to a JSON file. Designed for reviewing suspected false positives with full context.
- **Teleport to flag** (`/praxic tp <player>`) — teleports the moderator to the coordinates of the player's most recent violation for quick scene inspection.
- **Confidence bar** — `/praxic check` now includes a colour-coded visual bar alongside the confidence score for faster at-a-glance assessment.
- **Config validation** — out-of-range values in `praxic.json` are automatically clamped with a log warning instead of causing silent misbehaviour.
- **Config auto-backup** — every save creates `praxic.json.bak` from the previous version, protecting against accidental configuration loss.
- **Session summary on disconnect** — a one-line log entry is recorded when a player leaves, capturing session duration, total violations and overall suspicion for retro-analysis.
- **Lag-adaptive detection** — when server performance degrades, detection thresholds automatically relax to prevent lag-induced false positives.
- **Richer Discord alerts** — webhook embeds now include session context and server performance alongside the violation details.

### Fixed
- `ConfidenceEngine.java` — one combat detection module was missing from the evidence weight configuration, causing it to contribute less than intended.
- `PraxicWebServer.java` — the dashboard reset endpoint could not resolve valid player names due to a URL parsing error.
- `PraxicCommand.java` — the freeze punishment was displayed without colour in command output.
- `SpeedCheck.java`, `CheckManager.java` — speed detection relied on a timing value that was refreshed every tick, preventing the server-lag guard from ever activating.
- `WhitelistManager.java` — saving the whitelist could fail when the config directory did not yet exist.
- `ServerGamePacketListenerMixin.java`, `CheckManager.java` — replaced per-packet stream searches with direct references, reducing unnecessary processing overhead in the packet pipeline.

### Changed
- `/praxic status` now lists all 29 checks across 4 groups.
- Four new subcommands: `perf`, `debug`, `tp` and improved `check` output.

## 0.13.0 - Hardening

### Added
- **TeleportCheck**: detects Blink / Teleport cheats by comparing the position declared in consecutive move packets, before the vanilla server can correct it. Legitimate teleports (ender pearls, chorus fruit, `/tp`, portals, respawn) are exempt via teleport confirmation. Lag and connection stalls do not false-positive.
- **ReachCheck through-wall detection**: attacks whose line of sight passes through a full solid block are flagged independently of distance. Thin blocks (fences, panes, bars) are ignored.
- **Freeze punishment**: a new `freeze` action holds a player in place for a configurable duration (`freezeDurationTicks`, default 60). Falls between `warn` and `setback` in severity.
- **Config migrations**: `configVersion` is now matched by a `migrate()` step so future field renames and type changes can be applied automatically.
- **Unit tests**: 21 JUnit tests covering the physics engine, confidence and anomaly engines, lag compensation and the profiler's pure logic.

### Changed
- **ReachCheck**: distance is now measured to the closest point of the target's bounding box (vanilla semantics) instead of its centre. Survival threshold lowered from 5.0 to 3.5, creative from 6.0 to 5.5, catching reach in the 3.2–4.5 range that was previously missed.
- **TeleportCheck** replaces the earlier tick-level draft; detection now runs at packet level for accuracy.
- CI now builds on JDK 21, matching the project's `release = 21` target.

## 0.12.1 - Sentinel (false positive fixes)

This release fixes multiple false positives introduced in 0.12.0: vanilla-accurate block breaking times, correct placement counting, and fall damage checks that respect vanilla damage reductions. No new checks are added in this release.

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
