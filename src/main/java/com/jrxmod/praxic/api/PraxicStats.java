package com.jrxmod.praxic.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class PraxicStats {

    // Total flags since server start
    private static final AtomicInteger totalFlags = new AtomicInteger(0);

    // Flags per check since server start
    private static final Map<String, AtomicInteger> flagsByCheck =
            Collections.synchronizedMap(new HashMap<>());

    // Register listener via PraxicViolationEvent to track stats
    public static void init() {
        PraxicViolationEvent.EVENT.register((player, checkName, violations, details, action) -> {
            totalFlags.incrementAndGet();
            flagsByCheck.computeIfAbsent(checkName, k -> new AtomicInteger(0))
                    .incrementAndGet();
            // Stats listener never cancels PRAXIC's action
            return false;
        });
    }

    // Returns total flags since server start
    public static int getTotalFlags() {
        return totalFlags.get();
    }

    // Returns flags per check since server start
    public static Map<String, Integer> getFlagsByCheck() {
        Map<String, Integer> result = new HashMap<>();
        flagsByCheck.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    // Returns top N checks sorted by flag count descending
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

    private PraxicStats() {}
}
