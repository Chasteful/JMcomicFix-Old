package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.RenderUtils
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.ccbluex.liquidbounce.utils.client.toDegrees
import net.ccbluex.liquidbounce.utils.entity.RenderedEntities
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.gl.ShaderProgramKeys
import net.minecraft.client.render.VertexFormat
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.joml.Quaternionf
import java.awt.Color
import kotlin.math.atan2

object ModulePointerESP : ClientModule("PointerESP", Category.RENDER) {

    private val modes = choices("ColorMode", 0) {
        arrayOf(
            DistanceColor,
            GenericEntityHealthColorMode(it),
            GenericStaticColorMode(it, Color4b.WHITE)
        )
    }

    private object DistanceColor : GenericColorMode<LivingEntity>("Distance") {
        override val parent: ChoiceConfigurable<*>
            get() = modes

        private val gradientRange by floatRange("GradientRange", 8f..48f, 0f..256f)
        private val saturation by float("Saturation", 1f, 0f..1f)
        private val brightness by float("Brightness", 1f, 0f..1f)

        override fun getColors(param: LivingEntity): Pair<Color4b, Color4b> {
            val length = gradientRange.endInclusive - gradientRange.start
            val hue = (param.distanceTo(player).coerceIn(gradientRange) - gradientRange.start) / length / 3f
            val color = Color4b(Color.getHSBColor(hue, saturation, brightness))
            return color to color
        }

        override fun getColor(param: LivingEntity): Color4b {
            return getColors(param).first
        }
    }

    private val renderRadius by int("Radius", 200, 50..500)
    private val pointerSize by float("Size", 1f, 0.5f..1.5f)
    private val pointerAlpha by float("Alpha", 0.75f, 0f..1f)
    private val pitchLimit by floatRange("PitchLimit", 30f..90f, 0f..90f).onChanged {
            negativePitchLimit = -it.endInclusive..-it.start
    }
    private val smoothingFactor by float("SmoothingFactor", 0.25f, 0.1f..0.5f)
    private val renderOffset by vec3d("RenderOffset", Vec3d.ZERO)


    private var negativePitchLimit: ClosedFloatingPointRange<Float> = -pitchLimit.endInclusive..-pitchLimit.start
    private val pointerData = mutableMapOf<LivingEntity, Pair<Float, Float>>()

    private val pointerTexture: Identifier = "image/hud/triangle.png".registerAsDynamicImageFromClientResources()
    val pointerShader: ShaderProgram by lazy {
        mc.shaderLoader.getOrCreateProgram(ShaderProgramKeys.POSITION_TEX_COLOR)!!
    }

    private data class Pointer(
        val radius: Int,
        val color: Color4b,
        val rotateX: Float,
        val rotateZ: Float
    )

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val matrices = event.context.matrices
        val pointers = RenderedEntities
            .filter { it != player }
            .mapToArray {
            val diff = it.interpolateCurrentPosition(event.tickDelta) - player.pos
            val rawAngle = atan2(diff.z, diff.x).toFloat().toDegrees()
            val targetAngle = (player.yaw - 90f - rawAngle).let { theta ->
                if (mc.options.perspective.isFrontView) -theta else theta
            }

            val targetRotateX = player.pitch.let { p ->
                when {
                    p < 0 -> 90f + p.coerceIn(negativePitchLimit)
                    else -> 90f + p.coerceIn(pitchLimit)
                }
            }

            val (prevRotateX, prevRotateZ) = pointerData.getOrPut(it) {
                Pair(targetRotateX, targetAngle)
            }

            val smoothedRotateX = MathHelper.lerp(smoothingFactor, prevRotateX, targetRotateX)
            val smoothedRotateZ = MathHelper.lerp(smoothingFactor,prevRotateZ, targetAngle)

            pointerData[it] = Pair(smoothedRotateX, smoothedRotateZ)

            Pointer(
                renderRadius,
                modes.activeChoice.getColor(it),
                smoothedRotateX,
                smoothedRotateZ
            )
        }

        renderEnvironmentForGUI(matrices) {
            val window = mc.window
            val cx = window.scaledWidth / 2f
            val cy = window.scaledHeight / 2f

            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableCull()
            RenderSystem.depthMask(false)
            RenderSystem.setShader(pointerShader)
            RenderSystem.setShaderTexture(0, pointerTexture)

            pointers.forEach { p ->
                matrices.push()
                val scale = (window.scaledWidth.coerceAtMost(window.scaledHeight)) / 1000f

                val adaptiveRadius = renderRadius * scale
                val adaptiveSize = pointerSize * scale * 10

                matrices.translate(
                    (cx + renderOffset.x),
                    (cy + renderOffset.y),
                    renderOffset.z * 100f
                )

                matrices.multiply(Quaternionf().rotateX(Math.toRadians(p.rotateX.toDouble()).toFloat()))
                matrices.multiply(Quaternionf().rotateZ(Math.toRadians(p.rotateZ.toDouble()).toFloat()))
                matrices.translate(0.0, -adaptiveRadius.toDouble(), 0.0)

                matrices.scale(adaptiveSize, adaptiveSize, 1f)
                matrices.translate(-0.5, -0.5, 0.0)

                val color = p.color.with(a = (pointerAlpha * 255f).toInt().coerceIn(0, 255))
                drawCustomMesh(
                    VertexFormat.DrawMode.QUADS,
                    VertexInputType.PosTexColor,
                ) { mat ->
                    val halfSize = adaptiveSize / 2f
                    RenderUtils.emitQuadInline(
                        color.toARGB(),
                        0f, 0f, 0f,
                        mat,
                        halfSize, 0f, 0f,
                        0f, -halfSize, 0f,
                        this
                    )
                }

                matrices.pop()
            }

            RenderSystem.disableBlend()
            RenderSystem.enableCull()
            RenderSystem.depthMask(true)
        }


        pointerData.keys.retainAll(RenderedEntities.toSet())
    }


    override fun onDisabled() {
        pointerData.clear()
        RenderedEntities.unsubscribe(this)
        super.onDisabled()
    }

    override fun onEnabled() {
        pointerData.clear()
        RenderedEntities.subscribe(this)
        super.onEnabled()
    }
}
