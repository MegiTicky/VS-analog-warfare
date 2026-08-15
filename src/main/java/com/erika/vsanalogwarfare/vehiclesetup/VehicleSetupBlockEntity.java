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

    public VehicleSetupBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VEHICLE_SETUP.get(), pos, state);
    }

    public void addAction(VehicleSetupAction action) {
        actions.add(action);
        markAndSync();
    }

    public void clearActions() {
        actions.clear();
        markAndSync();
    }

    public List<VehicleSetupAction> actions() { return List.copyOf(actions); }
    public int actionCount() { return actions.size(); }

    public String actionSummary() {
        int placements = 0, removals = 0, dbw = 0, controllers = 0, stiffness = 0;
        for (VehicleSetupAction action : actions) {
            switch (action.type()) {
                case PLACE_BLOCK -> placements++;
                case REMOVE_BLOCK -> removals++;
                case LINK_DBW_BACKUPS -> dbw++;
                case CREATE_TWEAKED_CONTROLLER -> controllers++;
                case SET_TRACKWORK_STIFFNESS -> stiffness++;
            }
        }
        StringBuilder summary = new StringBuilder();
        appendCount(summary, placements, "placement");
        appendCount(summary, removals, "removal");
        appendCount(summary, dbw, "DBW link");
        appendCount(summary, controllers, "controller");
        appendCount(summary, stiffness, "suspension setting");
        return summary.length() == 0 ? "0 saved actions" : summary.toString();
    }

    private static void appendCount(StringBuilder summary, int count, String name) {
        if (count == 0) return;
        if (summary.length() > 0) summary.append(", ");
        summary.append(count).append(' ').append(name);
        if (count != 1) summary.append('s');
    }

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
            if (error == null) succeeded++;
            else if (firstError == null) firstError = error;
        }
        player.displayClientMessage(Component.literal(firstError == null
                ? "Vehicle setup complete: " + succeeded + " actions."
                : "Vehicle setup: " + succeeded + " complete. " + firstError), true);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag actionTags = new ListTag();
        for (VehicleSetupAction action : actions) actionTags.add(action.save());
        tag.put("VehicleSetupActions", actionTags);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        actions.clear();
        if (!tag.contains("VehicleSetupActions", Tag.TAG_LIST)) return;
        ListTag actionTags = tag.getList("VehicleSetupActions", Tag.TAG_COMPOUND);
        for (int index = 0; index < actionTags.size(); index++) {
            VehicleSetupAction action = VehicleSetupAction.load(actionTags.getCompound(index));
            if (action != null) actions.add(action);
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public void handleUpdateTag(CompoundTag tag) { load(tag); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
