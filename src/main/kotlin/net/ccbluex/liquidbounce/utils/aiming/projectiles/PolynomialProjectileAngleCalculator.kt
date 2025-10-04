package net.ccbluex.liquidbounce.utils.aiming.projectiles

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.entity.EntityDimensions
import net.minecraft.util.math.Vec3d

/**
 * Solves this problem by approximating the trajectory as a second degree polynomial. This approximation is good for
 * ~20 ticks.
 *
 * Currently only used as backup
 */
object PolynomialProjectileAngleCalculator : ProjectileAngleCalculator() {

    override fun calculateAngleFor(
        projectileInfo: TrajectoryInfo,
        sourcePos: Vec3d,
        targetPosFunction: PositionExtrapolation,
        targetShape: EntityDimensions
    ): Rotation? {
        val basePos = targetPosFunction.getPositionInTicks(0.0)
        val estimatedTicksUntilImpact = basePos.distanceTo(sourcePos) / TrajectoryInfo.GENERIC.initialVelocity

        val diff: Vec3d = targetPosFunction.getPositionInTicks(estimatedTicksUntilImpact).subtract(sourcePos)

        return computeProjectileRotation(diff, TrajectoryInfo.GENERIC.initialVelocity, TrajectoryInfo.GENERIC.gravity)
    }
}
