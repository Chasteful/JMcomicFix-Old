@file:Suppress("unused")
package net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.modes

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.BreadcrumbsMode
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.ModuleBreadcrumbs.colorMode
import net.ccbluex.liquidbounce.render.drawSparkleTrail
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.utils.RenderUtils.getVector3fCameraBillboard
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import java.util.*

object SparkleMode : BreadcrumbsMode("Sparkle") {

    private val alive by int("Alive", 20, 2..3000, "ticks")
    private val size by float("Size", 0.33f, 0.1f..1.0f)
    private val gradientSpeed by int("GradientSpeed", 5, 1..10)

    private object Animation : ToggleableConfigurable(this, "Animation", true) {
        val fade by boolean("Fade", false)
        val shrink by boolean("Shrink", true)
        val fadeDuration by float("FadeDuration", 5f, 4f..10f, "tick")
        val shrinkDuration by float("ShrinkDuration", 5f, 4f..10f, "tick")
    }

    private object DistanceLimit : ToggleableConfigurable(this, "DistanceLimit", false) {
        val distanceRange by floatRange("DistanceLimit", 256f..256f, 16f..256f)
    }

    init {
        tree(Animation)
        tree(DistanceLimit)
    }

    private val darkImprint by boolean("DarkImprint", true)
    private val onlyOwn by boolean("OnlyOwn", true)

    private val trails = IdentityHashMap<Entity, Trail>()
    private val lastPositions = IdentityHashMap<Entity, DoubleArray>()
    private var pooledPosArray = FloatArray(0)
    private var pooledTimeArray = LongArray(0)

    private class Trail {
        val positions = ArrayDeque<Sparkle>()
    }
    private data class Sparkle(
        val pos: Vec3d,
        val creationTime: Long
    )

    private val debugUpdateHandler = handler<GameTickEvent> {
        trails.forEach { (entity, trail) ->
            debugParameter(this@SparkleMode, "Entity ${entity.id} TrailSize", trail.positions.size)
        }
    }

    private val updateHandler = handler<GameTickEvent> {
        val time = mc.world?.time ?: return@handler
        val entities = if (onlyOwn) listOf(player) else mc.world?.players ?: return@handler

        entities.forEach { entity ->
            val last = lastPositions[entity]
            val x = entity.x; val y = entity.y; val z = entity.z
            if (last == null || last[0] != x || last[1] != y || last[2] != z) {
                lastPositions[entity] = doubleArrayOf(x, y, z)
                trails.getOrPut(entity, ::Trail).positions.add(Sparkle(Vec3d(x, y, z), time))
            }
        }

        trails.forEach { (_, trail) ->
            while (trail.positions.isNotEmpty() && time - trail.positions.peekFirst().creationTime > alive) {
                trail.positions.removeFirst()
            }
        }

        trails.keys.retainAll { entity -> entity.isAlive && (!onlyOwn || entity === player) }
    }

    private val worldChangeHandler = handler<WorldChangeEvent> {
        trails.clear()
    }

    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (trails.isEmpty()) return@handler
        val currentTime = mc.world?.time ?: return@handler
        val matrixStack = event.matrixStack
        val (color1Base, color2Base) = colorMode.activeChoice.getColors(player)
        val distanceLimit = if (DistanceLimit.enabled) {
            DistanceLimit.distanceRange.start to DistanceLimit.distanceRange.endInclusive
        } else {
            null
        }

        renderEnvironmentForWorld(matrixStack) {
            val billboard = getVector3fCameraBillboard()

            var totalPoints = 0
            trails.forEach { (entity, trail) ->
                if (!onlyOwn || entity === player) totalPoints += trail.positions.size
            }
            if (totalPoints == 0) return@renderEnvironmentForWorld

            if (pooledPosArray.size < totalPoints * 3) pooledPosArray = FloatArray(totalPoints * 3)
            if (pooledTimeArray.size < totalPoints) pooledTimeArray = LongArray(totalPoints)

            var writeIndex = 0
            trails.forEach { (entity, trail) ->
                if (onlyOwn && entity !== player) return@forEach
                for (sparkle in trail.positions) {
                    val base = writeIndex * 3
                    pooledPosArray[base    ] = sparkle.pos.x.toFloat()
                    pooledPosArray[base + 1] = sparkle.pos.y.toFloat()
                    pooledPosArray[base + 2] = sparkle.pos.z.toFloat()
                    pooledTimeArray[writeIndex] = sparkle.creationTime
                    writeIndex++
                }
            }

            drawSparkleTrail(
                positions = pooledPosArray,
                creationTimes = pooledTimeArray,
                totalSize = totalPoints,
                env = this,
                size = size,
                currentTime = currentTime,
                alive = alive,
                gradientSpeed = gradientSpeed,
                primaryColor = color1Base,
                secondaryColor = color2Base,
                darkImprint = darkImprint,
                fade = Animation.enabled && Animation.fade,
                shrink = Animation.enabled && Animation.shrink,
                fadeDuration = Animation.fadeDuration,
                shrinkDuration = Animation.shrinkDuration,
                distanceLimit = distanceLimit,
                cameraPos = billboard.pos,
                right = billboard.right,
                up = billboard.up,
                time = billboard.time
            )
        }
    }

    override fun enable() {
        trails.clear()
    }

    override fun disable() {
        trails.clear()
    }
}
