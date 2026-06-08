package com.erika.vsanalogwarfare.scope.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

public class DhCompat {
    private static final boolean IS_DH_LOADED = ModList.get().isLoaded("distanthorizons");

    public static double getDistantHorizonsRaycastDistance(Vec3 start, Vec3 direction, double maxDistance) {
        if (!IS_DH_LOADED) {
            return -1.0;
        }

        try {
            return doDhRaycast(start, direction, maxDistance);
        } catch (Throwable e) {
            return -1.0;
        }
    }

    private static double doDhRaycast(Vec3 start, Vec3 direction, double maxDistance) {
        IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
        if (levelWrapper == null) {
            return -1.0;
        }

        DhApiResult<DhApiRaycastResult> result = DhApi.Delayed.terrainRepo.raycast(
                levelWrapper,
                start.x, start.y, start.z,
                (float) direction.x, (float) direction.y, (float) direction.z,
                (int) maxDistance
        );

        // CHANGED: Using .payload instead of .getValue() or .value()
        if (result != null && result.payload != null) {
            DhApiRaycastResult hit = result.payload;

            if (hit.pos != null) {
                // Using .x, .y, .z from the 2.1.0 Vec3i math structures
                Vec3 hitPos = new Vec3(hit.pos.x, hit.pos.y, hit.pos.z);
                return start.distanceTo(hitPos);
            }
        }

        return -1.0;
    }
}