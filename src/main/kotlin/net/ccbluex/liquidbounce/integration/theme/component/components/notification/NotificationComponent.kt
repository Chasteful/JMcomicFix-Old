package net.ccbluex.liquidbounce.integration.theme.component.components.notification

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent.Severity.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeComponent
import net.ccbluex.liquidbounce.integration.theme.component.components.notification.mode.NovolineMode
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.SoundUtil.playSound
import net.ccbluex.liquidbounce.utils.render.Alignment



object NotificationComponent: NativeComponent(
    "Notification", false, Alignment(
        horizontalAlignment = Alignment.ScreenAxisX.RIGHT,
        horizontalOffset = 5,
        verticalAlignment = Alignment.ScreenAxisY.BOTTOM,
        verticalOffset = 30,
    )
)  {
    init {
        registerComponentListen(this)
    }

    val modes = choices(this, "Mode", NovolineMode, arrayOf(NovolineMode))
    val size by float("Size", 0.6f, 0.5f..1f)
    val backgroundColor by color("Background", Color4b.BLACK.withAlpha(75))

    fun playSuccess() = playSound("sound/notification/success.wav")
    fun playEnabled() = playSound("sound/notification/enable.wav")
    fun playDisable() = playSound("sound/notification/disable.wav")
    fun playError() = playSound("sound/notification/error.wav")
    fun playInfo() = playSound("sound/notification/info.wav")
    fun playBlink() = playSound("sound/notification/blink.wav")
    fun playBlinked() = playSound("sound/notification/blinked.wav")

    @Suppress("unused")
    private val handleNotificationEvent = handler<NotificationEvent> { event ->
        when (event.severity) {
            SUCCESS -> playSuccess()
            ENABLED -> playEnabled()
            DISABLED -> playDisable()
            ERROR -> playError()
            INFO -> playInfo()
            BLINK -> playBlink()
            BLINKED -> playBlinked()
            BLINKING -> return@handler
        }
    }
    override fun onEnabled() {
        modes.activeChoice.enable()
    }

    override fun onDisabled() {
        modes.activeChoice.disable()
    }

}
