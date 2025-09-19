package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.minecraft.client.gl.ShaderProgramKeys
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.math.Vec3d
import kotlin.math.sin

private val glowTexture = "particles/glow.png".registerAsDynamicImageFromClientResources()

/**
 * Draws a sparkling trail of quads with customizable parameters.
 *
 * @param positions List of positions to render the trail.
 * @param env The rendering environment.
 * @param size Base size of the quads.
 * @param currentTime Current world time for animations.
 * @param alive Duration for which sparkles remain visible (in ticks).
 * @param gradientSpeed Speed of the color gradient animation.
 * @param darkImprint Whether to use additive blending for dark backgrounds.
 * @param fade Enable fade animation.
 * @param shrink Enable shrink animation.
 * @param fadeDuration Duration of fade animation (in ticks).
 * @param shrinkDuration Duration of shrink animation (in ticks).
 * @param distanceLimit Optional distance range for limiting rendering (min, max).
 */

@Suppress("CognitiveComplexMethod","LongMethod","LongParameterList")
fun drawSparkleTrail(
    positions: Collection<Pair<Vec3d, Long>>,
    env: WorldRenderEnvironment,
    size: Float,
    currentTime: Long,
    alive: Int,
    gradientSpeed: Int,
    primaryColor: Color4b,
    secondaryColor: Color4b,
    darkImprint: Boolean = true,
    fade: Boolean = false,
    shrink: Boolean = false,
    fadeDuration: Float = 5f,
    shrinkDuration: Float = 5f,
    distanceLimit: Pair<Float, Float>? = null,
    cameraPos: Vec3d,
    right: Vec3d,
    up: Vec3d,
    time: Double
) {
    if (positions.isEmpty()) return

    RenderSystem.depthMask(false)
    RenderSystem.disableCull()
    RenderSystem.disableDepthTest()
    RenderSystem.enableBlend()
    RenderSystem.blendFuncSeparate(
        GlStateManager.SrcFactor.SRC_ALPHA,
        if (darkImprint) GlStateManager.DstFactor.ONE else GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SrcFactor.ZERO,
        GlStateManager.DstFactor.ONE
    )

    RenderSystem.setShaderTexture(0, glowTexture)

    val maxDistSq1 = distanceLimit?.second?.let { it * it } ?: Float.MAX_VALUE
    val maxDistSq2 = distanceLimit?.first?.let { it * it } ?: Float.MAX_VALUE

    val totalSize = positions.size
    if (totalSize == 0) {
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        return
    }

    env.drawCustomMesh(
        VertexFormat.DrawMode.QUADS,
        VertexFormats.POSITION_TEXTURE_COLOR,
        ShaderProgramKeys.POSITION_TEX_COLOR
    ) { mat ->
        var index = 0
        val it = positions.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val pos = entry.first
            val creationTime = entry.second

            val cx = pos.x - cameraPos.x
            val cy = pos.y - cameraPos.y
            val cz = pos.z - cameraPos.z
            val distSq = pos.squaredDistanceTo(cameraPos)
            if (distanceLimit != null) {
                if (index % 10 != 0 && distSq > maxDistSq1) {
                    index++; continue
                }
                if (index % 3 == 0 && distSq > maxDistSq2) {
                    index++; continue
                }
            }

            val life = (currentTime - creationTime).toFloat()
            val totalLife = alive.toFloat()
            val progress = if (fade) 1f - (life / totalLife).coerceIn(0f, 1f) else 1f
            val fadeFactor = if (fade && life >= totalLife - fadeDuration) {
                1f - ((life - (totalLife - fadeDuration)) / fadeDuration).coerceIn(0f, 1f)
            } else {
                1f
            }
            val shrinkFactor = if (shrink && life >= totalLife - shrinkDuration) {
                1f - ((life - (totalLife - shrinkDuration)) / shrinkDuration).coerceIn(0f, 1f)
            } else {
                1f
            }

            val quadSize = (size * shrinkFactor) * (0.5f + 0.5f * progress)
            val half = quadSize * 0.5f
            val invTotal = 1f / totalSize.toFloat()
            val basePhase = (time * gradientSpeed)

            val t = (sin(basePhase + -(index * invTotal) * 13.0) + 1.0) * 0.5
            val color = primaryColor.blend(secondaryColor, t.toFloat())
            val alpha = (color.a * progress * fadeFactor).toInt()
            val renderColor = color.withAlpha(alpha)
            val argb = renderColor.toARGB()

            val rx = (right.x * half).toFloat()
            val ry = (right.y * half).toFloat()
            val rz = (right.z * half).toFloat()
            val ux2 = (up.x * half).toFloat()
            val uy2 = (up.y * half).toFloat()
            val uz2 = (up.z * half).toFloat()

            val v0x = (cx - rx - ux2).toFloat()
            val v0y = (cy - ry - uy2).toFloat()
            val v0z = (cz - rz - uz2).toFloat()
            val v1x = (cx + rx - ux2).toFloat()
            val v1y = (cy + ry - uy2).toFloat()
            val v1z = (cz + rz - uz2).toFloat()
            val v2x = (cx + rx + ux2).toFloat()
            val v2y = (cy + ry + uy2).toFloat()
            val v2z = (cz + rz + uz2).toFloat()
            val v3x = (cx - rx + ux2).toFloat()
            val v3y = (cy - ry + uy2).toFloat()
            val v3z = (cz - rz + uz2).toFloat()

            vertex(mat, v0x, v0y, v0z).texture(0f, 1f).color(argb)
            vertex(mat, v1x, v1y, v1z).texture(1f, 1f).color(argb)
            vertex(mat, v2x, v2y, v2z).texture(1f, 0f).color(argb)
            vertex(mat, v3x, v3y, v3z).texture(0f, 0f).color(argb)

            index++
        }
    }

    RenderSystem.enableDepthTest()
    RenderSystem.depthMask(true)
}

