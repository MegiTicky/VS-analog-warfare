package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.scope.compat.DbwWireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Releases every wire channel a scope session holds by feeding the linked hub a
 * zero button mask — exactly the packet a real tweaked controller sends on
 * deactivate — so leaving the scope (shift, logout, dimension change,
 * invalidation) never leaves signals stuck on the hub's network.
 */
public final class ScopeWireRelease {
    private ScopeWireRelease() {
    }

    public static void releaseChannels(ServerPlayer player, ScopeSession session) {
        if (player == null || session == null) return;
        if (!(player.level().getBlockEntity(session.scopePos()) instanceof ScopeBlockEntity scope)) return;
        ScopeCannonLink link = scope.getWireHubLink();
        if (link == null) return;
        BlockPos hubPos = link.resolve(player.level(), null);
        if (hubPos == null) return;
        if (!DbwWireCompat.isTweakedHub(player.level().getBlockState(hubPos).getBlock())) return;
        DbwWireCompat.receiveButton(player.level(), hubPos, (short) 0);
        DbwWireCompat.receiveAxis(player.level(), hubPos, new byte[10]);
    }
}
