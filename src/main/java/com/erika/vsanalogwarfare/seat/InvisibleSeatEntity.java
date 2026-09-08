package com.erika.vsanalogwarfare.seat;

import com.erika.vsanalogwarfare.registry.ModEntities;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Create seat whose riders stand instead of sitting.
 *
 * <p>Riding animation is suppressed via {@link #shouldRiderSit()} (Forge's
 * renderer hook), the rider's feet are parked on the seat block's floor level
 * via the {@link #positionRider} override, and both sides re-assert a STANDING
 * pose every tick so sneaking cannot fold the rider into a crouch (players use
 * the vanilla {@code forcedPose} hook, which also wins over the client's own
 * pose update). The pose is released in {@link #removePassenger}, which fires
 * both on sneak-dismount and when the seat itself discards.
 *
 * <p>When the seat is placed on a Valkyrien Skies ship, no special handling is
 * needed: the entity is paired with VS2's {@code valkyrienskies:shipyard}
 * handler (data/vs_analog_warfare/vs_entities/invisible_seat.json, plus the
 * class-name heuristic), so VS2 keeps it in shipyard coordinates — where the
 * blocks physically live — and transforms the rider shipyard-to-world itself.
 * Moving the entity to world coordinates would break the inherited keep-alive
 * check, so the entity must never be repositioned manually.
 */
public class InvisibleSeatEntity extends SeatEntity {
    public InvisibleSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public InvisibleSeatEntity(Level world, BlockPos pos) {
        this(ModEntities.INVISIBLE_SEAT.get(), world);
        this.noPhysics = true;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        // Feet at the seat cell's bottom: with the seat on a deck the rider
        // stands exactly on the deck surface (standing eye ~1.62 above it).
        callback.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                if (player.getForcedPose() != Pose.STANDING) {
                    player.setForcedPose(Pose.STANDING);
                }
            } else if (passenger.getPose() != Pose.STANDING) {
                passenger.setPose(Pose.STANDING);
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (passenger instanceof Player player && player.getForcedPose() == Pose.STANDING) {
            player.setForcedPose(null);
        }
        super.removePassenger(passenger);
    }

    /** Renderer that draws nothing — the seat entity is invisible by design. */
    public static class Render extends EntityRenderer<InvisibleSeatEntity> {
        public Render(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(InvisibleSeatEntity entity, Frustum frustum,
                                    double camX, double camY, double camZ) {
            return false;
        }

        @Override
        public ResourceLocation getTextureLocation(InvisibleSeatEntity entity) {
            return null;
        }
    }
}
