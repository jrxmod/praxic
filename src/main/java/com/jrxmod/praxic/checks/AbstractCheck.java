package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;

public abstract class AbstractCheck {

    public abstract String getName();

    public abstract void check(ServerPlayer player, PlayerData data);
}
