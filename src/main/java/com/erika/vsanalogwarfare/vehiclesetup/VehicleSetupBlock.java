package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.vehiclesetup.compat.OptionalModCompatibility;
import com.simibubi.create.AllShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.assembly.ICopyableBlock;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class VehicleSetupBlock extends BaseEntityBlock implements ICopyableBlock {
    private static final VoxelShape TOOLBOX_SHAPE = AllShapes.TOOLBOX.get(Direction.NORTH);

    public VehicleSetupBlock(Properties properties) { super(properties); }

    @Nullable
    @Override
    public CompoundTag onCopy(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
                               List<? extends ServerShip> shipsBeingCopied,
                               Map<Long, ? extends Vector3dc> centerPositions) {
        return null;
    }

    @Nullable
    @Override
    public CompoundTag onPaste(ServerLevel level, BlockPos pos, BlockState state,
                               Map<Long, Long> oldShipIdToNewId,
                               Map<Long, ? extends kotlin.Pair<? extends Vector3dc, ? extends Vector3dc>> centerPositions,
                               @Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("VehicleSetupActions", Tag.TAG_LIST)) return tag;
        ListTag actions = tag.getList("VehicleSetupActions", Tag.TAG_COMPOUND);
        for (int index = 0; index < actions.size(); index++) {
            VehicleSetupAction.remapShipIds(actions.getCompound(index), oldShipIdToNewId);
        }
        return tag;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VehicleSetupBlockEntity setup) {
            OptionalModCompatibility.warnIfIssues(serverPlayer);
            setup.run(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TOOLBOX_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return TOOLBOX_SHAPE;
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleSetupBlockEntity(pos, state);
    }
}
