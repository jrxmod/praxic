package com.jrxmod.praxic.util;

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

    // Color codes for Discord embeds
    private static final int COLOR_FLAG = 0xFFA500;  // orange — violation flagged
    private static final int COLOR_KICK = 0xFF4444;  // red — player kicked
    private static final int COLOR_BAN  = 0x8B0000;  // dark red — player banned

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
            case "kick" -> "⚠️ Kicked";
            case "ban"  -> "🔨 Banned";
            default     -> "🚩 Flagged";
        };

        String timestamp = Instant.now().toString();

        // Build Discord embed JSON payload
        String payload = String.format("""
                {
                  "embeds": [{
                    "title": "%s — %s",
                    "color": %d,
                    "fields": [
                      { "name": "Player",     "value": "%s", "inline": true },
                      { "name": "Check",      "value": "%s", "inline": true },
                      { "name": "Violations", "value": "%d", "inline": true },
                      { "name": "Details",    "value": "%s", "inline": false },
                      { "name": "Action",     "value": "%s", "inline": true }
                    ],
                    "footer": { "text": "PRAXIC AntiCheat" },
                    "timestamp": "%s"
                  }]
                }
                """,
                actionLabel, checkName,
                color,
                playerName,
                checkName,
                violations,
                details.replace("\"", "'"),
                actionLabel,
                timestamp
        );

        // Send async — does not block server thread
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
}
