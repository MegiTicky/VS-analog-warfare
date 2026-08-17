package com.erika.vsanalogwarfare.vehiclesetup;

import com.simibubi.create.AllShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class GroundCollisionDisablerBlock extends BaseEntityBlock {
    private static final VoxelShape TOOLBOX_SHAPE = AllShapes.TOOLBOX.get(Direction.NORTH);

    public GroundCollisionDisablerBlock(Properties properties) {
        super(properties);
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

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundCollisionDisablerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !level.isClientSide
                ? (tickerLevel, pos, tickerState, blockEntity) ->
                ((GroundCollisionDisablerBlockEntity) blockEntity).serverTick((net.minecraft.server.level.ServerLevel) tickerLevel)
                : null;
    }
}
