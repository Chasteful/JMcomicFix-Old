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

package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.util.math.Vec3d
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.atan2

object VelocityWatchdogPrediction : VelocityMode("WatchdogPrediction") {

    private var isTakingVelocity = false
    private var shouldJump = false
    private var targetYaw = 0f
    private val velocityPackets = mutableListOf<Packet<*>>()
    private var knockbackX = 0.0
    private var knockbackZ = 0.0
    private var velocityStartTick = 0

    override fun enable() {
        isTakingVelocity = false
        shouldJump = false
        velocityPackets.clear()
        velocityStartTick = 0
    }

    private fun replayPacket(packet: Packet<*>) {
        val handler = mc.networkHandler
        if (handler != null) {
            try {
                val method = handler.javaClass.methods.firstOrNull { m ->
                    val params = m.parameterTypes
                    params.size == 1 && params[0].isAssignableFrom(packet.javaClass)
                }
                if (method != null) {
                    method.invoke(handler, packet)
                    return
                }
            } catch (_: Exception) {
            }
        }

        when (packet) {
            is EntityVelocityUpdateS2CPacket -> {
                player.velocity = Vec3d(
                    packet.velocityX / 8000.0,
                    packet.velocityY / 8000.0,
                    packet.velocityZ / 8000.0
                )
            }
            is ExplosionS2CPacket -> {
                packet.playerKnockback.ifPresent { kb ->
                    player.velocity = player.velocity.add(kb.x, kb.y, kb.z)
                }
            }
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is EntityVelocityUpdateS2CPacket -> {
                if (packet.entityId != player.id || event.isCancelled) return@handler

                val vx = packet.velocityX / 8000.0
                val vy = packet.velocityY / 8000.0
                val vz = packet.velocityZ / 8000.0
                if (vy > 0.0 || player.health <= 14.0f || player.hurtTime <= 1) {
                    shouldJump = true
                }

                if (player.isInvulnerable || player.hurtTime <= 4) return@handler

                knockbackX = vx
                knockbackZ = vz
                var yaw = toDegrees(atan2(knockbackZ, knockbackX)).toFloat()
                if (yaw < -180f) yaw += 360f
                if (yaw > 180f) yaw -= 360f
                targetYaw = yaw

                isTakingVelocity = true
                velocityStartTick = player.age
                velocityPackets.add(packet)
                event.cancelEvent()
            }

            is ExplosionS2CPacket -> {
                if (!isTakingVelocity) return@handler
                velocityPackets.add(packet)
                event.cancelEvent()
            }

            is EntityDamageS2CPacket -> {
                if (!isTakingVelocity) return@handler
                velocityPackets.add(packet)
                event.cancelEvent()
            }
        }
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (shouldJump) {
            event.directionalInput = DirectionalInput.FORWARDS
            event.jump = true

            player.isSprinting = true
            shouldJump = false
        }

        if (isTakingVelocity) {
            event.directionalInput = DirectionalInput.FORWARDS
            player.isSprinting = true
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!isTakingVelocity) return@handler

        var playerYaw = player.yaw % 360f
        if (playerYaw < -180f) playerYaw += 360f
        if (playerYaw > 180f) playerYaw -= 360f

        val rawDiff = abs(playerYaw - targetYaw)
        val yawDiff = if (rawDiff > 180f) 360f - rawDiff else rawDiff
        val tickDiff = player.age - velocityStartTick

        if (yawDiff <= 15f || player.isInvulnerable || tickDiff > 12) {
            velocityPackets.forEach { p ->
                replayPacket(p)
            }

            velocityPackets.clear()
            isTakingVelocity = false
            player.isSprinting = false
        }
    }
}
