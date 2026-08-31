package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.physics.ImpulseEngine;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.GameType;

/**
 * Detects fake mace smash: attacking with a mace while hovering (long air time,
 * not actually falling) with fallDistance below the vanilla 1.5-block smash
 * threshold. A real smash records an Impulse MACE window so movement
 * mitigation does not rubberband the hang-time.
 *
 * Jump-peak mace hits (short air time, small fallDistance) are vanilla and
 * are not flagged.
 */
public class MaceSmashCheck extends AbstractCheck {

    private static final float VANILLA_SMASH_FALL = 1.5f;
    private static final int HOVER_AIR_TICKS = 25;
    private static final int BUFFER_THRESHOLD = 2;

    @Override
    public String getName() {
        return "MaceSmashCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven from ServerGamePacketListenerMixin.
    }

    /**
     * @return true if the attack should be cancelled (mitigation)
     */
    public boolean checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {
        if (!Praxic.getConfig().maceSmashCheckEnabled) return false;
        if (attacker.isSpectator()) return false;
        if (attacker.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (attacker.isDeadOrDying()) return false;
        if (data.joinGraceTicks > 0) return false;
        if (Praxic.getWhitelistManager() != null
                && Praxic.getWhitelistManager().isWhitelisted(attacker.getUUID())) return false;
        if (!(attacker.getMainHandItem().getItem() instanceof MaceItem)) return false;

        float fall = attacker.fallDistance;
        if (fall >= VANILLA_SMASH_FALL) {
            if (Praxic.getImpulseEngine() != null) {
                Praxic.getImpulseEngine().record(attacker.getUUID(), ImpulseEngine.Kind.MACE);
            }
            data.maceSmashBuffer = 0;
            return false;
        }

        double dy = attacker.getY() - data.prevY;
        boolean hoverHit = !attacker.onGround()
                && data.airTicks >= HOVER_AIR_TICKS
                && dy >= -0.05
                && fall < VANILLA_SMASH_FALL;

        if (!hoverHit) {
            data.maceSmashBuffer = Math.max(0, data.maceSmashBuffer - 1);
            return false;
        }

        data.maceSmashBuffer++;
        if (data.maceSmashBuffer < BUFFER_THRESHOLD) return false;

        if (data.canFlag(getName(), 2000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Mace hover-smash fall=%.2f airTicks=%d dy=%.3f",
                            fall, data.airTicks, dy));
        }
        data.maceSmashBuffer = 0;
        return Praxic.getConfig().enableMitigation;
    }
}
