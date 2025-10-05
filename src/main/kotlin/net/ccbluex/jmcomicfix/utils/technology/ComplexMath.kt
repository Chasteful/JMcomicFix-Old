@file:Suppress("TooManyFunctions")
package net.ccbluex.jmcomicfix.utils.technology

import net.ccbluex.jmcomicfix.utils.tuples.Pair
import net.ccbluex.liquidbounce.utils.math.Vec2i
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.*

object ComplexMath {
    fun getAngleInDegrees(delta: Vec2i): Double {
        val angleInRadians = atan2(delta.x.toDouble(), delta.y.toDouble())
        var angleInDegrees = Math.toDegrees(angleInRadians)
        if (angleInDegrees < 0.0) {
            angleInDegrees += 360.0
        }
        return angleInDegrees
    }

    fun getMax(p: FloatArray): Double {
        return p.maxOrNull()?.toDouble() ?: -2.147483647E9
    }

    fun getOutliers(collection: Collection<Number>): Pair<List<Double>, List<Double>> {
        val values = collection.map { it.toDouble() }
        val q1 = getMedian(values.subList(0, values.size / 2))
        val q3 = getMedian(values.subList(values.size / 2, values.size))
        val iqr = abs(q1 - q3)
        val lowThreshold = q1 - 1.5 * iqr
        val highThreshold = q3 + 1.5 * iqr
        val lowOutliers = mutableListOf<Double>()
        val highOutliers = mutableListOf<Double>()
        values.forEach { value ->
            when {
                value < lowThreshold -> lowOutliers.add(value)
                value > highThreshold -> highOutliers.add(value)
            }
        }
        return Pair.of(lowOutliers, highOutliers)
    }

    fun getGCD(s: Double): Float {
        val f1 = (s * 0.6 + 0.2).toFloat()
        return f1 * f1 * f1 * 8.0f
    }

    fun getGCDValue(s: Double): Float {
        return getGCD(s) * 0.15f
    }

    fun getZScoreOutliers(data: Collection<Float>, threshold: Float): List<Float> {
        val mean = getAverage(data).toFloat()
        val stdDev = getStandardDeviation(data).toFloat()
        return data.filter { number ->
            val zScore = (number - mean) / stdDev
            abs(zScore) > threshold
        }
    }

    fun getEquidistantSequence(length: Int, p: Float): FloatArray {
        return FloatArray(length) { i -> (i + 1) * p }
    }

    fun linearRegression(x: List<Number>, y: List<Number>): DoubleArray {
        val n = x.size
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        x.zip(y).forEach { (xi, yi) ->
            val xVal = xi.toDouble()
            val yVal = yi.toDouble()
            sumX += xVal
            sumY += yVal
            sumXY += xVal * yVal
            sumX2 += xVal * xVal
        }
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        return doubleArrayOf(slope, intercept)
    }

    fun getJiffDelta(data: List<Number>, depth: Int): List<Float> {
        var result = data.map { it.toFloat() }
        repeat(depth) {
            val calculate = mutableListOf<Float>()
            var old = Float.MIN_VALUE
            result.forEach { n ->
                if (old == Float.MIN_VALUE) {
                    old = n
                } else {
                    calculate.add(abs(abs(n) - abs(old)))
                    old = n
                }
            }
            result = calculate
        }
        return result
    }

    fun getShannonEntropy(data: Collection<Number>): Double {
        val total = data.size.toDouble()
        val freqMap = data.groupingBy { it.toDouble() }.eachCount()
        return -freqMap.values.sumOf { count ->
            (count / total) * (ln(count / total) / ln(2.0))
        }
    }

    fun hypot(x: Double, z: Double): Double {
        return sqrt(x * x + z * z)
    }

    fun magnitude(x: Double, y: Double, z: Double): Double {
        return sqrt(x * x + y * y + z * z)
    }

    fun distanceBetweenAngles(alpha: Float, beta: Float): Float {
        val alphaX = alpha % 360f
        val betaX = beta % 360f
        val delta = abs(alphaX - betaX)
        return abs(min(360f - delta, delta))
    }

    fun getVariance(data: Collection<Number>): Double {
        val average = getAverage(data)
        return data.sumOf { number -> (number.toDouble() - average).pow(2) }
    }

    fun getStandardDeviation(data: Collection<Number>): Double {
        return sqrt(getVariance(data))
    }

    fun getSkewness(data: Collection<Number>): Double {
        val numbers = data.map { it.toDouble() }.sorted()
        val count = numbers.size
        val mean = numbers.average()
        val median = if (count % 2 != 0) {
            numbers[count / 2]
        }
        else {
            (numbers[(count - 1) / 2] + numbers[count / 2]) / 2.0
        }
        val variance = getVariance(data)
        return 3.0 * (mean - median) / variance
    }

    fun toArrayList(t: Array<Double>): List<Double> {
        return t.toList()
    }

    fun getAverageDelta(data: Collection<Number>): Double {
        if (data.isEmpty()) return 0.0
        var sum = 0.0
        var ls = 0.0
        var first = true
        data.forEach { number ->
            if (first) {
                ls = number.toDouble()
                first = false
            } else {
                sum += number.toDouble() - ls
                ls = number.toDouble()
            }
        }
        return sum / (data.size - 1)
    }

    fun interpolate(newValue: Float, oldValue: Float, partialTicks: Float): Float {
        return oldValue + (newValue - oldValue) * partialTicks
    }

    fun interpolate(newValue: Double, oldValue: Double, partialTicks: Float): Double {
        return oldValue + (newValue - oldValue) * partialTicks
    }

    fun interpolate(newValue: Double, oldValue: Double, partialTicks: Double): Double {
        return oldValue + (newValue - oldValue) * partialTicks
    }

    fun getAverage(data: Collection<Number>?): Double {
        return data?.takeIf { it.isNotEmpty() }?.sumOf { it.toDouble() }?.div(data.size) ?: 0.0
    }

    fun getKurtosis(data: Collection<Number>): Double {
        val count = data.size
        if (count < 3) return 0.0
        val average = getAverage(data)
        val variance = data.sumOf { (average - it.toDouble()).pow(2) }
        val varianceSquared = data.sumOf { (average - it.toDouble()).pow(4) }
        val efficiencyFirst = count * (count + 1.0) / ((count - 1.0) * (count - 2.0) * (count - 3.0))
        val efficiencySecond = 3.0 * (count - 1.0).pow(2) / ((count - 2.0) * (count - 3.0))
        return efficiencyFirst * (varianceSquared / (variance / count).pow(2)) - efficiencySecond
    }

    fun getMode(array: Collection<Number>): Int {
        var mode = array.first().toInt()
        var maxCount = 0
        array.forEach { value ->
            val count = array.count { it == value }
            if (count > maxCount) {
                mode = value.toInt()
                maxCount = count
            }
        }
        return mode
    }

    private fun getMedian(data: List<Double>): Double {
        return if (data.size % 2 == 0) {
            (data[data.size / 2] + data[data.size / 2 - 1]) / 2.0
        } else {
            data[data.size / 2]
        }
    }

    fun isExponentiallySmall(number: Number): Boolean {
        val value = number.toDouble()
        return value < 1.0 && value.toString().contains("E")
    }

    fun getGcd(current: Long, previous: Long): Long {
        return if (previous <= 16384L) current else getGcd(previous, current % previous)
    }

    fun gcd(limit: Double, a: Double, b: Double): Double {
        return if (b <= limit) a else gcd(limit, b, a % b)
    }

    fun getGcd(a: Double, b: Double): Double {
        return if (a < b) {
            getGcd(b, a)
        } else if (abs(b) < 0.001) {
            a
        } else {
            getGcd(b, a - floor(a / b) * b)
        }
    }

    fun angleOf(minX: Double, minZ: Double, maxX: Double, maxZ: Double): Double {
        val deltaY = minZ - maxZ
        val deltaX = maxX - minX
        val result = Math.toDegrees(atan2(deltaY, deltaX))
        return if (result < 0.0) 360.0 + result else result
    }

    fun getDistanceBetweenAngles360(alpha: Double, beta: Double): Double {
        val abs = abs(alpha % 360.0 - beta % 360.0)
        return min(360.0 - abs, abs)
    }

    fun getDistanceBetweenAngles360Raw(alpha: Double, beta: Double): Double {
        return abs(alpha % 360.0 - beta % 360.0)
    }

    fun getCps(data: Collection<Number>): Double {
        return 20.0 / getAverage(data) * 50.0
    }

    fun getDuplicates(data: Collection<Number>): Int {
        return data.size - data.distinct().size
    }

    fun getDistinct(data: Collection<Number>): Int {
        return data.distinct().size
    }
}
