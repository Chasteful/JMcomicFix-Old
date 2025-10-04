package net.ccbluex.liquidbounce.injection.mixins.minecraft.render.entity.feature;

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleChams;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleTrueSight;
import net.ccbluex.liquidbounce.interfaces.EntityRenderStateAddition;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.ccbluex.liquidbounce.utils.combat.CombatExtensionsKt;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeFeatureRenderer.class)
public abstract class MixinCapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public MixinCapeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Final
    @Shadow
    private BipedEntityModel<PlayerEntityRenderState> model;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    public void render(
            MatrixStack matrixStack,
            VertexConsumerProvider vertexConsumerProvider,
            int light,
            PlayerEntityRenderState playerEntityRenderState,
            float f,
            float g,
            CallbackInfo ci
    ) {
        if (!playerEntityRenderState.invisible && playerEntityRenderState.capeVisible) {
            SkinTextures skinTextures = playerEntityRenderState.skinTextures;
            if (skinTextures.capeTexture() != null) {
                matrixStack.push();
                VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getEntityTranslucent(skinTextures.capeTexture()));
                this.getContextModel().copyTransforms(this.model);
                this.model.setAngles(playerEntityRenderState);

                int color = 0xFFFFFFFF;
                var entity = ((EntityRenderStateAddition) playerEntityRenderState).liquid_bounce$getEntity();
                if (ModuleTrueSight.canRenderEntities(playerEntityRenderState)) {
                    var trueSight = ModuleTrueSight.INSTANCE;
                    color = (trueSight.getRunning() && trueSight.getEntities())
                            ? trueSight.getEntityFeatureLayerColor().toARGB()
                            : new Color4b(255, 255, 255, 120).toARGB();
                } else if (ModuleChams.INSTANCE.getRunning() && !playerEntityRenderState.invisible && CombatExtensionsKt.shouldBeShown(entity)) {

                    color = ModuleChams.INSTANCE.getColor((LivingEntity) entity).toARGB();
                }

                this.model.render(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV, color);
                matrixStack.pop();
                ci.cancel();
            }
        }
    }
}
