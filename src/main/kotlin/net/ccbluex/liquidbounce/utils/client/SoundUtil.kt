package net.ccbluex.liquidbounce.utils.client

import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

object SoundUtil {
    private val executor = Executors.newSingleThreadExecutor()

    fun playSound(resourcePath: String) {
        executor.submit {
            try {
                val url = javaClass.classLoader.getResource("resources/liquidbounce/$resourcePath")
                    ?: throw IllegalArgumentException("Sound not found: $resourcePath")

                AudioSystem.getAudioInputStream(url).use { audioStream ->
                    val clip: Clip = AudioSystem.getClip()
                    clip.open(audioStream)
                    clip.framePosition = 0
                    clip.start()

                    clip.addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) {
                            clip.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.getLogger("LiquidBounce").warning("Failed to play sound: ${e.message}")
            }
        }
    }
}
