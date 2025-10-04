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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule

/**
 * Reach module
 *
 * Increases your reach.
 */

object ModuleReach : ClientModule("Reach", Category.PLAYER) {
    val combatReach by float("CombatReach", 3f, 3f..8f).apply { tagBy(this) }
    val blockReach by float("BlockReach", 4.7f, 4.5f..8f)
    val verticalExempt by boolean("VerticalExempt",false)

    @JvmStatic
    fun getExemptBlockReach(pitch: Float): Double {
        return when {
            pitch <= 70 -> 4.5
            pitch >= 83 -> 6.0
            else -> 5.0 + (pitch - 70) / (83 - 70)
        }
    }

}
