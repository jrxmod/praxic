package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * Position packets that claim onGround while both the feet and the block
 * below are air. StatusOnly (no position) is NoFall, not this check: vanilla
 * and NoFall both send those while standing.
 */
public class GroundSpoofCheck extends AbstractCheck {

    private static final int SPOOF_THRESHOLD = 8;

    @Override
    public String getName() {
        return "GroundSpoofCheck";
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!Praxic.getConfig().groundSpoofCheckEnabled) return;
        if (!packet.hasPosition()) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly || player.isDeadOrDying()) return;
        if (player.isPassenger() || player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying() || player.onClimbable()) return;
        if (data.joinGraceTicks > 0) return;

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());

        if (!packet.isOnGround()) {
            data.groundSpoofTicks = Math.max(0, data.groundSpoofTicks - 1);
            return;
        }
        if (standing(player, x, y, z)) {
            data.groundSpoofTicks = 0;
            return;
        }

        data.groundSpoofTicks++;
        if (data.groundSpoofTicks >= SPOOF_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("onGround=true with no foot collision at y=%.2f (n=%d)",
                            y, data.groundSpoofTicks));
            data.groundSpoofTicks = 0;
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
    }

    private static boolean standing(ServerPlayer player, double x, double y, double z) {
        AABB feetAabb = new AABB(x - 0.31, y - 0.04, z - 0.31, x + 0.31, y + 0.04, z + 0.31);
        if (player.level().findSupportingBlock(player, feetAabb).isPresent()) return true;

        BlockPos feet = BlockPos.containing(x, y, z);
        if (!player.level().getFluidState(feet).isEmpty()
                || !player.level().getFluidState(feet.below()).isEmpty()) return true;

        AABB supportBox = new AABB(x - 0.35, y - 0.35, z - 0.35, x + 0.35, y + 0.15, z + 0.35);
        for (Entity entity : player.level().getEntitiesOfClass(Entity.class, supportBox, e -> e != player)) {
            if (!(entity instanceof ItemEntity)) return true;
        }
        return false;
    }
}
