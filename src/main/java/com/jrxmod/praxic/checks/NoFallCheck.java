package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * A no-fall module claims {@code onGround=true} while the player is still
 * above the ground. The server trusts that bit, never accumulates
 * fallDistance and never applies fall damage  -  the exploit. The claim can be
 * carried by an injected status-only packet or by the ground bit of a
 * position packet; both are detected the same way: an onGround claim whose
 * distance to the nearest support (block, liquid or entity) exceeds a few
 * centimetres.
 *
 * Legitimate onGround claims always have support at the feet (standing on a
 * block, block edge, slab, boat, ...). A landing packet has support at
 * distance ~0; a real fall carries onGround=false, so it never counts.
 */
public class NoFallCheck extends AbstractCheck {

    /** Spoofed ground packets required before flagging (~0.25 s at 20 TPS). */
    private static final int SPOOF_THRESHOLD = 5;

    /** Support must be at least this far below the feet to count as a spoof. */
    private static final double MAX_GROUND_GAP = 0.35;

    @Override
    public String getName() {
        return "NoFallCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().noFallCheckEnabled) return;
        if (exempt(player)) {
            data.noFallSpoofTicks = 0;
            return;
        }
        if (data.joinGraceTicks > 0 || data.teleportGraceTicks > 0) return;
        if (player.isPassenger() || data.recentVehicleExit()) {
            data.noFallSpoofTicks = 0;
            return;
        }

        // Mirror of the packet logic for the already-processed position:
        // ground claims without support count, supported ground resets, and
        // airborne is neutral (see onMovePacket).
        if (player.onGround()) {
            if (supportGap(player, player.getX(), player.getY(), player.getZ()) > MAX_GROUND_GAP) {
                data.noFallSpoofTicks++;
            } else {
                data.noFallSpoofTicks = 0;
            }
        }
        flagIfNeeded(player, data);
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!Praxic.getConfig().noFallCheckEnabled) return;
        if (exempt(player)) {
            data.noFallSpoofTicks = 0;
            return;
        }
        if (data.joinGraceTicks > 0 || data.teleportGraceTicks > 0) return;
        if (player.isPassenger() || data.recentVehicleExit()) {
            data.noFallSpoofTicks = 0;
            return;
        }

        double x = packet.hasPosition() ? packet.getX(player.getX()) : player.getX();
        double y = packet.hasPosition() ? packet.getY(player.getY()) : player.getY();
        double z = packet.hasPosition() ? packet.getZ(player.getZ()) : player.getZ();

        // A grounded claim is the no-fall signal; its absence (onGround=false)
        // is not evidence either way  -  the module injects the spoof between
        // the honest movement packets, so those packets must not cancel the
        // counter. A grounded claim WITH support is a real landing and
        // resets it.
        double gap = supportGap(player, x, y, z);
        if (packet.isOnGround() && gap > MAX_GROUND_GAP) {
            data.noFallSpoofTicks++;
        } else if (packet.isOnGround()) {
            data.noFallSpoofTicks = 0;
        }
        flagIfNeeded(player, data);
    }

    private void flagIfNeeded(ServerPlayer player, PlayerData data) {
        if (data.noFallSpoofTicks >= SPOOF_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("On-ground claim while airborne for %d packets", data.noFallSpoofTicks));
            data.noFallSpoofTicks = 0;
        }
    }

    /**
     * Smallest vertical distance from the feet to a supporting surface below
     * (block top, liquid surface) over the player's 3x3 footprint. Entity
     * support is treated as distance 0. Returns infinity when no support
     * exists within the scan range.
     */
    private static double supportGap(ServerPlayer player, double x, double y, double z) {
        // Block support exactly under the feet, using the same query the
        // server uses for landings (findSupportingBlock). Scanning 3x3 cells
        // N blocks down misread walls / distant floors as support: any block
        // below the feet produced a negative gap, so the check never fired.
        AABB feetAabb = new AABB(x - 0.31, y - 0.02, z - 0.31, x + 0.31, y + 0.02, z + 0.31);
        if (player.level().findSupportingBlock(player, feetAabb).isPresent()) return 0.0;

        // Liquid directly under the feet also neutralises a fall.
        BlockPos foot = BlockPos.containing(x, y, z);
        if (!player.level().getFluidState(foot).isEmpty()
                || !player.level().getFluidState(foot.below()).isEmpty()) return 0.0;

        AABB box = new AABB(x - 1.2, y - 1.3, z - 1.2, x + 1.2, y + 0.3, z + 1.2);
        for (Entity entity : player.level().getEntitiesOfClass(Entity.class, box, e -> e != player)) {
            if (!(entity instanceof ItemEntity)) return 0.0;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean exempt(ServerPlayer player) {
        if (!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE)) return true;
        if (player.isSpectator()) return true;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return true;
        if (player.isDeadOrDying() || player.isPassenger()) return true;
        if (player.isFallFlying() || player.getAbilities().flying || player.getAbilities().mayfly) return true;
        return player.hasEffect(MobEffects.SLOW_FALLING);
    }
}
