package net.ccbluex.liquidbounce.render

import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin

data class CameraBillboard(
    val pos: Vec3d,
    val forward: Vec3d,
    val up: Vec3d,
    val right: Vec3d,
    val time: Double
)

fun getCameraBillboard(env: WorldRenderEnvironment): CameraBillboard {
    val camera = env.camera
    val cameraPos = camera.pos
    val clientTime = System.nanoTime() / 1_000_000_000.0

    val yawRad = Math.toRadians(camera.yaw.toDouble())
    val pitchRad = Math.toRadians(camera.pitch.toDouble())
    val forward = Vec3d(
        -sin(yawRad) * cos(pitchRad),
        -sin(pitchRad),
        cos(yawRad) * cos(pitchRad)
    ).normalize()

    val upVec = Vec3d(0.0, 1.0, 0.0)
    var right = forward.crossProduct(upVec).normalize()
    if (right.lengthSquared() == 0.0) right = Vec3d(1.0, 0.0, 0.0)
    val up = right.crossProduct(forward).normalize()

    return CameraBillboard(cameraPos, forward, up, right, clientTime)
}
