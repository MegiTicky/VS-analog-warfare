package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class DecorationBearingBlock extends BearingBlock implements EntityBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(1, 1, 1, 15, 15, 15);

    public DecorationBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.getItemInHand(hand).getItem() instanceof AnalogScrewdriverItem) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof DecorationBearingBlockEntity bearing) {
                DecorationBearingLinkManager.select(player, pos);
                return InteractionResult.CONSUME;
            }
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DecorationBearingBlockEntity bearing) {
            if (player.isShiftKeyDown()) {
                bearing.disassemble();
            } else if (bearing.isRunning()) {
                bearing.disassemble();
            } else {
                if (bearing.getLinkedMountPos() == null) {
                    player.displayClientMessage(Component.literal(
                            "Decoration bearing is not linked to a cannon mount. Use the Analog Screwdriver first."),
                            true);
                } else {
                    bearing.assemble();
                }
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecorationBearingBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return type == ModBlockEntities.DECORATION_BEARING.get()
                ? (level1, pos1, state1, be) -> DecorationBearingBlockEntity.tick(level1, pos1, state1,
                (DecorationBearingBlockEntity) be)
                : null;
    }
}
