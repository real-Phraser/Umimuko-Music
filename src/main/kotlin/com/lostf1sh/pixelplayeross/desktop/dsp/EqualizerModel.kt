package com.lostf1sh.pixelplayeross.desktop.dsp

enum class CrossfadeCurve {
    LINEAR,
    EQUAL_POWER
}

data class EqualizerPreset(
    val name: String,
    val preamp: Float = 0.0f,
    val bandGains: List<Float>,
    val isBuiltIn: Boolean = false
) {
    init {
        require(bandGains.size == 10) { "Equalizer preset must contain exactly 10 band gains" }
    }
}

data class EqualizerConfig(
    val enabled: Boolean = false,
    val preamp: Float = 0.0f,
    val bands: List<Float> = List(10) { 0.0f },
    val activePreset: String? = "Flat"
) {
    init {
        require(bands.size == 10) { "Equalizer config must contain exactly 10 band gains" }
    }
}

data class CrossfadeConfig(
    val durationSeconds: Float = 0.0f,
    val curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER
) {
    val isGapless: Boolean get() = durationSeconds <= 0.05f
}

data class AudioSettingsState(
    val equalizer: EqualizerConfig = EqualizerConfig(),
    val crossfade: CrossfadeConfig = CrossfadeConfig(),
    val playbackRate: Float = 1.0f
)

object EqualizerDefaults {
    val BAND_FREQUENCIES_HZ = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)

    val BAND_LABELS = listOf("60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz", "14kHz", "16kHz")

    const val MIN_GAIN_DB = -12.0f
    const val MAX_GAIN_DB = 12.0f
    const val MIN_PREAMP_DB = -15.0f
    const val MAX_PREAMP_DB = 15.0f
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2.0f
    const val MAX_CROSSFADE_SECONDS = 12.0f

    val BUILT_IN_PRESETS: List<EqualizerPreset> = listOf(
        EqualizerPreset("Flat", 0.0f, List(10) { 0.0f }, isBuiltIn = true),
        EqualizerPreset("Bass Boost", 1.0f, listOf(6.0f, 5.0f, 4.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), isBuiltIn = true),
        EqualizerPreset("Rock", 0.0f, listOf(4.5f, 3.0f, -1.0f, -2.5f, -1.5f, 1.5f, 3.5f, 4.0f, 4.5f, 4.5f), isBuiltIn = true),
        EqualizerPreset("Pop", 0.0f, listOf(-1.5f, 1.5f, 3.5f, 4.0f, 3.0f, 0.0f, -1.5f, -1.5f, -1.0f, -1.0f), isBuiltIn = true),
        EqualizerPreset("Jazz", 0.0f, listOf(3.0f, 2.0f, 1.0f, 1.5f, -1.5f, -1.5f, 0.0f, 1.5f, 2.5f, 3.0f), isBuiltIn = true),
        EqualizerPreset("Classical", 0.0f, listOf(4.0f, 3.0f, 2.5f, 2.0f, -1.5f, -1.5f, 0.0f, 2.0f, 3.0f, 3.5f), isBuiltIn = true),
        EqualizerPreset("Vocal", 0.5f, listOf(-2.0f, -1.5f, -1.0f, 2.0f, 4.0f, 4.0f, 2.5f, 1.0f, 0.0f, -1.0f), isBuiltIn = true),
        EqualizerPreset("Electronic", 1.0f, listOf(4.0f, 3.5f, 1.0f, 0.0f, -2.0f, 1.5f, 1.0f, 2.5f, 4.0f, 4.5f), isBuiltIn = true),
        EqualizerPreset("Acoustic", 0.0f, listOf(3.5f, 2.5f, 1.5f, 1.0f, 1.0f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f), isBuiltIn = true),
        EqualizerPreset("Club", 1.0f, listOf(0.0f, 0.0f, 2.0f, 3.5f, 3.5f, 3.5f, 2.0f, 0.0f, 0.0f, 0.0f), isBuiltIn = true)
    )

    fun findPreset(name: String, customPresets: List<EqualizerPreset> = emptyList()): EqualizerPreset? {
        return BUILT_IN_PRESETS.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: customPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
