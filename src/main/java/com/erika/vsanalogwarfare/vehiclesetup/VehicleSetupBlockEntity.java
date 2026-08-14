package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class VehicleSetupBlockEntity extends BlockEntity {
    private final List<VehicleSetupAction> actions = new ArrayList<>();

    public VehicleSetupBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VEHICLE_SETUP.get(), pos, state); }
    public void addAction(VehicleSetupAction action) { actions.add(action); markAndSync(); }
    public List<VehicleSetupAction> actions() { return List.copyOf(actions); }
    public void clearActions() { actions.clear(); markAndSync(); }
    public int actionCount() { return actions.size(); }

    public void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void run(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        int succeeded = 0;
        String firstError = null;
        for (VehicleSetupAction action : actions) {
            String error = VehicleSetupExecutor.run(level, worldPosition, player, action);
            if (error == null) succeeded++; else if (firstError == null) firstError = error;
        }
        player.displayClientMessage(Component.literal(firstError == null
                ? "Vehicle setup complete: " + succeeded + " actions."
                : "Vehicle setup: " + succeeded + " complete. " + firstError), true);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tags = new ListTag();
        for (VehicleSetupAction action : actions) tags.add(action.save());
        tag.put("VehicleSetupActions", tags);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        actions.clear();
        if (!tag.contains("VehicleSetupActions", Tag.TAG_LIST)) return;
        ListTag tags = tag.getList("VehicleSetupActions", Tag.TAG_COMPOUND);
        for (int index = 0; index < tags.size(); index++) {
            VehicleSetupAction action = VehicleSetupAction.load(tags.getCompound(index));
            if (action != null) actions.add(action);
        }
    }

    @Override public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
