package com.jrxmod.praxic.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jrxmod.praxic.Praxic;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/praxic/version?loaders=[%22fabric%22]&game_versions=[%221.21.1%22]";

    private static volatile String latestVersion = null;

    public static void init() {
        if (!Praxic.getConfig().enableUpdateChecker) return;

        String currentVersion = FabricLoader.getInstance()
                .getModContainer(Praxic.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        Praxic.LOGGER.info("[PRAXIC] Checking for updates... (current: {})", currentVersion);

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MODRINTH_API))
                        .header("User-Agent", "jrxmod/praxic/" + currentVersion)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    Praxic.LOGGER.warn("[PRAXIC] Update check failed: HTTP {}", response.statusCode());
                    return;
                }

                try {
                    JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        JsonElement first = arr.get(0);
                        if (first.isJsonObject()) {
                            JsonObject obj = first.getAsJsonObject();
                            if (obj.has("version_number")) {
                                String remote = obj.get("version_number").getAsString();
                                if (!remote.equalsIgnoreCase(currentVersion)) {
                                    latestVersion = remote;
                                    Praxic.LOGGER.info("[PRAXIC] Update available: {} -> {}", currentVersion, remote);
                                } else {
                                    Praxic.LOGGER.info("[PRAXIC] PRAXIC is up to date ({}).", currentVersion);
                                }
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    Praxic.LOGGER.warn("[PRAXIC] Update check JSON parse failed: {}", parseEx.getMessage());
                }

            } catch (Exception e) {
                Praxic.LOGGER.warn("[PRAXIC] Update check failed: {}", e.getMessage());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (latestVersion == null) return;
            if (!handler.getPlayer().hasPermissions(2)) return;

            String current = FabricLoader.getInstance()
                    .getModContainer(Praxic.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");

            handler.getPlayer().sendSystemMessage(Component.literal(
                "§6[PRAXIC] §eUpdate available! §7" + current + " §8→ §a" + latestVersion +
                "\n§8» §7Download: §bhttps://modrinth.com/mod/praxic"
            ));
        });
    }

    public static String getLatestVersion() {
        return latestVersion;
    }
}
