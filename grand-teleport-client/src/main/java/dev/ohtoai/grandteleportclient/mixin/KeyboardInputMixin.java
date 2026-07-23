package dev.ohtoai.grandteleportclient.mixin;

import dev.ohtoai.grandteleportclient.CameraTransitionController;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks keyboard movement input during a camera transition.
 * The player's character shouldn't move while the camera is detached.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(boolean slowDown, float movementMultiplier, CallbackInfo ci) {
        if (!CameraTransitionController.shouldBlockPlayerInput()) {
            return;
        }

        // Zero out all input
        this.leftImpulse = 0.0F;
        this.forwardImpulse = 0.0F;
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.jumping = false;
        this.shiftKeyDown = false;
        ci.cancel();
    }
}
