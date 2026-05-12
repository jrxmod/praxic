package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class TimerCheck extends AbstractCheck {

    // 5-second sliding window
    private static final long WINDOW_MS = 5000L;

    // Vanilla sprint+jump peaks at ~40 pos packets/sec
    // Timer x1.5 = ~55/sec average = 275 packets in 5s
    // Threshold set above vanilla peaks, below Timer x1.5
    private static final int MAX_PACKETS_IN_WINDOW = 275;

    @Override
    public String getName() {
        return "TimerCheck";
    }

    // Tick-based check not used — fully event-driven via onMovePacket()
    @Override
    public void check(ServerPlayer player, PlayerData data) {}

    public void onMovePacket(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().timerCheckEnabled) return;
        if (player.isDeadOrDying()) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        long now = System.currentTimeMillis();

        // Add current packet timestamp to sliding window
        data.movePacketTimestamps.addLast(now);

        // Remove timestamps outside the 5-second window
        while (!data.movePacketTimestamps.isEmpty() &&
               now - data.movePacketTimestamps.peekFirst() > WINDOW_MS) {
            data.movePacketTimestamps.pollFirst();
        }

        // Only evaluate after at least 4 seconds of data to avoid join/teleport spikes
        long windowStart = data.movePacketTimestamps.isEmpty() ? now
                : data.movePacketTimestamps.peekFirst();
        long elapsed = now - windowStart;
        if (elapsed < 4000) return;

        int packets = data.movePacketTimestamps.size();

        if (packets > MAX_PACKETS_IN_WINDOW && data.canFlag(getName(), 3000)) {
            double avgPerSec = packets / (elapsed / 1000.0);
            ViolationManager.flag(player, data, this,
                    String.format("Avg %.1f packets/sec over %.1fs (max: %d in 5s)",
                            avgPerSec, elapsed / 1000.0, MAX_PACKETS_IN_WINDOW));
        }
    }
}
