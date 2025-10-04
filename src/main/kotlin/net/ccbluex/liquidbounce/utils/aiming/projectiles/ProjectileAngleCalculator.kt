package net.ccbluex.liquidbounce.utils.aiming.projectiles

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.ConstantPositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Calculates the shooting angle which hits the supplied target
 */
abstract class ProjectileAngleCalculator {
    abstract fun calculateAngleFor(
        projectileInfo: TrajectoryInfo,
        sourcePos: Vec3d,
        targetPosFunction: PositionExtrapolation,
        targetShape: EntityDimensions,
    ): Rotation?

    fun calculateAngleForStaticTarget(
        projectileInfo: TrajectoryInfo,
        target: Vec3d,
        shape: EntityDimensions
    ): Rotation? {
        return this.calculateAngleFor(
            projectileInfo,
            sourcePos = player.eyePos,
            targetPosFunction = ConstantPositionExtrapolation(target),
            targetShape = shape
        )
    }

    fun calculateAngleForEntity(projectileInfo: TrajectoryInfo, entity: LivingEntity): Rotation? {
        return this.calculateAngleFor(
            projectileInfo,
            sourcePos = player.eyePos,
            targetPosFunction = PositionExtrapolation.getBestForEntity(entity),
            targetShape = entity.dimensions
        )
    }

    protected fun computeProjectileRotation(
        diff: Vec3d,
        velocity: Double,
        gravity: Double
    ): Rotation? {
        val horizontalDistance = MathHelper.sqrt((diff.x * diff.x + diff.z * diff.z).toFloat()).toDouble()

        val v2 = velocity * velocity
        val v4 = v2 * v2
        val y = diff.y
        val sqrt = v4 - gravity * (gravity * horizontalDistance * horizontalDistance + 2 * y * v2)
        if (sqrt < 0) {
            return null
        }

        val pitchRad = atan((v2 - sqrt(sqrt)) / (gravity * horizontalDistance))
        val yawRad = atan2(diff.z, diff.x)

        return Rotation(
            MathHelper.wrapDegrees(Math.toDegrees(yawRad).toFloat() - 90f),
            MathHelper.wrapDegrees((-Math.toDegrees(pitchRad)).toFloat())
        )
    }
}
