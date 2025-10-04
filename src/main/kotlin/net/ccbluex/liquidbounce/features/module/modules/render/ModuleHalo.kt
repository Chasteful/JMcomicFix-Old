package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.UV2f
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.client.option.Perspective
import net.minecraft.client.render.VertexFormat
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin

object ModuleHalo : ClientModule("Halo", Category.RENDER) {
    private val character by enumChoice("Character", HaloImage.ALICE)

    private val onlySelf by boolean("OnlySelf", true)
    private val size by float("Scale", 0.8f, 0.75f..1f)
    private val offsetX by float("OffsetX", 90f, -180f..180f)
    private val offsetY by float("OffsetY", 5f, -180f..180f)

    private object DynamicOffset : ToggleableConfigurable(this, "DynamicFloat", true) {
        val range by float("FloatRange", 0.05f, 0.01f..0.1f)
        val speed by float("FloatSpeed", 0.1f, 0.1f..2f)
    }

    private object FirstPerson : ToggleableConfigurable(this,"FirstPerson",true){
        val raiseReduceOpacity by boolean("RaiseReduceOpacity",true)
        val minOpacity by int("MinOpacity",30,0..255)
    }

    private object TrackCamera : ToggleableConfigurable(this, "TrackCamera", true) {
        val radius by float("TrackRange", 0.4f, 0.35f..0.5f)
        val positivePitchLimit by floatRange("PositivePitchLimit", 30f..30f, 0f..30f)
        val negativePitchLimit by floatRange("NegativePitchLimit", -30f..-30f, -30f..0f)
    }

    init {
        tree(DynamicOffset)
        tree(FirstPerson)
        tree(TrackCamera)
    }
    private val prevEyeHeights = mutableMapOf<PlayerEntity, Double>()

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val world = mc.world ?: return@handler
        var players = if (!onlySelf) world.players else listOfNotNull(mc.player)

        val time = world.time.toDouble() + event.partialTicks.toDouble()
        if (!FirstPerson.enabled && mc.player != null) {
            if (mc.options.perspective == Perspective.FIRST_PERSON && players.contains(mc.player)) {
                players -= mc.player
            }
        }
        val freq = DynamicOffset.speed.toDouble()
        val amp = DynamicOffset.range.toDouble()
        val floatY = sin(time * freq) * amp

        renderEnvironmentForWorld(event.matrixStack) {
            RenderSystem.disableCull()
            RenderSystem.enableBlend()
            RenderSystem.depthMask(false)
            RenderSystem.blendFunc(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
            )
            RenderSystem.setShaderTexture(0, character.texture)
            if (!ModuleChams.running) {
                RenderSystem.enableDepthTest()
            }

            players.forEach { player ->
                if (player.isSpectator || !player.isAlive) return@forEach
                val interp = player.interpolateCurrentPosition(event.partialTicks)
                val integrated = mc.options.perspective == Perspective.FIRST_PERSON &&
                player == mc.player && !ModuleFreeLook.enabled &&!ModuleFreeCam.enabled
                val baseY = if (integrated) {
                    val camera = mc.gameRenderer.camera
                    val cameraPos = camera.pos
                    cameraPos.y - interp.y
                } else {
                    val eyeHeight = MathHelper.lerp(
                        event.partialTicks.toDouble(),
                        prevEyeHeights.getOrDefault(player, player.getEyeHeight(player.pose).toDouble()) + player.prevY,
                        player.getEyeHeight(player.pose).toDouble() + player.y
                    )
                    eyeHeight - MathHelper.lerp(event.partialTicks.toDouble(), player.prevY, player.y)
                }
                prevEyeHeights[player] = baseY


                // Calculate circular offset based on pitch
                val pitch = if (TrackCamera.enabled) {
                    player.pitch.coerceIn(
                        TrackCamera.negativePitchLimit.start,
                        TrackCamera.positivePitchLimit.endInclusive
                    )
                } else {
                    0f
                }
                val pitchRad = Math.toRadians(pitch.toDouble())
                val yawRad = Math.toRadians(player.yaw.toDouble())

                var alpha = 255
                if (integrated && FirstPerson.raiseReduceOpacity) {
                    if (player.pitch < -30f) {
                        val t = ((player.pitch + 30f) / -60f).coerceIn(0f, 1f)
                        val min = FirstPerson.minOpacity
                        alpha = (255 - (255 - min) * t).toInt()
                    }
                }
                val pitchOffset = Vec3d(
                    -sin(yawRad) * sin(pitchRad),
                    cos(pitchRad),
                    cos(yawRad) * sin(pitchRad)
                ).multiply(TrackCamera.radius.toDouble())

                val haloPos = interp.add(
                    pitchOffset.x, baseY + floatY + pitchOffset.y, pitchOffset.z)

                withPositionRelativeToCamera(haloPos) {
                    val ms = event.matrixStack
                    ms.push()

                    ms.scale(size, size, size)
                    if (TrackCamera.enabled) {
                        val yaw = player.yaw
                        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw)) // Only rotate with yaw
                    }
                    if (enabled) {
                        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(offsetX))
                        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(offsetY))
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
                        color = Color4b(255, 255, 255).withAlpha(alpha)
                    )

                    builder.draw()
                    ms.pop()
                }
            }
        }

        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
    }
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
