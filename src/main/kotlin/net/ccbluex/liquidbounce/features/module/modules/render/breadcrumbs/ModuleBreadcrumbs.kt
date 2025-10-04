package net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.modes.DashTrailMode
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.modes.SectorMode
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.modes.SparkleMode
import net.ccbluex.liquidbounce.features.module.modules.render.breadcrumbs.modes.TrailMode
import net.ccbluex.liquidbounce.render.GenericCustomColorMode
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.render.GenericSyncColorMode
import net.ccbluex.liquidbounce.render.engine.type.Color4b

object ModuleBreadcrumbs : ClientModule("Breadcrumbs", Category.RENDER, aliases = listOf("PlayerTrails")) {

    override val baseKey: String
        get() = "liquidbounce.module.breadcrumbs"

    val modes = choices(
        "Mode", SparkleMode, arrayOf(
            TrailMode,SparkleMode,SectorMode,DashTrailMode
        )
    )

    val colorMode = choices("ColorMode", 0) {
        arrayOf(
            GenericSyncColorMode(it),
            GenericCustomColorMode(it, Color4b.LIQUID_BOUNCE, Color4b.CYAN),
            GenericStaticColorMode(it, Color4b.CYAN),
            GenericRainbowColorMode(it),
        )
    }

    override fun onEnabled() {
        modes.activeChoice.enable()
    }

    override fun onDisabled() {
        modes.activeChoice.disable()
    }
}
