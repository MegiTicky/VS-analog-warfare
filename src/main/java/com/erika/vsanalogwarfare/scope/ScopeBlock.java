package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.simibubi.create.AllShapes;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;

public class ScopeBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.1875, 0.1875, 0.8125, 0.8125, 0.8125);

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    public ScopeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState blockstate = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());

        if (clickedFace == Direction.UP) {
            return blockstate.setValue(FACE, AttachFace.FLOOR);
        } else if (clickedFace == Direction.DOWN) {
            return blockstate.setValue(FACE, AttachFace.CEILING);
        } else {
            return blockstate.setValue(FACE, AttachFace.WALL).setValue(FACING, clickedFace);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ScopeBlockEntity scope)) {
            return InteractionResult.PASS;
        }
        ScopeSessionManager.start(serverPlayer, scope);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ScopeBlockEntity scope) {
            scope.captureVsAnchor();
        }
    }

    // --- CARDINAL WALL BASES ---
    private static final VoxelShape SHAPE_NORTH = Shapes.box(0.09375, 0.375, 0.8125, 0.90625, 0.8125, 1.0);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.09375, 0.375, 0.0, 0.90625, 0.8125, 0.1875);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0.0, 0.375, 0.09375, 0.1875, 0.8125, 0.90625);
    private static final VoxelShape SHAPE_WEST = Shapes.box(0.8125, 0.375, 0.09375, 1.0, 0.8125, 0.90625);

    // --- HORIZONTAL RECTANGLE ROTATIONS (Y-AXIS GRID) ---
// Base configuration (North)
    private static final VoxelShape FLAT_NORTH = Shapes.box(0.09375, 0.0, 0.375, 0.90625, 0.1875, 0.8125);

    // Rotated 90 degrees (East/West layout role swapping)
    private static final VoxelShape FLAT_EAST_WEST_AXIS = Shapes.box(0.375, 0.0, 0.09375, 0.8125, 0.1875, 0.90625);

    // Rotated 180 degrees (South / your updated Floor East requirement)
    private static final VoxelShape FLAT_SOUTH = Shapes.box(0.09375, 0.0, 0.1875, 0.90625, 0.1875, 0.625);


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction connectedDirection;
        AttachFace face = state.getValue(FACE);

        // Map the shape to align directly with Create's structural placement matrix logic
        switch (face) {
            case CEILING:
                connectedDirection = Direction.DOWN;
                break;
            case FLOOR:
                connectedDirection = Direction.UP;
                break;
            case WALL:
            default:
                connectedDirection = state.getValue(FACING);
                break;
        }

        // Return Create's dynamic pre-calculated shape profile
        return AllShapes.PLACARD.get(connectedDirection);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScopeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SCOPE.get(), ScopeBlockEntity::tick);
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == actual ? (BlockEntityTicker<A>) ticker : null;
    }
}