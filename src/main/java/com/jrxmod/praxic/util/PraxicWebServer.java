package com.jrxmod.praxic.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicStats;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.PlayerAnalytics;
import com.jrxmod.praxic.engine.analysis.PlayerBaseline;
import com.jrxmod.praxic.manager.EvidenceManager;
import com.jrxmod.praxic.manager.HistoryManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP dashboard server bound to 127.0.0.1 only.
 * Serves multi-file console UI from resources/dashboard/ with token injection.
 * Optional token auth via X-Praxic-Token header or ?token= query param.
 */
public class PraxicWebServer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HttpServer      httpServer;
    private ExecutorService executor;
    private MinecraftServer mcServer;

    // Cache for static resources
    private final Map<String, String> templateCache = new HashMap<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start(MinecraftServer server, int port) {
        this.mcServer = server;
        loadTemplates();
        try {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            executor = Executors.newFixedThreadPool(4);
            httpServer.setExecutor(executor);
            // Main console routes
            httpServer.createContext("/", this::handleRoot);
            // API
            httpServer.createContext("/api/players", this::handlePlayers);
            httpServer.createContext("/api/players/all", this::handleAllPlayers);
            httpServer.createContext("/api/player/", this::handlePlayer);
            httpServer.createContext("/api/status", this::handleStatus);
            httpServer.createContext("/api/incidents", this::handleIncidents);
            httpServer.createContext("/api/incidents/all", this::handleIncidentsAll);
            httpServer.createContext("/api/metrics", this::handleMetrics);
            httpServer.createContext("/api/action/reset", this::handleActionReset);
            httpServer.createContext("/api/action/whitelist", this::handleActionWhitelist);
            httpServer.createContext("/api/whitelist", this::handleWhitelist);
            httpServer.createContext("/api/revex/status", this::handleRevexStatus);
            httpServer.createContext("/api/revex/bans", this::handleRevexBans);
            httpServer.createContext("/api/revex/escalation", this::handleRevexEscalation);
            httpServer.createContext("/api/revex/inspect/", this::handleRevexInspect);
            httpServer.createContext("/api/action/revex/unban", this::handleRevexUnban);
            httpServer.createContext("/api/action/revex/reset", this::handleRevexReset);
            httpServer.createContext("/api/action/revex/pardon", this::handleRevexPardon);
            // Static assets
            httpServer.createContext("/css/", this::handleStatic);
            httpServer.createContext("/js/", this::handleStatic);
            httpServer.start();
            Praxic.LOGGER.info("[PRAXIC] Web console started at http://127.0.0.1:{}/", port);
            String token = Praxic.getConfig().webDashboardToken;
            if (token != null && !token.isEmpty()) {
                Praxic.LOGGER.info("[PRAXIC] Console token auth enabled.");
            }
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to start web console on port {}.", port, e);
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            Praxic.LOGGER.info("[PRAXIC] Web console stopped.");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void loadTemplates() {
        // Load all known templates
        String[] resources = {
                "dashboard/index.html",
                "dashboard/checks.html",
                "dashboard/settings.html",
                "dashboard/overview.html",
                "dashboard/css/app.css",
                "dashboard/js/i18n.js",
                "dashboard/js/api.js",
                "dashboard/js/panels.js",
                "dashboard/js/app.js",
                // legacy single file fallback
                "dashboard.html"
        };
        for (String res : resources) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(res)) {
                if (is != null) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    templateCache.put(res, content);
                    if (res.equals("dashboard/index.html")) {
                        templateCache.put("dashboard.html", content); // legacy key
                    }
                }
            } catch (IOException e) {
                Praxic.LOGGER.warn("[PRAXIC] Failed to read resource {}", res);
            }
        }
        if (!templateCache.containsKey("dashboard/index.html") && !templateCache.containsKey("dashboard.html")) {
            Praxic.LOGGER.error("[PRAXIC] No dashboard templates found!");
        }
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    private boolean isAuthorised(HttpExchange ex) {
        String required = Praxic.getConfig().webDashboardToken;
        if (required == null || required.isEmpty()) return true;
        String header = ex.getRequestHeaders().getFirst("X-Praxic-Token");
        if (required.equals(header)) return true;
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            try {
                for (String part : query.split("&")) {
                    if (part.startsWith("token=")) {
                        String val = URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
                        if (required.equals(val)) return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static final String UNAUTH_HTML =
        "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
        + "<style>body{background:#0a0a0b;color:#71717a;font-family:system-ui;"
        + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}"
        + "h1{color:#fafafa;font-size:18px;margin-bottom:8px}"
        + "code{background:#18181b;padding:2px 6px;border-radius:4px;color:#6366f1;font-family:monospace}"
        + "</style></head><body><div style='text-align:center'>"
        + "<h1>401 - Unauthorised</h1>"
        + "<p>Open <code>http://127.0.0.1:PORT/?token=YOUR_TOKEN</code></p>"
        + "</div></body></html>";

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendHtml(ex, 401, UNAUTH_HTML); return; }

        String path = ex.getRequestURI().getPath();
        // Normalize
        if (path == null || path.isEmpty()) path = "/";
        // Route to template
        String resourceKey;
        switch (path) {
            case "/":
            case "/index.html":
            case "/dashboard":
            case "/dashboard/":
                resourceKey = "dashboard/index.html";
                break;
            case "/checks":
            case "/checks/":
            case "/checks.html":
                resourceKey = "dashboard/checks.html";
                break;
            case "/settings":
            case "/settings/":
            case "/settings.html":
                resourceKey = "dashboard/settings.html";
                break;
            case "/overview":
            case "/overview/":
            case "/overview.html":
                resourceKey = "dashboard/overview.html";
                break;
            case "/dashboard.html":
                resourceKey = "dashboard.html";
                break;
            default:
                // Try direct resource mapping: e.g. /dashboard/index.html
                if (path.startsWith("/dashboard/")) {
                    resourceKey = path.substring(1); // remove leading /
                } else {
                    // Unknown path -> serve index for SPA fallback
                    resourceKey = "dashboard/index.html";
                }
                break;
        }

        String template = templateCache.get(resourceKey);
        if (template == null) {
            // Try legacy
            template = templateCache.get("dashboard.html");
            if (template == null) {
                sendHtml(ex, 404, "<h1>404 - Console template not found: " + escapeHtml(resourceKey) + "</h1>");
                return;
            }
        }

        String token = Praxic.getConfig().webDashboardToken;
        // Safe injection using JSON encoding to prevent XSS
        String safeToken = GSON.toJson(token != null ? token : "");
        // Remove surrounding quotes from GSON string for JS assignment? Actually we want a JS string literal.
        // Our HTML uses window.PRAXIC_TOKEN = '{{TOKEN}}'; so we need to replace '{{TOKEN}}' with safe inner content without outer quotes? Let's do full replacement.
        // The HTML contains '{{TOKEN}}' inside single quotes. We replace the placeholder including quotes with JSON string.
        // Safer: replace '{{TOKEN}}' with token value escaped, but also handle old pattern "'{{TOKEN}}'"
        String html = template;
        // Replace "'{{TOKEN}}'" pattern (old dashboard) with JSON string
        html = html.replace("'{{TOKEN}}'", safeToken);
        // Replace "{{TOKEN}}" pattern (new) with raw token escaped for JS (without quotes, because we do JSON)
        // In new files we have window.PRAXIC_TOKEN = '{{TOKEN}}'; -> after replace "'{{TOKEN}}'" it becomes JSON string, so we also handle "{{TOKEN}}" alone
        html = html.replace("{{TOKEN}}", token != null ? escapeJs(token) : "");
        // Also replace safe placeholder if we used window.PRAXIC_TOKEN = '{{TOKEN}}' and we already replaced "'{{TOKEN}}'" -> that covers it.
        // For extra safety, replace any remaining {{TOKEN}} with empty
        sendHtml(ex, 200, html);
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        String path = ex.getRequestURI().getPath(); // e.g. /css/app.css or /js/app.js
        String resourceKey = "dashboard" + path; // dashboard/css/app.css
        String content = templateCache.get(resourceKey);
        if (content == null) {
            // Try without dashboard prefix for legacy?
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String contentType = "text/plain; charset=utf-8";
        if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
        else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
        else if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";

        byte[] b = content.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void handlePlayers(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonArray arr = new JsonArray();
        for (ServerPlayer player : mcServer.getPlayerList().getPlayers()) {
            UUID uuid           = player.getUUID();
            PlayerData data     = Praxic.getCheckManager().getPlayerData(uuid);
            double confidence   = Praxic.getConfidenceEngine().getScore(uuid);
            double anomaly      = Praxic.getAnomalyScoreEngine().getScore(uuid);
            boolean banned      = isPlayerBanned(uuid, player.getName().getString());
            if (banned) confidence = 1.0;

            JsonObject obj = new JsonObject();
            obj.addProperty("name",          player.getName().getString());
            obj.addProperty("uuid",          uuid.toString());
            obj.addProperty("ping",          player.connection.latency());
            obj.addProperty("confidence",    round3(confidence));
            obj.addProperty("anomaly",       round3(anomaly));
            obj.addProperty("ghostTraps",    ghostCount(uuid));
            obj.addProperty("online",        true);
            obj.addProperty("banned",        banned);
            int vl = 0;
            if (data != null) vl = data.violations.values().stream().mapToInt(Integer::intValue).sum();
            obj.addProperty("totalVl", vl);
            arr.add(obj);
        }
        sendJson(ex, 200, arr.toString());
    }

    private void handleAllPlayers(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonArray arr = new JsonArray();
        Set<String> seenNames = new HashSet<>();
        Set<UUID> seenUuids = new HashSet<>();

        // Online first
        for (ServerPlayer player : mcServer.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String name = player.getName().getString();
            seenNames.add(name.toLowerCase());
            seenUuids.add(uuid);
            PlayerData data = Praxic.getCheckManager().getPlayerData(uuid);
            double confidence = Praxic.getConfidenceEngine().getScore(uuid);
            double anomaly = Praxic.getAnomalyScoreEngine().getScore(uuid);
            boolean banned = isPlayerBanned(uuid, name);
            if (banned) confidence = 1.0;

            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("uuid", uuid.toString());
            obj.addProperty("ping", player.connection.latency());
            obj.addProperty("confidence", round3(confidence));
            obj.addProperty("anomaly", round3(anomaly));
            obj.addProperty("ghostTraps", ghostCount(uuid));
            obj.addProperty("online", true);
            obj.addProperty("banned", banned);
            int vl = 0;
            if (data != null) vl = data.violations.values().stream().mapToInt(Integer::intValue).sum();
            obj.addProperty("totalVl", vl);
            arr.add(obj);
        }

        // Offline from evidence (unique names not already seen)
        try {
            for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                if (e.playerName == null) continue;
                String lower = e.playerName.toLowerCase();
                if (seenNames.contains(lower)) continue;
                UUID uuid = null;
                try { uuid = UUID.fromString(e.uuid); } catch (Exception ignored) {}
                if (uuid != null && seenUuids.contains(uuid)) continue;

                seenNames.add(lower);
                if (uuid != null) seenUuids.add(uuid);

                boolean banned = isPlayerBanned(uuid, e.playerName);
                double conf = banned ? 1.0 : 0.0;

                JsonObject obj = new JsonObject();
                obj.addProperty("name", e.playerName);
                obj.addProperty("uuid", e.uuid != null ? e.uuid : "");
                obj.addProperty("ping", -1);
                obj.addProperty("confidence", conf);
                obj.addProperty("anomaly", 0.0);
                obj.addProperty("ghostTraps", 0);
                obj.addProperty("online", false);
                obj.addProperty("banned", banned);
                obj.addProperty("totalVl", e.vl);
                obj.addProperty("lastConfidence", round3(e.confidence));
                arr.add(obj);
                if (arr.size() >= 100) break;
            }
            // Also include banned players that have no evidence yet
            if (java.nio.file.Files.exists(REVEX_BANS_PATH)) {
                try {
                    String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
                    com.google.gson.JsonElement el = GSON.fromJson(content, com.google.gson.JsonElement.class);
                    if (el != null && el.isJsonObject()) {
                        com.google.gson.JsonObject bans = el.getAsJsonObject();
                        long now = System.currentTimeMillis();
                        for (Map.Entry<String, com.google.gson.JsonElement> entry : bans.entrySet()) {
                            if (!entry.getValue().isJsonObject()) continue;
                            com.google.gson.JsonObject ban = entry.getValue().getAsJsonObject();
                            boolean permanent = ban.has("permanent") && ban.get("permanent").getAsBoolean();
                            long expiresAt = ban.has("expiresAt") ? ban.get("expiresAt").getAsLong() : -1;
                            if (!permanent && expiresAt > 0 && now >= expiresAt) continue;
                            String pName = ban.has("playerName") ? ban.get("playerName").getAsString() : entry.getKey().substring(0, 8);
                            String lower = pName.toLowerCase();
                            if (seenNames.contains(lower)) continue;
                            UUID u = null;
                            try { u = UUID.fromString(entry.getKey()); } catch (Exception ignored) {}
                            if (u != null && seenUuids.contains(u)) continue;
                            seenNames.add(lower);
                            if (u != null) seenUuids.add(u);
                            JsonObject obj = new JsonObject();
                            obj.addProperty("name", pName);
                            obj.addProperty("uuid", entry.getKey());
                            obj.addProperty("ping", -1);
                            obj.addProperty("confidence", 1.0);
                            obj.addProperty("anomaly", 0.0);
                            obj.addProperty("ghostTraps", 0);
                            obj.addProperty("online", false);
                            obj.addProperty("banned", true);
                            obj.addProperty("totalVl", 0);
                            obj.addProperty("lastConfidence", 1.0);
                            arr.add(obj);
                            if (arr.size() >= 120) break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        sendJson(ex, 200, arr.toString());
    }

    private void handlePlayer(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) { sendJson(ex, 400, "{\"error\":\"Missing name\"}"); return; }
        String requestedName = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);

        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(requestedName);
        UUID uuid = null;
        boolean online = true;

        if (player == null) {
            // Try find by UUID string or by name from evidence
            try {
                uuid = UUID.fromString(requestedName);
                // Find player by UUID from online list
                for (ServerPlayer p : mcServer.getPlayerList().getPlayers()) {
                    if (p.getUUID().equals(uuid)) { player = p; break; }
                }
            } catch (Exception ignored) {}
            if (player == null) {
                // Offline lookup via evidence
                EvidenceManager.EvidenceEntry found = null;
                for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(requestedName)) {
                        found = e;
                        break;
                    }
                    if (e.uuid != null && e.uuid.equalsIgnoreCase(requestedName)) {
                        found = e;
                        break;
                    }
                }
                if (found != null) {
                    try { uuid = UUID.fromString(found.uuid); } catch (Exception ignored) {}
                    // Build offline response - keep risk clean on disconnect
                    JsonObject obj = new JsonObject();
                    obj.addProperty("name", found.playerName);
                    obj.addProperty("uuid", found.uuid);
                    obj.addProperty("ping", -1);
                    obj.addProperty("confidence", 0.0);
                    obj.addProperty("anomaly", 0.0);
                    obj.addProperty("online", false);
                    obj.addProperty("offline", true);
                    obj.addProperty("whitelisted", false);
                    obj.addProperty("ghostTraps", 0);
                    obj.addProperty("gameMode", "unknown");
                    obj.addProperty("health", -1);
                    obj.addProperty("lastConfidence", round3(found.confidence));

                    JsonObject vl = new JsonObject();
                    obj.add("violations", vl);

                    List<HistoryManager.ViolationEntry> history = uuid != null ? Praxic.getHistoryManager().getHistory(uuid) : Collections.emptyList();
                    JsonArray hist = new JsonArray();
                    int start = Math.max(0, history.size() - 15);
                    for (int i = history.size() - 1; i >= start; i--) {
                        HistoryManager.ViolationEntry en = history.get(i);
                        JsonObject he = new JsonObject();
                        he.addProperty("timestamp", en.timestamp);
                        he.addProperty("check", en.check);
                        he.addProperty("vl", en.vl);
                        he.addProperty("action", en.action);
                        he.addProperty("details", en.details);
                        hist.add(he);
                    }
                    obj.add("history", hist);

                    JsonArray evidence = new JsonArray();
                    if (uuid != null) {
                        for (EvidenceManager.EvidenceEntry ev : Praxic.getEvidenceManager().getRecent(uuid, 15)) {
                            evidence.add(evidenceToJson(ev));
                        }
                    } else {
                        for (EvidenceManager.EvidenceEntry ev : Praxic.getEvidenceManager().getRecent(50)) {
                            if (ev.playerName != null && ev.playerName.equalsIgnoreCase(requestedName)) {
                                evidence.add(evidenceToJson(ev));
                                if (evidence.size() >= 15) break;
                            }
                        }
                    }
                    obj.add("evidence", evidence);
                    sendJson(ex, 200, GSON.toJson(obj));
                    return;
                }
                sendJson(ex, 404, "{\"error\":\"Not found\"}");
                return;
            }
        }

        if (player != null) uuid = player.getUUID();
        if (uuid == null) { sendJson(ex, 404, "{\"error\":\"Not found\"}"); return; }

        PlayerData data     = Praxic.getCheckManager().getPlayerData(uuid);
        PlayerAnalytics anl = Praxic.getCheckManager().getAnalytics(uuid);
        double confidence   = Praxic.getConfidenceEngine().getScore(uuid);
        double anomaly      = Praxic.getAnomalyScoreEngine().getScore(uuid);

        JsonObject obj = new JsonObject();
        obj.addProperty("name",        player != null ? player.getName().getString() : requestedName);
        obj.addProperty("uuid",        uuid.toString());
        obj.addProperty("ping",        player != null ? player.connection.latency() : -1);
        obj.addProperty("confidence",  round3(confidence));
        obj.addProperty("anomaly",     round3(anomaly));
        obj.addProperty("online",      player != null);
        obj.addProperty("health",      player != null ? player.getHealth() : -1);
        obj.addProperty("gameMode",    player != null ? player.gameMode.getGameModeForPlayer().getName() : "offline");
        obj.addProperty("whitelisted", Praxic.getWhitelistManager().isWhitelisted(uuid));
        obj.addProperty("ghostTraps",  ghostCount(uuid));

        if (data != null) {
            obj.addProperty("movementState", data.movementState.name());
            obj.addProperty("airTicks", data.airTicks);
            obj.addProperty("phaseTicks", data.phaseTicks);
            obj.addProperty("noSlowBuffer", data.noSlowBuffer);
            obj.addProperty("badPacketBuffer", data.badPacketBuffer);
            obj.addProperty("criticalsBuffer", data.criticalsBuffer);
            obj.addProperty("sessionStart", data.sessionStartMs);
            obj.addProperty("sessionTicks", data.totalTicks);
        }

        JsonObject vl = new JsonObject();
        if (data != null) data.violations.forEach(vl::addProperty);
        obj.add("violations", vl);

        if (anl != null) {
            JsonObject a = new JsonObject();
            a.addProperty("entropy",      round2(anl.rotation.entropy));
            a.addProperty("maxSnapAngle", round2(anl.rotation.maxSnapAngle));
            a.addProperty("postKillSnap", round2(anl.rotation.postKillSnapAngle));
            a.addProperty("rotationSpeedCV", round3(anl.rotation.rotationSpeedCV));
            a.addProperty("avgCps",       round2(anl.timing.avgCps));
            a.addProperty("totalCps",     round2(anl.timing.totalCps));
            a.addProperty("clickStdDev",  round2(anl.timing.clickIntervalStdDev));
            a.addProperty("packetStdDev", round2(anl.timing.packetIntervalStdDev));
            a.addProperty("avgSpeed",     round2(anl.movement.avgSpeed));
            a.addProperty("strafeRatio",  round2(anl.movement.strafeRatio));
            a.addProperty("jumpFreq",     round2(anl.movement.jumpFrequency));
            obj.add("analytics", a);

            PlayerBaseline bl = anl.baseline;
            JsonObject b = new JsonObject();
            b.addProperty("ready",          bl.baselineReady);
            b.addProperty("collected",      bl.baselineTicksCollected);
            b.addProperty("required",       bl.baselineTicksRequired);
            b.addProperty("deviationScore", round2(bl.deviationScore));
            obj.add("baseline", b);
        }

        List<HistoryManager.ViolationEntry> history = Praxic.getHistoryManager().getHistory(uuid);
        JsonArray hist = new JsonArray();
        int start = Math.max(0, history.size() - 15);
        for (int i = history.size() - 1; i >= start; i--) {
            HistoryManager.ViolationEntry e = history.get(i);
            JsonObject he = new JsonObject();
            he.addProperty("timestamp", e.timestamp);
            he.addProperty("check",     e.check);
            he.addProperty("vl",        e.vl);
            he.addProperty("action",    e.action);
            he.addProperty("details",   e.details);
            hist.add(he);
        }
        obj.add("history", hist);

        JsonArray evidence = new JsonArray();
        for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(uuid, 15)) {
            evidence.add(evidenceToJson(e));
        }
        obj.add("evidence", evidence);

        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        PraxicConfig cfg = Praxic.getConfig();
        JsonObject obj = new JsonObject();
        obj.addProperty("version",       Praxic.VERSION);
        obj.addProperty("onlinePlayers", mcServer.getPlayerList().getPlayers().size());
        obj.addProperty("maxPlayers",    mcServer.getMaxPlayers());
        obj.addProperty("totalFlags",    PraxicStats.getTotalFlags());
        obj.addProperty("evidenceCount", Praxic.getEvidenceManager().count());
        obj.addProperty("port",          cfg.webDashboardPort);

        JsonObject checks = new JsonObject();
        checks.addProperty("FlyCheck",          cfg.flyCheckEnabled);
        checks.addProperty("YPredictionCheck",  cfg.yPredictionCheckEnabled);
        checks.addProperty("SpeedCheck",        cfg.speedCheckEnabled);
        checks.addProperty("PhaseCheck",        cfg.phaseCheckEnabled);
        checks.addProperty("NoSlowCheck",       cfg.noSlowCheckEnabled);
        checks.addProperty("JesusCheck",        cfg.jesusCheckEnabled);
        checks.addProperty("SprintCheck",       cfg.sprintCheckEnabled);
        checks.addProperty("BoatFlyCheck",      cfg.boatFlyCheckEnabled);
        checks.addProperty("ElytraFlyCheck",    cfg.elytraFlyCheckEnabled);
        checks.addProperty("StepCheck",         cfg.stepCheckEnabled);
        checks.addProperty("GroundSpoofCheck",  cfg.groundSpoofCheckEnabled);
        checks.addProperty("TeleportCheck",     cfg.teleportCheckEnabled);
        checks.addProperty("VehicleFlyCheck",   cfg.vehicleFlyCheckEnabled);
        checks.addProperty("AimAssistCheck",    cfg.aimAssistCheckEnabled);
        checks.addProperty("FastUseCheck",      cfg.fastUseCheckEnabled);
        checks.addProperty("AirPlaceCheck",     cfg.airPlaceCheckEnabled);
        checks.addProperty("ReachCheck",        cfg.reachCheckEnabled);
        checks.addProperty("KillAuraCheck",     cfg.killAuraCheckEnabled);
        checks.addProperty("GhostTrapCheck",    cfg.ghostTrapCheckEnabled);
        checks.addProperty("CriticalsCheck",    cfg.criticalsCheckEnabled);
        checks.addProperty("VelocityCheck",     cfg.velocityCheckEnabled);
        checks.addProperty("RotationCheck",     cfg.rotationCheckEnabled);
        checks.addProperty("PostKillSnapCheck", cfg.postKillSnapCheckEnabled);
        checks.addProperty("ScaffoldCheck",     cfg.scaffoldCheckEnabled);
        checks.addProperty("FastBreakCheck",    cfg.fastBreakCheckEnabled);
        checks.addProperty("FastPlaceCheck",    cfg.fastPlaceCheckEnabled);
        checks.addProperty("TowerCheck",        cfg.towerCheckEnabled);
        checks.addProperty("NoFallCheck",       cfg.noFallCheckEnabled);
        checks.addProperty("AutoClickerCheck",  cfg.autoClickerCheckEnabled);
        checks.addProperty("AutoTotemCheck",    cfg.autoTotemCheckEnabled);
        checks.addProperty("InventoryCheck",    cfg.inventoryCheckEnabled);
        checks.addProperty("TimerCheck",        cfg.timerCheckEnabled);
        checks.addProperty("BadPacketsCheck",   cfg.badPacketsCheckEnabled);
        checks.addProperty("MaceSmashCheck",    cfg.maceSmashCheckEnabled);
        checks.addProperty("WindChargeAbuseCheck", cfg.windChargeAbuseCheckEnabled);
        checks.addProperty("AutoArmorCheck",  cfg.autoArmorCheckEnabled);
        checks.addProperty("FastLadderCheck", cfg.fastLadderCheckEnabled);
        obj.add("checks", checks);
        obj.addProperty("mitigation", cfg.enableMitigation);
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleIncidents(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        // Live feed: only current session, only online players, only evidence created after session start
        JsonArray arr = new JsonArray();
        try {
            Map<String, Long> onlineSessionStart = new HashMap<>();
            for (ServerPlayer p : mcServer.getPlayerList().getPlayers()) {
                PlayerData d = Praxic.getCheckManager().getPlayerData(p.getUUID());
                long start = d != null ? d.sessionStartMs : System.currentTimeMillis() - 600_000L; // fallback 10min
                onlineSessionStart.put(p.getUUID().toString(), start);
                onlineSessionStart.put(p.getName().getString().toLowerCase(), start);
            }

            for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(100)) {
                if (e.uuid == null) continue;
                Long sessionStart = onlineSessionStart.get(e.uuid);
                if (sessionStart == null && e.playerName != null) {
                    sessionStart = onlineSessionStart.get(e.playerName.toLowerCase());
                }
                if (sessionStart == null) continue; // offline -> not live
                // Only if created after session start, and has createdAt (new entries)
                if (e.createdAt > 0 && e.createdAt < sessionStart) continue;
                // For backward compat, if createdAt==0, exclude from live (old session)
                if (e.createdAt == 0) continue;
                arr.add(evidenceToJson(e));
                if (arr.size() >= 30) break;
            }
        } catch (Exception ignored) {
            // On error return empty live feed, not all history
        }
        sendJson(ex, 200, GSON.toJson(arr));
    }

    private void handleIncidentsAll(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        JsonArray arr = new JsonArray();
        for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(100)) {
            arr.add(evidenceToJson(e));
        }
        sendJson(ex, 200, GSON.toJson(arr));
    }

    private void handleMetrics(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonObject obj = new JsonObject();
        obj.addProperty("version", Praxic.VERSION);
        double mspt = -1;
        double tps = -1;
        try {
            Object msptVal = null;
            try {
                var m = mcServer.getClass().getMethod("getAverageTickTime");
                msptVal = m.invoke(mcServer);
            } catch (NoSuchMethodException e) {
                try {
                    var m2 = mcServer.getClass().getMethod("getAverageTickTimeNanos");
                    Object nano = m2.invoke(mcServer);
                    if (nano instanceof Number n) msptVal = n.doubleValue() / 1_000_000.0;
                } catch (Exception ignored) {}
            }
            if (msptVal instanceof Number n) {
                mspt = n.doubleValue();
                tps = mspt > 0 ? Math.min(20.0, 1000.0 / mspt) : 20.0;
            }
        } catch (Exception ignored) {}
        obj.addProperty("mspt", mspt >= 0 ? Math.round(mspt * 100.0) / 100.0 : -1);
        obj.addProperty("tps", tps >= 0 ? Math.round(tps * 100.0) / 100.0 : -1);
        obj.addProperty("online", mcServer.getPlayerList().getPlayers().size());
        obj.addProperty("totalFlags", PraxicStats.getTotalFlags());
        obj.addProperty("evidence", Praxic.getEvidenceManager().count());
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleActionReset(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 5) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        String name = URLDecoder.decode(parts[parts.length - 1], StandardCharsets.UTF_8);
        // Try online first, then offline via UUID
        ServerPlayer target = mcServer.getPlayerList().getPlayerByName(name);
        UUID uuid = null;
        if (target != null) {
            uuid = target.getUUID();
        } else {
            try { uuid = UUID.fromString(name); } catch (Exception ignored) {}
            if (uuid == null) {
                // Find UUID from evidence by name
                for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(name)) {
                        try { uuid = UUID.fromString(e.uuid); break; } catch (Exception ignored) {}
                    }
                }
            }
        }
        if (uuid == null) { sendJson(ex, 404, "{\"error\":\"Player not found\"}"); return; }
        var data = Praxic.getCheckManager().getPlayerData(uuid);
        if (data != null) {
            data.violations.clear();
            data.lastFlagTime.clear();
        }
        Praxic.getConfidenceEngine().reset(uuid);
        Praxic.getAnomalyScoreEngine().reset(uuid);
        sendJson(ex, 200, "{\"status\":\"reset\"}");
    }

    private void handleActionWhitelist(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String query = ex.getRequestURI().getQuery();
        boolean add = true;
        String playerName = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("player=")) playerName = URLDecoder.decode(part.substring(7), StandardCharsets.UTF_8);
                if (part.startsWith("action=")) add = !part.substring(7).equalsIgnoreCase("remove");
            }
        }
        if (playerName == null) { sendJson(ex, 400, "{\"error\":\"Missing player param\"}"); return; }
        ServerPlayer target = mcServer.getPlayerList().getPlayerByName(playerName);
        UUID uuid = null;
        if (target != null) uuid = target.getUUID();
        else {
            try { uuid = UUID.fromString(playerName); } catch (Exception ignored) {}
            if (uuid == null) {
                for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(playerName)) {
                        try { uuid = UUID.fromString(e.uuid); break; } catch (Exception ignored) {}
                    }
                }
            }
        }
        if (uuid == null) { sendJson(ex, 404, "{\"error\":\"Player not found\"}"); return; }
        if (add) Praxic.getWhitelistManager().add(uuid);
        else Praxic.getWhitelistManager().remove(uuid);
        sendJson(ex, 200, "{\"status\":\"ok\",\"whitelisted\":" + add + "}");
    }

    private void handleWhitelist(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonArray arr = new JsonArray();
        try {
            Set<UUID> all = Praxic.getWhitelistManager().getAll();
            Map<UUID, String> nameMap = new HashMap<>();
            for (ServerPlayer p : mcServer.getPlayerList().getPlayers()) {
                nameMap.put(p.getUUID(), p.getName().getString());
            }
            for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                try {
                    UUID u = UUID.fromString(e.uuid);
                    if (e.playerName != null && !nameMap.containsKey(u)) {
                        nameMap.put(u, e.playerName);
                    }
                } catch (Exception ignored) {}
            }
            for (UUID uuid : all) {
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", uuid.toString());
                obj.addProperty("name", nameMap.getOrDefault(uuid, uuid.toString().substring(0, 8)));
                obj.addProperty("online", mcServer.getPlayerList().getPlayer(uuid) != null);
                arr.add(obj);
            }
        } catch (Exception ignored) {}
        sendJson(ex, 200, GSON.toJson(arr));
    }

    // -------------------------------------------------------------------------
    // REVEX integration - file based, no hard dependency
    // -------------------------------------------------------------------------

    private static final java.nio.file.Path REVEX_CONFIG_PATH = java.nio.file.Paths.get("config", "revex.json");
    private static final java.nio.file.Path REVEX_BANS_PATH = java.nio.file.Paths.get("config", "revex-bans.json");
    private static final java.nio.file.Path REVEX_ESCALATION_PATH = java.nio.file.Paths.get("config", "revex-escalation.json");

    private boolean isPlayerBanned(UUID uuid, String name) {
        if (uuid == null && (name == null || name.isEmpty())) return false;
        // Try live manager via reflection
        try {
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            Object banMgr = revexClass.getMethod("getBanManager").invoke(null);
            if (banMgr != null && uuid != null) {
                try {
                    Object banEntry = banMgr.getClass().getMethod("getBan", UUID.class).invoke(banMgr, uuid);
                    if (banEntry != null) return true;
                } catch (Exception ignored) {}
                if (name != null) {
                    try {
                        Object found = banMgr.getClass().getMethod("findBannedUuidByName", String.class).invoke(banMgr, name);
                        if (found instanceof UUID) return true;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        // Fallback file check
        if (!java.nio.file.Files.exists(REVEX_BANS_PATH)) return false;
        try {
            String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
            com.google.gson.JsonElement el = GSON.fromJson(content, com.google.gson.JsonElement.class);
            if (el == null || !el.isJsonObject()) return false;
            com.google.gson.JsonObject bans = el.getAsJsonObject();
            long now = System.currentTimeMillis();
            if (uuid != null && bans.has(uuid.toString())) {
                com.google.gson.JsonElement be = bans.get(uuid.toString());
                if (be.isJsonObject()) {
                    com.google.gson.JsonObject ban = be.getAsJsonObject();
                    boolean permanent = ban.has("permanent") && ban.get("permanent").getAsBoolean();
                    long expiresAt = ban.has("expiresAt") ? ban.get("expiresAt").getAsLong() : -1;
                    if (permanent) return true;
                    if (expiresAt > 0 && now < expiresAt) return true;
                }
            }
            if (name != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : bans.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    com.google.gson.JsonObject ban = e.getValue().getAsJsonObject();
                    String pn = ban.has("playerName") ? ban.get("playerName").getAsString() : "";
                    if (pn.equalsIgnoreCase(name)) {
                        boolean permanent = ban.has("permanent") && ban.get("permanent").getAsBoolean();
                        long expiresAt = ban.has("expiresAt") ? ban.get("expiresAt").getAsLong() : -1;
                        if (permanent) return true;
                        if (expiresAt > 0 && now < expiresAt) return true;
                        if (expiresAt <= 0) return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void handleRevexStatus(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        JsonObject obj = new JsonObject();
        boolean present = java.nio.file.Files.exists(REVEX_CONFIG_PATH);
        obj.addProperty("present", present);
        if (present) {
            try {
                String content = java.nio.file.Files.readString(REVEX_CONFIG_PATH, StandardCharsets.UTF_8);
                com.google.gson.JsonElement el = GSON.fromJson(content, com.google.gson.JsonElement.class);
                if (el != null && el.isJsonObject()) {
                    JsonObject cfg = el.getAsJsonObject();
                    obj.add("config", cfg);
                    obj.addProperty("enabled", cfg.has("enabled") ? cfg.get("enabled").getAsBoolean() : true);
                    obj.addProperty("escalationMode", cfg.has("escalationMode") ? cfg.get("escalationMode").getAsString() : "global");
                    obj.addProperty("escalationResetTime", cfg.has("escalationResetTime") ? cfg.get("escalationResetTime").getAsString() : "24h");
                    if (cfg.has("defaultEscalation") && cfg.get("defaultEscalation").isJsonArray()) {
                        obj.add("defaultEscalation", cfg.get("defaultEscalation"));
                    }
                    if (cfg.has("perCheckEscalation") && cfg.get("perCheckEscalation").isJsonObject()) {
                        obj.add("perCheckEscalation", cfg.get("perCheckEscalation"));
                    }
                }
            } catch (Exception e) {
                obj.addProperty("error", e.getMessage());
            }
        }
        // Also try reflection to get live managers if Revex is loaded
        try {
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            obj.addProperty("loaded", true);
            try {
                Object banMgr = revexClass.getMethod("getBanManager").invoke(null);
                Object escMgr = revexClass.getMethod("getEscalationManager").invoke(null);
                obj.addProperty("banManager", banMgr != null ? "active" : "null");
                obj.addProperty("escalationManager", escMgr != null ? "active" : "null");
            } catch (Exception ignored) {}
        } catch (ClassNotFoundException ignored) {
            obj.addProperty("loaded", false);
        }
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleRevexBans(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        JsonArray arr = new JsonArray();
        if (java.nio.file.Files.exists(REVEX_BANS_PATH)) {
            try {
                String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
                com.google.gson.JsonElement el = GSON.fromJson(content, com.google.gson.JsonElement.class);
                if (el != null && el.isJsonObject()) {
                    JsonObject bans = el.getAsJsonObject();
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : bans.entrySet()) {
                        String uuid = entry.getKey();
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject ban = entry.getValue().getAsJsonObject();
                        boolean permanent = ban.has("permanent") && ban.get("permanent").getAsBoolean();
                        long expiresAt = ban.has("expiresAt") ? ban.get("expiresAt").getAsLong() : -1;
                        if (!permanent && expiresAt > 0 && now >= expiresAt) continue; // expired
                        JsonObject obj = new JsonObject();
                        obj.addProperty("uuid", uuid);
                        obj.addProperty("playerName", ban.has("playerName") ? ban.get("playerName").getAsString() : uuid.substring(0,8));
                        obj.addProperty("reason", ban.has("reason") ? ban.get("reason").getAsString() : "");
                        obj.addProperty("bannedAt", ban.has("bannedAt") ? ban.get("bannedAt").getAsString() : "");
                        obj.addProperty("expiresAt", expiresAt);
                        obj.addProperty("permanent", permanent);
                        long remaining = permanent ? -1 : Math.max(0, expiresAt - now);
                        obj.addProperty("remainingMs", remaining);
                        obj.addProperty("remainingText", remaining < 0 ? "permanent" : formatDuration(remaining));
                        arr.add(obj);
                    }
                }
            } catch (Exception e) {
                Praxic.LOGGER.warn("[PRAXIC] Failed to read revex bans", e);
            }
        }
        sendJson(ex, 200, GSON.toJson(arr));
    }

    private void handleRevexEscalation(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        JsonObject result = new JsonObject();
        if (java.nio.file.Files.exists(REVEX_ESCALATION_PATH)) {
            try {
                String content = java.nio.file.Files.readString(REVEX_ESCALATION_PATH, StandardCharsets.UTF_8);
                com.google.gson.JsonElement el = GSON.fromJson(content, com.google.gson.JsonElement.class);
                if (el != null && el.isJsonObject()) {
                    result = el.getAsJsonObject();
                }
            } catch (Exception e) {
                Praxic.LOGGER.warn("[PRAXIC] Failed to read revex escalation", e);
            }
        }
        sendJson(ex, 200, GSON.toJson(result));
    }

    private void handleRevexInspect(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 5) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        String requested = URLDecoder.decode(parts[4], StandardCharsets.UTF_8);
        UUID uuid = null;
        String name = requested;
        // Try resolve uuid
        try { uuid = UUID.fromString(requested); } catch (Exception ignored) {}
        if (uuid == null) {
            ServerPlayer p = mcServer.getPlayerList().getPlayerByName(requested);
            if (p != null) { uuid = p.getUUID(); name = p.getName().getString(); }
            else {
                // Try find in bans
                if (java.nio.file.Files.exists(REVEX_BANS_PATH)) {
                    try {
                        String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
                        JsonObject bans = GSON.fromJson(content, JsonObject.class);
                        if (bans != null) {
                            for (Map.Entry<String, com.google.gson.JsonElement> e : bans.entrySet()) {
                                if (e.getValue().isJsonObject()) {
                                    JsonObject ban = e.getValue().getAsJsonObject();
                                    String pn = ban.has("playerName") ? ban.get("playerName").getAsString() : "";
                                    if (pn.equalsIgnoreCase(requested)) {
                                        uuid = UUID.fromString(e.getKey());
                                        name = pn;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                // Try evidence
                if (uuid == null) {
                    for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                        if (e.playerName != null && e.playerName.equalsIgnoreCase(requested)) {
                            try { uuid = UUID.fromString(e.uuid); name = e.playerName; break; } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("requested", requested);
        obj.addProperty("name", name);
        obj.addProperty("uuid", uuid != null ? uuid.toString() : "");
        // Ban info
        if (uuid != null && java.nio.file.Files.exists(REVEX_BANS_PATH)) {
            try {
                String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
                JsonObject bans = GSON.fromJson(content, JsonObject.class);
                if (bans != null && bans.has(uuid.toString())) {
                    obj.add("ban", bans.get(uuid.toString()));
                }
            } catch (Exception ignored) {}
        }
        // Escalation info
        if (uuid != null && java.nio.file.Files.exists(REVEX_ESCALATION_PATH)) {
            try {
                String content = java.nio.file.Files.readString(REVEX_ESCALATION_PATH, StandardCharsets.UTF_8);
                JsonObject esc = GSON.fromJson(content, JsonObject.class);
                if (esc != null && esc.has(uuid.toString())) {
                    obj.add("escalation", esc.get(uuid.toString()));
                }
            } catch (Exception ignored) {}
        }
        // Also try reflection for live data
        try {
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            Object banMgr = revexClass.getMethod("getBanManager").invoke(null);
            Object escMgr = revexClass.getMethod("getEscalationManager").invoke(null);
            if (uuid != null && banMgr != null) {
                try {
                    Object banEntry = banMgr.getClass().getMethod("getBan", UUID.class).invoke(banMgr, uuid);
                    if (banEntry != null) {
                        JsonObject banJson = new JsonObject();
                        banJson.addProperty("playerName", (String) banEntry.getClass().getField("playerName").get(banEntry));
                        banJson.addProperty("reason", (String) banEntry.getClass().getField("reason").get(banEntry));
                        banJson.addProperty("bannedAt", (String) banEntry.getClass().getField("bannedAt").get(banEntry));
                        banJson.addProperty("expiresAt", (Long) banEntry.getClass().getField("expiresAt").get(banEntry));
                        banJson.addProperty("permanent", (Boolean) banEntry.getClass().getField("permanent").get(banEntry));
                        obj.add("banLive", banJson);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleRevexUnban(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String query = ex.getRequestURI().getQuery();
        String player = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("player=")) player = URLDecoder.decode(part.substring(7), StandardCharsets.UTF_8);
            }
        }
        if (player == null) {
            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length >= 5) player = URLDecoder.decode(parts[4], StandardCharsets.UTF_8);
        }
        if (player == null) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        boolean ok = false;
        // Try reflection first
        try {
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            Object banMgr = revexClass.getMethod("getBanManager").invoke(null);
            UUID uuid = null;
            try { uuid = UUID.fromString(player); } catch (Exception ignored) {}
            if (uuid == null) {
                ServerPlayer p = mcServer.getPlayerList().getPlayerByName(player);
                if (p != null) uuid = p.getUUID();
                else {
                    // try find by name in bans
                    Object found = banMgr.getClass().getMethod("findBannedUuidByName", String.class).invoke(banMgr, player);
                    if (found instanceof UUID) uuid = (UUID) found;
                }
            }
            if (uuid != null) {
                Object res = banMgr.getClass().getMethod("unban", UUID.class).invoke(banMgr, uuid);
                ok = res instanceof Boolean && (Boolean) res;
            }
        } catch (Exception ignored) {}
        // Fallback file edit
        if (!ok) {
            ok = editRevexBansFile(player, true);
        }
        sendJson(ex, 200, "{\"status\":\"" + (ok ? "unbanned" : "not_found") + "\"}");
    }

    private void handleRevexReset(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String query = ex.getRequestURI().getQuery();
        String player = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("player=")) player = URLDecoder.decode(part.substring(7), StandardCharsets.UTF_8);
            }
        }
        if (player == null) {
            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length >= 5) player = URLDecoder.decode(parts[4], StandardCharsets.UTF_8);
        }
        if (player == null) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        boolean ok = false;
        try {
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            Object escMgr = revexClass.getMethod("getEscalationManager").invoke(null);
            UUID uuid = null;
            try { uuid = UUID.fromString(player); } catch (Exception ignored) {}
            if (uuid == null) {
                ServerPlayer p = mcServer.getPlayerList().getPlayerByName(player);
                if (p != null) uuid = p.getUUID();
            }
            if (uuid == null) {
                // try from evidence
                for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(player)) {
                        try { uuid = UUID.fromString(e.uuid); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (uuid != null) {
                escMgr.getClass().getMethod("resetPlayer", UUID.class).invoke(escMgr, uuid);
                ok = true;
            }
        } catch (Exception ignored) {}
        if (!ok) {
            ok = editRevexEscalationFile(player, true);
        }
        sendJson(ex, 200, "{\"status\":\"" + (ok ? "reset" : "not_found") + "\"}");
    }

    private void handleRevexPardon(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }
        String query = ex.getRequestURI().getQuery();
        String player = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("player=")) player = URLDecoder.decode(part.substring(7), StandardCharsets.UTF_8);
            }
        }
        if (player == null) {
            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length >= 5) player = URLDecoder.decode(parts[4], StandardCharsets.UTF_8);
        }
        if (player == null) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        boolean unbanOk = false;
        boolean resetOk = false;
        try {
            // Unban
            Class<?> revexClass = Class.forName("com.jrxmod.revex.Revex");
            Object banMgr = revexClass.getMethod("getBanManager").invoke(null);
            Object escMgr = revexClass.getMethod("getEscalationManager").invoke(null);
            UUID uuid = null;
            try { uuid = UUID.fromString(player); } catch (Exception ignored) {}
            if (uuid == null) {
                ServerPlayer p = mcServer.getPlayerList().getPlayerByName(player);
                if (p != null) uuid = p.getUUID();
                else {
                    Object found = banMgr.getClass().getMethod("findBannedUuidByName", String.class).invoke(banMgr, player);
                    if (found instanceof UUID) uuid = (UUID) found;
                }
            }
            if (uuid == null) {
                for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                    if (e.playerName != null && e.playerName.equalsIgnoreCase(player)) {
                        try { uuid = UUID.fromString(e.uuid); break; } catch (Exception ignored) {}
                    }
                }
            }
            if (uuid != null) {
                Object res = banMgr.getClass().getMethod("unban", UUID.class).invoke(banMgr, uuid);
                unbanOk = res instanceof Boolean && (Boolean) res;
                escMgr.getClass().getMethod("resetPlayer", UUID.class).invoke(escMgr, uuid);
                resetOk = true;
            }
        } catch (Exception ignored) {}
        if (!unbanOk) unbanOk = editRevexBansFile(player, true);
        if (!resetOk) resetOk = editRevexEscalationFile(player, true);
        sendJson(ex, 200, "{\"status\":\"pardon\",\"unbanned\":" + unbanOk + ",\"reset\":" + resetOk + "}");
    }

    private boolean editRevexBansFile(String player, boolean remove) {
        try {
            if (!java.nio.file.Files.exists(REVEX_BANS_PATH)) return false;
            String content = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
            JsonObject bans = GSON.fromJson(content, JsonObject.class);
            if (bans == null) return false;
            boolean changed = false;
            // Try by uuid key
            try {
                UUID uuid = UUID.fromString(player);
                if (bans.has(uuid.toString())) { bans.remove(uuid.toString()); changed = true; }
            } catch (Exception ignored) {}
            // Try by name value
            if (!changed) {
                List<String> toRemove = new ArrayList<>();
                for (Map.Entry<String, com.google.gson.JsonElement> e : bans.entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        JsonObject ban = e.getValue().getAsJsonObject();
                        String pn = ban.has("playerName") ? ban.get("playerName").getAsString() : "";
                        if (pn.equalsIgnoreCase(player)) toRemove.add(e.getKey());
                    }
                }
                for (String k : toRemove) { bans.remove(k); changed = true; }
            }
            if (changed) {
                java.nio.file.Files.writeString(REVEX_BANS_PATH, GSON.toJson(bans), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (Exception e) { return false; }
    }

    private boolean editRevexEscalationFile(String player, boolean remove) {
        try {
            if (!java.nio.file.Files.exists(REVEX_ESCALATION_PATH)) return false;
            String content = java.nio.file.Files.readString(REVEX_ESCALATION_PATH, StandardCharsets.UTF_8);
            JsonObject esc = GSON.fromJson(content, JsonObject.class);
            if (esc == null) return false;
            boolean changed = false;
            try {
                UUID uuid = UUID.fromString(player);
                if (esc.has(uuid.toString())) { esc.remove(uuid.toString()); changed = true; }
            } catch (Exception ignored) {}
            if (!changed) {
                // Need to find uuid by name from bans or evidence
                UUID found = null;
                if (java.nio.file.Files.exists(REVEX_BANS_PATH)) {
                    try {
                        String bansContent = java.nio.file.Files.readString(REVEX_BANS_PATH, StandardCharsets.UTF_8);
                        JsonObject bans = GSON.fromJson(bansContent, JsonObject.class);
                        if (bans != null) {
                            for (Map.Entry<String, com.google.gson.JsonElement> e : bans.entrySet()) {
                                if (e.getValue().isJsonObject()) {
                                    JsonObject ban = e.getValue().getAsJsonObject();
                                    String pn = ban.has("playerName") ? ban.get("playerName").getAsString() : "";
                                    if (pn.equalsIgnoreCase(player)) { found = UUID.fromString(e.getKey()); break; }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (found == null) {
                    for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(500)) {
                        if (e.playerName != null && e.playerName.equalsIgnoreCase(player)) {
                            try { found = UUID.fromString(e.uuid); break; } catch (Exception ignored) {}
                        }
                    }
                }
                if (found != null && esc.has(found.toString())) { esc.remove(found.toString()); changed = true; }
            }
            if (changed) {
                java.nio.file.Files.writeString(REVEX_ESCALATION_PATH, GSON.toJson(esc), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (Exception e) { return false; }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long sec = ms / 1000;
        long days = sec / 86400; sec %= 86400;
        long hours = sec / 3600; sec %= 3600;
        long minutes = sec / 60; sec %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (sec > 0 || sb.length()==0) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int ghostCount(UUID uuid) {
        return Praxic.getGhostEntityManager() != null
                ? Praxic.getGhostEntityManager().getActiveGhostCount(uuid) : 0;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("<", "\\u003c").replace(">", "\\u003e");
    }

    private static JsonObject evidenceToJson(EvidenceManager.EvidenceEntry e) {
        JsonObject obj = new JsonObject();
        obj.addProperty("timestamp", e.timestamp);
        obj.addProperty("uuid", e.uuid);
        obj.addProperty("playerName", e.playerName);
        obj.addProperty("check", e.check);
        obj.addProperty("vl", e.vl);
        obj.addProperty("details", e.details);
        obj.addProperty("action", e.action);
        obj.addProperty("confidence", e.confidence);
        obj.addProperty("anomaly", e.anomaly);
        obj.addProperty("ping", e.ping);
        obj.addProperty("world", e.world);
        obj.addProperty("x", e.x);
        obj.addProperty("y", e.y);
        obj.addProperty("z", e.z);
        obj.addProperty("movementState", e.movementState);
        obj.addProperty("airTicks", e.airTicks);
        obj.addProperty("ghostTraps", e.ghostTraps);
        obj.addProperty("createdAt", e.createdAt);
        obj.addProperty("sessionStart", e.sessionStart);
        return obj;
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void sendHtml(HttpExchange ex, int code, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static double round2(double v) { return v < 0 ? -1.0 : Math.round(v*100.0)/100.0; }
    private static double round3(double v) { return v < 0 ? -1.0 : Math.round(v*1000.0)/1000.0; }
}
