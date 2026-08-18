package com.erika.vsanalogwarfare.vehiclemount;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.VehicleMountPacket;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerPlayer;
import javax.annotation.Nullable;

public class VehicleMountHandleBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public VehicleMountHandleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.getItemInHand(hand).getItem() instanceof AnalogScrewdriverItem) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof VehicleMountHandleBlockEntity handle && handle.locked()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("This vehicle mount handle is locked."), true);
                return InteractionResult.FAIL;
            }
            player.getItemInHand(hand).getOrCreateTag().putLong("VehicleMountHandle", pos.asLong());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Handle selected. Right-click a Create seat with the screwdriver to link it."), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof VehicleMountHandleBlockEntity handle) {
            if (handle.seats().isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("This handle has no linked seats."), true);
            } else if (handle.seats().size() == 1) {
                VehicleMountManager.mount(serverPlayer, pos, 0);
            } else {
                ModNetwork.sendToPlayer(serverPlayer, new VehicleMountPacket.OpenSelection(pos, handle.revision(), handle.seats().stream().map(VehicleMountSeatLink::role).toList()));
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof VehicleMountHandleBlockEntity handle) {
            handle.captureShipPosition();
            handle.initializeRedstoneState(level.hasNeighborSignal(pos));
        }
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.getBlockEntity(pos) instanceof VehicleMountHandleBlockEntity handle) {
            handle.updateRedstoneState(level.hasNeighborSignal(pos));
        }
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return Shapes.empty(); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VehicleMountHandleBlockEntity(pos, state); }

    @Nullable @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !level.isClientSide
                ? (tickerLevel, pos, tickerState, blockEntity) -> {
                    VehicleMountHandleBlockEntity handle = (VehicleMountHandleBlockEntity) blockEntity;
                    if ((tickerLevel.getGameTime() & 7L) == 0L) handle.captureShipPosition();
                }
                : null;
    }
}
