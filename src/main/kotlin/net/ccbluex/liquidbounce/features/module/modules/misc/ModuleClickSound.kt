package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SoundUtil.playSound
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetMouseButton


object ModuleClickSound: ClientModule("ClickSound", Category.MISC) {
    fun playClickSound() {
        if (running && MinecraftClient.getInstance().currentScreen == null) {
            playSound("sound/click/standard.wav")
        }
    }

    private var lastLeft = false
    private var lastRight = false

    @Suppress("unused")
    private val handler = tickHandler {
        val window = MinecraftClient.getInstance().window.handle

        val leftNow = glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS
        val rightNow = glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS

        if (leftNow && !lastLeft) playClickSound()
        if (rightNow && !lastRight) playClickSound()

        lastLeft = leftNow
        lastRight = rightNow
    }
}
