package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class ReachCheck extends AbstractCheck {

    // Vanilla reach: 3.0 survival, 4.5 creative
    // 5.0 accounts for hitbox center offset, network latency and server tick delay
    // Real reach cheats start at 6.0+
    private static final double MAX_REACH_SURVIVAL = 5.0;
    private static final double MAX_REACH_CREATIVE = 6.0;

    @Override
    public String getName() {
        return "ReachCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven check — called from ServerGamePacketListenerMixin
    }

    public void checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {

        if (!Praxic.getConfig().reachCheckEnabled) return;

        if (attacker.isSpectator()) return;
        if (attacker.isDeadOrDying()) return;

        boolean isCreative = attacker.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        double maxReach = isCreative ? MAX_REACH_CREATIVE : MAX_REACH_SURVIVAL;

        int ping = attacker.connection.latency();

        // Expand reach threshold based on player latency
        maxReach += LagCompensation.extraReach(ping);

        // Distance from attacker eye position to target bounding box center.
        // Using center instead of position() (feet) avoids false positives
        // when attacking from above or at an angle.
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double distance = attacker.getEyePosition().distanceTo(targetCenter);

        if (distance > maxReach && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack distance: %.2f blocks (max: %.2f, ping: %dms)",
                            distance, maxReach, ping));
        }
    }
}
