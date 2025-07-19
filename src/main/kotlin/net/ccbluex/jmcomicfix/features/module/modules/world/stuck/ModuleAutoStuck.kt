package net.ccbluex.jmcomicfix.features.module.modules.world.stuck

import net.ccbluex.jmcomicfix.features.module.modules.player.autoClutch.ModuleAutoClutch
import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import kotlin.math.ceil
import kotlin.math.floor


@Suppress("TooManyFunctions")
object ModuleAutoStuck : ClientModule("AutoStuck", Category.WORLD) {

    private val resetTicks by int("ResetTicks", 20, 1..200, "ticks")

    private object StuckSettings : ToggleableConfigurable(ModuleAutoStuck, "StuckSettings", true) {
        val stuckOnlyVoid by boolean("OnlyVoid", true)
        val stuckOnlyPearl by boolean("OnlyPearl", true)
        val stuckFallDistance by int("FallDistance", 5, 1..50, "blocks")
    }

    private object ScaffoldSettings : ToggleableConfigurable(ModuleAutoStuck, "ScaffoldSettings", true) {
        val scaffoldOnlyVoid by boolean("OnlyVoid", true)
        val scaffoldWhilePearl by boolean("WhilePearl", true)
        val scaffoldVoidDistance by int("VoidDistance", 1, 1..50, "blocks")
        val scaffoldOnlyDuringCombat by boolean("OnlyDuringCombat", true)
    }

    init {
        tree(StuckSettings)
        tree(ScaffoldSettings)
    }

    private var stuckTicks = 0
    var isInAir = false
    private var lastGroundY = LOWEST_Y
    private var scaffoldSaving = false
    var shouldEnableStuck = false

    const val LOWEST_Y = -64
    const val EDGE = 0.3

    private fun hasPearlInHotbar(): Boolean {
        return player.inventory.main.any { it?.item == Items.ENDER_PEARL }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> {
        if (shouldEnableStuck) {
            player.movement.x = 0.0
            player.movement.y = 0.0
            player.movement.z = 0.0
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (!shouldEnableStuck) return@handler

        if (!player.isOnGround) {
            isInAir = true

            if (event.packet is PlayerPositionLookS2CPacket) {
                shouldEnableStuck = false
            }

            if (ModuleAutoClutch.state == ModuleAutoClutch.State.ROTATING) {
                if (event.packet is PlayerMoveC2SPacket) {

                    if (event.packet.changesPosition()) {
                        event.cancelEvent()
                    }
                }
            } else {

                if (event.packet is PlayerMoveC2SPacket) {
                    event.cancelEvent()
                }
            }

            if (event.packet is PlayerInteractItemC2SPacket) {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        player.yaw, player.pitch, player.isOnGround, player.horizontalCollision
                    )
                )
                sendPacketSilently(
                    PlayerInteractItemC2SPacket(
                        event.packet.hand, event.packet.sequence, player.yaw, player.pitch
                    )
                )
            }
        } else if (isInAir) {
            shouldEnableStuck = false
        }
    }

    private fun shouldDisableStuck(): Boolean {
        if (player.isInsideWaterOrBubbleColumn) {
            return true
        }
        if (isTouchingLadder()) {
            return true
        }


        if (hasSolidBlockBelow()) {
            return true
        }


        return false
    }

    private fun hasSolidBlockBelow(): Boolean {
        val checkDepth = 3
        for (i in 0..checkDepth) {
            val pos = BlockPos(player.x.toInt(), (player.y - i).toInt(), player.z.toInt())
            val state = pos.getState() ?: continue

            if (!state.isAir) {
                return true
            }
        }
        return false
    }

    private fun isTouchingLadder(): Boolean {

        val checkPositions = mutableListOf<BlockPos>().apply {

            add(BlockPos(player.x.toInt(), (player.y - 0.1).toInt(), player.z.toInt()))

            val lookVec = player.rotationVector
            when {
                lookVec.x > 0.7 -> add(BlockPos((player.x + 1).toInt(), player.y.toInt(), player.z.toInt()))  // 东
                lookVec.x < -0.7 -> add(BlockPos((player.x - 1).toInt(), player.y.toInt(), player.z.toInt()))  // 西
                lookVec.z > 0.7 -> add(BlockPos(player.x.toInt(), player.y.toInt(), (player.z + 1).toInt()))   // 南
                lookVec.z < -0.7 -> add(BlockPos(player.x.toInt(), player.y.toInt(), (player.z - 1).toInt()))  // 北
            }
        }

        return checkPositions.any { pos ->
            world.getBlockState(pos).block.translationKey.contains("ladder")
        }
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        reset(true)
    }

    private fun reset(disable: Boolean) {
        lastGroundY = LOWEST_Y
        if (disable) {
            if (scaffoldSaving) ModuleScaffold.enabled = false
            shouldEnableStuck = false
        }
        scaffoldSaving = false
    }

    fun aboveVoid(voidDistance: Int = -1): Boolean {
        if (player.isOnGround) return false

        val xMinOffset = if (player.x - floor(player.x) <= EDGE) -1 else 0
        val xMaxOffset = if (ceil(player.x) - player.x <= EDGE) 1 else 0
        val zMinOffset = if (player.z - floor(player.z) <= EDGE) -1 else 0
        val zMaxOffset = if (ceil(player.z) - player.z <= EDGE) 1 else 0

        return (xMinOffset..xMaxOffset).any { xOffset ->
            (zMinOffset..zMaxOffset).any { zOffset ->
                val yRange = if (voidDistance == -1) LOWEST_Y..lastGroundY else lastGroundY - voidDistance..lastGroundY
                yRange.all { y ->
                    BlockPos(player.x.toInt() + xOffset, y, player.z.toInt() + zOffset)
                        .getBlock()
                        ?.translationKey == "block.minecraft.air"
                }
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.isOnGround) {
            lastGroundY = player.y.toInt() - 1
        }

        if (shouldEnableStuck) {
            stuckTicks++
            if (stuckTicks >= resetTicks) {
                stuckTicks = 0
                shouldEnableStuck = false
                shouldEnableStuck = true
            }

            if (shouldDisableStuck()) {
                shouldEnableStuck = false

            }
        } else {
            stuckTicks = 0
        }

        if (StuckSettings.enabled) {
            val shouldActivate = (!StuckSettings.stuckOnlyVoid || aboveVoid()) &&
                (!StuckSettings.stuckOnlyPearl || hasPearlInHotbar()) &&
                !player.isOnGround && player.y <= lastGroundY + 1 - StuckSettings.stuckFallDistance

            if (shouldActivate && !shouldEnableStuck && !shouldDisableStuck()) {
                shouldEnableStuck = true
                isInAir = false
            } else if (!shouldActivate && shouldEnableStuck) {

                shouldEnableStuck = false

            }
        }

        if (ScaffoldSettings.enabled) {
            val shouldActivateScaffold =
                (!ScaffoldSettings.scaffoldOnlyDuringCombat || CombatManager.isInCombat) &&
                    (!ScaffoldSettings.scaffoldWhilePearl || !hasPearlInHotbar()) &&
                    aboveVoid(
                        if (ScaffoldSettings.scaffoldOnlyVoid) {
                            -1
                        } else {
                            ScaffoldSettings.scaffoldVoidDistance
                        }
                    )
            if (shouldActivateScaffold) {
                if (!scaffoldSaving && !ModuleScaffold.enabled) {
                    ModuleScaffold.enabled = true
                    scaffoldSaving = true
                }
            } else {
                if (scaffoldSaving) {
                    ModuleScaffold.enabled = false
                    scaffoldSaving = false
                }
            }
        }
    }

    override fun enable() {
        stuckTicks = 0
        isInAir = false
        reset(false)
    }

    override fun disable() {
        shouldEnableStuck = false
    }
}
