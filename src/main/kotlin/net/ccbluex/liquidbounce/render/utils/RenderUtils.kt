@file:Suppress("LongParameterList")
package net.ccbluex.liquidbounce.render.utils

import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.utils.client.fastCos
import net.ccbluex.liquidbounce.utils.client.fastSin
import net.ccbluex.liquidbounce.utils.client.toRadians
import net.minecraft.client.render.VertexConsumer
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f

object RenderUtils {

    data class CameraBillboardVec3d(
        val pos: Vec3d,
        val forward: Vec3d,
        val up: Vec3d,
        val right: Vec3d,
        val time: Double
    )

    data class CameraBillboardVector3d(
        val pos: Vec3d,
        val forward: Vector3d,
        val up: Vector3d,
        val right: Vector3d,
        val time: Double
    )

    data class CameraBillboardVector3f(
        val pos: Vec3d,
        val forward: Vector3f,
        val up: Vector3f,
        val right: Vector3f,
        val time: Double
    )

    inline fun <V> WorldRenderEnvironment.buildCameraBillboard(
        vecFactory: (Number, Number, Number) -> V,
        normalize: (V) -> V,
        cross: (V, V) -> V,
        zeroCheck: (V) -> Boolean,
        fallback: () -> V
    ): Triple<Vec3d, Triple<V, V, V>, Double> {
        val cameraPos = camera.pos
        val clientTime = System.nanoTime() / 1_000_000_000.0

        val yawRad = camera.yaw.toRadians()
        val pitchRad = camera.pitch.toRadians()
        val fx = (-(yawRad).fastSin() * (pitchRad).fastCos())
        val fy = (-(pitchRad).fastSin())
        val fz = ((yawRad).fastCos() * (pitchRad).fastCos())
        val forward = normalize(vecFactory(fx.toDouble(), fy.toDouble(), fz.toDouble()))

        val upVec = vecFactory(0.0, 1.0, 0.0)
        var right = normalize(cross(forward, upVec))
        if (zeroCheck(right)) right = fallback()
        val up = normalize(cross(right, forward))

        return Triple(cameraPos, Triple(forward, up, right), clientTime)
    }

    fun WorldRenderEnvironment.getCameraBillboard(): CameraBillboardVec3d {
        val (pos, dirs, time) = buildCameraBillboard(
            vecFactory = { x, y, z -> Vec3d(x.toDouble(), y.toDouble(), z.toDouble()) },
            normalize = { it.normalize() },
            cross = { a, b -> a.crossProduct(b) },
            zeroCheck = { it.lengthSquared() == 0.0 },
            fallback = { Vec3d(1.0, 0.0, 0.0) }
        )
        val (forward, up, right) = dirs
        return CameraBillboardVec3d(pos, forward, up, right, time)
    }

    fun WorldRenderEnvironment.getVector3dCameraBillboard(): CameraBillboardVector3d {
        val (pos, dirs, time) = buildCameraBillboard(
            vecFactory = { x, y, z -> Vector3d(x.toDouble(), y.toDouble(), z.toDouble()) },
            normalize = { v -> if (v.lengthSquared() < 1e-10) v else v.normalize(v) },
            cross = { a, b ->
                val res = Vector3d()
                a.cross(b, res)
                if (res.lengthSquared() < 1e-10) Vector3d(1.0, 0.0, 0.0) else res
            },
            zeroCheck = { it.lengthSquared() < 1e-10 },
            fallback = { Vector3d(1.0, 0.0, 0.0) }
        )

        val (forward, up, right) = dirs
        return CameraBillboardVector3d(pos, forward, up, right, time)
    }

    fun WorldRenderEnvironment.getVector3fCameraBillboard(): CameraBillboardVector3f {
        val (pos, dirs, time) = buildCameraBillboard(
            vecFactory = { x, y, z -> Vector3f(x.toFloat(), y.toFloat(), z.toFloat()) },
            normalize = { it.normalize(it) },
            cross = { a, b ->
                val res = Vector3f()
                a.cross(b, res)
                if (res.lengthSquared() < 1e-6) Vector3f(1.0f, 0.0f, 0.0f) else res
            },
            zeroCheck = { it.lengthSquared() == 0f },
            fallback = { Vector3f(1f, 0f, 0f) }
        )
        val (forward, up, right) = dirs
        return CameraBillboardVector3f(pos, forward, up, right, time)
    }

    fun emitQuadInline(
        argb: Int,
        cx: Float, cy: Float, cz: Float,
        matrix: Matrix4f,
        halfRx: Float, halfRy: Float, halfRz: Float,
        halfUx: Float, halfUy: Float, halfUz: Float,
        vertexConsumer: VertexConsumer
    ) {
        val v0x = cx - halfRx - halfUx
        val v0y = cy - halfRy - halfUy
        val v0z = cz - halfRz - halfUz
        val v1x = cx + halfRx - halfUx
        val v1y = cy + halfRy - halfUy
        val v1z = cz + halfRz - halfUz
        val v2x = cx + halfRx + halfUx
        val v2y = cy + halfRy + halfUy
        val v2z = cz + halfRz + halfUz
        val v3x = cx - halfRx + halfUx
        val v3y = cy - halfRy + halfUy
        val v3z = cz - halfRz + halfUz

        vertexConsumer.vertex(matrix, v0x, v0y, v0z).texture(0f, 1f).color(argb)
        vertexConsumer.vertex(matrix, v1x, v1y, v1z).texture(1f, 1f).color(argb)
        vertexConsumer.vertex(matrix, v2x, v2y, v2z).texture(1f, 0f).color(argb)
        vertexConsumer.vertex(matrix, v3x, v3y, v3z).texture(0f, 0f).color(argb)
    }
}
