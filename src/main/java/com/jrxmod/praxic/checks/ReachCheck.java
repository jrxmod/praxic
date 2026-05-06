package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

public class ReachCheck extends AbstractCheck {

    // Vanilla reach: 3.0 survival, 4.5 creative
    // Buffer accounts for mob movement, network latency and server tick delay
    private static final double MAX_REACH_SURVIVAL = 4.2;
    private static final double MAX_REACH_CREATIVE = 5.5;

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

        // Distance from attacker eye position to target center
        double distance = attacker.getEyePosition().distanceTo(target.position());

        if (distance > maxReach && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack distance: %.2f blocks (max: %.2f)", distance, maxReach));
        }
    }
}
