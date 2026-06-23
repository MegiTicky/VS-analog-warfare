package com.erika.vsanalogwarfare.scope.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

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
}