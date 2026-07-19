package com.jrxmod.praxic.engine.trap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Represents a "ghost" entity used as a honeypot for KillAura/AimAssist detection.
 * The entity is invisible to legitimate players and only reacts to attacks from cheaters.
 */
public class GhostEntity {

    public enum GhostType {
        ZOMBIE,
        SKELETON,
        CREEPER
    }

    private final UUID uuid;
    private final GhostType type;
    private final ServerLevel level;
    private Entity entity;
    private long spawnTime;
    private boolean active = true;

    public GhostEntity(ServerLevel level, Vec3 position, GhostType type) {
        this.uuid = UUID.randomUUID();
        this.type = type;
        this.level = level;
        this.spawnTime = System.currentTimeMillis();
        spawn(position);
    }

    private void spawn(Vec3 pos) {
        switch (type) {
            case ZOMBIE -> entity = new Zombie(EntityType.ZOMBIE, level);
            case SKELETON -> entity = new Skeleton(EntityType.SKELETON, level);
            case CREEPER -> entity = new Creeper(EntityType.CREEPER, level);
        }

        if (entity != null) {
            entity.setPos(pos.x, pos.y, pos.z);
            entity.setInvisible(true);
            entity.setInvulnerable(true);
            entity.setNoGravity(true);
            level.addFreshEntity(entity);
        }
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

    public GhostType getType() {
        return type;
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