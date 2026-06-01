package com.lauya.vsanalogwarfare.mouseaim;

import com.lauya.vsanalogwarfare.registry.ModBlockEntities;
import com.lauya.vsanalogwarfare.scope.compat.CbcCompat;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MouseAimBlock extends RotatedPillarKineticBlock implements IBE<MouseAimBlockEntity> {
    public MouseAimBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, Axis.Y);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        if (CbcCompat.isCannonMount(world.getBlockEntity(pos.relative(face)))) {
            return false;
        }
        return face.getAxis() != state.getValue(AXIS);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public Class<MouseAimBlockEntity> getBlockEntityClass() {
        return MouseAimBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MouseAimBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MOUSE_AIM.get();
    }
}
