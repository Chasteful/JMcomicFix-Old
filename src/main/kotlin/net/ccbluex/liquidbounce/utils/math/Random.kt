package net.ccbluex.liquidbounce.utils.math

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFireFlies.mc
import org.joml.Vector3f
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

fun getRandomInRange(min: Float, max: Float): Float {
    return (Math.random() * (max - min) + min).toFloat()
}

fun generateRandomVelocity(rand: ThreadLocalRandom): Vector3f {
    val yaw = rand.nextDouble() * Math.PI * 2.0
    val speed = (rand.nextDouble() * 0.15 + 0.1).toFloat()
    val motionX = (-sin(yaw) * speed).toFloat()
    val motionZ = (cos(yaw) * speed).toFloat()
    val motionY = (rand.nextDouble() * 0.2 - 0.1).toFloat()
    return Vector3f(motionX, motionY, motionZ)
}

fun generateSpawnPosition(rand: ThreadLocalRandom): Vector3f {
    val player = mc.player ?: return Vector3f()
    val rangeXZ = 10.0
    val rangeY = 4.0
    val x = player.x + (rand.nextDouble() - 0.5) * 2.0 * rangeXZ
    val y = player.y + (rand.nextDouble() - 0.5) * rangeY
    val z = player.z + (rand.nextDouble() - 0.5) * 2.0 * rangeXZ
    return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
}
