package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.until
import net.ccbluex.liquidbounce.features.module.modules.player.delayblink.ModuleDelayBlink
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

internal object NoFallMatrix : Choice("Matrix1.18.2+") {
    override val parent: ChoiceConfigurable<*>
        get() = ModuleNoFall.modes

    private var shouldSendLagPacket = false
    private var shouldHandleFall = false
    private var lagConfirmed = false
    private var cancelEvent = false
    private val packets = mutableListOf<Packet<*>>()

    override fun enable() {
        shouldSendLagPacket = false
        shouldHandleFall = false
        lagConfirmed = false
        cancelEvent = false
        super.enable()
    }

    override fun disable() {
        shouldSendLagPacket = false
        shouldHandleFall = false
        lagConfirmed = false
        cancelEvent = false
        super.disable()
    }

    private fun reset() {
        shouldSendLagPacket = false
        shouldHandleFall = false
        lagConfirmed = false
        cancelEvent = false
        packets.clear()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (ModuleDelayBlink.enabled) {
            return@tickHandler
        }
        if (player.isOnGround || player.fallDistance < player.getAttributeValue(
                EntityAttributes.SAFE_FALL_DISTANCE).toFloat()){
            return@tickHandler
        }

        if (!shouldHandleFall && !player.isOnGround) {
            shouldHandleFall = true
            shouldSendLagPacket = false
            cancelEvent = false
            lagConfirmed = false
        }

        until<PlayerNetworkMovementTickEvent> { event ->

            if (!player.isOnGround || event.state != EventState.PRE) {
                return@until false
            }
            if (shouldHandleFall) {
                if (!shouldSendLagPacket && player.fallDistance < 3.0f) {
                    network.sendPacket(
                        PlayerMoveC2SPacket.PositionAndOnGround(
                            player.x - 1000.0,
                            player.y,
                            player.z,
                            false,
                            player.horizontalCollision
                        )
                    )
                    shouldSendLagPacket = true
                    cancelEvent = true

                }
            }
            true
        }

        tickUntil {
            if (player.isOnGround && lagConfirmed) {
                player.jump()
                reset()

                packets.removeIf {
                    handlePacket(it)
                    true
                }
                true
            } else {
                player.isOnGround
            }
        }
    }

    @Suppress("unused")
    private val sprintHandler = handler<SprintEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (player.isOnGround) {
            return@handler
        }
        if (!shouldHandleFall && !shouldSendLagPacket) {
            return@handler
        }

        event.sprint = false
    }

    @Suppress("unused")
    private val packetHandler = until<PacketEvent> { event ->
        if (ModuleDelayBlink.enabled) {
            return@until false
        }
        when (val packet = event.packet) {
            is PlayerMoveC2SPacket ->
                if (shouldHandleFall && shouldSendLagPacket && cancelEvent) {
                    event.cancelEvent()
                    packets.add(packet)
                    cancelEvent = false
                }
            is PlayerPositionLookS2CPacket ->
                if (shouldHandleFall && shouldSendLagPacket && !lagConfirmed) {
                    lagConfirmed = true
                }
        }
        false
    }
}
