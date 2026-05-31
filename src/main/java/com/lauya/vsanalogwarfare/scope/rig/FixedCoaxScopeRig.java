package com.lauya.vsanalogwarfare.scope.rig;

import com.lauya.vsanalogwarfare.scope.ScopeBlock;
import com.lauya.vsanalogwarfare.scope.ScopeBlockEntity;
import com.lauya.vsanalogwarfare.scope.compat.CbcCompat;
import com.lauya.vsanalogwarfare.scope.compat.VsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FixedCoaxScopeRig implements CameraRig {
    private static final int MAX_AIR_SEARCH_BLOCKS = 32;

    private final ScopeBlockEntity scope;
    private final Level level;
    private final BlockPos scopePos;
    private final BlockPos mountPos;

    public FixedCoaxScopeRig(ScopeBlockEntity scope, BlockPos mountPos) {
        this.scope = scope;
        this.level = scope.getLevel();
        this.scopePos = scope.getBlockPos();
        this.mountPos = mountPos;
    }

    @Override
    public CameraPose getCameraPose(float partialTicks) {
        Direction facing = getFacing();
        Direction viewOffsetDirection = facing.getOpposite(); // Tallyho-style: walk away from the scope until air.
        Vec3 localCameraPos = findFirstAirViewPosition(viewOffsetDirection).add(scope.getCameraOffset());
        Vec3 position = VsCompat.shipToWorldPosition(level, scopePos, localCameraPos);
        Vec3 direction = CbcCompat.getAimDirection(level, mountPos, viewOffsetDirection)
                .orElse(Vec3.atLowerCornerOf(viewOffsetDirection.getNormal()).normalize());
        Vec3 up = CbcCompat.getAimUpDirection(level, mountPos, viewOffsetDirection, Direction.UP)
                .orElse(VsCompat.shipToWorldDirection(level, scopePos, new Vec3(0.0, 1.0, 0.0)));
        return CameraPose.looking(position, direction, up);
    }

    @Override
    public float getFov() {
        return scope.getFov();
    }

    private Direction getFacing() {
        BlockState state = level.getBlockState(scopePos);
        return state.hasProperty(ScopeBlock.FACING) ? state.getValue(ScopeBlock.FACING) : Direction.NORTH;
    }

    private Vec3 findFirstAirViewPosition(Direction direction) {
        BlockPos.MutableBlockPos cursor = scopePos.mutable();
        for (int i = 0; i < MAX_AIR_SEARCH_BLOCKS; i++) {
            if (!level.isLoaded(cursor)) {
                break;
            }
            if (level.getBlockState(cursor).isAir()) {
                return Vec3.atCenterOf(cursor);
            }
            cursor.move(direction);
        }

        // If no air was found, still move the camera off the block instead of leaving it inside the scope.
        return Vec3.atCenterOf(scopePos).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.65));
    }
}
