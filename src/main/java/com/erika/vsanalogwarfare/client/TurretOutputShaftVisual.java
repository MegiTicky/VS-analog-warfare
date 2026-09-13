package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.mouseaim.MouseAimBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

/**
 * Renders the Mouse Aim Controller's internal shaft spinning at the turret
 * rotation output's commanded RPM instead of the input network's speed, so
 * the shaft goes still whenever the block is not driving a turret.
 */
public class TurretOutputShaftVisual extends SingleAxisRotatingVisual<MouseAimBlockEntity> {
    public TurretOutputShaftVisual(VisualizationContext context, MouseAimBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Models.partial(AllPartialModels.SHAFT));
    }

    @Override
    public void update(float partialTick) {
        rotatingModel.setup(blockEntity, blockEntity.getTurretOutputRpm())
                .setChanged();
    }
}
