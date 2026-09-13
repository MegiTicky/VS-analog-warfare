package com.erika.vsanalogwarfare.ponder;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModItems;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class VehicleSetupPonder implements PonderPlugin {

    @Override
    public String getModId() {
        return VSAnalogWarfare.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation component = ForgeRegistries.ITEMS.getKey(ModItems.VEHICLE_SETUP.get());
        if (component == null) {
            return;
        }
        helper.addStoryBoard(component, "vehicle_setup/record", VehicleSetupPonder::record);
        helper.addStoryBoard(component, "vehicle_setup/run", VehicleSetupPonder::run);
        helper.addStoryBoard(component, "vehicle_setup/removal_markers", VehicleSetupPonder::removalMarkers);
    }

    /** Registers the plugin with Ponder; call once during client setup. */
    public static void register() {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new VehicleSetupPonder());
    }

    private static void showVehicle(SceneBuilder scene, SceneBuildingUtil util) {
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 0, 0, 6, 0, 6), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 5, 2, 5), Direction.UP);
        scene.idle(10);
    }

    private static void record(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vehicle_setup_record", "Recording a Vehicle");
        showVehicle(scene, util);

        scene.overlay().showText(100)
                .text("This is the Setup Block. It only records and runs when placed on a ship.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(105);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()));
        scene.idle(65);

        scene.overlay().showText(90)
                .text("Right-click the Setup Block with the Analog Screwdriver to start recording.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(95);

        scene.world().setBlock(util.grid().at(4, 2, 2),
                AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), false);
        scene.idle(10);

        scene.overlay().showText(90)
                .text("Any block you place from now on is recorded, such as this Shaft.")
                .pointAt(util.vector().topOf(util.grid().at(4, 2, 2)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showText(90)
                .text("Turrets and crew are also recorded automatically.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()));
        scene.idle(65);

        scene.overlay().showText(90)
                .text("Right-click the Setup Block again to stop recording.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .whileSneaking()
                .rightClick();
        scene.idle(65);

        scene.overlay().showText(100)
                .text("Sneak and right-click the Setup Block with an empty hand to open the editor, where you can tweak the recording.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(105);

        scene.markAsFinished();
    }

    private static void run(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vehicle_setup_run", "Running a Vehicle");
        showVehicle(scene, util);

        scene.overlay().showText(100)
                .text("On a ship, right-click the Setup Block with an empty hand to run its recording.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(105);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .rightClick();
        scene.idle(65);

        scene.world().setBlock(util.grid().at(4, 2, 2),
                AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), false);
        scene.idle(10);

        scene.overlay().showText(90)
                .text("The recorded block is placed automatically.")
                .pointAt(util.vector().topOf(util.grid().at(4, 2, 2)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showText(90)
                .text("Re-running the setup again places any remaining recorded blocks.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(95);

        scene.markAsFinished();
    }

    private static void removalMarkers(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vehicle_setup_removal_markers", "Marking for Removal");
        showVehicle(scene, util);

        scene.overlay().showText(100)
                .text("Blocks that were recorded can also be marked for removal. Sneak and scroll to switch the Screwdriver to removal mode.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(105);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .whileSneaking()
                .scroll();
        scene.idle(65);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()));
        scene.idle(65);

        scene.overlay().showText(90)
                .text("Right-click the Setup Block to begin marking.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(new Vec3(2.5, 3.25, 4.5), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()));
        scene.idle(65);

        scene.overlay().showText(90)
                .text("Right-click a recorded block to mark it for removal, such as this Black Wool.")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 4)))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(new Vec3(3.5, 3.25, 3.5), Pointing.DOWN, 60)
                .rightClick()
                .withItem(new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()));
        scene.idle(65);

        scene.overlay().showText(100)
                .text("Right-click the Setup Block again to stop marking. Marked blocks are removed the next time the setup runs.")
                .pointAt(util.vector().topOf(util.grid().at(3, 2, 3)))
                .placeNearTarget();
        scene.idle(105);

        scene.markAsFinished();
    }
}
