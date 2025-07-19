package net.ccbluex.jmcomicfix.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.getActualHealth
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.item.isNothing
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.Items
import net.minecraft.util.hit.EntityHitResult

@Suppress("TooManyFunctions")
object ModuleAutoRod : ClientModule("AutoRod", Category.COMBAT) {
    val facingEnemy by boolean("FacingEnemy", true)
    val ignoreOnEnemyLowHealth by boolean("IgnoreOnEnemyLowHealth", true)
    val healthFromScoreboard by boolean("HealthFromScoreboard", false)
    private object Settings : ToggleableConfigurable(this, "AdvancedSettings", true) {
        val activationDistance by float("ActivationDistance", 8f, 1f..20f)
        val enemiesNearby by int("EnemiesNearby", 1, 1..5)
        val playerHealthThreshold by int("PlayerHealthThreshold", 5, 1..20)
        val enemyHealthThreshold by int("EnemyHealthThreshold", 5, 1..20)
        val escapeHealthThreshold by int("EscapeHealthThreshold", 10, 1..20)
        val pushDelay by int("PushDelay", 100, 50..1000)
        val pullbackDelay by int("PullbackDelay", 250, 50..1000)
        val onUsingItem by boolean("OnUsingItem", false)
    }

    init {
        tree(Settings)
    }

    private val pushTimer = object {
        private var lastMS = System.currentTimeMillis()

        fun reset() {
            lastMS = System.currentTimeMillis()
        }

        fun hasTimePassed(time: Long): Boolean {
            return System.currentTimeMillis() - lastMS >= time
        }
    }

    private val rodPullTimer = object {
        private var lastMS = System.currentTimeMillis()

        fun reset() {
            lastMS = System.currentTimeMillis()
        }

        fun hasTimePassed(time: Long): Boolean {
            return System.currentTimeMillis() - lastMS >= time
        }
    }

    private var rodInUse = false
    private var switchBack = -1

    @Suppress("unused")
    private val onUpdate = tickHandler {
        // Check if player is using rod
        val usingRod = (player.isUsingItem && player.mainHandStack.item == Items.FISHING_ROD) || rodInUse

        if (usingRod) {
            // Check if rod pull timer has reached delay
            if (rodPullTimer.hasTimePassed(Settings.pullbackDelay.toLong())) {
                if (switchBack != -1 && player.inventory.selectedSlot != switchBack) {
                    // Switch back to previous item
                    SilentHotbar.selectSlotSilently(this, HotbarItemSlot(switchBack), 0)
                } else {
                    // Stop using rod
                    player.stopUsingItem()
                }

                switchBack = -1
                rodInUse = false

                pushTimer.reset()
            }
        } else {
            var shouldRod = false

            if (facingEnemy &&
                player.getActualHealth(healthFromScoreboard) >= Settings.playerHealthThreshold) {
                var facingEntity: Entity? = null
                val crosshairTarget = mc.crosshairTarget

                // First try to get entity from crosshair
                if (crosshairTarget is EntityHitResult && crosshairTarget.entity is LivingEntity) {
                    facingEntity = crosshairTarget.entity
                }

                val nearbyEnemies = getAllNearbyEnemies()

                // If no entity found in crosshair, try raycasting
                if (facingEntity == null) {
                    facingEntity = raytraceEntity(Settings.activationDistance.toDouble()) { entity ->
                        entity is LivingEntity && entity.shouldBeAttacked()
                    }
                }

                // Check if player is using items/blocking
                if (!Settings.onUsingItem) {
                    if (player.activeItem.item != Items.FISHING_ROD &&
                        (player.isUsingItem || KillAuraAutoBlock.blockingStateEnforced)) {
                        return@tickHandler
                    }
                }

                // Check if we have a valid target
                if (facingEntity != null && facingEntity is LivingEntity && facingEntity.shouldBeAttacked()) {
                    // Check enemy count
                    if (nearbyEnemies.size <= Settings.enemiesNearby) {
                        // Check enemy health if needed
                        if (ignoreOnEnemyLowHealth) {
                            if (facingEntity.getActualHealth
                                    (healthFromScoreboard) >= Settings.enemyHealthThreshold) {
                                shouldRod = true
                            }
                        } else {
                            shouldRod = true
                        }
                    }
                }
            } else if (player.getActualHealth(healthFromScoreboard) <= Settings.escapeHealthThreshold) {
                // use rod for escaping when health is low
                shouldRod = true
            } else if (!facingEnemy) {
                // Rod anyway, spam it
                shouldRod = true
            }

            if (shouldRod && pushTimer.hasTimePassed(Settings.pushDelay.toLong())) {
                // Check if player has rod in hand
                if (player.mainHandStack.item != Items.FISHING_ROD) {
                    // Check if player has rod in hotbar
                    val rod = findRod()

                    if (rod == -1) {
                        // There is no rod in hotbar
                        return@tickHandler
                    }

                    // Switch to rod
                    switchBack = player.inventory.selectedSlot
                    SilentHotbar.selectSlotSilently(this, HotbarItemSlot(rod), 0)
                }

                rod()
            }
        }
    }

    /**
     * Use rod
     */
    private fun rod() {
        val rod = findRod()

        SilentHotbar.selectSlotSilently(this, HotbarItemSlot(rod), 0)
        interaction.interactItem(player, player.getActiveHand())

        rodInUse = true
        rodPullTimer.reset()
    }

    /**
     * Find rod in hotbar
     */
    private fun findRod(): Int {
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (!stack.isNothing() && stack.item == Items.FISHING_ROD) {
                return i
            }
        }
        return -1
    }

    private fun getAllNearbyEnemies(): List<Entity> {
        if (player == null) return emptyList()

        return world.entities.filter {
            it.shouldBeAttacked() && player.distanceTo(it) < Settings.activationDistance
        }
    }


    private fun raytraceEntity(
        range: Double,
        filter: (Entity) -> Boolean
    ): Entity? {
        val rotation = RotationManager.serverRotation
        val from = player.getCameraPosVec(1f)
        val direction = rotation.directionVector.multiply(range)
        val to = from.add(direction)

        return world.entities
            .filter { filter(it) && it.boundingBox.intersects(from, to) }
            .minByOrNull { player.squaredDistanceTo(it) }
    }
}
