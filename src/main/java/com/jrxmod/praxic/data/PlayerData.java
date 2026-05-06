package com.jrxmod.praxic.data;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {

    // Позиция на прошлом тике
    public double prevX;
    public double prevY;
    public double prevZ;

    // Сколько тиков игрок в воздухе без причины
    public int airTicks = 0;

    // Количество нарушений по каждому чеку
    public Map<String, Integer> violations = new HashMap<>();

    // Время последнего предупреждения по чеку (в мс), чтобы не спамить
    public Map<String, Long> lastFlagTime = new HashMap<>();

    public PlayerData(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }

    public int getViolations(String checkName) {
        return violations.getOrDefault(checkName, 0);
    }

    public void addViolation(String checkName) {
        violations.put(checkName, getViolations(checkName) + 1);
        lastFlagTime.put(checkName, System.currentTimeMillis());
    }

    public void resetViolations(String checkName) {
        violations.put(checkName, 0);
    }

    public boolean canFlag(String checkName, long cooldownMs) {
        long last = lastFlagTime.getOrDefault(checkName, 0L);
        return System.currentTimeMillis() - last >= cooldownMs;
    }

    public void updatePosition(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }
}
