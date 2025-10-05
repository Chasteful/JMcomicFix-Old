package net.ccbluex.jmcomicfix.utils.aiming.features.processors

import net.ccbluex.jmcomicfix.utils.technology.SixNodeNineStateNeuralNetwork
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.processors.RotationProcessor
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil

class NeuralNetworkRotationProcessor(owner: EventListener? = null) : ToggleableConfigurable(
    owner, "6-9NN", false), RotationProcessor {

    private val learningRate by float("LearningRate", 0.01f, 0.001f..0.1f)
    private val momentum by float("Momentum", 0.9f, 0.8f..0.99f)
    private val stateTransitionRate by float("StateTransitionRate", 0.05f, 0.01f..0.1f)
    private val neuralNetwork = SixNodeNineStateNeuralNetwork().apply {
        learningRate = this@NeuralNetworkRotationProcessor.learningRate.toDouble()
        momentum = this@NeuralNetworkRotationProcessor.momentum.toDouble()
        stateTransitionRate = this@NeuralNetworkRotationProcessor.stateTransitionRate.toDouble()
    }

    private var lastServerRotation: Rotation? = null

    override fun process(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation
    ): Rotation {
        lastServerRotation = RotationManager.serverRotation
        if (!this.running || rotationTarget.entity == null) {
            lastServerRotation = null
            return targetRotation
        }

        val entity = rotationTarget.entity!!
        val player = mc.player ?: return targetRotation
        val serverRotation = RotationManager.serverRotation

        val yawDiff = RotationUtil.angleDifference(targetRotation.yaw, serverRotation.yaw).toDouble()
        val pitchDiff = RotationUtil.angleDifference(targetRotation.pitch, serverRotation.pitch).toDouble()

        val distance = player.distanceTo(entity)
        val distanceFactor = (1.0 - (distance / 10.0).coerceIn(0.0, 1.0))

        val adjustments = neuralNetwork.predict(yawDiff, pitchDiff, distanceFactor)

        return Rotation(
            targetRotation.yaw + (adjustments[0].toFloat()),
            targetRotation.pitch + (adjustments[1].toFloat())
        )
    }


    fun onAttack(actualRotation: Rotation) {
        if (!running) return

        lastServerRotation?.let { predicted ->
            val yawError = (actualRotation.yaw - predicted.yaw).toDouble()
            val pitchError = (actualRotation.pitch - predicted.pitch).toDouble()
            neuralNetwork.train(yawError, pitchError)
        }
        lastServerRotation = null
    }
}
