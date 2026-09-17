package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.CheckManager;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class SpeedCheck extends AbstractCheck {

    // Skip check if the server tick took longer than this (server lag protection).
    // Measured via CheckManager.getCurrentMspt() - smoothed wall-clock interval.
    private static final double MAX_SERVER_MSPT = 100.0;

    // Skip if distance suggests teleport or severe lag
    private static final double TELEPORT_THRESHOLD = 6.0;

    // Flag only after this many consecutive suspicious ticks
    private static final int REQUIRED_BUFFER = 2;

    // Decrease buffer by this amount on normal movement
    private static final int BUFFER_DECAY = 1;

    @Override
    public String getName() {
        return "SpeedCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().speedCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;
        if (player.isAutoSpinAttack()) return;
        // Impulse is not skipped: SpeedHack is a ground mini-jump, not knockback.

        // Skip if player was recently hit  -  knockback causes false positives
        if (player.hurtTime > 0) return;

        // Wall / ceiling contact resets momentum and produces movement
        // bursts that exceed a per-tick cap at vanilla speed (head-bump
        // sprint-jumping). Never a speed-multiplier signal.
        if (player.verticalCollision || player.horizontalCollision) {
            data.speedBuffer = Math.max(0, data.speedBuffer - 1);
            return;
        }

        // Skip on server lag  -  smoothed MSPT from CheckManager tick monitor
        if (CheckManager.getCurrentMspt() > MAX_SERVER_MSPT) return;

        // Skip on ice - high slipperiness causes natural speed buildup.
        // Check not only directly below but also 1 block ahead in movement direction and below.
        BlockPos below = player.blockPosition().below();
        if (isIce(player.level().getBlockState(below).getBlock())
                || isIce(player.level().getBlockState(below.north()).getBlock())
                || isIce(player.level().getBlockState(below.south()).getBlock())
                || isIce(player.level().getBlockState(below.east()).getBlock())
                || isIce(player.level().getBlockState(below.west()).getBlock())) {
            data.speedBuffer = 0;
            return;
        }

        // Several move packets processed in one server tick measure the sum of
        // more than one client step, so the per-tick speed is not comparable
        // to vanilla caps (common when jump-spamming or on a loaded local
        // server). Skip such ticks entirely.
        if (data.timerPacketsThisTick > 1) {
            data.speedBuffer = Math.max(0, data.speedBuffer - 1);
            return;
        }

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double distancePerTick = Math.sqrt(dx * dx + dz * dz);

        // Skip teleports and severe lag jumps
        if (distancePerTick > TELEPORT_THRESHOLD) return;

        int ping = player.connection.latency();

        // Speed cheats multiply ground speed (often ~1.8, cap ~0.66).
        // Vanilla sprint is ~0.286, sprint-jump air peaks ~0.55. A 0.72 cap
        // therefore never sees them. Use a tighter grounded cap.
        double attr = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double attrScale = attr > 0.1 ? (attr / 0.1) : 1.0;
        // Server truth, not a block scan: during a jump (including a
        // head-bump under a ceiling) the scan below picks up the ground cell
        // and wrongly applies the grounded cap. The ground state comes from
        // the last accepted packet.
        boolean grounded = player.onGround();
        double base = grounded ? 0.50 : 0.70;
        double predicted = (base * attrScale + LagCompensation.extraSpeed(ping))
                * LagCompensation.tpsSensitivity();
        // A low-FPS client folds several client ticks into one packet: each
        // step legitimately exceeds the per-tick cap. Scale the cap by the
        // inter-packet gap (capped at 3x) so vanilla speed survives at 10-20
        // FPS while a real speed multiplier still exceeds it at 20 FPS.
        if (data.lastMoveGapMs > 80) {
            double timeScale = Math.min(3.0, data.lastMoveGapMs / 50.0);
            predicted *= timeScale;
        }
        // The configured cap is a grounded cap: sprint-jump arcs legitimately
        // reach ~0.66-0.70 while airborne, so only grounded movement is
        // clamped to it.
        double maxSpeed = grounded
                ? Math.min(Praxic.getConfig().speedMaxBlocksPerTick, predicted)
                : predicted;

        // Soul sand / soul soil with Soul Speed produces bursts above sprint speed.
        BlockPos feet = player.blockPosition();
        if (isSoulTerrain(player.level().getBlockState(feet).getBlock())
                || isSoulTerrain(player.level().getBlockState(below).getBlock())) {
            data.speedBuffer = 0;
            return;
        }

        if (distancePerTick > maxSpeed) {
            data.speedBuffer++;

            if (data.speedBuffer >= REQUIRED_BUFFER && data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Speed: %.3f blocks/tick (max: %.3f, ping: %dms)",
                                distancePerTick, maxSpeed, ping));
                data.speedBuffer = 0;
            }
        } else {
            data.speedBuffer = Math.max(0, data.speedBuffer - BUFFER_DECAY);
        }
    }

    private boolean isIce(net.minecraft.world.level.block.Block block) {
        return block == Blocks.ICE
                || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE
                || block == Blocks.FROSTED_ICE;
    }

    private boolean isSoulTerrain(net.minecraft.world.level.block.Block block) {
        return block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL;
    }

    /**
     * Packet-level speed. Ground speed cheats often cap near 0.66 b/packet.
     * Uses last move packet, not the tick snapshot vanilla may already have
     * rubberbanded.
     */
    public void onMovePacket(ServerPlayer player,
                             net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,
                             PlayerData data) {
        if (!Praxic.getConfig().speedCheckEnabled) return;
        if (!packet.hasPosition()) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying() || player.isPassenger()
                || player.isFallFlying() || data.wasFallFlying) return;
        if (data.joinGraceTicks > 0 || data.lastPacketTime == 0L) return;
        // Same packet-burst guard as the tick path.
        if (data.timerPacketsThisTick > 1) {
            data.speedBuffer = Math.max(0, data.speedBuffer - 1);
            return;
        }
        // Same wall / ceiling guard as the tick path (elytra wall impacts,
        // head bumps).
        if (player.verticalCollision || player.horizontalCollision) {
            data.speedBuffer = Math.max(0, data.speedBuffer - 1);
            return;
        }

        double x = packet.getX(player.getX());
        double z = packet.getZ(player.getZ());
        double horiz = Math.sqrt(
                (x - data.lastPacketX) * (x - data.lastPacketX)
                        + (z - data.lastPacketZ) * (z - data.lastPacketZ));
        if (horiz > TELEPORT_THRESHOLD || horiz < 0.01) return;

        BlockPos below = BlockPos.containing(x, packet.getY(player.getY()) - 0.2, z).below();
        if (isIce(player.level().getBlockState(below).getBlock())) return;

        // Packet ground bit (same reasoning as the tick path).
        boolean grounded = packet.isOnGround();
        double max = grounded ? 0.48 : 0.70;
        // Same low-FPS allowance as the tick path.
        if (data.lastMoveGapMs > 80) {
            max *= Math.min(3.0, data.lastMoveGapMs / 50.0);
        }
        if (horiz <= max) {
            data.speedBuffer = Math.max(0, data.speedBuffer - 1);
            return;
        }
        data.speedBuffer++;
        if (data.speedBuffer >= REQUIRED_BUFFER && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Packet speed %.3f (max %.3f grounded=%s)", horiz, max, grounded));
            data.speedBuffer = 0;
        }
    }
}
