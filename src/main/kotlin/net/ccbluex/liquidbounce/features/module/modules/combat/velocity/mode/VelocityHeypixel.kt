package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.Hand

internal object VelocityHeypixel : VelocityMode("Heypixel") {

    private enum class NoXZMode(override val choiceName: String) : NamedChoice {
        OneTime("OneTime"),
        PerTick("PerTick")
    }

    private val noxzMode by enumChoice("NoXZ-Mode", NoXZMode.OneTime)
    private val attacks by int("AttackCount", 3, 1..5)
    private val debug by boolean("Debug", false)

    private var target: Entity? = null
    private var attackQueue = 0

    fun isActive(): Boolean {
        return attackQueue > 0
    }

    private fun reset() {
        target = null
        attackQueue = 0
    }

    private fun findTarget(): Entity? {
        if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
            return ModuleKillAura.targetTracker.target
        }

        return raytraceEntity(
            ModuleKillAura.range.toDouble(),
            RotationManager.serverRotation
        ) { !it.isRemoved && it.shouldBeAttacked() }?.entity
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (target != null && attackQueue > 0) {
            when (noxzMode) {
                NoXZMode.OneTime -> {
                    while (attackQueue > 0) {
                        val entityHitResult = raytraceEntity(
                            ModuleKillAura.range.toDouble(),
                            RotationManager.serverRotation
                        ) { it == target }
                        if (entityHitResult == null) {
                            reset()
                            if (debug) {
                                debugParameter(
                                    this@VelocityHeypixel,
                                    "entityHitResult",
                                    "Fail to attack the target"
                                )
                            }
                            break
                        }
                        network.sendPacket(PlayerInteractEntityC2SPacket.attack(target, false))
                        player.velocity = player.velocity.multiply(0.6, 1.0, 0.6)
                        player.isSprinting = false
                        network.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
                        if (debug) {
                            debugParameter(this@VelocityHeypixel, "attack", "Attacking")
                        }
                        attackQueue--
                    }
                }
                NoXZMode.PerTick -> {
                    if (attackQueue >= 1) {
                        if (debug) {
                            debugParameter(
                                this@VelocityHeypixel,
                                "attack",
                                "Executing 1 attack in PerTick mode. Remaining: ${attackQueue - 1}"
                            )
                        }
                        val entityHitResult = raytraceEntity(
                            ModuleKillAura.range.toDouble(),
                            RotationManager.serverRotation
                        ) { it == target }
                        if (entityHitResult == null) {
                            reset()
                            if (debug) {
                                debugParameter(
                                    this@VelocityHeypixel,
                                    "entityHitResult",
                                    "Fail to attack the target"
                                )
                            }
                        }
                        network.sendPacket(PlayerInteractEntityC2SPacket.attack(target, false))
                        player.velocity = player.velocity.multiply(0.6, 1.0, 0.6)
                        player.isSprinting = false
                        network.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
                    }
                    attackQueue--
                }
            }
            if (attackQueue == 0) {
                if (debug) {
                    debugParameter(this@VelocityHeypixel, "status", "Velocity has finished its work.")
                }
            }
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            target = findTarget() ?: return@handler
            attackQueue = attacks
            if (debug) {
                debugParameter(
                    this@VelocityHeypixel,
                    "velocity",
                    "Received velocity packet. Attack queue set to $attackQueue."
                )
            }
        }
    }

    override fun enable() {
        reset()
    }

    override fun disable() {
        reset()
    }
}
