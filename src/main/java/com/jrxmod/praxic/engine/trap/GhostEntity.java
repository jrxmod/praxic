package com.jrxmod.praxic.engine.trap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Honeypot entity for KillAura / AimAssist detection.
 *
 * Invisible marker ArmorStand: no collision, no gravity, silent. Marker and
 * small flags are applied through reflection because both setters are private
 * under official Mojang mappings for 1.21.1. Visibility to other players is
 * filtered by ChunkMapTrackedEntityMixin.
 */
public class GhostEntity {

    private final UUID ownerUuid;
    private final ServerLevel level;
    private ArmorStand entity;
    private long spawnTime;
    private boolean active = true;

    public GhostEntity(ServerLevel level, Vec3 position, UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.level = level;
        this.spawnTime = System.currentTimeMillis();
        spawn(position);
    }

    private void spawn(Vec3 pos) {
        entity = new ArmorStand(EntityType.ARMOR_STAND, level);
        entity.setPos(pos.x, pos.y, pos.z);

        entity.setInvisible(true);
        entity.setNoGravity(true);
        entity.setCustomNameVisible(false);
        entity.setSilent(true);
        entity.setInvulnerable(false);
        entity.noPhysics = true;

        invokeBooleanSetter(entity, "setMarker", true);
        invokeBooleanSetter(entity, "setSmall", true);
        try {
            entity.setNoBasePlate(true);
        } catch (Exception | Error ignored) {}

        level.addFreshEntity(entity);
    }

    /**
     * Invokes a private or public {@code name(boolean)} method if present.
     * Failure leaves the default ArmorStand flag; the honeypot still functions.
     */
    private static void invokeBooleanSetter(Object target, String name, boolean value) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Method m = cls.getDeclaredMethod(name, boolean.class);
                m.setAccessible(true);
                m.invoke(target, value);
                return;
            } catch (NoSuchMethodException e) {
                cls = cls.getSuperclass();
            } catch (Exception ignored) {
                return;
            }
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

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public Entity getEntity() {
        return entity;
    }

    public UUID getEntityUuid() {
        return entity != null ? entity.getUUID() : null;
    }

    public Vec3 getPosition() {
        return entity != null ? entity.position() : Vec3.ZERO;
    }
}
