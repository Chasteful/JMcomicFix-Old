package net.ccbluex.jmcomicfix.utils.technology

import kotlin.math.exp
import kotlin.random.Random
@Suppress("NestedBlockDepth","CognitiveComplexMethod")
class SixNodeNineStateNeuralNetwork {
    private val random: Random = Random.Default
    private lateinit var inputToHiddenWeights: Array<DoubleArray>
    private lateinit var hiddenToOutputWeights: Array<DoubleArray>
    private lateinit var hiddenBiases: DoubleArray
    private lateinit var outputBiases: DoubleArray
    private lateinit var stateWeights: Array<Array<DoubleArray>>
    var currentState: Int = 0
        private set
    private var stateTransitionCounter = 0
    var learningRate: Double = 0.01
    var momentum: Double = 0.9
    var stateTransitionRate: Double = 0.05
    var inputLayer: DoubleArray = DoubleArray(3)
    var hiddenLayer: DoubleArray = DoubleArray(6)
    var outputLayer: DoubleArray = DoubleArray(2)

    init {
        this.initializeNetwork()
        this.initializeStates()
    }

    private fun initializeNetwork() {
        this.inputToHiddenWeights = Array(3) { DoubleArray(6) }.also { array ->
            for (i in 0 until 3) {
                for (j in 0 until 6) {
                    array[i][j] = (random.nextDouble() - 0.5) * 0.2
                }
            }
        }
        var j: Int
        var i = 0
        while (i < 3) {
            j = 0
            while (j < 6) {
                this.inputToHiddenWeights[i][j] = (this.random.nextDouble() - 0.5) * 0.2
                ++j
            }
            ++i
        }

        this.hiddenToOutputWeights = Array(6) { DoubleArray(2) }.also { array ->
            for (i in 0 until 6) {
                for (j in 0 until 2) {
                    array[i][j] = (random.nextDouble() - 0.5) * 0.2
                }
            }
        }

        i = 0
        while (i < 6) {
            j = 0
            while (j < 2) {
                this.hiddenToOutputWeights[i][j] = (this.random.nextDouble() - 0.5) * 0.2
                ++j
            }
            ++i
        }

        this.hiddenBiases = DoubleArray(6)
        this.outputBiases = DoubleArray(2)

        i = 0
        while (i < 6) {
            this.hiddenBiases[i] = (this.random.nextDouble() - 0.5) * 0.2
            ++i
        }

        i = 0
        while (i < 2) {
            this.outputBiases[i] = (this.random.nextDouble() - 0.5) * 0.2
            ++i
        }
    }

    private fun initializeStates() {
        stateWeights = Array(9) { Array(6) { DoubleArray(2) } }

        for (s in 0..8) {
            for (i in 0..5) {
                for (j in 0..1) {
                    val baseValue: Double = (this.random.nextDouble() - 0.5) * 0.2
                    val stateModifier = 0.8 + s.toDouble() * 0.05
                    this.stateWeights[s][i][j] = baseValue * stateModifier
                }
            }
        }
    }

    fun predict(yawDiff: Double, pitchDiff: Double, distanceFactor: Double): DoubleArray {
        if (this.random.nextDouble() < this.stateTransitionRate) {
            this.currentState = (this.currentState + 1 + this.random.nextInt(2)) % 9
            this.stateTransitionCounter = 0
        } else {
            ++this.stateTransitionCounter
        }

        this.inputLayer[0] = this.sigmoid(yawDiff / 180.0)
        this.inputLayer[1] = this.sigmoid(pitchDiff / 90.0)
        this.inputLayer[2] = distanceFactor

        var var10000: DoubleArray?
        var j: Int
        var i = 0
        while (i < 6) {
            this.hiddenLayer[i] = 0.0

            j = 0
            while (j < 3) {
                var10000 = this.hiddenLayer
                var10000[i] += this.inputLayer[j] * this.inputToHiddenWeights[j][i]
                ++j
            }

            var10000 = this.hiddenLayer
            var10000[i] += this.hiddenBiases[i]
            this.hiddenLayer[i] = this.sigmoid(this.hiddenLayer[i])
            ++i
        }

        i = 0
        while (i < 2) {
            this.outputLayer[i] = 0.0

            j = 0
            while (j < 6) {
                var10000 = this.outputLayer
                var10000[i] += this.hiddenLayer[j] *
                    (this.hiddenToOutputWeights[j][i] +
                        stateWeights[currentState][j][i])
                ++j
            }

            var10000 = this.outputLayer
            var10000[i] += this.outputBiases[i]
            this.outputLayer[i] = this.outputLayer[i] * 2.0 - 1.0
            ++i
        }

        val precisionFactor = 0.5 + (1.0 - distanceFactor) * 0.5
        var10000 = this.outputLayer
        var10000[0] *= precisionFactor * 15.0 * this.momentum
        var10000 = this.outputLayer
        var10000[1] *= precisionFactor * 10.0 * this.momentum
        return doubleArrayOf(
            outputLayer[0],
            outputLayer[1]
        )
    }

    fun train(yawError: Double, pitchError: Double) {
        val outputErrors = doubleArrayOf(yawError / 180.0, pitchError / 90.0)
        val hiddenErrors = DoubleArray(6)
        var j: Int
        var i = 0
        while (i < 6) {
            hiddenErrors[i] = 0.0

            j = 0
            while (j < 2) {
                hiddenErrors[i] += outputErrors[j] *
                    (this.hiddenToOutputWeights[i][j] + this.stateWeights[this.currentState][i][j])
                ++j
            }

            hiddenErrors[i] *= this.hiddenLayer[i] * (1.0 - this.hiddenLayer[i])
            ++i
        }

        var var10000: DoubleArray
        var delta: Double
        i = 0
        while (i < 6) {
            j = 0
            while (j < 2) {
                delta = this.learningRate * outputErrors[j] * this.hiddenLayer[i]
                var10000 = this.hiddenToOutputWeights[i]
                var10000[j] -= delta
                ++j
            }
            ++i
        }

        i = 0
        while (i < 3) {
            j = 0
            while (j < 6) {
                for (i in 0 until 6) {
                    for (j in 0 until 2) {
                        val delta = learningRate * outputErrors[j] * hiddenLayer[i]
                        hiddenToOutputWeights[i][j] -= delta
                    }
                }
                ++j
            }
            ++i
        }

        i = 0
        while (i < 2) {
            var10000 = this.outputBiases
            var10000[i] -= this.learningRate * outputErrors[i]
            ++i
        }

        i = 0
        while (i < 6) {
            var10000 = this.hiddenBiases
            var10000[i] -= this.learningRate * hiddenErrors[i]
            ++i
        }
    }

    private fun sigmoid(x: Double): Double {
        return 1.0 / (1.0 + exp(-x))
    }

    fun reset() {
        this.initializeNetwork()
        this.currentState = 0
        this.stateTransitionCounter = 0
    }
}
