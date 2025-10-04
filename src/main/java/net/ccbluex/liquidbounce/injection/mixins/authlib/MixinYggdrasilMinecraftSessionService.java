package net.ccbluex.liquidbounce.injection.mixins.authlib;

import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.TextureUrlChecker;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleYggdrasilSignatureFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(YggdrasilMinecraftSessionService.class)
public class MixinYggdrasilMinecraftSessionService {
    @Inject(
            method = "getPropertySignatureState",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void bypassSignature(Property property, CallbackInfoReturnable<SignatureState> cir) {
        if (ModuleYggdrasilSignatureFix.INSTANCE.getRunning()) {
            cir.setReturnValue(SignatureState.SIGNED);
        }
    }

    @Redirect(
            method = "unpackTextures",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/yggdrasil/TextureUrlChecker;isAllowedTextureDomain(Ljava/lang/String;)Z"
            ),
            remap = false
    )
    private boolean bypassUrlCheck(String url) {
        if (ModuleYggdrasilSignatureFix.INSTANCE.getRunning()) {
            return true;
        }
        return TextureUrlChecker.isAllowedTextureDomain(url);
    }


    @Inject(method = "unpackTextures", at = @At("RETURN"), cancellable = true, remap = false)
    private void onUnpackTexturesReturn(Property packedTextures, CallbackInfoReturnable<MinecraftProfileTextures> cir) {
        if (!ModuleYggdrasilSignatureFix.INSTANCE.getRunning() || !ModuleYggdrasilSignatureFix.INSTANCE.getForceSlimModel())
            return;

        MinecraftProfileTextures original = cir.getReturnValue();
        if (original == null || original == MinecraftProfileTextures.EMPTY || original.skin() == null) return;

        String url = original.skin().getUrl();
        if (!url.contains("127.0.0.1")) return;

        MinecraftProfileTextures forced = new MinecraftProfileTextures(
                new MinecraftProfileTexture(url, Map.of("model", "slim")),
                original.cape(),
                original.elytra(),
                original.signatureState()
        );

        cir.setReturnValue(forced);
    }


}
