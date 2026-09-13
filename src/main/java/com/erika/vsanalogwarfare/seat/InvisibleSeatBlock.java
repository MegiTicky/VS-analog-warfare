package com.erika.vsanalogwarfare.seat;

import java.util.List;

import com.erika.vsanalogwarfare.client.ClientInvisibleSeatHighlight;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Create seat that renders nothing, never blocks movement, and whose riders
 * stand at the spot instead of sitting (see {@link InvisibleSeatEntity}).
 *
 * <p>Seating behaviour is inherited from Create's {@link SeatBlock}: the block
 * keeps the seat outline shape so it can be targeted, right-clicked, and
 * broken, but its collision shape is empty so invisible furniture never bumps
 * passers-by. Because nothing ever lands on it, Create's mob-pickup-on-fall
 * hook cannot fire; leashed-mob pickup via right-click still works. Dye
 * recolouring is deliberately dropped so the seat can never turn visible, and
 * the spawned seat entity is {@link InvisibleSeatEntity}.
 */
public class InvisibleSeatBlock extends SeatBlock {
    public InvisibleSeatBlock(Properties properties, DyeColor color) {
        super(properties, color);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Held-item reveal (client only): a dust puff per random display tick
     * marks the seat while the player holds the reveal item. Delegates to a
     * client-only helper — the body never runs on dedicated servers, so the
     * client class is never classloaded there.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ClientInvisibleSeatHighlight.spawnIndicatorParticles(level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        List<SeatEntity> seats = world.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!seats.isEmpty()) {
            SeatEntity seatEntity = seats.get(0);
            List<Entity> passengers = seatEntity.getPassengers();
            if (!passengers.isEmpty() && passengers.get(0) instanceof Player) {
                return InteractionResult.PASS;
            }
            if (!world.isClientSide) {
                seatEntity.unRide();
                player.startRiding(seatEntity);
            }
            return InteractionResult.SUCCESS;
        }
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Entity toSeat = SeatBlock.getLeashed(world, player).or(player);
        sitDown(world, pos, toSeat);
        return InteractionResult.SUCCESS;
    }

    public static void sitDown(Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide) {
            return;
        }
        InvisibleSeatEntity seat = new InvisibleSeatEntity(world, pos);
        seat.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // No ship handling here: the entity is a VS2 shipyard entity (shipyard
        // coordinates are exactly what setPos receives, since VS2 remaps the
        // interaction position), so VS2 keeps it attached to the ship.
        world.addFreshEntity(seat);
        entity.startRiding(seat, true);
        if (entity instanceof TamableAnimal tamable) {
            tamable.setOrderedToSit(true);
        }
    }
}
