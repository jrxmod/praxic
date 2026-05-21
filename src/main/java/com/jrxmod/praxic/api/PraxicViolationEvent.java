package com.jrxmod.praxic.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public final class PraxicViolationEvent {

    // Returns true if the action should be cancelled (e.g. REVEX handles punishment instead)
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> (player, checkName, violations, details, action) -> {
                boolean cancelled = false;
                for (Listener listener : listeners) {
                    if (listener.onViolation(player, checkName, violations, details, action)) {
                        cancelled = true;
                    }
                }
                return cancelled;
            }
    );

    @FunctionalInterface
    public interface Listener {
        /**
         * Called when a player is flagged by PRAXIC.
         * Return true to cancel PRAXIC's built-in action (kick/ban/setback/warn).
         * PRAXIC will still log and send staff alerts — only the punishment is skipped.
         *
         * @param player     the flagged player
         * @param checkName  name of the check that flagged (e.g. "FlyCheck")
         * @param violations current violation count for this check
         * @param details    human-readable details of the violation
         * @param action     action that would be taken: "flag", "warn", "kick", "ban", "setback"
         * @return true to cancel PRAXIC's action, false to let PRAXIC handle it normally
         */
        boolean onViolation(ServerPlayer player, String checkName, int violations, String details, String action);
    }

    private PraxicViolationEvent() {}
}
