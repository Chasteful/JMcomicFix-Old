package net.ccbluex.jmcomicfix.utils.aiming.technology

import java.util.Arrays
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object KolmogorovSmirnovTest {
    fun ksTest(dist: NormalDistribution, data: DoubleArray): Double {
        Arrays.sort(data)
        val n = data.size.toDouble()
        var dPlus = 0.0
        var dMinus = 0.0

        var i = 0
        while (i.toDouble() < n) {
            val cdf: Double = dist.cumulativeProbability(data[i])
            dPlus = max(dPlus, (i + 1).toDouble() / n - cdf)
            dMinus = max(dMinus, cdf - i.toDouble() / n)
            ++i
        }

        return max(dPlus, dMinus)
    }

    fun analyzeMachineProbability(data: FloatArray): Double {
        val samples = DoubleArray(data.size)

        for (i in data.indices) {
            samples[i] = data[i].toDouble()
        }

        val mean: Double = Arrays.stream(samples).average().orElse(0.0)
        val stdDev = sqrt(Arrays.stream(samples).map({ x -> (x - mean).pow(2.0) }).average().orElse(0.0))
        val normalized: DoubleArray = Arrays.stream(samples).map({ x -> (x - mean) / stdDev }).toArray()
        val dStatistic = ksTest(NormalDistribution(0.0, 1.0), normalized)
        return sigmoidScale(dStatistic).toDouble()
    }

    private fun sigmoidScale(x: Double): Float {
        return (0.10000000149011612 / (1.0 + exp(-20.0 * (x - 0.04)))).toFloat()
    }
}

