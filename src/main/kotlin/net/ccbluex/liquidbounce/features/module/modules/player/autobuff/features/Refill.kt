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
 *
 *
 */

package net.ccbluex.liquidbounce.features.module.modules.player.autobuff.features

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.features.module.modules.player.autobuff.ModuleAutoBuff
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getPotionEffects
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.PotionItem

object Refill : ToggleableConfigurable(ModuleAutoBuff, "Refill", true) {

    private val inventoryConstraints = tree(PlayerInventoryConstraints())

    @Suppress("LongMethod")
    fun execute(event: ScheduleInventoryActionEvent) {
        // Check if we have space in the hotbar
        val player = mc.player ?: return
        val speedEffect = player.getStatusEffect(StatusEffects.SPEED)
        val fireResistanceEffect = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE)

        val needSpeedRefill = !(speedEffect != null && speedEffect.duration > 600)
        val needFireRefill = !(fireResistanceEffect != null && fireResistanceEffect.duration > 600)

        val hasSpeedInHotbar = Slots.OffhandWithHotbar.any {
            val stack = it.itemStack
            !stack.isEmpty && stack.item is PotionItem &&
                stack.getPotionEffects().any { effect -> effect.effectType == StatusEffects.SPEED }
        }

        val hasFireResistanceInHotbar = Slots.OffhandWithHotbar.any {
            val stack = it.itemStack
            !stack.isEmpty && stack.item is PotionItem &&
                stack.getPotionEffects().any { effect -> effect.effectType == StatusEffects.FIRE_RESISTANCE }
        }

        val speedPotions = Slots.Inventory.filter {
            val itemStack = it.itemStack
            itemStack.item is PotionItem &&
                itemStack.getPotionEffects().any { effect -> effect.effectType == StatusEffects.SPEED }
        }

        val fireResistancePotions = Slots.Inventory.filter {
            val itemStack = it.itemStack
            itemStack.item is PotionItem &&
                itemStack.getPotionEffects().any { effect -> effect.effectType == StatusEffects.FIRE_RESISTANCE }
        }

        if (needSpeedRefill) {
            handlePotionRefill(speedPotions, hasSpeedInHotbar, event)
            if (!hasSpeedInHotbar) {
                speedPotions.firstOrNull()?.let { speedSlot ->
                    event.schedule(
                        inventoryConstraints, InventoryAction.Click.performQuickMove(slot = speedSlot),
                        Priority.IMPORTANT_FOR_USAGE_1
                    )
                }
            }
        }

        if (needFireRefill) {
            handlePotionRefill(fireResistancePotions, hasFireResistanceInHotbar, event)
            if (!hasFireResistanceInHotbar) {
                fireResistancePotions.firstOrNull()?.let { fireResistanceSlot ->
                    event.schedule(
                        inventoryConstraints, InventoryAction.Click.performQuickMove(slot = fireResistanceSlot),
                        Priority.IMPORTANT_FOR_USAGE_1
                    )
                }
            }
        }

        if (!findEmptyHotbarSlot()) {
            return
        }

        val validFeatures = ModuleAutoBuff.activeFeatures

        // Find valid items in the inventory
        val otherValidItems = Slots.Inventory.filter { slot ->
            val itemStack = slot.itemStack
            validFeatures.any { f -> f.isValidItem(itemStack, false) } &&
                !speedPotions.contains(slot) && !fireResistancePotions.contains(slot)
        }

        // Check if we have any valid items
        if (otherValidItems.isEmpty()) {
            return
        }

        // Sort the items by the order of the features
        for (slot in otherValidItems) {
            event.schedule(
                inventoryConstraints, InventoryAction.Click.performQuickMove(slot = slot),
                Priority.IMPORTANT_FOR_USAGE_1
            )
        }
    }
    private fun handlePotionRefill(
        potions: List<ItemSlot>,
        hasInHotbar: Boolean,
        event: ScheduleInventoryActionEvent
    ) {
        if (potions.isEmpty() || hasInHotbar || findEmptyHotbarSlot()) return

        val nonRelevantHotbar = Slots.OffhandWithHotbar.firstOrNull {
            val stack = it.itemStack
            !stack.isEmpty && !(stack.item is PotionItem && stack.getPotionEffects()
                .any { effect -> effect.effectType == StatusEffects.SPEED ||
                    effect.effectType == StatusEffects.FIRE_RESISTANCE })
        }

        nonRelevantHotbar?.let { slot ->
            event.schedule(
                inventoryConstraints,
                InventoryAction.Click.performQuickMove(slot = slot),
                Priority.IMPORTANT_FOR_USAGE_1
            )
        } ?: return
    }

    private fun findEmptyHotbarSlot(): Boolean {
        return Slots.OffhandWithHotbar.findSlot { it.isEmpty } != null
    }

}
