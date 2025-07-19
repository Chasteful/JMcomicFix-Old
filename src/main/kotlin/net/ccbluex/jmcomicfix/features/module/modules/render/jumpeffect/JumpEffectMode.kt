package net.ccbluex.jmcomicfix.features.module.modules.render.jumpeffect

import net.ccbluex.liquidbounce.config.types.Choice

abstract class JumpEffectMode(
    name: String
) : Choice(name) {
    override val parent
        get() = ModuleJumpEffect.modes
}
