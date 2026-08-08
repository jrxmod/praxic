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

    // Skip evaluation when server TPS is low — clients send more catch-up packets
    private static final double MIN_TPS_FOR_CHECK = 17.0;

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

        data.movePacketTimestamps.addLast(now);

        while (!data.movePacketTimestamps.isEmpty() &&
               now - data.movePacketTimestamps.peekFirst() > WINDOW_MS) {
            data.movePacketTimestamps.pollFirst();
        }

        long windowStart = data.movePacketTimestamps.isEmpty() ? now
                : data.movePacketTimestamps.peekFirst();
        long elapsed = now - windowStart;
        if (elapsed < 4000) return;

        // TPS guard: skip when server is lagging. Use reflection to avoid hard dependency on
        // mappings where getAverageTickTime name differs between Yarn and Mojang official.
        try {
            var srv = player.getServer();
            double mspt = -1;
            try {
                var m = srv.getClass().getMethod("getAverageTickTime");
                Object v = m.invoke(srv);
                if (v instanceof Number n) mspt = n.doubleValue();
            } catch (NoSuchMethodException e) {
                // Try alternative names used in some mappings
                try {
                    var m2 = srv.getClass().getMethod("getAverageTickTimeNanos");
                    Object v = m2.invoke(srv);
                    if (v instanceof Number n) mspt = n.doubleValue() / 1_000_000.0;
                } catch (Exception ignored) {}
            }
            if (mspt > 0) {
                double tps = Math.min(20.0, 1000.0 / mspt);
                if (tps < MIN_TPS_FOR_CHECK) return;
            }
        } catch (Exception ignored) {}

        int packets = data.movePacketTimestamps.size();

        if (packets > MAX_PACKETS_IN_WINDOW && data.canFlag(getName(), 3000)) {
            double avgPerSec = packets / (elapsed / 1000.0);
            ViolationManager.flag(player, data, this,
                    String.format("Avg %.1f packets/sec over %.1fs (max: %d in 5s)",
                            avgPerSec, elapsed / 1000.0, MAX_PACKETS_IN_WINDOW));
        }
    }
}
