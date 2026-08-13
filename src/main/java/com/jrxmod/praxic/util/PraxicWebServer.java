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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP dashboard server bound to 127.0.0.1 only.
 * HTML served from resources/dashboard.html with server-side token injection.
 * Optional token auth via X-Praxic-Token header or ?token= query param.
 */
public class PraxicWebServer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HttpServer      httpServer;
    private MinecraftServer mcServer;
    private String          dashboardTemplate = "<h1>Dashboard failed to load.</h1>";

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start(MinecraftServer server, int port) {
        this.mcServer = server;
        loadTemplate();
        try {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.createContext("/",              this::handleDashboard);
            httpServer.createContext("/api/players",   this::handlePlayers);
            httpServer.createContext("/api/player/",   this::handlePlayer);
            httpServer.createContext("/api/status",    this::handleStatus);
            httpServer.createContext("/api/incidents", this::handleIncidents);
            httpServer.createContext("/api/metrics",   this::handleMetrics);
            httpServer.createContext("/api/action/reset", this::handleActionReset);
            httpServer.createContext("/api/action/whitelist", this::handleActionWhitelist);
            httpServer.start();
            Praxic.LOGGER.info("[PRAXIC] Web dashboard started at http://127.0.0.1:{}/", port);
            String token = Praxic.getConfig().webDashboardToken;
            if (token != null && !token.isEmpty()) {
                Praxic.LOGGER.info("[PRAXIC] Dashboard token auth enabled.");
            }
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to start web dashboard on port {}.", port, e);
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            Praxic.LOGGER.info("[PRAXIC] Web dashboard stopped.");
        }
    }

    private void loadTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("dashboard.html")) {
            if (is == null) { Praxic.LOGGER.error("[PRAXIC] dashboard.html not found."); return; }
            dashboardTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to read dashboard.html.", e);
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
        "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
        "<style>body{background:#0a0a0b;color:#71717a;font-family:system-ui;" +
        "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}" +
        "h1{color:#fafafa;font-size:18px;margin-bottom:8px}" +
        "code{background:#18181b;padding:2px 6px;border-radius:4px;color:#6366f1;font-family:monospace}" +
        "</style></head><body><div style='text-align:center'>" +
        "<h1>401 — Unauthorised</h1>" +
        "<p>Open <code>http://127.0.0.1:PORT/?token=YOUR_TOKEN</code></p>" +
        "</div></body></html>";

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleDashboard(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendHtml(ex, 401, UNAUTH_HTML); return; }
        // Inject token into JS so API calls are authorised
        String token = Praxic.getConfig().webDashboardToken;
        String html  = dashboardTemplate.replace("'{{TOKEN}}'", jsStringLiteral(token));
        sendHtml(ex, 200, html);
    }

    private void handlePlayers(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonArray arr = new JsonArray();
        for (ServerPlayer player : mcServer.getPlayerList().getPlayers()) {
            UUID uuid           = player.getUUID();
            PlayerData data     = Praxic.getCheckManager().getPlayerData(uuid);
            PlayerAnalytics anl = Praxic.getCheckManager().getAnalytics(uuid);
            double confidence   = Praxic.getConfidenceEngine().getScore(uuid);
            double anomaly      = Praxic.getAnomalyScoreEngine().getScore(uuid);

            JsonObject obj = new JsonObject();
            obj.addProperty("name",          player.getName().getString());
            obj.addProperty("uuid",          uuid.toString());
            obj.addProperty("ping",          player.connection.latency());
            obj.addProperty("confidence",    round3(confidence));
            obj.addProperty("anomaly",       round3(anomaly));
            obj.addProperty("ghostTraps",    ghostCount(uuid));
            int vl = 0;
            if (data != null) vl = data.violations.values().stream().mapToInt(Integer::intValue).sum();
            obj.addProperty("totalVl", vl);
            double dev = -1.0;
            if (anl != null && anl.baseline.baselineReady) dev = round2(anl.baseline.deviationScore);
            obj.addProperty("deviationScore", dev);
            arr.add(obj);
        }
        sendJson(ex, 200, arr.toString());
    }

    private void handlePlayer(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) { sendJson(ex, 400, "{\"error\":\"Missing name\"}"); return; }
        String requestedName = URLDecoder.decode(parts[3], StandardCharsets.UTF_8);
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(requestedName);
        if (player == null) { sendJson(ex, 404, "{\"error\":\"Not found\"}"); return; }

        UUID uuid           = player.getUUID();
        PlayerData data     = Praxic.getCheckManager().getPlayerData(uuid);
        PlayerAnalytics anl = Praxic.getCheckManager().getAnalytics(uuid);
        double confidence   = Praxic.getConfidenceEngine().getScore(uuid);
        double anomaly      = Praxic.getAnomalyScoreEngine().getScore(uuid);

        JsonObject obj = new JsonObject();
        obj.addProperty("name",        player.getName().getString());
        obj.addProperty("uuid",        uuid.toString());
        obj.addProperty("ping",        player.connection.latency());
        obj.addProperty("confidence",  round3(confidence));
        obj.addProperty("anomaly",     round3(anomaly));
        obj.addProperty("health",      player.getHealth());
        obj.addProperty("gameMode",    player.gameMode.getGameModeForPlayer().getName());
        obj.addProperty("whitelisted", Praxic.getWhitelistManager().isWhitelisted(uuid));
        obj.addProperty("ghostTraps",  ghostCount(uuid));

        if (data != null) {
            obj.addProperty("movementState", data.movementState.name());
            obj.addProperty("airTicks", data.airTicks);
            obj.addProperty("phaseTicks", data.phaseTicks);
            obj.addProperty("noSlowBuffer", data.noSlowBuffer);
            obj.addProperty("badPacketBuffer", data.badPacketBuffer);
            obj.addProperty("criticalsBuffer", data.criticalsBuffer);
        }

        JsonObject vl = new JsonObject();
        if (data != null) data.violations.forEach(vl::addProperty);
        obj.add("violations", vl);

        if (anl != null) {
            JsonObject a = new JsonObject();
            a.addProperty("entropy",      round2(anl.rotation.entropy));
            a.addProperty("maxSnapAngle", round2(anl.rotation.maxSnapAngle));
            a.addProperty("postKillSnap", round2(anl.rotation.postKillSnapAngle));
            a.addProperty("avgCps",       round2(anl.timing.avgCps));
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
        obj.add("checks", checks);
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleIncidents(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        JsonArray arr = new JsonArray();
        for (EvidenceManager.EvidenceEntry e : Praxic.getEvidenceManager().getRecent(50)) {
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
        if (parts.length < 4) { sendJson(ex, 400, "{\"error\":\"Missing player\"}"); return; }
        String name = URLDecoder.decode(parts[parts.length - 1], StandardCharsets.UTF_8);
        ServerPlayer target = mcServer.getPlayerList().getPlayerByName(name);
        if (target == null) { sendJson(ex, 404, "{\"error\":\"Player not found\"}"); return; }
        UUID uuid = target.getUUID();
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
        if (target == null) { sendJson(ex, 404, "{\"error\":\"Player not found\"}"); return; }
        if (add) Praxic.getWhitelistManager().add(target.getUUID());
        else Praxic.getWhitelistManager().remove(target.getUUID());
        sendJson(ex, 200, "{\"status\":\"ok\",\"whitelisted\":" + add + "}");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int ghostCount(UUID uuid) {
        return Praxic.getGhostEntityManager() != null
                ? Praxic.getGhostEntityManager().getActiveGhostCount(uuid) : 0;
    }

    private static String jsStringLiteral(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "'";
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
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static double round2(double v) { return v < 0 ? -1.0 : Math.round(v*100.0)/100.0; }
    private static double round3(double v) { return v < 0 ? -1.0 : Math.round(v*1000.0)/1000.0; }
}
