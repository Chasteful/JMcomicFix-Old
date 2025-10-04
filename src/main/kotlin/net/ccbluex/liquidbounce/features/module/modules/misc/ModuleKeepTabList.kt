package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.inGame

object ModuleKeepTabList : ClientModule("KeepTabList", Category.MISC) {


    @Suppress("Unused","UnusedPrivateProperty")
    private val handler = tickHandler {
        if (!inGame){
            return@tickHandler
        }
        mc.options.playerListKey.isPressed = true
    }

    override fun onDisabled() {
        mc.options.playerListKey.isPressed = false
        super.onDisabled()
    }

}
