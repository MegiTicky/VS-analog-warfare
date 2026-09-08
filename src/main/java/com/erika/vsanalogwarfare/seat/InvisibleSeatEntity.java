package com.erika.vsanalogwarfare.seat;

import com.erika.vsanalogwarfare.registry.ModEntities;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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
 * <p>When the seat is placed on a Valkyrien Skies ship, the entity stores the
 * ship id plus its ship-local anchor and re-derives its world position from
 * the ship transform every tick, both sides. That keeps the inherited
 * SeatEntity keep-alive check (block must still be under the entity) true on
 * moving ships and glues the rider to the spot; VS2's own entity dragger is
 * not involved because the entity is {@code noPhysics} and the rider is a
 * passenger.
 */
public class InvisibleSeatEntity extends SeatEntity {
    private long shipId;
    private double shipLocalX;
    private double shipLocalY;
    private double shipLocalZ;

    public InvisibleSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public InvisibleSeatEntity(Level world, BlockPos pos) {
        this(ModEntities.INVISIBLE_SEAT.get(), world);
        this.noPhysics = true;
    }

    /** Pins the seat to a ship position; called before the entity is added. */
    public void setShipAnchor(long shipId, Vec3 shipLocalAnchor) {
        this.shipId = shipId;
        this.shipLocalX = shipLocalAnchor.x;
        this.shipLocalY = shipLocalAnchor.y;
        this.shipLocalZ = shipLocalAnchor.z;
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
        if (shipId != 0L) {
            repositionToShip();
        }
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

    private void repositionToShip() {
        Level level = this.level();
        if (level == null) {
            return;
        }
        Object ship = findShipById(level, shipId);
        if (ship == null) {
            return;
        }
        Vec3 world = VsCompat.shipToWorldPosition(ship, new Vec3(shipLocalX, shipLocalY, shipLocalZ));
        this.setPos(world.x, world.y, world.z);
    }

    private static Object findShipById(Level level, long id) {
        for (Object ship : VsCompat.getAllShips(level)) {
            if (VsCompat.getShipId(ship) == id) {
                return ship;
            }
        }
        return null;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (passenger instanceof Player player && player.getForcedPose() == Pose.STANDING) {
            player.setForcedPose(null);
        }
        super.removePassenger(passenger);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeVarLong(shipId);
        buffer.writeDouble(shipLocalX);
        buffer.writeDouble(shipLocalY);
        buffer.writeDouble(shipLocalZ);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf additionalData) {
        this.shipId = additionalData.readVarLong();
        this.shipLocalX = additionalData.readDouble();
        this.shipLocalY = additionalData.readDouble();
        this.shipLocalZ = additionalData.readDouble();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("VSAWShipId", shipId);
        tag.putDouble("VSAWLocalX", shipLocalX);
        tag.putDouble("VSAWLocalY", shipLocalY);
        tag.putDouble("VSAWLocalZ", shipLocalZ);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.shipId = tag.getLong("VSAWShipId");
        this.shipLocalX = tag.getDouble("VSAWLocalX");
        this.shipLocalY = tag.getDouble("VSAWLocalY");
        this.shipLocalZ = tag.getDouble("VSAWLocalZ");
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
