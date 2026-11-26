package com.omega.core

/** Versioned boundary for later neural modules, not a claim of implemented models. */
object Architecture {
    const val schema = 1
    const val main = 824
    const val intermediate = 32
    const val final = 2
    const val agents = 160
    const val layers = 16
    const val width = 2048
    const val total = main + intermediate + final
    const val hiddenUnits = total.toLong() * layers * width
    const val denseHiddenWeights = total.toLong() * (layers - 1) * width * width
}
data class FrameMetadata(val sessionId: String, val timestampNs: Long, val width: Int, val height: Int, val rotation: Int)
data class ModelDescriptor(val id: String, val domain: String, val hiddenLayers: Int, val hiddenWidth: Int,
                           val materialized: Boolean, val trained: Boolean)
