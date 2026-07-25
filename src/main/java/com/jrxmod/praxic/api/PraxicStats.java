package com.jrxmod.praxic.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class PraxicStats {

    // Total flags since server start
    private static final AtomicInteger totalFlags = new AtomicInteger(0);

    // Flags per check since server start
    private static final Map<String, AtomicInteger> flagsByCheck =
            Collections.synchronizedMap(new HashMap<>());

    // Flags per player since server start
    private static final Map<UUID, AtomicInteger> flagsByPlayer =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, String> playerNames =
            Collections.synchronizedMap(new HashMap<>());

    // Register listener via PraxicViolationEvent to track stats
    public static void init() {
        PraxicViolationEvent.EVENT.register((player, checkName, violations, details, action) -> {
            totalFlags.incrementAndGet();
            flagsByCheck.computeIfAbsent(checkName, k -> new AtomicInteger(0))
                    .incrementAndGet();
            UUID uuid = player.getUUID();
            playerNames.put(uuid, player.getName().getString());
            flagsByPlayer.computeIfAbsent(uuid, k -> new AtomicInteger(0))
                    .incrementAndGet();
            // Stats listener never cancels PRAXIC's action
            return false;
        });
    }

    public static int getTotalFlags() {
        return totalFlags.get();
    }

    public static Map<String, Integer> getFlagsByCheck() {
        Map<String, Integer> result = new HashMap<>();
        flagsByCheck.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public static Map<String, Integer> getFlagsByPlayer() {
        Map<String, Integer> result = new HashMap<>();
        flagsByPlayer.forEach((uuid, v) -> result.put(
                playerNames.getOrDefault(uuid, uuid.toString()), v.get()));
        return result;
    }

    public static Map<String, Integer> getTopChecks(int limit) {
        return getFlagsByCheck().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static Map<String, Integer> getTopPlayers(int limit) {
        return getFlagsByPlayer().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    private PraxicStats() {}
}
