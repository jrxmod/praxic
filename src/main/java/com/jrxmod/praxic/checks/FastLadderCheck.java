package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects FastLadder - climbing ladders or vines faster than vanilla allows.
 * Vanilla ladder speed is about 0.1176 blocks per tick when colliding with wall.
 * Some modifications raise this to about 0.2872 blocks per tick, more than double.
 */
public class FastLadderCheck extends AbstractCheck {

    private static final double VANILLA_LADDER_SPEED = 0.1176;
    private static final double MAX_LEGIT_SPEED = 0.20;
    private static final double FAST_THRESHOLD = 0.25;
    private static final int BUFFER_THRESHOLD = 6;

    @Override
    public String getName() {
        return "FastLadderCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastLadderCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;
        if (player.getAbilities().mayfly) return;

        boolean onClimbable = player.onClimbable();
        boolean isClimbing = data.movementState == com.jrxmod.praxic.data.MovementState.CLIMB;

        if (!onClimbable && !isClimbing) {
            data.fastLadderBuffer = Math.max(0, data.fastLadderBuffer - 1);
            data.ladderTicks = 0;
            return;
        }

        double dy = player.getY() - data.prevY;

        if (dy <= 0) {
            data.ladderTicks = 0;
            if (data.fastLadderBuffer > 0 && data.totalTicks % 5 == 0) {
                data.fastLadderBuffer = Math.max(0, data.fastLadderBuffer - 1);
            }
            return;
        }

        data.ladderTicks++;

        boolean horizontalCollision = player.horizontalCollision;

        if (dy > MAX_LEGIT_SPEED) {
            if (horizontalCollision || onClimbable) {
                data.fastLadderBuffer++;
                if (data.fastLadderBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 1500)) {
                    ViolationManager.flag(player, data, this,
                            String.format("FastLadder: dy=%.4f b/t (vanilla %.4f) | ticks %d | buffer %d", dy, VANILLA_LADDER_SPEED, data.ladderTicks, data.fastLadderBuffer));
                    data.fastLadderBuffer = 0;
                }
            }
        } else if (dy > FAST_THRESHOLD) {
            data.fastLadderBuffer += 2;
            if (data.fastLadderBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 1000)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastLadder fast: dy=%.4f (max legit %.2f)", dy, MAX_LEGIT_SPEED));
                data.fastLadderBuffer = 0;
            }
        } else {
            if (data.fastLadderBuffer > 0) {
                data.fastLadderBuffer = Math.max(0, data.fastLadderBuffer - 1);
            }
        }

        if (data.ladderTicks > 10) {
            double avgDy = (player.getY() - data.lastLadderY) / data.ladderTicks;
            if (avgDy > MAX_LEGIT_SPEED && data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastLadder avg: %.4f b/t over %d ticks (vanilla %.4f)", avgDy, data.ladderTicks, VANILLA_LADDER_SPEED));
                data.ladderTicks = 0;
                data.lastLadderY = player.getY();
            }
        }

        if (data.ladderTicks == 1) {
            data.lastLadderY = data.prevY;
        }
    }
}
