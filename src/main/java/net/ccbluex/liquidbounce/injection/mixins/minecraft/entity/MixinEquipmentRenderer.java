package net.ccbluex.liquidbounce.injection.mixins.minecraft.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleChams;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleTrueSight;
import net.ccbluex.liquidbounce.render.context.ArmorRenderContext;
import net.ccbluex.liquidbounce.render.context.GlobalArmorContext;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.ccbluex.liquidbounce.utils.combat.CombatExtensionsKt;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EquipmentRenderer.class)
public class MixinEquipmentRenderer {

    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;" +
                    "Lnet/minecraft/registry/RegistryKey;" +
                    "Lnet/minecraft/client/model/Model;" +
                    "Lnet/minecraft/item/ItemStack;" +
                    "Lnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/VertexConsumerProvider;" +
                    "ILnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
            )
    )
    private RenderLayer modifyRenderLayer(Identifier texture, Operation<RenderLayer> original) {
        ArmorRenderContext ctx = GlobalArmorContext.currentArmorContext;
        if (ctx != null) {
            LivingEntity entity = ctx.getEntity();
            if (ModuleChams.INSTANCE.getRunning() && !entity.isInvisible() && CombatExtensionsKt.shouldBeShown(entity)) {
                return RenderLayer.getItemEntityTranslucentCull(texture);
            }
            return RenderLayer.getItemEntityTranslucentCull(texture);
        }
        return original.call(texture);
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;" +
                    "Lnet/minecraft/registry/RegistryKey;" +
                    "Lnet/minecraft/client/model/Model;" +
                    "Lnet/minecraft/item/ItemStack;" +
                    "Lnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/VertexConsumerProvider;" +
                    "ILnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
                    ordinal = 0
            ),
            index = 4
    )
    private int modifyAlpha(int color) {
        ArmorRenderContext ctx = GlobalArmorContext.currentArmorContext;
        if (ctx != null) {
            LivingEntity entity = ctx.getEntity();
            if (ModuleChams.INSTANCE.getRunning() && !entity.isInvisible() && CombatExtensionsKt.shouldBeShown(entity)) {
                Color4b c = ModuleChams.INSTANCE.getColor(entity);
                return ColorHelper.withAlpha((int)(ctx.getApplicablePieceTransparency() * 255), c.toARGB());
            }
            Color4b c = ModuleTrueSight.INSTANCE.getEntityColor();
            return ColorHelper.withAlpha((int)(ctx.getApplicablePieceTransparency() * 255), c.toARGB());
        }
        return color;
    }
}
