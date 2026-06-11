package meow.meow.client.mixin;

import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import meow.meow.client.security.TokenProtector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public class YggdrasilMinecraftSessionServiceMixin {
	@ModifyVariable(method = "joinServer", at = @At("HEAD"), argsOnly = true, index = 2)
	private static String antiSsid$decryptProtectedAccessToken(String accessToken) {
		return TokenProtector.decryptIfProtected(accessToken);
	}
}
