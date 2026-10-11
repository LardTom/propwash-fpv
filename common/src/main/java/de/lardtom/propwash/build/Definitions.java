package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.resources.Identifier;

final class Definitions {
    private Definitions() {
    }

    static PartDefinition withId(PartDefinition definition, Identifier id) {
        return switch (definition) {
            case FrameDefinition d -> new FrameDefinition(id, d.basics(), d.role(), d.shape(), d.wheelbaseMm(), d.minPropMm(), d.maxPropMm(),
                    d.motorMounts(), d.stackMounts(), d.accessoryMounts(), d.geometry(), d.aero(), d.duct(), d.impactTolerance(), d.batteryFit());
            case StackDefinition d -> new StackDefinition(id, d.basics(), d.mount(), d.escContinuousAmps(), d.minCells(), d.maxCells(),
                    d.allInOne(), d.escBurstAmps(), d.escResistanceOhm(), d.brownoutVoltage(), d.boardSizeMm());
            case MotorDefinition d -> new MotorDefinition(id, d.basics(), d.statorLabel(), d.statorDiameterMm(), d.statorHeightMm(), d.kv(),
                    d.mount(), d.maxCurrentAmps(), d.resistanceOhm(), d.minCells(), d.maxCells(), d.propMount(), d.thermal());
            case PropDefinition d -> new PropDefinition(id, d.basics(), d.diameterMm(), d.pitchInch(), d.blades(), d.thrustCoefficient(),
                    d.powerCoefficient(), d.material(), d.stiffness(), d.mount(), d.reverseThrust());
            case VideoDefinition d -> new VideoDefinition(id, d.basics(), d.link(), d.latencyMs(), d.rangeBlocks(), d.imageQuality(),
                    d.cameraWidthMm(), d.cameraFovDeg(), d.vtxPowerMw(), d.powerW());
            case BatteryDefinition d -> new BatteryDefinition(id, d.basics(), d.cells(), d.capacityMah(), d.cellResistanceOhm(), d.cRating(),
                    d.chemistry(), d.packSize(), d.connector());
            case AccessoryDefinition d -> new AccessoryDefinition(id, d.basics(), d.slot(), d.widthMm(), d.heightMm(), d.depthMm(),
                    d.flightBatteryWatts(), d.frontalAreaM2(), d.ledCount(), d.actionCam(), d.lensXMm(), d.lensLengthMm());
        };
    }
}
