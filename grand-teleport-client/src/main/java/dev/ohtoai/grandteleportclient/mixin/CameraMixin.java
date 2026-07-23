package dev.ohtoai.grandteleportclient.mixin;

import dev.ohtoai.grandteleportclient.CameraTransitionController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("GrandTeleport");

    @Unique
    private static int frameLogCounter;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void onCameraSetup(BlockGetter level, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickProgress, CallbackInfo ci) {
        CameraTransitionController.CameraFrame frame = CameraTransitionController.getCameraFrame(tickProgress);
        if (frame != null) {
            frameLogCounter++;
            if (frameLogCounter <= 5 || frameLogCounter % 20 == 0) {
                LOGGER.info("[MIXIN] Camera.setup TAIL — frame #{} pos=({:.1f},{:.1f},{:.1f}) yaw={:.1f} pitch={:.1f}",
                        frameLogCounter, frame.pos().x, frame.pos().y, frame.pos().z,
                        frame.yaw(), frame.pitch());
            }
            this.setPosition(frame.pos());
            this.setRotation(frame.yaw(), frame.pitch());
        }
    }
}
