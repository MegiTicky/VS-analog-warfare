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
import java.util.Map;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;

public class VehicleSetupBlockEntity extends BlockEntity {
    private final List<VehicleSetupAction> actions = new ArrayList<>();
    private final List<VehicleSetupAction> markedRemovals = new ArrayList<>();
    private int removalDelayTicks;
    private int revision;

    public VehicleSetupBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VEHICLE_SETUP.get(), pos, state); }
    public void addAction(VehicleSetupAction action) { actions.add(action); markAndSync(); }
    public void removeAction(VehicleSetupAction action) {
        if (actions.remove(action)) markAndSync();
    }
    public List<VehicleSetupAction> actions() { return List.copyOf(actions); }
    public void clearActions() { actions.clear(); markAndSync(); }
    public List<VehicleSetupAction> markedRemovals() { return List.copyOf(markedRemovals); }
    public void addMarkedRemoval(VehicleSetupAction action) { markedRemovals.add(action); markAndSync(); }
    public boolean deleteMarkedRemoval(int index) { if (index < 0 || index >= markedRemovals.size()) return false; markedRemovals.remove(index); markAndSync(); return true; }
    public void clearMarkedRemovals() { if (!markedRemovals.isEmpty()) { markedRemovals.clear(); markAndSync(); } }
    public int removalDelayTicks() { return removalDelayTicks; }
    public boolean setRemovalDelayTicks(int delay) { if (delay < 0 || delay > 20 * 60 * 60) return false; removalDelayTicks = delay; markAndSync(); return true; }
    public int actionCount() { return actions.size(); }
    public int revision() { return revision; }
    public boolean deleteAction(int index) {
        if (index < 0 || index >= actions.size()) return false;
        actions.remove(index);
        markAndSync();
        return true;
    }
    public boolean moveAction(int from, int to) {
        if (from < 0 || from >= actions.size() || to < 0 || to >= actions.size()) return false;
        if (from != to) actions.add(to, actions.remove(from));
        markAndSync();
        return true;
    }
    public boolean setActionDelay(int index, int delay) {
        if (index < 0 || index >= actions.size() || delay < 0 || delay > 20 * 60 * 60) return false;
        actions.set(index, actions.get(index).withDelayBeforeTicks(delay));
        markAndSync();
        return true;
    }
    public void useStandardTiming() {
        for (int index = 0; index < actions.size(); index++) {
            actions.set(index, actions.get(index).withDelayBeforeTicks(index == 0 ? 0 : 1));
        }
        markAndSync();
    }
    public String actionSummary() {
        int placements = 0, removals = 0, dbw = 0, stiffness = 0, hullMgs = 0, tallyho = 0, crew = 0,
                interactions = 0, leftClicks = 0, transmitters = 0, other = 0;
        for (VehicleSetupAction action : actions) {
            switch (action.type()) {
                case PLACE_BLOCK -> placements++;
                case REMOVE_BLOCK -> removals++;
                case LINK_DBW_BACKUPS -> dbw++;
                case SET_TRACKWORK_STIFFNESS -> stiffness++;
                case SPAWN_TALLYHO_HULL_MG -> hullMgs++;
                case SPAWN_TALLYHO_ENTITY -> tallyho++;
                case SPAWN_VEHICLE_CREW -> crew++;
                case GENERIC_BLOCK_INTERACTION -> interactions++;
                case GENERIC_BLOCK_LEFT_CLICK -> leftClicks++;
                case CONFIGURE_ENDER_TRANSMITTER -> transmitters++;
                case CREATE_TWEAKED_CONTROLLER -> other++;
                default -> other++;
            }
        }
        StringBuilder summary = new StringBuilder();
        appendCount(summary, placements, "placement");
        appendCount(summary, removals, "removal");
        appendCount(summary, dbw, "DBW link");
        appendCount(summary, stiffness, "suspension setting");
        appendCount(summary, hullMgs, "hull MG");
        appendCount(summary, tallyho, "Tallyho entity");
        appendCount(summary, crew, "vehicle crew");
        appendCount(summary, interactions, "block interaction");
        appendCount(summary, leftClicks, "left-click interaction");
        appendCount(summary, transmitters, "Ender transmitter");
        appendCount(summary, other, "controller/action");
        return summary.length() == 0 ? "0 saved actions" : summary.toString();
    }

    private static void appendCount(StringBuilder summary, int count, String name) {
        if (count == 0) return;
        if (summary.length() > 0) summary.append(", ");
        summary.append(count).append(' ').append(name);
        if (count != 1) summary.append('s');
    }

    public void upsertEnderTransmitter(VehicleSetupAction action) {
        actions.removeIf(existing -> existing.type() == VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER
                && existing.targetShipId() == action.targetShipId()
                && action.shipOffset() != null && action.shipOffset().equals(existing.shipOffset()));
        actions.add(action);
        markAndSync();
    }

    public void markAndSync() {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void run(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        VehicleSetupRecordingManager.runScheduled(player, this);
    }

    /**
     * Schematic NBT carries the ship ids of the world the setup was recorded in, which never
     * match the freshly allocated ids of the pasted ships — and after a save→paste→save cycle
     * the schematic stores the pasted ship's id while the recorded actions still reference the
     * original one, so paste-time resolution misses every ship-anchored action. Rewrite each
     * recorded ship id onto the pasted ship (the same rewrite the scope block applies to its
     * links) so both the immediate post-paste run and every future save/paste generation resolve.
     */
    public void rebaseAfterSchematicPlacement(Map<Long, Object> placedShips) {
        if (placedShips == null || placedShips.isEmpty() || level == null || level.isClientSide) return;
        boolean changed = rebaseActionList(placedShips, actions);
        boolean removalsChanged = rebaseActionList(placedShips, markedRemovals);
        if (changed || removalsChanged) markAndSync();
    }

    private boolean rebaseActionList(Map<Long, Object> placedShips, List<VehicleSetupAction> list) {
        boolean changed = false;
        for (int index = 0; index < list.size(); index++) {
            VehicleSetupAction action = list.get(index);
            long targetShipId = rebasedShipId(action.targetShipId(), placedShips);
            long secondaryShipId = rebasedShipId(action.secondaryShipId(), placedShips);
            if (targetShipId == action.targetShipId() && secondaryShipId == action.secondaryShipId()) continue;
            list.set(index, action.withShipIds(targetShipId, secondaryShipId));
            changed = true;
        }
        return changed;
    }

    private static long rebasedShipId(long shipId, Map<Long, Object> placedShips) {
        if (shipId < 0L) return shipId;
        Object ship = placedShips.get(shipId);
        if (ship == null) return shipId;
        long newShipId = VehicleSetupReflection.shipId(ship);
        if (newShipId < 0L || newShipId == shipId) return shipId;
        VSAnalogWarfare.LOGGER.debug("[VSAW setup-debug] Rebased recorded ship id {} onto pasted ship {}", shipId, newShipId);
        return newShipId;
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tags = new ListTag();
        for (VehicleSetupAction action : actions) tags.add(action.save());
        tag.put("VehicleSetupActions", tags);
        ListTag removals = new ListTag(); for (VehicleSetupAction action : markedRemovals) removals.add(action.save());
        tag.put("VehicleSetupMarkedRemovals", removals);
        tag.putInt("VehicleSetupRemovalDelay", removalDelayTicks);
        tag.putInt("VehicleSetupRevision", revision);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        actions.clear();
        markedRemovals.clear(); removalDelayTicks = tag.getInt("VehicleSetupRemovalDelay");
        revision = tag.getInt("VehicleSetupRevision");
        if (tag.contains("VehicleSetupActions", Tag.TAG_LIST)) {
            ListTag tags = tag.getList("VehicleSetupActions", Tag.TAG_COMPOUND);
            for (int index = 0; index < tags.size(); index++) {
                VehicleSetupAction action = VehicleSetupAction.load(tags.getCompound(index));
                if (action != null) actions.add(action);
            }
        }
        if (tag.contains("VehicleSetupMarkedRemovals", Tag.TAG_LIST)) {
            ListTag removals = tag.getList("VehicleSetupMarkedRemovals", Tag.TAG_COMPOUND);
            for (int index = 0; index < removals.size(); index++) { VehicleSetupAction action = VehicleSetupAction.load(removals.getCompound(index)); if (action != null && action.type() == VehicleSetupActionType.REMOVE_BLOCK) markedRemovals.add(action); }
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
