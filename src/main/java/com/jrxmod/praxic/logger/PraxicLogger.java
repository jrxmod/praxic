package com.jrxmod.praxic.logger;

import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PraxicLogger {

    private static final Path LOG_PATH = Paths.get("logs", "praxic.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Max log file size in bytes before rotation (5MB)
    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024;

    public static void init() {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            if (!Files.exists(LOG_PATH)) {
                Files.createFile(LOG_PATH);
            }
            log("INFO", "PRAXIC logger initialized.");
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to initialize file logger.", e);
        }
    }

    public static void logViolation(String checkName, String playerName, int violations, String details) {
        log("VIOLATION", String.format("[%s] Player: %s | VL: %d | %s", checkName, playerName, violations, details));
    }

    public static void logKick(String playerName, String checkName) {
        log("KICK", String.format("Player %s was kicked by %s", playerName, checkName));
    }

    public static void logInfo(String message) {
        log("INFO", message);
    }

    private static void log(String level, String message) {
        try {
            rotateIfNeeded();
            String line = String.format("[%s] [%s] %s%n",
                    LocalDateTime.now().format(FORMATTER), level, message);
            Files.writeString(LOG_PATH, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to write to log file.", e);
        }
    }

    // Rotate log file if it exceeds max size
    private static void rotateIfNeeded() throws IOException {
        if (!Files.exists(LOG_PATH)) return;
        if (Files.size(LOG_PATH) < MAX_LOG_SIZE) return;

        Path rotated = Paths.get("logs", "praxic-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log");
        Files.move(LOG_PATH, rotated);
        Files.createFile(LOG_PATH);
        log("INFO", "Log rotated. Previous log saved to: " + rotated.getFileName());
    }
}
