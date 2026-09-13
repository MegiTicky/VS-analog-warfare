package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.vehiclesetup.compat.GroundCollisionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class GroundCollisionDisablerBlockEntity extends BlockEntity {
    private long shipId = Long.MIN_VALUE;
    private int tickCounter;
    private boolean configured;

    public GroundCollisionDisablerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GROUND_COLLISION_DISABLER.get(), pos, state);
    }

    public void serverTick(ServerLevel level) {
        if ((tickCounter++ & 15) != 0) return;

        long currentShipId = GroundCollisionCompat.findShipId(level, worldPosition);
        if (currentShipId < 0L) {
            GroundCollisionCompat.reportNoShip(level, worldPosition);
            return;
        }
        if (shipId != currentShipId) {
            shipId = currentShipId;
            configured = false;
            setChanged();
        }
        if (configured) return;
        if (GroundCollisionCompat.disableGroundCollision(level, shipId)) {
            configured = true;
            setChanged();
        }
    }

    public boolean enableGroundCollision(ServerLevel level, ServerPlayer player) {
        long currentShipId = GroundCollisionCompat.findShipId(level, worldPosition);
        if (currentShipId < 0L) {
            player.displayClientMessage(Component.literal("Ground Collision Disabler is not on a ship."), true);
            return false;
        }
        if (!GroundCollisionCompat.enableGroundCollision(level, currentShipId)) {
            player.displayClientMessage(Component.literal("Could not enable ground collision. See latest.log."), true);
            return false;
        }
        shipId = currentShipId;
        configured = true;
        setChanged();
        player.displayClientMessage(Component.literal("Ground collision enabled for this ship."), true);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (shipId != Long.MIN_VALUE) tag.putLong("ShipId", shipId);
        tag.putBoolean("Configured", configured);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shipId = tag.contains("ShipId") ? tag.getLong("ShipId") : Long.MIN_VALUE;
        configured = tag.getBoolean("Configured");
    }
}
