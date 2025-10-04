package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.types.NamedChoice

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.ServerConnectEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleStuck
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.entity.VoidFallPredictor
import net.ccbluex.liquidbounce.utils.entity.hasSolidBlockBelow
import net.ccbluex.liquidbounce.utils.entity.isInVoid
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object ModuleAutoStuck : ClientModule("AutoStuck", Category.WORLD) {

    private val voidFallPrediction = tree(VoidFallPredictor( ticksToPredict = 120, voidThreshold = -64 ))
    private val fallDistance by float("FallDistance", 15f, 0f..25f, "blocks")
    private val pauseOnFlag by int("PauseOnFlag", 20, 0..100, "ticks")
    private val immediately by boolean("Immediately", true)
    private val disableOnFlag by boolean("DisablerOnFlag",false)
    private val notCondition by multiEnumChoice("Not", NotCondition.WhilePearl)

    private const val LOWEST_Y = -64
    private var lastGroundY = LOWEST_Y
    private var pauseTicks = 0
    private var ignoreTicks = 0

    private var freezing = false
    var shouldActivate = false

    private fun hasPearlInHotbar() =
        player.inventory.main.any { it?.item == Items.ENDER_PEARL }

    private fun reset(disable: Boolean) {
        if (disable && freezing) {
            freezing = false
            shouldActivate = false
            ModuleStuck.enabled = false
        }
        lastGroundY = LOWEST_Y
        pauseTicks = 0
        ignoreTicks = 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is PlayerPositionLookS2CPacket && disableOnFlag) {
            reset(true)
            pauseTicks = pauseOnFlag
        }
    }

    @Suppress("unused")
    private val serverConnectHandler = handler<ServerConnectEvent> {
        ignoreTicks = 20
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        reset(true)
    }

    private fun shouldDisableFreeze(): Boolean =
        player.isInsideWaterOrBubbleColumn ||hasSolidBlockBelow()

    private fun isReadyToActivate(): Boolean {
        if (player.isOnGround ){
            return false
        }
        val voidFallImminent = if (immediately) {
            player.y <= lastGroundY + 1.25 && isInVoid(player.pos)
        } else {
            player.y <= lastGroundY + 1.25 - fallDistance && isInVoid(player.pos)
        }

        return voidFallPrediction.isVoidFallImminent && notCondition.all { it.testCondition() } && voidFallImminent
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.isSpectator || player.abilities.flying || ignoreTicks > 0 || player.y <= (mc.world?.bottomY
                ?: LOWEST_Y)
        ) {
            ignoreTicks--
            reset(true)
            return@tickHandler
        }
        if (player.isOnGround) {
            lastGroundY = player.y.toInt() - 1
        }

        if (pauseTicks > 0) {
            pauseTicks--
            shouldActivate = false
        } else {
            shouldActivate = isReadyToActivate()
        }

        if (shouldActivate && !freezing && !shouldDisableFreeze()) {
            freezing = true
            ModuleStuck.enabled = true
        } else if (!shouldActivate && freezing) {
            freezing = false
            ModuleStuck.enabled = false
        }

        if (freezing) {
            if (shouldDisableFreeze()) {
                reset(true)
            }
        }
    }

    override fun onEnabled() {
        reset(false)
    }

    override fun onDisabled() {
        reset(true)
    }
    @Suppress("unused")
    private enum class NotCondition(
        override val choiceName: String,
        val testCondition: () -> Boolean
    ) : NamedChoice {
        WhileReceiveHit("WhileReceiveHit", { !CombatManager.isReceiveHit }),
        WhileDuringCombat("WhileDuringCombat", { !CombatManager.isInCombat }),
        WhilePearl("WhilePearl",{ hasPearlInHotbar() }),
    }
}
