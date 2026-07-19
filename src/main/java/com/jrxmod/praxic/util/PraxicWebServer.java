package com.jrxmod.praxic.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.PlayerAnalytics;
import com.jrxmod.praxic.engine.analysis.PlayerBaseline;
import com.jrxmod.praxic.manager.HistoryManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
            httpServer.setExecutor(Executors.newFixedThreadPool(2));
            httpServer.createContext("/",             this::handleDashboard);
            httpServer.createContext("/api/players",  this::handlePlayers);
            httpServer.createContext("/api/player/",  this::handlePlayer);
            httpServer.createContext("/api/status",   this::handleStatus);
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
            for (String part : query.split("&")) {
                if (part.startsWith("token=") && required.equals(part.substring(6))) return true;
            }
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
        String html  = dashboardTemplate.replace("'{{TOKEN}}'", "'" + token + "'");
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
        ServerPlayer player = mcServer.getPlayerList().getPlayerByName(parts[3]);
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
        sendJson(ex, 200, GSON.toJson(obj));
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405,-1); return; }
        if (!isAuthorised(ex)) { sendJson(ex, 401, "{\"error\":\"Unauthorised\"}"); return; }

        PraxicConfig cfg = Praxic.getConfig();
        JsonObject obj = new JsonObject();
        obj.addProperty("version",       "0.10.0");
        obj.addProperty("onlinePlayers", mcServer.getPlayerList().getPlayers().size());
        obj.addProperty("maxPlayers",    mcServer.getMaxPlayers());

        JsonObject checks = new JsonObject();
        checks.addProperty("FlyCheck",          cfg.flyCheckEnabled);
        checks.addProperty("YPredictionCheck",  cfg.yPredictionCheckEnabled);
        checks.addProperty("SpeedCheck",        cfg.speedCheckEnabled);
        checks.addProperty("JesusCheck",        cfg.jesusCheckEnabled);
        checks.addProperty("SprintCheck",       cfg.sprintCheckEnabled);
        checks.addProperty("BoatFlyCheck",      cfg.boatFlyCheckEnabled);
        checks.addProperty("ReachCheck",        cfg.reachCheckEnabled);
        checks.addProperty("KillAuraCheck",     cfg.killAuraCheckEnabled);
        checks.addProperty("VelocityCheck",     cfg.velocityCheckEnabled);
        checks.addProperty("RotationCheck",     cfg.rotationCheckEnabled);
        checks.addProperty("PostKillSnapCheck", cfg.postKillSnapCheckEnabled);
        checks.addProperty("ScaffoldCheck",     cfg.scaffoldCheckEnabled);
        checks.addProperty("FastBreakCheck",    cfg.fastBreakCheckEnabled);
        checks.addProperty("NoFallCheck",       cfg.noFallCheckEnabled);
        checks.addProperty("AutoClickerCheck",  cfg.autoClickerCheckEnabled);
        checks.addProperty("AutoTotemCheck",    cfg.autoTotemCheckEnabled);
        checks.addProperty("InventoryCheck",    cfg.inventoryCheckEnabled);
        checks.addProperty("TimerCheck",        cfg.timerCheckEnabled);
        obj.add("checks", checks);
        sendJson(ex, 200, GSON.toJson(obj));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
