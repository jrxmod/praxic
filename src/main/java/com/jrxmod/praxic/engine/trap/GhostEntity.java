package com.jrxmod.praxic.engine.trap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Honeypot entity for KillAura/AimAssist detection.
 * Uses invisible ArmorStand.
 */
public class GhostEntity {

    private final UUID uuid;
    private final ServerLevel level;
    private ArmorStand entity;
    private long spawnTime;
    private boolean active = true;

    public GhostEntity(ServerLevel level, Vec3 position) {
        this.uuid = UUID.randomUUID();
        this.level = level;
        this.spawnTime = System.currentTimeMillis();
        spawn(position);
    }

    private void spawn(Vec3 pos) {
        entity = new ArmorStand(EntityType.ARMOR_STAND, level);
        entity.setPos(pos.x, pos.y, pos.z);
        
        entity.setInvisible(true);
        entity.setInvulnerable(true);
        entity.setNoGravity(true);
        entity.setCustomNameVisible(false);
        
        level.addFreshEntity(entity);
    }

    public void despawn() {
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
        active = false;
    }

    public boolean isActive() {
        return active && entity != null && entity.isAlive();
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public Entity getEntity() {
        return entity;
    }

    public Vec3 getPosition() {
        return entity != null ? entity.position() : Vec3.ZERO;
    }
}