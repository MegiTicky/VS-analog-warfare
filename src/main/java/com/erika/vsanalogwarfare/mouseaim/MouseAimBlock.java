package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MouseAimBlock extends RotatedPillarKineticBlock implements IBE<MouseAimBlockEntity> {
    /** Marker for the internal turret-output persona of this block. It is never
     * written into the world: only the synthetic block state handed to
     * {@link MouseAimOutputInterface} carries it, so that the output sub-block
     * entity connects kinetically through the arrow-marked face alone, while the
     * main block entity keeps its ordinary power connections.
     */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    public MouseAimBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OUTPUT, Boolean.FALSE));
    }

    /**
     * The face the turret-mode rotation leaves the block through — the face the
     * arrow texture marks. It follows the block's placed {@link #AXIS} so the
     * output is consistent with how the model rotates the arrow (see the
     * blockstate's axis transforms): X → east, Z → south, Y → up.
     */
    public static Direction getOutputFace(BlockState state) {
        return switch (state.getValue(AXIS)) {
            case X -> Direction.EAST;
            case Z -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OUTPUT);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        BlockEntity neighbor = world.getBlockEntity(pos.relative(face));
        if (CbcCompat.isCannonMount(neighbor)) {
            return false;
        }
        Direction outputFace = getOutputFace(state);
        if (state.getValue(OUTPUT)) {
            // The turret-output persona only reaches through the arrow face.
            return face == outputFace;
        }
        // The power persona accepts shafts on any face that isn't the arrowed
        // output face (and isn't the mount itself).
        return face != outputFace && face.getAxis() != state.getValue(AXIS);
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
