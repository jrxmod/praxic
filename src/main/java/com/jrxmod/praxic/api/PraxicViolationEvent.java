package com.jrxmod.praxic.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public final class PraxicViolationEvent {

    // Other mods listen to this event to react to PRAXIC violations
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> (player, checkName, violations, details, action) -> {
                for (Listener listener : listeners) {
                    listener.onViolation(player, checkName, violations, details, action);
                }
            }
    );

    @FunctionalInterface
    public interface Listener {
        /**
         * Called when a player is flagged by PRAXIC.
         *
         * @param player     the flagged player
         * @param checkName  name of the check that flagged (e.g. "FlyCheck")
         * @param violations current violation count for this check
         * @param details    human-readable details of the violation
         * @param action     action taken: "flag", "warn", "kick", or "ban"
         */
        void onViolation(ServerPlayer player, String checkName, int violations, String details, String action);
    }

    private PraxicViolationEvent() {}
}
