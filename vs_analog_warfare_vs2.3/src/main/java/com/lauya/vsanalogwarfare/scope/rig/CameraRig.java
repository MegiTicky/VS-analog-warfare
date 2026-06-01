package com.lauya.vsanalogwarfare.scope.rig;

public interface CameraRig {
    CameraPose getCameraPose(float partialTicks);

    float getFov();
}
