package meow.meow.client.mixin;

import meow.meow.client.security.TokenProtector;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(User.class)
public class UserMixin {
	@Shadow
	@Final
	private String accessToken;

	@Inject(method = "getAccessToken", at = @At("HEAD"), cancellable = true)
	private void antiSsid$protectAccessToken(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(TokenProtector.protectAccessToken(this.accessToken));
	}
}
