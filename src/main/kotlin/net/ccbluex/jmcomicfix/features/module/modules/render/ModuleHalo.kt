package net.ccbluex.jmcomicfix.features.module.modules.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.RenderBufferBuilder
import net.ccbluex.liquidbounce.render.VertexInputType
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.RotationAxis
import net.minecraft.client.render.VertexFormat
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.UV2f
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.client.option.Perspective
import kotlin.math.sin


object ModuleHalo : ClientModule("Halo", Category.RENDER) {

    private val character by enumChoice("Character", HaloImage.ALICE)

    private val size by float("Scale", 0.5f, 0.5f..1f)
    private val onlySelf by boolean("OnlySelf", true)
    private val trackCamera by boolean("TrackCamera", true)
    private val firstPerson by boolean("InTheFirstPerson", false)
    private object ImageOffset : ToggleableConfigurable(this, "Offset", true) {
        val offsetX by float("OffsetX", 0f, -1f..1f)
        val offsetY by float("OffsetY", 0.4f, -1f..1f)
        val offsetZ by float("OffsetZ", 0f, -1f..1f)
    }

    private object DynamicOffset : ToggleableConfigurable(this, "DynamicFloat", true) {
        val Range by float("FloatRange", 0.05f, 0.01f..0.2f)
        val Speed by float("FloatSpeed", 0.1f, 0.1f..2f)
    }

    private object ImageFlip : ToggleableConfigurable(this, "Flip", true) {
        val offsetX by float("OffsetX", 0f, -180f..180f)
        val offsetY by float("OffsetY", 5f, -180f..180f)
        val offsetZ by float("OffsetZ", 0f, -180f..180f)
    }


    init {
        tree(ImageFlip)
        tree(DynamicOffset)
        tree(ImageOffset)
    }

    val renderHandler = handler<WorldRenderEvent> { event ->
        val mc = MinecraftClient.getInstance()


        val world = mc.world ?: return@handler
        var players = if (!onlySelf) world.players else listOfNotNull(mc.player)

        val time = world.time.toDouble() + event.partialTicks.toDouble()
        if (!firstPerson && mc.player != null) {
            if (mc.options.perspective == Perspective.FIRST_PERSON && players.contains(mc.player)) {
                players -= mc.player
            }
        }
        val freq = DynamicOffset.Speed.toDouble()
        val amp = DynamicOffset.Range.toDouble()
        val floatY = sin(time * freq) * amp

        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SrcFactor.SRC_ALPHA,
            GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.setShader(VertexInputType.PosTexColor.shaderProgram)
        RenderSystem.setShaderTexture(0, character.texture)
        renderEnvironmentForWorld(event.matrixStack) {
            players.forEach { player ->
                if (player.isSpectator || !player.isAlive) return@forEach
                val interp = player.interpolateCurrentPosition(event.partialTicks)
                val baseY = player.standingEyeHeight + ImageOffset.offsetY.toDouble()
                val haloPos = interp.add(
                    ImageOffset.offsetX.toDouble(),
                    baseY + floatY,
                    ImageOffset.offsetZ.toDouble()
                )

                withPositionRelativeToCamera(haloPos) {
                    val ms = event.matrixStack
                    ms.push()

                    ms.scale(size, size, size)
                    ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f))
                    if (trackCamera) {
                        val yaw = mc.gameRenderer.camera.yaw
                        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-yaw))
                    }
                    if (ImageFlip.enabled) {
                        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ImageFlip.offsetX))
                        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ImageFlip.offsetY))
                        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ImageFlip.offsetZ))
                    }

                    val builder = RenderBufferBuilder(
                        VertexFormat.DrawMode.QUADS,
                        VertexInputType.PosTexColor,
                        RenderBufferBuilder.TESSELATOR_A
                    )
                    val half = 0.5
                    builder.drawQuad(
                        this,
                        pos1 = Vec3d(-half, -half, 0.0),
                        uv1 = UV2f(0f, 1f),
                        pos2 = Vec3d(half, half, 0.0),
                        uv2 = UV2f(1f, 0f),
                        color = Color4b(255, 255, 255, 255)
                    )
                    builder.draw()
                    ms.pop()
                }
            }
        }

        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
    }

    @Suppress("UNUSED")
    private enum class HaloImage(
        override val choiceName: String,
        private val textureName: String
    ) : NamedChoice {
        ALICE("Alice", "alice"),
        SHIROKO("Shiroko", "shiroko"),
        REISA("Reisa", "reisa"),
        HOSHINO("Hoshino", "hoshino"),
        AZUSA("Azusa", "azusa"),
        IORI("Iori", "iori"),
        IZUNA("Izuna", "izuna"),
        KAYOKO("Kayoko", "kayoko");

        val texture: Identifier by lazy {
            "image/halo/$textureName.png".registerAsDynamicImageFromClientResources()
        }
    }
}
