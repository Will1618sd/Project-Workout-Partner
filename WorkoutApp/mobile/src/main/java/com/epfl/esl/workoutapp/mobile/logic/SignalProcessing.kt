package com.epfl.esl.workoutapp.mobile.logic

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.absoluteValue
import com.epfl.esl.workoutapp.mobile.data.model.ImuSample


data class ProcessedWindow(
    val features: FloatArray, // The 10 features needed for the model
    val rawForCounting: Map<String, DoubleArray> // Data retained for repetition counting
)

// Class: resample at 200ms
class Resampler {
    fun resample(data: List<ImuSample>, intervalMs: Long = 200): List<ImuSample> {
        if (data.isEmpty()) return emptyList()

        val startTime = data.first().timestampMs
        // Group by 200ms buckets
        val grouped = data.groupBy { (it.timestampMs - startTime) / intervalMs }

        return grouped.map { (bucketIndex, records) ->
            ImuSample(
                timestampMs = startTime + (bucketIndex * intervalMs),
                accX = records.map { it.accX.toDouble() }.average().toFloat(),
                accY = records.map { it.accY.toDouble() }.average().toFloat(),
                accZ = records.map { it.accZ.toDouble() }.average().toFloat(),
                gyroX = records.map { it.gyroX.toDouble() }.average().toFloat(),
                gyroY = records.map { it.gyroY.toDouble() }.average().toFloat(),
                gyroZ = records.map { it.gyroZ.toDouble() }.average().toFloat()

            )
        }.sortedBy { it.timestampMs }
    }
}

// Class: low pass filter
class LowPassFilter {
    // Coefficients for Butterworth: Order=5, Fs=5Hz, Fc=1.3Hz
    // Calculated via scipy.signal.butter(5, 1.3/2.5, btype='low')
    private val b = doubleArrayOf(0.0495329964, 0.247664982, 0.495329964, 0.495329964, 0.247664982, 0.0495329964)
    private val a = doubleArrayOf(1.0, -1.822694925, 1.96866597, -1.16462768, 0.38541999, -0.05170747)

    fun filter(data: DoubleArray): DoubleArray {
        // Forward filter
        val forward = lfilter(b, a, data)
        // Backward filter (reverse, filter, reverse)
        val backward = lfilter(b, a, forward.reversedArray())
        return backward.reversedArray()
    }

    private fun lfilter(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val y = DoubleArray(x.size)
        for (i in x.indices) {
            var sum = 0.0
            for (j in b.indices) {
                if (i - j >= 0) sum += b[j] * x[i - j]
            }
            for (j in 1 until a.size) {
                if (i - j >= 0) sum -= a[j] * y[i - j]
            }
            y[i] = sum
        }
        return y
    }
}

// Class: feature extraction
// input: resampled data
// filter, extract norm, extract features (temporal and frequency)
// output: 10 main features
class FeatureExtractor {

    private val filter = LowPassFilter()

    fun preProcessLowPass(resampledData: List<ImuSample>): Map<String, DoubleArray> {
        // CONVERSION HAPPENS HERE: .map { it.value.toDouble() }
        val accX = resampledData.map { it.accX.toDouble() }.toDoubleArray()
        val accY = resampledData.map { it.accY.toDouble() }.toDoubleArray()
        val accZ = resampledData.map { it.accZ.toDouble() }.toDoubleArray()
        val gyroX = resampledData.map { it.gyroX.toDouble() }.toDoubleArray()
        val gyroY = resampledData.map { it.gyroY.toDouble() }.toDoubleArray()
        val gyroZ = resampledData.map { it.gyroZ.toDouble() }.toDoubleArray()


        return mapOf(
            "accX" to filter.filter(accX),
            "accY" to filter.filter(accY),
            "accZ" to filter.filter(accZ),
            "gyroZ" to filter.filter(gyroZ),
            "gyroR" to DoubleArray(resampledData.size) { i ->
                val gx = filter.filter(gyroX)[i]
                val gy = filter.filter(gyroY)[i]
                val gz = filter.filter(gyroZ)[i]
                sqrt(gx*gx + gy*gy + gz*gz)
            }
        )
    }

    fun extractFeaturesAtIndex(resampledData: List<ImuSample>, accX_lp: DoubleArray,
                               accY_lp: DoubleArray, accZ_lp: DoubleArray,
                               gyroZ_lp: DoubleArray, gyroR_lp: DoubleArray, idx: Int): FloatArray {

        // --- Temporal Features (Window 5) ---
        val accY_std = calculateStd(accY_lp, idx, 5)
        val gyroZ_std = calculateStd(gyroZ_lp, idx, 5)

        // --- Frequency Features (Window 14) ---
        // Frequencies of interest: 0.0 Hz (Idx 0), 0.357 Hz (Idx 1), 0.714 Hz (Idx 2)
        val accX_fft = computeRealFFT(accX_lp, idx, 14)
        val accY_fft = computeRealFFT(accY_lp, idx, 14)
        val accZ_fft = computeRealFFT(accZ_lp, idx, 14)
        val gyroZ_fft = computeRealFFT(gyroZ_lp, idx, 14)
        val gyroR_fft = computeRealFFT(gyroR_lp, idx, 14)

        return floatArrayOf(
            accY_std.toFloat(),
            gyroZ_std.toFloat(),
            accY_fft[0].toFloat(), // 0.0 Hz
            accY_fft[2].toFloat(), // 0.714 Hz
            accX_fft[0].toFloat(),
            accX_fft[2].toFloat(),
            accZ_fft[0].toFloat(),
            gyroZ_fft[2].toFloat(),
            gyroR_fft[0].toFloat(),
            gyroR_fft[1].toFloat()  // 0.357 Hz
        )
    }

    // Helper function
    private fun calculateStd(data: DoubleArray, endIndex: Int, ws: Int): Double {
        val slice = data.slice(endIndex - ws + 1..endIndex)
        val mean = slice.average()
        val variance = slice.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    // A simple DFT implementation since N=14 is small. Returns Real part only.
    private fun computeRealFFT(data: DoubleArray, endIndex: Int, ws: Int): DoubleArray {
        val slice = data.slice(endIndex - ws + 1..endIndex)
        val n = slice.size
        // We only need indices 0, 1, 2 for the features requested
        val result = DoubleArray(3)

        for (k in 0 until 3) {
            var sumReal = 0.0
            for (t in 0 until n) {
                val angle = 2 * PI * t * k / n
                sumReal += slice[t] * cos(angle)
            }
            result[k] = sumReal
        }
        return result
    }
}

// Load model
private fun readModelFromAssets(context: Context, fileName: String): ByteArray {
    return context.assets.open(fileName).use { inputStream ->
        inputStream.readBytes()
    }
}

// Function to count repetitions
fun countRepetitions(label: String, data: List<ImuSample>): Int {
    // 1. Select the column and parameters based on the exercise
    // Squat/OHP use Accelerometer X, Row uses Gyroscope X
    val (columnData, cutoffThreshold) = when (label) {
        "squat" -> data.map { it.accX.toDouble() } to 0.35
        "row"   -> data.map { it.gyroX.toDouble() } to 0.65
        "ohp"   -> data.map { it.accX.toDouble() } to 0.35
        else    -> data.map { it.accX.toDouble() } to 0.40 // Default
    }

    // 2. Apply a Heavy Smoother (Replacing the Order 10 LowPass)
    // A 0.4Hz cutoff at 5Hz sampling means a period of ~2.5 seconds.
    // We can use a Moving Average window of ~5-7 samples to mimic this smoothing.
    val smoothed = simpleMovingAverage(columnData, windowSize = 6)

    // 3. Find Peaks
    return countPeaks(smoothed)
}

// Helper: Simple Moving Average (Robust for rep counting)
private fun simpleMovingAverage(data: List<Double>, windowSize: Int): List<Double> {
    if (data.size < windowSize) return data
    return data.windowed(windowSize, 1) { it.average() }
}

// Helper: Peak Counter (Replica of argrelextrema)
private fun countPeaks(data: List<Double>): Int {
    var peaks = 0
    if (data.size < 3) return 0

    // We look for points that are higher than their neighbors
    // And also ensure they are actual peaks (above a local mean or zero)
    // The Python code simply used "greater", so we will do the same.

    for (i in 1 until data.size - 1) {
        val prev = data[i - 1]
        val curr = data[i]
        val next = data[i + 1]

        // Check if it is a local maximum
        if (curr > prev && curr > next) {
            peaks++
        }
    }
    return peaks
}

// Complete processing function
// input: raw data
fun processExerciseSet(context: Context, rawData: List<ImuSample>) : Pair<String, Int> {
    val resampler = Resampler()
    val resampled = resampler.resample(rawData)
    val n = resampled.size

    if (n < 14) {
        println("Need at least 14 samples (2.8 seconds)")
        return Pair("unknown", 0)
    }

    val extractor = FeatureExtractor()

    // Low pass Data
    val lpData = extractor.preProcessLowPass(resampled)

    // Features
    val allFeaturesList = mutableListOf<Float>()
    var validWindows = 0
    for (i in 14 until n) {
        val features = extractor.extractFeaturesAtIndex(resampled, lpData["accX"]!!, lpData["accY"]!!, lpData["accZ"]!!,
            lpData["gyroZ"]!!, lpData["gyroR"]!!, i)
        allFeaturesList.addAll(features.toList())
        validWindows++
    }

    // Batch Prediction
    val env = OrtEnvironment.getEnvironment()
    val modelBytes = readModelFromAssets(context, "activity_classifier.onnx")

    env.createSession(modelBytes, OrtSession.SessionOptions()).use { session ->
        val floatBuffer = FloatBuffer.wrap(allFeaturesList.toFloatArray())

        // Shape is [BatchSize, 10]
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(validWindows.toLong(), 10))

        val result = session.run(mapOf("float_input" to inputTensor))
        @Suppress("UNCHECKED_CAST")
        val labels = result[0].value as Array<String>

        // 3. Find the Majority Vote (Mode)
        val finalLabel = labels.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "unknown"

        // 4. Count Repetitions
        val reps = countRepetitions(finalLabel, resampled)

        println("FINAL PREDICTION: $finalLabel (Votes: ${labels.size}), REPS: $reps")
        return Pair(finalLabel, reps)
    }
}