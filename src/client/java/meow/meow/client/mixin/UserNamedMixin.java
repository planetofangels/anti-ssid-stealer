package meow.meow.client.mixin;

import meow.meow.client.security.TokenProtector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.User", remap = false)
public class UserNamedMixin {
	@Shadow
	@Final
	private String accessToken;

	@Inject(method = "getAccessToken", at = @At("HEAD"), cancellable = true, remap = false)
	private void antiSsid$protectAccessToken(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(TokenProtector.protectAccessToken(this.accessToken));
	}
}
