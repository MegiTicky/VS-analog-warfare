package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.MouseAimConfigPacket;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

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
     * blockstate's axis transforms): X → east, Z → south, Y → up. The opposite
     * (negative) axis end is the single power input.
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
        // The power persona is purely in-line: a single input hole on the end
        // opposite the arrowed output face, exactly like an encased chain drive.
        return face.getAxis() == state.getValue(AXIS)
                && face.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
    }

    /**
     * Right-click with an empty hand opens the config screen (aim mode +
     * output strength). The wrench still takes precedence: as an item its
     * useOn runs before the block's use, so axis rotation keeps working.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MouseAimBlockEntity aim) {
            ModNetwork.sendToPlayer(serverPlayer, new MouseAimConfigPacket.Snapshot(
                    pos, aim.getMode(), aim.getTurretStrength()));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
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
