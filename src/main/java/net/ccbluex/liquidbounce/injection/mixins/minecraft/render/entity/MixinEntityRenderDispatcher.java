package net.ccbluex.liquidbounce.injection.mixins.minecraft.render.entity;


import net.ccbluex.liquidbounce.render.context.GlobalEntityContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {


    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;" +
            "Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At("HEAD"))
    private void captureEntityContext(Entity entity, double x, double y, double z,
                                      float tickDelta, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers,
                                      int light, EntityRenderer<?, ?> renderer,
                                      CallbackInfo ci) {
        GlobalEntityContext.currentEntity = entity;
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;" +
            "Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At("RETURN"))
    private void clearEntityContext(Entity entity, double x, double y, double z,
                                    float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int light, EntityRenderer<?, ?> renderer,
                                    CallbackInfo ci) {
        GlobalEntityContext.currentEntity = null;
    }
}
