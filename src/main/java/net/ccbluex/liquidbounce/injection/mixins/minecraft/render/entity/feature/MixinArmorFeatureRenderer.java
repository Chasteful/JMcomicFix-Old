package net.ccbluex.liquidbounce.injection.mixins.minecraft.render.entity.feature;

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleChams;
import net.ccbluex.liquidbounce.render.context.GlobalArmorContext;
import net.ccbluex.liquidbounce.render.context.ArmorRenderContext;
import net.ccbluex.liquidbounce.render.context.GlobalEntityContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer<T extends BipedEntityRenderState, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> {

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void preRenderArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack,
                                EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {

        if (ModuleChams.INSTANCE.getRunning() && GlobalEntityContext.currentEntity instanceof LivingEntity le) {
            float alpha = ModuleChams.INSTANCE.getAlpha() / 255f;
            GlobalArmorContext.currentArmorContext = new ArmorRenderContext(le, alpha);
        }
    }

    @Inject(method = "renderArmor", at = @At("RETURN"))
    private void postRenderArmor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack,
                                 EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {
        GlobalArmorContext.currentArmorContext = null;
    }

}

