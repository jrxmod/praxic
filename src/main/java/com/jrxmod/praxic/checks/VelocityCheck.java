package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class VelocityCheck extends AbstractCheck {

    // Ticks to wait after hit before measuring displacement
    private static final int KNOCKBACK_CHECK_DELAY = 5;

    // Minimum horizontal displacement expected after knockback
    private static final double MIN_KNOCKBACK_DISPLACEMENT = 0.05;

    @Override
    public String getName() {
        return "VelocityCheck";
    }

    /**
     * Called from the hurt mixin while the damage source is still known.
     * Damage types tagged #minecraft:no_knockback (fall, void, fire, drowning,
     * starvation, ...) apply no horizontal impulse, so they are excluded here
     * instead of guessing from the movement state. Guessing by onGround()
     * suppressed every regular melee/projectile hit on a standing player.
     */
    public void onHurt(ServerPlayer player, PlayerData data, DamageSource source) {
        if (!Praxic.getConfig().velocityCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isPassenger()) return;
        if (player.isDeadOrDying()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (source.is(DamageTypeTags.NO_KNOCKBACK)) return;

        data.knockbackPending     = true;
        data.knockbackStartX      = player.getX();
        data.knockbackStartZ      = player.getZ();
        data.knockbackTicksWaited = 0;

        // Save the horizontal knockback direction (attacker -> victim) so the
        // wall check below can distinguish "knockback cancelled by a block"
        // from "knockback removed by an anti-knockback module".
        Entity attacker = source.getEntity();
        data.knockbackDirSet = false;
        if (attacker != null) {
            double dirX = player.getX() - attacker.getX();
            double dirZ = player.getZ() - attacker.getZ();
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len > 1.0E-6) {
                data.knockbackDirX = dirX / len;
                data.knockbackDirZ = dirZ / len;
                data.knockbackDirSet = true;
            }
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().velocityCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isPassenger()) return;

        // Reset pending check on death — player teleports to spawn within 5 ticks,
        // displacement measurement would compare pre-death and post-respawn positions
        if (player.getHealth() <= 0) {
            data.knockbackPending = false;
            return;
        }

        // Skip players in water  -  knockback is absorbed by fluid
        if (player.isInWater() || player.isInLava()) {
            data.knockbackPending = false;
            return;
        }
        // The player touched water shortly before the measurement  -  the
        // fluid already absorbed the horizontal impulse (edge cases: hit
        // near a shore or pushed from a boat).
        if (data.lastInWaterMs > 0
                && System.currentTimeMillis() - data.lastInWaterMs < 1000L) {
            data.knockbackPending = false;
            return;
        }
        // Mid-fall the horizontal impulse is hard to isolate (falling from
        // a high place, dismount, flight-off). Only ground hits are measured.
        if (!player.onGround() && player.fallDistance > 0.5) {
            data.knockbackPending = false;
            return;
        }

        if (!data.knockbackPending) return;

        data.knockbackTicksWaited++;
        if (data.knockbackTicksWaited < KNOCKBACK_CHECK_DELAY) return;

        // Player was knocked into a wall  -  the wall absorbed the horizontal
        // movement, so zero displacement is legitimate. horizontalCollision
        // is not reliable by itself (it only reflects the last tick's
        // movement), so also raycast along the knockback direction.
        if (player.horizontalCollision || blockedByWall(player, data)) {
            data.knockbackPending = false;
            return;
        }

        double dx           = player.getX() - data.knockbackStartX;
        double dz           = player.getZ() - data.knockbackStartZ;
        double displacement = Math.sqrt(dx * dx + dz * dz);

        data.knockbackPending = false;

        if (displacement < MIN_KNOCKBACK_DISPLACEMENT && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Ignored knockback: displacement=%.3f (min: %.2f)",
                            displacement, MIN_KNOCKBACK_DISPLACEMENT));
        }
    }

    /** True when a full block sits within the knockback path right behind the player. */
    private static boolean blockedByWall(ServerPlayer player, PlayerData data) {
        if (!data.knockbackDirSet) return false;
        Vec3 start = player.position().add(0.0, 0.6, 0.0);
        Vec3 end = start.add(new Vec3(data.knockbackDirX, 0.0, data.knockbackDirZ).scale(0.6));
        BlockHitResult hit = player.level().clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
