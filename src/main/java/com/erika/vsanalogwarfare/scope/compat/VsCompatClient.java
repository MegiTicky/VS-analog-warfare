package com.erika.vsanalogwarfare.scope.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

import java.lang.reflect.Method;

final class VsCompatClient {
    private VsCompatClient() {}

    static boolean isPlayerMountedToShip(Method getShipMountedToMethod) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return false;

        try {
            Object result = getShipMountedToMethod.invoke(null, player);
            return result != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    static Quaternionf playerMountedShipRotation(Method getShipMountedToMethod) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() == null) return null;

        try {
            Object ship = getShipMountedToMethod.invoke(null, player);
            if (ship == null) return null;

            Object renderTransform = ship.getClass().getMethod("getRenderTransform").invoke(ship);
            if (renderTransform == null) return null;

            // Prefer the exact getter VS2's mounted camera uses so the
            // counter-rotation cancels it with no lag or drift.
            Method rotationGetter = null;
            for (String name : new String[]{"getShipCoordinatesToWorldCoordinatesRotation", "getShipToWorldRotation"}) {
                try {
                    rotationGetter = renderTransform.getClass().getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (rotationGetter == null) return null;

            Object rotation = rotationGetter.invoke(renderTransform);
            if (rotation instanceof Quaterniondc qd) {
                return new Quaternionf((float) qd.x(), (float) qd.y(), (float) qd.z(), (float) qd.w()).normalize();
            }
            if (rotation instanceof Quaternionf qf) {
                return new Quaternionf(qf).normalize();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }
}