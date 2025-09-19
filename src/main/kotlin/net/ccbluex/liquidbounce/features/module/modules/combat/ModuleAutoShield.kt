package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.autododge.ModuleAutoDodge
import net.ccbluex.liquidbounce.features.module.modules.combat.autododge.ModuleAutoDodge.findFlyingArrows
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.RigidPlayerSimulation
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.consume.UseAction
import net.minecraft.util.Hand
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.item.isConsumable
import net.ccbluex.liquidbounce.utils.kotlin.Priority

object ModuleAutoShield : ClientModule("AutoShield", Category.COMBAT) {

    private val raiseTicksNeeded by float("RaiseTicksNeeded",4.9f,4f..5f)
    private val rotationConfigurable = tree(RotationsConfigurable(this))


    @Suppress("unused","ComplexCondition")
    private val handler = handler<GameTickEvent> { event ->
        val player = mc.player ?: return@handler

        if (ModuleAutoDodge.dodging
            || ModuleBlink.running
            || ModuleScaffold.running
            || (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)
        ) {
            SilentHotbar.resetSlot(this)
            mc.options.useKey.isPressed = false
            return@handler
        }

        val inflictedHit = ModuleAutoDodge.world.findFlyingArrows()
            .mapNotNull { arrow ->
                ModuleAutoDodge.getInflictedHits(
                    RigidPlayerSimulation(mc.player!!.pos),
                    listOf(arrow),
                    maxTicks = 40
                )
            }
            .minByOrNull { it.tickDelta } ?: run {
            stopBlocking()
            return@handler
        }

        val ticksToImpact = inflictedHit.tickDelta + 1

        val currentlyBlocking = mc.player!!.activeItem?.item?.getUseAction(mc.player!!.activeItem) == UseAction.BLOCK

        val willSelectShield = !canBlock(player.mainHandStack) && !canBlock(player.offHandStack) &&
            Slots.OffhandWithHotbar.findClosestSlot(Items.SHIELD) != null

        fun yawDiff(a: Float, b: Float): Float {
            var diff = (a - b) % 360f
            if (diff >= 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return kotlin.math.abs(diff)
        }

        val rotation = Rotation.lookingAt(
            point = inflictedHit.arrowEntity.pos,
            from = mc.player!!.eyePos
        ).normalize()

        val serverYaw = RotationManager.serverRotation.yaw
        val angleToArrowYaw = rotation.yaw
        val needsRotation = yawDiff(serverYaw, angleToArrowYaw) > 90f

        if (!currentlyBlocking && ticksToImpact <= raiseTicksNeeded) {
            return@handler
        }

        if (needsRotation) {
            RotationManager.setRotationTarget(
                rotationConfigurable.toRotationTarget(rotation, considerInventory = true),
                Priority.IMPORTANT_FOR_USAGE_2,
                this
            )
        }

        val blockHand = determineAndSelectShieldHand(player) ?: run {
            stopBlocking()
            return@handler
        }

        startBlocking(blockHand)
    }

    private fun determineAndSelectShieldHand(player: net.minecraft.entity.player.PlayerEntity): Hand? {
        if (canBlock(player.mainHandStack)) return Hand.MAIN_HAND
        if (canBlock(player.offHandStack)) return Hand.OFF_HAND

        // Try to silently select a hotbar slot containing a shield (works like OffhandWithHotbar used elsewhere).
        val shieldSlot: HotbarItemSlot? = Slots.OffhandWithHotbar.findClosestSlot(Items.SHIELD)
        if (shieldSlot != null) {
            SilentHotbar.selectSlotSilently(this, shieldSlot, 1)
            // after silent selection the shield should be in main hand (hotbar -> main hand)
            return Hand.MAIN_HAND
        }

        return null
    }

    private fun startBlocking(blockHand: Hand) {
        val player = mc.player ?: return
        if (player.isUsingItem && player.activeItem.isConsumable) return
        if (mc.player!!.activeItem?.item?.getUseAction(mc.player!!.activeItem) == UseAction.BLOCK) {
            return
        }
        val actionResult = interaction.interactItem(player, blockHand)

        if (actionResult.isAccepted) {
            mc.options.useKey.isPressed = true
        }
    }

    private fun stopBlocking() {
        val player = mc.player ?: return

        if (mc.player!!.activeItem?.item?.getUseAction(mc.player!!.activeItem) == UseAction.BLOCK) {
            interaction.stopUsingItem(player)
            mc.options.useKey.isPressed = false
        }
    }

    private fun canBlock(itemStack: ItemStack) =
        itemStack.item?.getUseAction(itemStack) == UseAction.BLOCK
}
