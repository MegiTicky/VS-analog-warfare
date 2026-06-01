package com.erika.vsanalogwarfare.scope.ballistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.Optional;

public record BallisticProfile(boolean valid,
                               double muzzleSpeed,
                               double gravity,
                               double drag,
                               boolean quadraticDrag,
                               int lifetimeTicks,
                               String projectileId,
                               String cannonType,
                               String source) {
    public static final BallisticProfile EMPTY = new BallisticProfile(false, 0.0, 0.0, 0.0, false, 0, "", "", "");

    public static BallisticProfile of(double muzzleSpeed, double gravity, double drag, boolean quadraticDrag,
                                      int lifetimeTicks, @Nullable String projectileId, String cannonType, String source) {
        if (!Double.isFinite(muzzleSpeed) || muzzleSpeed <= 0.0) return EMPTY;
        return new BallisticProfile(true, muzzleSpeed, gravity, Math.max(0.0, drag), quadraticDrag,
                Math.max(0, lifetimeTicks), projectileId == null ? "" : projectileId, cannonType, source);
    }

    public static Optional<BallisticProfile> optional(BallisticProfile profile) {
        return profile != null && profile.valid ? Optional.of(profile) : Optional.empty();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.valid);
        if (!this.valid) return;
        buf.writeDouble(this.muzzleSpeed);
        buf.writeDouble(this.gravity);
        buf.writeDouble(this.drag);
        buf.writeBoolean(this.quadraticDrag);
        buf.writeVarInt(this.lifetimeTicks);
        buf.writeUtf(this.projectileId, 256);
        buf.writeUtf(this.cannonType, 128);
        buf.writeUtf(this.source, 256);
    }

    public static BallisticProfile decode(FriendlyByteBuf buf) {
        boolean valid = buf.readBoolean();
        if (!valid) return EMPTY;
        return new BallisticProfile(true, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                buf.readVarInt(), buf.readUtf(256), buf.readUtf(128), buf.readUtf(256));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Valid", this.valid);
        if (!this.valid) return tag;
        tag.putDouble("MuzzleSpeed", this.muzzleSpeed);
        tag.putDouble("Gravity", this.gravity);
        tag.putDouble("Drag", this.drag);
        tag.putBoolean("QuadraticDrag", this.quadraticDrag);
        tag.putInt("LifetimeTicks", this.lifetimeTicks);
        tag.putString("ProjectileId", this.projectileId);
        tag.putString("CannonType", this.cannonType);
        tag.putString("Source", this.source);
        return tag;
    }

    public static BallisticProfile load(CompoundTag tag) {
        if (tag == null || !tag.getBoolean("Valid")) return EMPTY;
        return new BallisticProfile(true, tag.getDouble("MuzzleSpeed"), tag.getDouble("Gravity"), tag.getDouble("Drag"),
                tag.getBoolean("QuadraticDrag"), tag.getInt("LifetimeTicks"), tag.getString("ProjectileId"),
                tag.getString("CannonType"), tag.getString("Source"));
    }
}
