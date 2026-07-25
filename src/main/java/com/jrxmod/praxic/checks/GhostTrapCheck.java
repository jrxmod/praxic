package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Formal check identity for invisible honeypot hits recorded by GhostEntityManager.
 */
public class GhostTrapCheck extends AbstractCheck {

    @Override
    public String getName() {
        return "GhostTrapCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven by GhostEntityManager when a honeypot entity is attacked.
    }
}
