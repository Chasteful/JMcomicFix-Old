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

package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.SharedConstants

object ModuleClientTitle: ClientModule("ClientTitle", Category.CLIENT, aliases = listOf("ModifyTitle")) {

    private val originTitle =
        LiquidBounce.CLIENT_NAME + " v" +
        LiquidBounce.clientVersion + " | " + SharedConstants.getGameVersion().name

    private val clientTitle by text("Title", "").onChanged {
        val title = it.ifEmpty {
            originTitle
        }
       if (enabled && !HideAppearance.isHidingNow) {
           mc.window.setTitle(title)
       }
    }
    override fun onEnabled() {
        val title = clientTitle.ifEmpty {
            originTitle
        }
        mc.window.setTitle(title)
    }

    override fun onDisabled() {
        mc.window.setTitle(originTitle)
        super.onDisabled()
    }
}
