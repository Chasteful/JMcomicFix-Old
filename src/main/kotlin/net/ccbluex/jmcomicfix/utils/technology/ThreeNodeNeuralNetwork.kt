package net.ccbluex.jmcomicfix.utils.technology

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
@Suppress("NestedBlockDepth","CognitiveComplexMethod")
class ThreeNodeNeuralNetwork(private var hiddenSize: Int) {
    companion object {
        val INSTANCE = ThreeNodeNeuralNetwork(8)
    }

    private lateinit var w1: Array<DoubleArray>
    private lateinit var b1: DoubleArray
    private lateinit var w2: Array<DoubleArray>
    private lateinit var b2: DoubleArray

    init {
        initializeWeights()
        train(InterpolationAlgorithms.inputs, InterpolationAlgorithms.inputs, 1000, 0.01)
    }

    private fun initializeWeights() {
        w1 = Array(2) { DoubleArray(hiddenSize) }
        b1 = DoubleArray(hiddenSize)

        for (i in 0 until 2) {
            for (j in 0 until hiddenSize) {
                w1[i][j] = (Math.random() - 0.5) * sqrt(1.0)
            }
        }

        w2 = Array(hiddenSize) { DoubleArray(2) }
        b2 = DoubleArray(2)

        for (i in 0 until hiddenSize) {
            for (j in 0 until 2) {
                w2[i][j] = (Math.random() - 0.5) * sqrt(2.0 / hiddenSize)
            }
        }
    }

    fun predict(yawDiff: Double, pitchDiff: Double): DoubleArray {
        val hidden = DoubleArray(hiddenSize)

        for (j in 0 until hiddenSize) {
            hidden[j] = w1[0][j] * yawDiff + w1[1][j] * pitchDiff + b1[j]
            hidden[j] = max(0.0, hidden[j])
        }

        val output = DoubleArray(2)

        for (k in 0 until 2) {
            for (j in 0 until hiddenSize) {
                output[k] += hidden[j] * w2[j][k]
            }
            output[k] += b2[k]
        }

        return output
    }

    fun train(inputs: Array<DoubleArray>, targets: Array<DoubleArray>, epochs: Int, lr: Double) {
        for (epoch in 0 until epochs) {
            var totalLoss = 0.0

            for (n in inputs.indices) {
                val hidden = DoubleArray(hiddenSize)
                val hiddenPreAct = DoubleArray(hiddenSize)

                for (j in 0 until hiddenSize) {
                    hiddenPreAct[j] = w1[0][j] * inputs[n][0] + w1[1][j] * inputs[n][1] + b1[j]
                    hidden[j] = max(0.0, hiddenPreAct[j])
                }

                val output = DoubleArray(2)

                for (k in 0 until 2) {
                    for (j in 0 until hiddenSize) {
                        output[k] += hidden[j] * w2[j][k]
                    }
                    output[k] += b2[k]
                }

                var loss = 0.0
                for (k in 0 until 2) {
                    loss += (output[k] - targets[n][k]).pow(2.0)
                }
                totalLoss += loss / 2.0

                val outputGrad = DoubleArray(2)
                for (k in 0 until 2) {
                    outputGrad[k] = 2.0 * (output[k] - targets[n][k])
                }

                for (k in 0 until hiddenSize) {
                    for (j in 0 until 2) {
                        w2[k][j] -= lr * hidden[k] * outputGrad[j]
                    }
                }

                for (k in 0 until 2) {
                    b2[k] -= lr * outputGrad[k]
                }

                val hiddenGrad = DoubleArray(hiddenSize)
                for (j in 0 until hiddenSize) {
                    for (k in 0 until 2) {
                        hiddenGrad[j] += w2[j][k] * outputGrad[k]
                    }
                    hiddenGrad[j] *= if (hiddenPreAct[j] > 0.0) 1.0 else 0.0
                }

                for (i in 0 until 2) {
                    for (j in 0 until hiddenSize) {
                        w1[i][j] -= lr * inputs[n][i] * hiddenGrad[j]
                    }
                }

                for (j in 0 until hiddenSize) {
                    b1[j] -= lr * hiddenGrad[j]
                }
            }

            if (epoch % 100 == 0) {
                println("Epoch $epoch, Loss: ${"%.4f".format(totalLoss / inputs.size)}")
            }
        }
    }
}
