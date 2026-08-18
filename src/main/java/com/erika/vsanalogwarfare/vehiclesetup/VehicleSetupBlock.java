package com.erika.vsanalogwarfare.vehiclesetup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleSetupEditorPacket;
import com.erika.vsanalogwarfare.vehiclesetup.compat.OptionalModCompatibility;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VmodVehicleSetupCompat;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public class VehicleSetupBlock extends BaseEntityBlock {
    private static final VoxelShape CRATE_SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public VehicleSetupBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof VehicleSetupBlockEntity setup) {
            if (player.isShiftKeyDown()) {
                ModNetwork.sendToPlayer(serverPlayer, new VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket(
                        pos, setup.revision(), setup.actions().stream().map(VehicleSetupAction::save).toList()));
                return InteractionResult.CONSUME;
            }
            OptionalModCompatibility.warnIfIssues(serverPlayer);
            VmodVehicleSetupCompat.runSetupOrLocal((ServerLevel) level, pos, serverPlayer, setup);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CRATE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return CRATE_SHAPE;
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleSetupBlockEntity(pos, state);
    }
}
