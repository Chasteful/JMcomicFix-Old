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

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.render.GenericSyncColorMode
import net.ccbluex.liquidbounce.render.engine.type.Color4b

object ModulePacketQueue : ClientModule("PacketQueueColor", Category.RENDER, disableActivation = true) {

    val colorMode = choices("ColorMode", 2) {
        arrayOf(
            GenericStaticColorMode(it, Color4b(0, 128, 255, 255)),
            GenericRainbowColorMode(it),
            GenericSyncColorMode(it),
        )
    }

    val lineAlpha by int("LineAlpha", 255, 0..255)
    val modelAlpha by int("ModelAlpha", 255, 0..255)
    val modelOutlineAlpha by int("ModelOutlineAlpha", 255, 0..255)
}
