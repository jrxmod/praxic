package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Scaffold cheats place a block into the cell directly under the player's
 * feet (or the 4 horizontal neighbors of that cell) while walking. Vanilla
 * rotation-only packets are not a signal: the client sends them on any turn
 * or stop, so a corridor of look packets plus a place is normal play. The
 * placement geometry plus the moving window is what distinguishes ScaffoldWalk.
 */
public class ScaffoldCheck extends AbstractCheck {

    private static final long WINDOW_MS = 2000;
    private static final double MIN_WINDOW_MOVE = 1.2;
    /**
     * Under-foot placements in the movement window. Each one must be preceded
     * by a rotation-only look packet (see WALK_LOOK_MS): automated bridging
     * faces the target cell and sends that packet immediately before the
     * click, while a player holding right-click and running places blocks
     * without signalling a separate look anywhere near the click. Fast manual
     * bridging reaches 7-8 placements in under 2 seconds, so the threshold is
     * kept clearly above human clicking; automated bridging places ~5/s with
     * a look packet every time and still trips it.
     */
    private static final int PLACE_THRESHOLD = 8;
    private static final long RATE_WINDOW_MS = 1000L;
    private static final long WALK_LOOK_MS = 100L;

    // Random-place heuristic: placements elsewhere than the under-foot cell,
    // each preceded by a rotation-only look packet and sustained over the
    // window.
    private static final long RANDOM_WINDOW_MS = 2000L;
    private static final long RANDOM_LOOK_MS = 250L;
    private static final int RANDOM_THRESHOLD = 5;
    private static final double RANDOM_MAX_RADIUS = 6.0;
    /**
     * Random-build modules scatter in a cube around the player. On flat
     * ground all successful placements are one cell above ground, so
     * direction scatter is the flat-ground signal; in built-up terrain
     * height scatter appears. Either is enough; manual building stays on one
     * direction and one level.
     */
    private static final int RANDOM_MIN_SECTORS = 3;
    private static final int RANDOM_MIN_Y_LEVELS = 2;

    @Override
    public String getName() {
        return "ScaffoldCheck";
    }

    public void onBlockPlace(ServerPlayer player, BlockPos placedPos, PlayerData data) {
        if (!Praxic.getConfig().scaffoldCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        int playerY = player.blockPosition().getY();
        int dxb = Math.abs(placedPos.getX() - player.blockPosition().getX());
        int dzb = Math.abs(placedPos.getZ() - player.blockPosition().getZ());

        long now = System.currentTimeMillis();

        // Random-place heuristic: placements in cells other than the
        // under-foot cell, inside the module radius, each preceded by a
        // rotation-only look packet (the module has to face a freshly picked
        // random block). A real run jumps to fresh random
        // cells, so it scatters across heights or directions; manual building
        // keeps one direction, one height and chains blocks adjacently. The
        // look packet resets on any manual turn, the scatter rule covers flat
        // ground and built-up terrain, and the non-adjacency rule keeps
        // roofs/shelters built around the player from flagging.
        boolean lookSpoof = now - data.lastLookOnlyMs <= RANDOM_LOOK_MS;
        if (!lookSpoof) {
            resetRandomPlace(data);
        } else if (placedPos.getY() != playerY - 1
                && Math.hypot(dxb, dzb) <= RANDOM_MAX_RADIUS) {
            // Restart the streak on a placement adjacent to the previous one:
            // random-build modules jump to a fresh random cell each time
            // (adjacent pairs are rare), manual building chains together.
            boolean adjacentToPrev = data.randomPlaceCount > 0
                    && Math.abs(placedPos.getX() - data.randomPlacePrevX) <= 1
                    && Math.abs(placedPos.getY() - data.randomPlacePrevY) <= 1
                    && Math.abs(placedPos.getZ() - data.randomPlacePrevZ) <= 1;
            if (data.randomPlaceCount == 0
                    || now - data.randomPlaceWindowStart > RANDOM_WINDOW_MS
                    || adjacentToPrev) {
                resetRandomPlace(data);
                data.randomPlaceWindowStart = now;
            }
            data.randomPlaceCount++;
            data.randomPlacePrevX = placedPos.getX();
            data.randomPlacePrevY = placedPos.getY();
            data.randomPlacePrevZ = placedPos.getZ();
            int offsetY = placedPos.getY() - playerY;
            if (offsetY >= -10 && offsetY <= 10) {
                data.randomPlaceYBits |= 1 << (offsetY + 10);
            }
            double dirX = placedPos.getX() - player.getX();
            double dirZ = placedPos.getZ() - player.getZ();
            int sector = (int) Math.round(Math.atan2(dirZ, dirX) / (Math.PI / 4.0)) & 7;
            data.randomPlaceSectors |= 1 << sector;
            boolean scattered = Integer.bitCount(data.randomPlaceSectors) >= RANDOM_MIN_SECTORS
                    || Integer.bitCount(data.randomPlaceYBits) >= RANDOM_MIN_Y_LEVELS;
            if (data.randomPlaceCount >= RANDOM_THRESHOLD
                    && scattered
                    && data.canFlag(getName() + "_random", 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Random placement: %d scattered places in %dms (%d heights, %d directions)",
                                data.randomPlaceCount, now - data.randomPlaceWindowStart,
                                Integer.bitCount(data.randomPlaceYBits),
                                Integer.bitCount(data.randomPlaceSectors)));
                resetRandomPlace(data);
            }
        }

        if (placedPos.getY() != playerY - 1 || dxb > 1 || dzb > 1) return;

        // A placement without the preceding rotation-only packet is a manual
        // click; it must not count toward the automated-bridge window.
        if (now - data.lastLookOnlyMs > WALK_LOOK_MS) {
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
            return;
        }

        if (data.scaffoldBlocksPlaced == 0 || now - data.scaffoldWindowStart > WINDOW_MS) {
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
            data.scaffoldStartX = player.getX();
            data.scaffoldStartZ = player.getZ();
        }

        // Configurable rate gate: sustained under-foot placement above
        // scaffoldMaxBlocksPerSecond is automated even without movement.
        while (!data.scaffoldPlaceTimes.isEmpty()
                && now - data.scaffoldPlaceTimes.peekFirst() > RATE_WINDOW_MS) {
            data.scaffoldPlaceTimes.pollFirst();
        }
        data.scaffoldPlaceTimes.addLast(now);
        int maxRate = Praxic.getConfig().scaffoldMaxBlocksPerSecond;
        if (data.scaffoldPlaceTimes.size() > maxRate
                && data.canFlag(getName() + "_rate", 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Scaffold rate: %d under-foot places in rolling 1s (max %d)",
                            data.scaffoldPlaceTimes.size(), maxRate));
            data.scaffoldPlaceTimes.clear();
        }

        data.scaffoldBlocksPlaced++;
        double moved = Math.hypot(player.getX() - data.scaffoldStartX,
                player.getZ() - data.scaffoldStartZ);
        if (data.scaffoldBlocksPlaced >= PLACE_THRESHOLD
                && moved >= MIN_WINDOW_MOVE
                && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("ScaffoldWalk: %d under-foot places in %dms moved=%.2f",
                            data.scaffoldBlocksPlaced, now - data.scaffoldWindowStart, moved));
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
            data.scaffoldStartX = player.getX();
            data.scaffoldStartZ = player.getZ();
        }
    }

    private static void resetRandomPlace(PlayerData data) {
        data.randomPlaceCount = 0;
        data.randomPlaceYBits = 0;
        data.randomPlaceSectors = 0;
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {}
}
