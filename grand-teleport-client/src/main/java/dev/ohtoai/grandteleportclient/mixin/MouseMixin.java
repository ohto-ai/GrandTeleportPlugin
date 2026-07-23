package dev.ohtoai.grandteleportclient.mixin;

import dev.ohtoai.grandteleportclient.CameraTransitionController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks mouse look and click input during a camera transition.
 * The camera is controlled by the transition animation, not the player.
 */
@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        if (CameraTransitionController.shouldBlockPlayerInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != 0 && CameraTransitionController.shouldBlockPlayerInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CameraTransitionController.shouldBlockPlayerInput()) {
            ci.cancel();
        }
    }
}
