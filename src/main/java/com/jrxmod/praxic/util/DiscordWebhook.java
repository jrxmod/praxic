package com.jrxmod.praxic.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jrxmod.praxic.Praxic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class DiscordWebhook {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final int COLOR_FLAG = 0xFFA500;
    private static final int COLOR_KICK = 0xFF4444;
    private static final int COLOR_BAN  = 0x8B0000;

    public static void send(String playerName, String checkName, int violations, String details, String action) {
        if (!Praxic.getConfig().enableDiscordWebhook) return;
        String url = Praxic.getConfig().discordWebhookUrl;
        if (url == null || url.isBlank() || url.equals("YOUR_WEBHOOK_URL_HERE")) return;

        int color = switch (action.toLowerCase()) {
            case "kick" -> COLOR_KICK;
            case "ban"  -> COLOR_BAN;
            default     -> COLOR_FLAG;
        };

        String actionLabel = switch (action.toLowerCase()) {
            case "kick" -> "Kicked";
            case "ban"  -> "Banned";
            default     -> "Flagged";
        };

        String timestamp = Instant.now().toString();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", actionLabel + " - " + checkName);
        embed.addProperty("color", color);

        JsonArray fields = new JsonArray();
        fields.add(field("Player", playerName, true));
        fields.add(field("Check", checkName, true));
        fields.add(field("Violations", String.valueOf(violations), true));
        String safeDetails = details.length() > 1024 ? details.substring(0, 1024) : details;
        fields.add(field("Details", safeDetails, false));
        fields.add(field("Action", actionLabel, true));

        // Context footer — session flag count and server TPS
        int sessionFlags = com.jrxmod.praxic.api.PraxicStats.getTotalFlags();
        double tps = com.jrxmod.praxic.manager.CheckManager.getCurrentTps();
        fields.add(field("Session Flags", String.valueOf(sessionFlags), true));
        fields.add(field("Server TPS", String.format("%.1f", tps), true));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "PRAXIC AntiCheat");
        embed.add("footer", footer);
        embed.addProperty("timestamp", timestamp);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject root = new JsonObject();
        root.add("embeds", embeds);
        String payload = root.toString();

        CLIENT.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Praxic.LOGGER.warn("[PRAXIC] Discord webhook failed: HTTP {}", response.statusCode());
            }
        }).exceptionally(e -> {
            Praxic.LOGGER.warn("[PRAXIC] Discord webhook error: {}", e.getMessage());
            return null;
        });
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }
}
