package com.erika.vsanalogwarfare.stabilizer;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client mirror of the server stabilizer state, written by
 * {@code StabilizerStatePacket}. Deliberately free of client-only classes so
 * the common-code control loop can read it on both sides.
 */
public final class ClientStabilizerState {

    public record Entry(boolean active, double targetElevDeg, long receivedGameTime) {
    }

    private static final Map<Long, Entry> STATES = new ConcurrentHashMap<>();
    private static final int ENTRY_TTL_TICKS = 100;

    private ClientStabilizerState() {
    }

    public static void set(BlockPos mountPos, boolean active, double targetElevDeg, long gameTime) {
        STATES.put(mountPos.asLong(), new Entry(active, targetElevDeg, gameTime));
    }

    @Nullable
    public static Entry get(BlockPos mountPos) {
        Entry entry = STATES.get(mountPos.asLong());
        if (entry == null) {
            return null;
        }
        return entry; // TTL filtering happens in tick() to keep the hot path cheap.
    }

    public static void tick(long gameTime) {
        if (STATES.isEmpty()) {
            return;
        }
        STATES.values().removeIf(entry -> gameTime - entry.receivedGameTime() > ENTRY_TTL_TICKS);
    }

    public static void clear(BlockPos mountPos) {
        STATES.remove(mountPos.asLong());
    }

    public static void clearAll() {
        STATES.clear();
    }
}
