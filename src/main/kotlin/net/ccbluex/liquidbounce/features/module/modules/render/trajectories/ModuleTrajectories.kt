/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render.trajectories

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.Configurable
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeCam
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.asText
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.math.toFixed
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryData
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.ccbluex.liquidbounce.utils.render.trajectory.categorize
import net.minecraft.entity.Entity
import net.minecraft.entity.Ownable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.*
import net.minecraft.entity.projectile.thrown.*
import net.minecraft.util.math.Vec3d
import java.text.DecimalFormat
import java.util.function.BiFunction
import java.util.function.IntFunction

/**
 * Trajectories module
 *
 * Allows you to see where projectile items will land.
 */
@Suppress("MagicNumber")
object ModuleTrajectories : ClientModule("Trajectories", Category.RENDER) {
    private val maxSimulatedTicks by int("MaxSimulatedTicks", 240, 1..1000, "ticks")
    private val show by multiEnumChoice(
        "Show",
        Show.OTHER_PLAYERS,
        Show.ACTIVE_HOLDING
    )

    val alwaysShowBow get() = Show.ALWAYS_SHOW_BOW in show
    private val otherPlayers get() = Show.OTHER_PLAYERS in show
    private val activeThrown get() = Show.ACTIVE_THROWN in show
    private val activeHolding get() = Show.ACTIVE_HOLDING in show

    sealed class ProjectileType(name: String, defaultColor: Color4b, enabled : Boolean) : ToggleableConfigurable(
        this, name, enabled =  enabled) {
        val color by color("Color", defaultColor)
        val blockHitESP by boolean("BlockHitESP", true)
        val entityHitESP by boolean("EntityHitESP", true)
        val showDetailedInfo by boolean("ShowDetailedInfo", false)

        object Arrow : ProjectileType("Arrow",
            Color4b(255, 0, 0, 100),true)

        object Potion : ProjectileType("Potion",
            Color4b(255, 192, 203, 100),true)

        object EnderPearl : ProjectileType("EnderPearl",
            Color4b(255, 0, 255, 100),true)

        object FishingBobber : ProjectileType("FishingBobber",
            Color4b(64, 64, 64, 100),true)

        object Trident : ProjectileType("Trident",
            Color4b(0, 255, 255, 100),true)

        object Snowball : ProjectileType("Snowball",
            Color4b(255, 255, 255, 100),true)

        object Egg : ProjectileType("Egg",
            Color4b(255, 255, 255, 100),true)

        object ExpBottle : ProjectileType("ExpBottle",
            Color4b(0, 255, 0, 100),false)

        object Fireball : ProjectileType("Fireball",
            Color4b(255, 165, 0, 100),true)

        object WindCharge : ProjectileType("WindCharge",
            Color4b(192, 192, 192, 100),true)
    }

    private object ShowDetailedInfo : Configurable("ShowDetailedInfo") {
        private val ownerName by boolean("OwnerName", false)
        private val distance by boolean("Distance", false)
        private val showAt by enumChoice("ShowAt", ShowAt.LANDING)
        private val durationUnit by enumChoice("DurationUnit", DurationUnit.TICKS)
        private val TICK_FORMATTER = DecimalFormat("0.#s")
        private val scale by float("Scale", 1F, 0.25F..4F)
        private val renderOffset by vec3d("RenderOffset", Vec3d.ZERO)

        private enum class ShowAt(
            override val choiceName: String,
        ) : NamedChoice, BiFunction<TrajectoryInfoRenderer, TrajectoryInfoRenderer.SimulationResult, Vec3d> {
            OWNER("Owner"),
            ENTITY("Entity"),
            LANDING("Landing");

            override fun apply(
                renderer: TrajectoryInfoRenderer,
                result: TrajectoryInfoRenderer.SimulationResult,
            ): Vec3d = when (this) {
                OWNER -> renderer.owner.pos
                ENTITY -> result.positions.firstOrNull()
                LANDING -> result.positions.lastOrNull()
            } ?: renderer.owner.pos
        }

        private enum class DurationUnit(
            override val choiceName: String,
        ) : NamedChoice, IntFunction<String> {
            TICKS("Ticks"),
            SECONDS("Seconds");

            override fun apply(ticks: Int): String = when (this) {
                TICKS -> ticks.toString()
                SECONDS -> TICK_FORMATTER.format(ticks * 0.05)
            }
        }

        @Suppress("unused")
        private val overlayRenderHandler = handler<OverlayRenderEvent> { event ->
            fun Vec3d.calcScreenPosWithOffset(): Vec3? {
                return WorldToScreen.calculateScreenPos(add(renderOffset))
            }

            val context = event.context

            simulationResults.forEachIndexed { index, (renderer, result) ->
                val type = when (renderer.type) {
                    TrajectoryInfoRenderer.Type.REAL -> renderer.trajectoryInfo.categorize()
                    TrajectoryInfoRenderer.Type.HYPOTHETICAL -> renderer.trajectoryInfo.categorize()
                } ?: return@forEachIndexed

                if (!type.showDetailedInfo) return@forEachIndexed

                val screenPos =
                    when {
                        showAt === ShowAt.OWNER && renderer.owner === player -> when (renderer.type) {
                            TrajectoryInfoRenderer.Type.HYPOTHETICAL ->
                                ShowAt.LANDING.apply(renderer, result).calcScreenPosWithOffset()
                            else -> {
                                val centerX = mc.window.scaledWidth * 0.5F
                                val centerY = mc.window.scaledHeight * 0.5F
                                Vec3(centerX + 50F, centerY + index * (mc.textRenderer.fontHeight + 1), 0F)
                            }
                        }
                        else -> showAt.apply(renderer, result).calcScreenPosWithOffset()
                    } ?: return@forEachIndexed

                context.matrices.push()
                context.matrices.translate(screenPos.x, screenPos.y, screenPos.z)
                context.matrices.scale(scale, scale, 1.0F)

                val text = durationUnit.apply(result.positions.size).asText()
                if (ownerName && renderer.owner !== player) {
                    text.append(" ").append(renderer.owner.name)
                }
                if (distance && result.positions.isNotEmpty()) {
                    text.append(" ${player.pos.distanceTo(result.positions.last()).toFixed(1)}m")
                }

                var y = 0

                context.drawCenteredTextWithShadow(
                    mc.textRenderer,
                    text,
                    0,
                    y,
                    Color4b.WHITE.toARGB(),
                )
                y += mc.textRenderer.fontHeight + 1

                context.matrices.pop()
            }
        }
    }

    init {
        tree(ProjectileType.Arrow)
        tree(ProjectileType.Potion)
        tree(ProjectileType.EnderPearl)
        tree(ProjectileType.FishingBobber)
        tree(ProjectileType.Trident)
        tree(ProjectileType.Snowball)
        tree(ProjectileType.Egg)
        tree(ProjectileType.ExpBottle)
        tree(ProjectileType.Fireball)
        tree(ProjectileType.WindCharge)
        tree(ShowDetailedInfo)
    }

    private val simulationResults = mutableListOf<Pair
    <TrajectoryInfoRenderer, TrajectoryInfoRenderer.SimulationResult>>()

    override fun onDisabled() {
        simulationResults.clear()
    }

    val renderHandler = handler<WorldRenderEvent> { event ->
        simulationResults.clear()

        if (activeThrown) {
            world.entities.forEach { entity ->
                val trajectoryInfo = TrajectoryData.getRenderTrajectoryInfoForOtherEntity(entity) ?: return@forEach

                val type = entity.categorize() ?: return@forEach
                if (!type.enabled) return@forEach

                val renderer = TrajectoryInfoRenderer(
                    owner = (entity as? Ownable)?.owner ?: entity,
                    velocity = entity.velocity,
                    pos = entity.pos,
                    trajectoryInfo = trajectoryInfo,
                    type = TrajectoryInfoRenderer.Type.REAL,
                    renderOffset = Vec3d.ZERO
                )

                simulationResults += renderer to renderer.drawTrajectoryForProjectile(
                    maxSimulatedTicks,
                    event,
                    trajectoryColor = type.color,
                    blockHitColor = if (type.blockHitESP) type.color else Color4b.TRANSPARENT,
                    entityHitColor = if (type.entityHitESP) type.color else Color4b.TRANSPARENT
                )
            }
        }

        if (activeHolding) {
            if (otherPlayers) {
                world.players.forEach { drawHypotheticalTrajectory(it, event) }
            } else {
                drawHypotheticalTrajectory(player, event)
            }
        }
    }


    private fun drawHypotheticalTrajectory(playerEntity: PlayerEntity, event: WorldRenderEvent) {
        val trajectoryInfo = playerEntity.handItems.firstNotNullOfOrNull {
            TrajectoryData.getRenderedTrajectoryInfo(playerEntity, it.item, alwaysShowBow)
        } ?: return

        val type = trajectoryInfo.categorize() ?: return
        if (!type.enabled) return

        val rotation = if (playerEntity === player) {
            if (ModuleFreeCam.running){
                RotationManager.serverRotation
            }
            else{
                RotationManager.activeRotationTarget?.rotation
                    ?: RotationManager.currentRotation
                    ?: playerEntity.rotation
            }
        } else {
            playerEntity.rotation
        }

        val renderer = TrajectoryInfoRenderer.getHypotheticalTrajectory(
            entity = playerEntity,
            trajectoryInfo = trajectoryInfo,
            rotation = rotation,
            partialTicks = event.partialTicks
        )

        simulationResults += renderer to renderer.drawTrajectoryForProjectile(
            maxSimulatedTicks,
            event,
            trajectoryColor = type.color,
            blockHitColor = if (type.blockHitESP) type.color else Color4b.TRANSPARENT,
            entityHitColor = if (type.entityHitESP) type.color else Color4b.TRANSPARENT
        )
    }

    private enum class Show(override val choiceName: String) : NamedChoice {
        ALWAYS_SHOW_BOW("AlwaysShowBow"),
        OTHER_PLAYERS("OtherPlayers"),
        ACTIVE_THROWN("ActiveThrown"),
        ACTIVE_HOLDING("ActiveHolding")
    }

    // ---------------- Categorize ----------------
    @JvmStatic
    fun Entity.categorize(): ProjectileType? = when (this) {
        is ArrowEntity -> ProjectileType.Arrow
        is PotionEntity -> ProjectileType.Potion
        is EnderPearlEntity -> ProjectileType.EnderPearl
        is FishingBobberEntity -> ProjectileType.FishingBobber
        is TridentEntity -> ProjectileType.Trident
        is SnowballEntity -> ProjectileType.Snowball
        is EggEntity -> ProjectileType.Egg
        is ExperienceBottleEntity -> ProjectileType.ExpBottle
        is FireballEntity -> ProjectileType.Fireball
        is WindChargeEntity -> ProjectileType.WindCharge
        else -> null
    }


}
