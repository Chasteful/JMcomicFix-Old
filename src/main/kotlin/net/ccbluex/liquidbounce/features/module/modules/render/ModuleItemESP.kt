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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.CleanupPlanGenerator
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ItemSlotType
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ModuleInventoryCleaner
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.font.FontRendererBuffers
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.ccbluex.liquidbounce.utils.inventory.findNonEmptySlotsInInventory
import net.ccbluex.liquidbounce.utils.math.average
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.minecraft.entity.Entity
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items.*
import net.minecraft.util.math.Box

/**
 * ItemESP module
 *
 * Allows you to see dropped items through walls.
 */

object ModuleItemESP : ClientModule("ItemESP", Category.RENDER) {
    private val fontRenderer
        get() = FontManager.FONT_RENDERER

    override val baseKey: String
        get() = "liquidbounce.module.itemEsp"
    private val useInvManager by boolean("UseInvManager", true)
    private val modes = choices("Mode", NametagsMode,
        arrayOf(GlowMode, OutlineMode, BoxMode, NametagsMode))

    private val colorMode = choices("ColorMode", 2) {
        arrayOf(
            GenericStaticColorMode(it, Color4b(255, 179, 72, 255)),
            GenericRainbowColorMode(it),
            GenericSyncColorMode(it),
            GenericItemRarityColorMode(it),
        )
    }

    private val defaultItems = setOf(
        WHEAT_SEEDS,
        PUMPKIN_SEEDS,
        MELON_SEEDS,
        BEETROOT_SEEDS,

        DANDELION,
        POPPY,
        BLUE_ORCHID,
        ALLIUM,
        AZURE_BLUET,
        RED_TULIP,
        ORANGE_TULIP,
        WHITE_TULIP,
        PINK_TULIP,
        OXEYE_DAISY,
        CORNFLOWER,
        LILY_OF_THE_VALLEY,
        SUNFLOWER,
        LILAC,
        ROSE_BUSH,
        PEONY,
        SUGAR_CANE,
        KELP,
        BAMBOO,
        CACTUS,
        VINE,
        SEAGRASS,
        SWEET_BERRIES,
        DRIED_KELP,
        DEAD_BUSH,
    )
    val filteredItems by items(
        "FilterItems",
        defaultItems.toMutableSet()
    )

    private object NametagsMode : Choice("Nametags") {
        override val parent: ChoiceConfigurable<Choice>
            get() = modes
        private val scale by float("Scale", 1f, 0.5f..4f)
        private val clusterRadius by float("ClusterRadius", 2f, 0.5f..3f)
        private val maximumDistance by float("MaximumDistance", 50f, 1f..256f)
        private val number by boolean("Number", true)
        private val vanillaName by boolean("VanillaName", false)

        @Suppress("unused")
        val renderHandler = handler<OverlayRenderEvent> { event ->
            val camera = mc.cameraEntity ?: return@handler

            val items = world.entities.filterIsInstance<ItemEntity>()
                .filter(::shouldRender)
                .filter { it.squaredDistanceTo(camera) <= maximumDistance * maximumDistance }

            renderEnvironmentForGUI {
                val groupedByItem = items.groupBy { it.stack.item }
                for ((item, sameItems) in groupedByItem) {
                    val maxRarity = sameItems.maxOf { it.stack.rarity }
                    val bestItemsInGroup = sameItems.filter { it.stack.rarity == maxRarity }
                    val clusters = computeItemClusters(bestItemsInGroup, event.tickDelta, clusterRadius)
                    for (cluster in clusters) {
                        val clusterItems = cluster.items
                        if (clusterItems.isEmpty()) continue
                        val totalCount = clusterItems.sumOf { it.stack.count }
                        val bestStack = clusterItems.first().stack

                        val centerPos = clusterItems.map { it.interpolateCurrentPosition(event.tickDelta) }.average()
                        val screenPos = WorldToScreen.calculateScreenPos(
                            centerPos.add(0.0, clusterItems.first().height + 0.25, 0.0)
                        ) ?: continue

                        matrixStack.push()
                        matrixStack.translate(screenPos.x, screenPos.y, 0f)

                        val distanceScale = mc.player?.let {
                            (1f / (it.pos.distanceTo(centerPos) * 0.1f)).coerceIn(0.5, 2.0) / 5 * scale
                        } ?: scale

                        matrixStack.scale(distanceScale.toFloat(), distanceScale.toFloat(), 1f)
                        val displayName = if (vanillaName) {
                            bestStack.name.string
                        } else {
                            bestStack.itemName.string
                        }
                        val text = if (number && totalCount > 1) "$displayName x$totalCount" else displayName

                        val processed = fontRenderer.process(text)
                        val textWidth = fontRenderer.getStringWidth(processed)
                        val textHeight = fontRenderer.height

                        val itemColor = this@ModuleItemESP.colorMode.activeChoice.getColor(bestStack)

                        val fontBuffers = FontRendererBuffers()
                        fontRenderer.draw(
                            processed,
                            -textWidth / 2f,
                            -textHeight / 2f,
                            shadow = true,
                            z = 0.001f
                        )
                        fontRenderer.commit(this, fontBuffers)
                        withColor(itemColor) {
                            fontBuffers.draw()
                        }

                        matrixStack.pop()
                    }
                }
            }
        }


        private data class ClusteredItems(val items: List<ItemEntity>)

        @Suppress("NestedBlockDepth","CognitiveComplexMethod")
        private fun computeItemClusters(
            items: List<ItemEntity>,
            tickDelta: Float,
            radius: Float
        ): List<ClusteredItems> {
            val visited = mutableSetOf<ItemEntity>()
            val clusters = mutableListOf<ClusteredItems>()
            val radiusSquared = radius * radius

            for (item in items) {
                if (item in visited) continue
                val cluster = mutableListOf<ItemEntity>()
                val queue = ArrayDeque<ItemEntity>()
                queue.add(item)
                visited.add(item)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    val currentPos = current.interpolateCurrentPosition(tickDelta)
                    cluster.add(current)

                    for (other in items) {
                        if (other in visited) continue
                        val otherPos = other.interpolateCurrentPosition(tickDelta)
                        if (currentPos.squaredDistanceTo(otherPos) <= radiusSquared) {
                            visited.add(other)
                            queue.add(other)
                        }
                    }
                }
                if (cluster.size > 1) {
                    clusters.add(ClusteredItems(cluster))
                } else {
                    clusters.add(ClusteredItems(cluster))
                }
            }

            return clusters
        }
    }

    private object BoxMode : Choice("Box") {

        override val parent: ChoiceConfigurable<Choice>
            get() = modes

        private val box = Box(-0.125, 0.125, -0.125, 0.125, 0.375, 0.125)

        @Suppress("unused")
        val renderHandler = handler<WorldRenderEvent> { event ->
            val matrixStack = event.matrixStack

            val base = getColor()
            val baseColor = base.with(a = 50)
            val outlineColor = base.with(a = 100)

            val filtered = world.entities.filter(::shouldRender)

            renderEnvironmentForWorld(matrixStack) {
                drawBoxes {
                    for (entity in filtered) {
                        val pos = entity.interpolateCurrentPosition(event.partialTicks)

                        withPositionRelativeToCamera(pos) {
                            drawBox(box, baseColor, outlineColor)
                        }
                    }
                }
            }
        }
    }

    object GlowMode : Choice("Glow") {
        override val parent: ChoiceConfigurable<Choice>
            get() = modes
    }

    object OutlineMode : Choice("Outline") {
        override val parent: ChoiceConfigurable<Choice>
            get() = modes
    }

    private fun ItemStack.filterPlanGenerator(): Boolean {
        val template = ModuleInventoryCleaner.cleanupTemplateFromSettings

        val invSlots: List<ItemSlot> = findNonEmptySlotsInInventory()

        val tempSlot = VirtualItemSlot(
            itemStack = this,
            slotType = ItemSlotType.CONTAINER,
            id = hashCode()
        )

        val available = ArrayList<ItemSlot>(invSlots.size + 1).apply {
            addAll(invSlots)
            add(tempSlot)
        }

        val plan = CleanupPlanGenerator(template, available).generatePlan()

        return plan.usefulItems.any { ItemStack.areEqual(it.itemStack, this) }
    }
    fun shouldRender(entity: Entity?): Boolean {
        if (entity !is ItemEntity) return false
        if (entity.stack.item in filteredItems) return false
        if (useInvManager && !entity.stack.filterPlanGenerator()) return false
        return true
    }

    fun getColor() = this.colorMode.activeChoice.getColor(null)
}
