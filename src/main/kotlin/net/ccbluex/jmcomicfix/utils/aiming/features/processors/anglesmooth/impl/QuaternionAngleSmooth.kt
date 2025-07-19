package net.ccbluex.jmcomicfix.utils.aiming.features.processors.anglesmooth.impl

import ai.djl.util.RandomUtils
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.FactorAngleSmooth
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.util.math.MathHelper
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Suppress("MaxLineLength")
class QuaternionAngleSmooth(
    parent: ChoiceConfigurable<*>,
    private val interpolationSpeed: ClosedFloatingPointRange<Float> = 0.1f..0.3f,
) : FactorAngleSmooth("Quaternion", parent) {

    // Configurable parameters
    private val horizontalSpeed by floatRange("HorizontalSpeed", 80.0f..85.0f, 1.0f..100.0f, "%")
    private val verticalSpeed by floatRange("VerticalSpeed", 20.0f..25.0f, 1.0f..100.0f, "%")
    private val directionChangeFactor by intRange("DirectionChangeFactor", 95..100, 0..100, "%")
    private val motionInfluence by float("MotionInfluence", 0.5f, 0.0f..1.0f)
    private val noiseIntensity by float("NoiseIntensity", 0.06f, 0.0f..0.2f)
    private val randomOffsetStrength by floatRange("RandomOffset", 0.02f..0.06f, 0.0f..0.2f)

    private var lastRotationTime: Long = 0
    private val fixedRandomHeightOffset = Random.Default.nextFloat()
    private val random = Random.Default

    data class Octonion(
        val e0: Double, val e1: Double, val e2: Double, val e3: Double,
        val e4: Double, val e5: Double, val e6: Double, val e7: Double
    ) {
        fun normalize(): Octonion {
            val mag = sqrt(e0 * e0 + e1 * e1 + e2 * e2 + e3 * e3 + e4 * e4 + e5 * e5 + e6 * e6 + e7 * e7)
            return if (mag > 0.0001) {
                Octonion(
                    e0 / mag, e1 / mag, e2 / mag, e3 / mag,
                    e4 / mag, e5 / mag, e6 / mag, e7 / mag
                )
            } else {
                this
            }
        }

        fun multiply(other: Octonion): Octonion {
            val a0 = e0 * other.e0 - e1 * other.e1 - e2 * other.e2 - e3 * other.e3 - e4 * other.e4 - e5 * other.e5 - e6 * other.e6 - e7 * other.e7
            val a1 = e0 * other.e1 + e1 * other.e0 + e2 * other.e3 - e3 * other.e2 + e4 * other.e5 - e5 * other.e4 - e6 * other.e7 + e7 * other.e6
            val a2 = e0 * other.e2 - e1 * other.e3 + e2 * other.e0 + e3 * other.e1 + e4 * other.e6 + e5 * other.e7 - e6 * other.e4 - e7 * other.e5
            val a3 = e0 * other.e3 + e1 * other.e2 - e2 * other.e1 + e3 * other.e0 + e4 * other.e7 - e5 * other.e6 + e6 * other.e5 - e7 * other.e4
            val a4 = e0 * other.e4 - e1 * other.e5 - e2 * other.e6 - e3 * other.e7 + e4 * other.e0 + e5 * other.e1 + e6 * other.e2 + e7 * other.e3
            val a5 = e0 * other.e5 + e1 * other.e4 - e2 * other.e7 + e3 * other.e6 - e4 * other.e1 + e5 * other.e0 - e6 * other.e3 + e7 * other.e2
            val a6 = e0 * other.e6 + e1 * other.e7 + e2 * other.e4 - e3 * other.e5 - e4 * other.e2 + e5 * other.e3 + e6 * other.e0 - e7 * other.e1
            val a7 = e0 * other.e7 - e1 * other.e6 + e2 * other.e5 + e3 * other.e4 - e4 * other.e3 - e5 * other.e2 + e6 * other.e1 + e7 * other.e0
            return Octonion(a0, a1, a2, a3, a4, a5, a6, a7)
        }

        fun toEuler(): Rotation {
            val sinPitch = 2.0 * (e0 * e2 - e3 * e1 + e4 * e7 - e5 * e6)
            val clampedPitch = sinPitch.coerceIn(-1.0, 1.0)
            val yaw = atan2(
                2.0 * (e0 * e3 + e1 * e2 + e4 * e6 + e5 * e7),
                1.0 - 2.0 * (e2 * e2 + e3 * e3 + e6 * e6 + e7 * e7)
            ).toFloat()
            val pitch = asin(clampedPitch).toFloat()
            return Rotation(
                MathHelper.wrapDegrees(Math.toDegrees(yaw.toDouble()).toFloat()),
                MathHelper.wrapDegrees(Math.toDegrees(pitch.toDouble()).toFloat())
            )
        }
    }

    private fun eulerToOctonion(yaw: Float, pitch: Float, playerMotion: Float): Octonion {
        val cy = cos(Math.toRadians(yaw.toDouble()) * 0.5)
        val sy = sin(Math.toRadians(yaw.toDouble()) * 0.5)
        val cp = cos(Math.toRadians(pitch.toDouble()) * 0.5)
        val sp = sin(Math.toRadians(pitch.toDouble()) * 0.5)

        val motionFactor =
            playerMotion.coerceIn(0f, 0.4f) * motionInfluence


        val e4 = if (motionFactor > 0) random.nextFloat() * 0.015 * fixedRandomHeightOffset * motionFactor else 0.0
        val e5 = if (motionFactor > 0) random.nextFloat() * 0.015 * (1.0 - fixedRandomHeightOffset) * motionFactor else 0.0
        val e6 = random.nextFloat() * 0.008 * fixedRandomHeightOffset
        val e7 = random.nextFloat() * 0.008 * (1.0 - fixedRandomHeightOffset)

        return Octonion(cy * cp, 0.0, sp * cy, sy * cp, e4, e5, e6, e7).normalize()
    }

    private fun octonionSlerp(o1: Octonion, o2: Octonion, t: Float): Octonion {
        var dot = o1.e0 * o2.e0 + o1.e1 * o2.e1 + o1.e2 * o2.e2 + o1.e3 * o2.e3 +
            o1.e4 * o2.e4 + o1.e5 * o2.e5 + o1.e6 * o2.e6 + o1.e7 * o2.e7

        val o2Adjusted = if (dot < 0.0) {
            dot = -dot
            Octonion(-o2.e0, -o2.e1, -o2.e2, -o2.e3, -o2.e4, -o2.e5, -o2.e6, -o2.e7)
        } else {
            o2
        }

        if (dot > 0.9995) {
            return Octonion(
                o1.e0 + t * (o2Adjusted.e0 - o1.e0),
                o1.e1 + t * (o2Adjusted.e1 - o1.e1),
                o1.e2 + t * (o2Adjusted.e2 - o1.e2),
                o1.e3 + t * (o2Adjusted.e3 - o1.e3),
                o1.e4 + t * (o2Adjusted.e4 - o1.e4),
                o1.e5 + t * (o2Adjusted.e5 - o1.e5),
                o1.e6 + t * (o2Adjusted.e6 - o1.e6),
                o1.e7 + t * (o2Adjusted.e7 - o1.e7)
            ).normalize()
        }

        val theta0 = acos(dot)
        val theta = theta0 * t
        val sinTheta = sin(theta)
        val sinTheta0 = sin(theta0)

        val scale1 = cos(theta) - dot * sinTheta / sinTheta0
        val scale2 = sinTheta / sinTheta0

        return Octonion(
            scale1 * o1.e0 + scale2 * o2Adjusted.e0,
            scale1 * o1.e1 + scale2 * o2Adjusted.e1,
            scale1 * o1.e2 + scale2 * o2Adjusted.e2,
            scale1 * o1.e3 + scale2 * o2Adjusted.e3,
            scale1 * o1.e4 + scale2 * o2Adjusted.e4,
            scale1 * o1.e5 + scale2 * o2Adjusted.e5,
            scale1 * o1.e6 + scale2 * o2Adjusted.e6,
            scale1 * o1.e7 + scale2 * o2Adjusted.e7
        ).normalize()
    }

    private fun perlinNoise(x: Float, seed: Float): Float {
        val i = floor(x)
        val f = x - i
        val a0 = (sin(seed + i) * 0.5 + 0.5).toFloat()
        val a1 = (sin(seed + i + 1) * 0.5 + 0.5).toFloat()
        val t = f * f * (3.0f - 2.0f * f)
        return a0 + (a1 - a0) * t
    }

    private fun gaussianRandom(strength: Float): Float {
        return (RandomUtils.nextGaussian() * strength * 0.7).toFloat()
    }

    override fun calculateFactors(
        rotationTarget: RotationTarget?,
        currentRotation: Rotation,
        targetRotation: Rotation
    ): Pair<Float, Float> {
        val currentTime = System.currentTimeMillis()
        val playerMotion = if (mc.player != null ) {
            sqrt(mc.player!!.velocity.x * mc.player!!.velocity.x + mc.player!!.velocity.z * mc.player!!.velocity.z).toFloat()
        } else {
            0f
        }

        val directionChange = if ( RotationManager.previousRotationTarget != null) {
            RotationManager.previousRotationTarget!!.rotation.angleTo(targetRotation).coerceIn(0f, 1f) *
                (directionChangeFactor.random().toFloat() / 100.0f)
        } else {
            0f
        }

        val horizontalSpeed = horizontalSpeed.random() / 100.0f
        val verticalSpeed = verticalSpeed.random() / 100.0f

        val t = interpolationSpeed.random() * ((1.0f + directionChange))
        val interpolationFactor = t * (1.0f + (playerMotion.coerceIn(0.1f, 0.4f) * motionInfluence))


        val currentOct = eulerToOctonion(
            currentRotation.yaw,
            currentRotation.pitch,
            playerMotion
        )

        val targetOct = eulerToOctonion(
            targetRotation.yaw,
            targetRotation.pitch,
            playerMotion
        )

        val interpolatedOct = octonionSlerp(currentOct, targetOct, interpolationFactor)
        var resultRotation = interpolatedOct.toEuler()


            resultRotation = applyBezierSmoothing(
                currentRotation,
                resultRotation,
                interpolationFactor
            )



            val dynamicRandomStrength =
                randomOffsetStrength.random() * (1.0f + fixedRandomHeightOffset * 0.35f)


            val timeFactor = (currentTime - lastRotationTime) / 1000f

            val yawNoise =
                sin(timeFactor * 0.09f) * 1.8f * horizontalSpeed +
                    cos(timeFactor * 0.035f) * 1.0f * horizontalSpeed +
                    perlinNoise(timeFactor * 0.8f, fixedRandomHeightOffset) * noiseIntensity


            val pitchNoise =
                sin(timeFactor * 0.09f) * 0.9f * verticalSpeed +
                    cos(timeFactor * 0.035f) * 0.5f * verticalSpeed +
                    perlinNoise(timeFactor * 0.8f, fixedRandomHeightOffset) * noiseIntensity

            resultRotation = resultRotation.add(
                yaw = gaussianRandom(dynamicRandomStrength) + yawNoise,
                pitch = gaussianRandom(dynamicRandomStrength) + pitchNoise
            )


        resultRotation = Rotation(
            MathHelper.wrapDegrees(resultRotation.yaw),
            MathHelper.wrapDegrees(resultRotation.pitch).coerceIn(-90f, 90f)
        )

        val finalDelta = resultRotation.rotationDeltaTo(currentRotation)

        lastRotationTime = currentTime

        return finalDelta.deltaYaw * horizontalSpeed to finalDelta.deltaPitch * verticalSpeed
    }

    private fun Rotation.add(yaw: Float = 0f, pitch: Float = 0f): Rotation {
        return Rotation(this.yaw + yaw, this.pitch + pitch)
    }

    private fun applyBezierSmoothing(
        startRot: Rotation,
        endRot: Rotation,
        t: Float
    ): Rotation {
        val delta = startRot.rotationDeltaTo(endRot)

        val control1 = Rotation(
            startRot.yaw + delta.deltaYaw * 0.25f,
            startRot.pitch + delta.deltaPitch * 0.25f
        )

        val control2 = Rotation(
            startRot.yaw + delta.deltaYaw * 0.75f,
            startRot.pitch + delta.deltaPitch * 0.75f
        )

        val invT = 1.0f - t
        val invT2 = invT * invT
        val t2 = t * t
        val t3 = t2 * t

        val yaw = invT * invT2 * startRot.yaw +
            3 * invT2 * t * control1.yaw +
            3 * invT * t2 * control2.yaw +
            t3 * endRot.yaw

        val pitch = invT * invT2 * startRot.pitch +
            3 * invT2 * t * control1.pitch +
            3 * invT * t2 * control2.pitch +
            t3 * endRot.pitch

        return Rotation(yaw, pitch)
    }
}
