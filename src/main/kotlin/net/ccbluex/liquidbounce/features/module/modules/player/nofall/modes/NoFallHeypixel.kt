package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

internal object NoFallHeypixel : Choice("Heypixel") {
    override val parent: ChoiceConfigurable<*>
        get() = ModuleNoFall.modes

    private val autoJump by boolean("AutoJump",true)
    private var lastFallDis = 0F
    private var lastGround = false
    private var shouldJump = false

    val packetHandler = handler<PacketEvent> {
        val packet = it.packet

        if (packet is PlayerMoveC2SPacket) {
            if (packet.onGround && !lastGround &&
                lastFallDis >= player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE).toFloat()
            ) {
                shouldJump = true
                packet.x += 1337.0
                packet.z += 1337.0
                packet.onGround = false
                player.onLanding()
            }

            lastGround = packet.onGround
            lastFallDis = player.fallDistance
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<MovementInputEvent> { event ->
        debugParameter(this@NoFallHeypixel,"shouldJump",shouldJump)
        if (shouldJump && player.isOnGround && autoJump) {
            event.jump = true
            shouldJump = false
        }
    }
}
