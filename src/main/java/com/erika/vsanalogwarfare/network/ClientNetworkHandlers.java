package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.client.ClientScopeState;
import com.erika.vsanalogwarfare.client.VehicleMountRoleScreen;
import com.erika.vsanalogwarfare.client.VehicleMountSelectionScreen;
import com.erika.vsanalogwarfare.client.VehicleSetupEditorScreen;
import com.erika.vsanalogwarfare.client.ScopeLinkScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void openRoleName(VehicleMountPacket.OpenRoleName packet) {
        Minecraft.getInstance().setScreen(new VehicleMountRoleScreen(
                packet.handle(), packet.seat(), packet.seatPos(), packet.shipId(), packet.shipOffset()));
    }

    public static void openSelection(VehicleMountPacket.OpenSelection packet) {
        Minecraft.getInstance().setScreen(new VehicleMountSelectionScreen(
                packet.handle(), packet.revision(), packet.roles()));
    }

    public static void handleRangefinderResult(ModNetwork.RangefinderResultPacket packet) {
        double currentDist = ClientScopeState.rangefinderDistance();
        if (packet.shipDistance() > 0
                && (currentDist < 0 || packet.shipDistance() < currentDist)) {
            ClientScopeState.setRangefinderDistance(packet.shipDistance());
        }
        ClientScopeState.decrementRangefinderTasks();
    }

    public static void handleScopeState(ScopeStatePacket packet) {
        ClientScopeState.set(packet.active(), packet.fov(), packet.zoomMagnification(), packet.scopePos(), packet.mountPos(),
                packet.x(), packet.y(), packet.z(), packet.yaw(), packet.pitch(),
                packet.qx(), packet.qy(), packet.qz(), packet.qw(), packet.ballisticProfile(), packet.zeroDistance(),
                packet.highAngleZero(), packet.maxDepressionDeg(), packet.maxElevationDeg());
    }

    public static void openVehicleSetupEditor(VehicleSetupEditorPacket.VehicleSetupEditorSnapshotPacket packet) {
        VehicleSetupEditorScreen.open(packet.pos(), packet.revision(), packet.actions(), packet.removals(), packet.removalDelay());
    }

    public static void openMouseAimConfig(MouseAimConfigPacket.Snapshot packet) {
        com.erika.vsanalogwarfare.client.MouseAimConfigScreen.open(packet.pos(), packet.mode());
    }

    public static void openScopeLinks(ScopeLinkPacket.Open packet) {
        Minecraft.getInstance().setScreen(new ScopeLinkScreen(packet.scope(), packet.revision(), packet.primary(),
                packet.secondary(), packet.wireHub()));
    }

    public static void handleStabilizerState(StabilizerStatePacket packet) {
        if (packet.mountPos() == null || !packet.active() || Double.isNaN(packet.targetElevDeg())) {
            if (packet.mountPos() != null) {
                com.erika.vsanalogwarfare.stabilizer.ClientStabilizerState.clear(packet.mountPos());
            }
            return;
        }
        Level level = Minecraft.getInstance().level;
        long gameTime = level == null ? 0L : level.getGameTime();
        com.erika.vsanalogwarfare.stabilizer.ClientStabilizerState.set(
                packet.mountPos(), packet.active(), packet.targetElevDeg(), gameTime);
    }
}
