package meow.meow.client.mixin;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import meow.meow.client.security.TokenProtector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MinecraftClient.class, remap = false)
public class MinecraftClientMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, index = 1)
	private static String antiSsid$decryptProtectedAccessToken(String accessToken) {
		return TokenProtector.decryptIfProtected(accessToken);
	}
}
