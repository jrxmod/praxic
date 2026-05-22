package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class RotationCheck extends AbstractCheck {

    // Snap angle threshold per tick (degrees). Humans can physically reach ~120-130 deg/tick
    // at extreme sensitivity; values above 150 are effectively impossible without assistance.
    private static final float MAX_SNAP_ANGLE = 150f;

    // Player must have attacked within this window to trigger snap evaluation.
    // Snap without combat context is not flagged — too many false positives.
    private static final long COMBAT_WINDOW_MS = 3000L;

    // Snap buffer threshold: this many consecutive suspicious ticks = flag
    private static final int SNAP_BUFFER_THRESHOLD = 3;

    @Override
    public String getName() {
        return "RotationCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().rotationCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;

        // Only evaluate rotation during or shortly after combat
        boolean inCombat = (System.currentTimeMillis() - data.lastAttackTime) < COMBAT_WINDOW_MS;
        if (!inCombat) {
            // Decay buffer when outside combat window
            if (data.rotationSnapBuffer > 0) data.rotationSnapBuffer--;
            return;
        }

        float currentYaw = player.getYRot();
        float prevYaw    = data.lastYaw;

        // Handle 360-degree wraparound: 350 -> 10 should be 20 deg, not 340 deg
        float rawDelta  = Math.abs(currentYaw - prevYaw);
        float deltaYaw  = rawDelta > 180f ? 360f - rawDelta : rawDelta;

        if (deltaYaw > MAX_SNAP_ANGLE) {
            data.rotationSnapBuffer++;
        } else {
            // Clean tick — decay buffer
            if (data.rotationSnapBuffer > 0) data.rotationSnapBuffer--;
        }

        if (data.rotationSnapBuffer >= SNAP_BUFFER_THRESHOLD
                && data.canFlag(getName(), 2500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Rotation snap %.1f deg/tick (threshold: %.0f, buffer: %d)",
                            deltaYaw, MAX_SNAP_ANGLE, data.rotationSnapBuffer));
            data.rotationSnapBuffer = 0;
        }
    }
}
