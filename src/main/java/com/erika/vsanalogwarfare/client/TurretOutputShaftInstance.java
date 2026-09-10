package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.mouseaim.MouseAimBlockEntity;
import com.jozufozu.flywheel.api.MaterialManager;
import com.simibubi.create.content.kinetics.base.ShaftInstance;

/**
 * Renders the Mouse Aim Controller's internal shaft spinning at the turret
 * rotation output's commanded RPM instead of the input network's speed, so
 * the shaft goes still whenever the block is not driving a turret.
 */
public class TurretOutputShaftInstance extends ShaftInstance<MouseAimBlockEntity> {
    public TurretOutputShaftInstance(MaterialManager materialManager, MouseAimBlockEntity blockEntity) {
        super(materialManager, blockEntity);
    }

    @Override
    protected float getBlockEntitySpeed() {
        return this.blockEntity.getTurretOutputRpm();
    }
}
