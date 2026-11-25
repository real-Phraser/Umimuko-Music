use biquad::{Biquad, Coefficients, DirectForm2Transposed, ToHertz, Type};
use serde::{Deserialize, Serialize};
use std::f32::consts::FRAC_PI_2;

/// Number of equalizer bands.
pub const EQ_BAND_COUNT: usize = 10;

/// Center frequencies (Hz) for the 10-band peaking equalizer.
pub const EQ_FREQUENCIES: [f32; EQ_BAND_COUNT] = [
    60.0, 170.0, 310.0, 600.0, 1000.0, 3000.0, 6000.0, 12000.0, 14000.0, 16000.0,
];

/// Default Q factor for constant-Q 10-band octave-ish peaking EQ.
pub const EQ_DEFAULT_Q: f32 = 1.41;

/// Minimum and maximum preamp gain in dB.
pub const PREAMP_MIN_DB: f32 = -15.0;
pub const PREAMP_MAX_DB: f32 = 15.0;

/// Minimum and maximum per-band gain in dB.
pub const BAND_GAIN_MIN_DB: f32 = -12.0;
pub const BAND_GAIN_MAX_DB: f32 = 12.0;

/// Minimum and maximum crossfade duration in seconds.
pub const CROSSFADE_MIN_SECS: f32 = 0.0;
pub const CROSSFADE_MAX_SECS: f32 = 12.0;

/// Convert decibels to linear amplitude multiplier.
#[inline]
pub fn db_to_linear(db: f32) -> f32 {
    10.0_f32.powf(db / 20.0)
}

/// Clamp preamp gain to `-15.0..=15.0` dB.
#[inline]
pub fn clamp_preamp_db(db: f32) -> f32 {
    db.clamp(PREAMP_MIN_DB, PREAMP_MAX_DB)
}

/// Clamp band gain to `-12.0..=12.0` dB.
#[inline]
pub fn clamp_band_gain_db(db: f32) -> f32 {
    db.clamp(BAND_GAIN_MIN_DB, BAND_GAIN_MAX_DB)
}

/// Built-in 10-band equalizer presets.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum EqPreset {
    Flat,
    BassBoost,
    Rock,
    Pop,
    Jazz,
    Classical,
    Vocal,
    Electronic,
    Acoustic,
    Club,
}

impl EqPreset {
    /// All 10 built-in presets in display order.
    pub const ALL: [EqPreset; 10] = [
        EqPreset::Flat,
        EqPreset::BassBoost,
        EqPreset::Rock,
        EqPreset::Pop,
        EqPreset::Jazz,
        EqPreset::Classical,
        EqPreset::Vocal,
        EqPreset::Electronic,
        EqPreset::Acoustic,
        EqPreset::Club,
    ];

    /// Human-readable preset name.
    pub fn name(self) -> &'static str {
        match self {
            EqPreset::Flat => "Flat",
            EqPreset::BassBoost => "Bass Boost",
            EqPreset::Rock => "Rock",
            EqPreset::Pop => "Pop",
            EqPreset::Jazz => "Jazz",
            EqPreset::Classical => "Classical",
            EqPreset::Vocal => "Vocal",
            EqPreset::Electronic => "Electronic",
            EqPreset::Acoustic => "Acoustic",
            EqPreset::Club => "Club",
        }
    }

    /// Look up a built-in preset by name (case-insensitive).
    pub fn from_name(name: &str) -> Option<Self> {
        let lower = name.trim().to_ascii_lowercase();
        Self::ALL
            .iter()
            .copied()
            .find(|p| p.name().to_ascii_lowercase() == lower)
    }

    /// 10-band gains in dB (`-12.0..=12.0`) for this preset.
    pub fn gains(self) -> [f32; EQ_BAND_COUNT] {
        match self {
            EqPreset::Flat => [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
            EqPreset::BassBoost => [6.5, 5.5, 4.0, 2.0, 0.5, 0.0, 0.0, 0.0, 0.5, 1.0],
            EqPreset::Rock => [4.5, 3.5, 1.5, -0.5, -1.5, 0.5, 2.5, 4.0, 4.5, 4.5],
            EqPreset::Pop => [-1.5, 1.0, 3.5, 4.5, 3.0, 0.5, -1.0, -1.0, 1.5, 2.5],
            EqPreset::Jazz => [3.0, 2.0, 1.0, 2.0, -1.5, -1.5, 0.0, 1.5, 2.5, 3.0],
            EqPreset::Classical => [4.0, 3.0, 2.0, 1.0, -1.0, -1.0, 0.0, 2.0, 3.0, 3.5],
            EqPreset::Vocal => [-2.5, -1.5, 0.5, 2.5, 4.5, 4.5, 3.0, 1.0, 0.0, -1.0],
            EqPreset::Electronic => [5.0, 4.0, 1.5, -0.5, -1.5, 1.5, 1.0, 2.5, 4.0, 5.0],
            EqPreset::Acoustic => [4.0, 3.0, 1.5, 0.5, 1.5, 1.5, 2.5, 3.0, 2.5, 1.5],
            EqPreset::Club => [0.0, 0.0, 3.0, 4.5, 4.5, 4.5, 3.0, 1.0, 0.0, 0.0],
        }
    }

    /// Recommended preamp gain in dB (`-15.0..=15.0`) to prevent clipping.
    pub fn preamp_db(self) -> f32 {
        match self {
            EqPreset::Flat => 0.0,
            EqPreset::BassBoost => -3.5,
            EqPreset::Rock => -2.5,
            EqPreset::Pop => -2.0,
            EqPreset::Jazz => -1.5,
            EqPreset::Classical => -2.0,
            EqPreset::Vocal => -2.0,
            EqPreset::Electronic => -3.0,
            EqPreset::Acoustic => -2.0,
            EqPreset::Club => -2.5,
        }
    }
}

/// Serializable equalizer configuration.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EqConfig {
    pub enabled: bool,
    pub preamp_db: f32,
    pub band_gains: [f32; EQ_BAND_COUNT],
}

impl Default for EqConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            preamp_db: 0.0,
            band_gains: [0.0; EQ_BAND_COUNT],
        }
    }
}

impl EqConfig {
    /// Build an `EqConfig` from a built-in preset.
    pub fn from_preset(preset: EqPreset) -> Self {
        Self {
            enabled: true,
            preamp_db: preset.preamp_db(),
            band_gains: preset.gains(),
        }
    }
}

fn unity_coefficients() -> Coefficients<f32> {
    Coefficients {
        a1: 0.0,
        a2: 0.0,
        b0: 1.0,
        b1: 0.0,
        b2: 0.0,
    }
}

fn peaking_coefficients(sample_rate: u32, freq_hz: f32, gain_db: f32) -> Coefficients<f32> {
    let clamped_gain = clamp_band_gain_db(gain_db);
    let nyquist = sample_rate as f32 * 0.5;
    if sample_rate == 0 || freq_hz >= nyquist {
        return unity_coefficients();
    }
    Coefficients::<f32>::from_params(
        Type::PeakingEQ(clamped_gain),
        (sample_rate as f32).hz(),
        freq_hz.hz(),
        EQ_DEFAULT_Q,
    )
    .unwrap_or_else(|_| unity_coefficients())
}

/// 10-band biquad peaking equalizer with preamp stage.
pub struct Equalizer {
    sample_rate: u32,
    channels: usize,
    enabled: bool,
    preamp_db: f32,
    preamp_linear: f32,
    band_gains: [f32; EQ_BAND_COUNT],
    coefficients: [Coefficients<f32>; EQ_BAND_COUNT],
    filters: Vec<[DirectForm2Transposed<f32>; EQ_BAND_COUNT]>,
}

impl Equalizer {
    /// Create a new flat equalizer for the given sample rate and channel count.
    pub fn new(sample_rate: u32, channels: usize) -> Self {
        let channels = channels.max(1);
        let band_gains = [0.0; EQ_BAND_COUNT];
        let coefficients = Self::compute_all_coefficients(sample_rate, &band_gains);
        let filters = Self::build_filter_banks(channels, &coefficients);
        Self {
            sample_rate,
            channels,
            enabled: true,
            preamp_db: 0.0,
            preamp_linear: 1.0,
            band_gains,
            coefficients,
            filters,
        }
    }

    fn compute_all_coefficients(
        sample_rate: u32,
        gains: &[f32; EQ_BAND_COUNT],
    ) -> [Coefficients<f32>; EQ_BAND_COUNT] {
        std::array::from_fn(|i| peaking_coefficients(sample_rate, EQ_FREQUENCIES[i], gains[i]))
    }

    fn build_filter_banks(
        channels: usize,
        coefficients: &[Coefficients<f32>; EQ_BAND_COUNT],
    ) -> Vec<[DirectForm2Transposed<f32>; EQ_BAND_COUNT]> {
        (0..channels)
            .map(|_| std::array::from_fn(|b| DirectForm2Transposed::<f32>::new(coefficients[b])))
            .collect()
    }

    /// Reconfigure sample rate and/or channel count, resetting filter delay lines.
    pub fn set_format(&mut self, sample_rate: u32, channels: usize) {
        let channels = channels.max(1);
        if self.sample_rate == sample_rate && self.channels == channels {
            return;
        }
        self.sample_rate = sample_rate;
        self.channels = channels;
        self.coefficients = Self::compute_all_coefficients(sample_rate, &self.band_gains);
        self.filters = Self::build_filter_banks(channels, &self.coefficients);
    }

    pub fn sample_rate(&self) -> u32 {
        self.sample_rate
    }

    pub fn channels(&self) -> usize {
        self.channels
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn preamp_db(&self) -> f32 {
        self.preamp_db
    }

    pub fn set_preamp_db(&mut self, db: f32) {
        self.preamp_db = clamp_preamp_db(db);
        self.preamp_linear = db_to_linear(self.preamp_db);
    }

    pub fn band_gains(&self) -> &[f32; EQ_BAND_COUNT] {
        &self.band_gains
    }

    pub fn coefficients(&self) -> &[Coefficients<f32>; EQ_BAND_COUNT] {
        &self.coefficients
    }

    /// Update a single band's gain in dB (`-12.0..=12.0`).
    pub fn set_band_gain(&mut self, band: usize, gain_db: f32) {
        if band >= EQ_BAND_COUNT {
            return;
        }
        let clamped = clamp_band_gain_db(gain_db);
        self.band_gains[band] = clamped;
        let coeff = peaking_coefficients(self.sample_rate, EQ_FREQUENCIES[band], clamped);
        self.coefficients[band] = coeff;
        for ch in &mut self.filters {
            ch[band].update_coefficients(coeff);
        }
    }

    /// Update all 10 band gains in dB (`-12.0..=12.0`).
    pub fn set_all_bands(&mut self, gains: [f32; EQ_BAND_COUNT]) {
        for (i, &g) in gains.iter().enumerate() {
            self.set_band_gain(i, g);
        }
    }

    /// Apply a built-in preset (both preamp and 10 band gains).
    pub fn apply_preset(&mut self, preset: EqPreset) {
        self.set_preamp_db(preset.preamp_db());
        self.set_all_bands(preset.gains());
    }

    /// Apply an `EqConfig`.
    pub fn apply_config(&mut self, config: &EqConfig) {
        self.set_enabled(config.enabled);
        self.set_preamp_db(config.preamp_db);
        self.set_all_bands(config.band_gains);
    }

    /// Reset internal filter state (e.g. after seeking or track change).
    pub fn reset_state(&mut self) {
        for ch in &mut self.filters {
            for filter in ch.iter_mut() {
                filter.reset_state();
            }
        }
    }

    /// Process interleaved `f32` PCM samples in-place.
    pub fn process_interleaved(&mut self, samples: &mut [f32]) {
        if !self.enabled || samples.is_empty() {
            return;
        }
        let channels = self.channels;
        let preamp = self.preamp_linear;
        for frame in samples.chunks_mut(channels) {
            for (ch_idx, sample) in frame.iter_mut().enumerate() {
                let mut s = *sample * preamp;
                if let Some(bank) = self.filters.get_mut(ch_idx) {
                    for filter in bank.iter_mut() {
                        s = filter.run(s);
                    }
                }
                *sample = s;
            }
        }
    }
}

/// Crossfade curve shape.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum CrossfadeCurve {
    Linear,
    EqualPower,
}

impl Default for CrossfadeCurve {
    fn default() -> Self {
        Self::EqualPower
    }
}

/// Stateless crossfade math helpers (`0.0..=12.0` seconds).
pub struct CrossfadeMath;

impl CrossfadeMath {
    /// Clamp crossfade duration to `0.0..=12.0` seconds.
    #[inline]
    pub fn clamp_duration_secs(secs: f32) -> f32 {
        secs.clamp(CROSSFADE_MIN_SECS, CROSSFADE_MAX_SECS)
    }

    /// Compute `(outgoing_gain, incoming_gain)` for normalized `progress` in `0.0..=1.0`.
    #[inline]
    pub fn gains(curve: CrossfadeCurve, progress: f32) -> (f32, f32) {
        let t = progress.clamp(0.0, 1.0);
        match curve {
            CrossfadeCurve::Linear => (1.0 - t, t),
            CrossfadeCurve::EqualPower => {
                let angle = t * FRAC_PI_2;
                (angle.cos(), angle.sin())
            }
        }
    }

    /// Mix a single outgoing and incoming sample at normalized `progress` (`0.0..=1.0`).
    #[inline]
    pub fn mix_sample(curve: CrossfadeCurve, progress: f32, outgoing: f32, incoming: f32) -> f32 {
        let (g_out, g_in) = Self::gains(curve, progress);
        outgoing * g_out + incoming * g_in
    }

    /// Mix interleaved `outgoing` and `incoming` buffers into `out`.
    pub fn mix_interleaved(
        curve: CrossfadeCurve,
        outgoing: &[f32],
        incoming: &[f32],
        out: &mut [f32],
        start_frame: usize,
        total_fade_frames: usize,
        channels: usize,
    ) {
        if channels == 0 || total_fade_frames == 0 {
            return;
        }
        let frames = out.len() / channels;
        for f in 0..frames {
            let progress = ((start_frame + f) as f32 / total_fade_frames as f32).clamp(0.0, 1.0);
            let (g_out, g_in) = Self::gains(curve, progress);
            let base = f * channels;
            for c in 0..channels {
                let idx = base + c;
                let a = outgoing.get(idx).copied().unwrap_or(0.0);
                let b = incoming.get(idx).copied().unwrap_or(0.0);
                out[idx] = a * g_out + b * g_in;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::f32::consts::FRAC_1_SQRT_2;

    #[test]
    fn test_eq_frequencies_and_clamping() {
        assert_eq!(
            EQ_FREQUENCIES,
            [60.0, 170.0, 310.0, 600.0, 1000.0, 3000.0, 6000.0, 12000.0, 14000.0, 16000.0]
        );
        assert_eq!(clamp_preamp_db(-20.0), -15.0);
        assert_eq!(clamp_preamp_db(20.0), 15.0);
        assert_eq!(clamp_preamp_db(3.5), 3.5);
        assert_eq!(clamp_band_gain_db(-18.0), -12.0);
        assert_eq!(clamp_band_gain_db(18.0), 12.0);
        assert_eq!(clamp_band_gain_db(-4.0), -4.0);
    }

    #[test]
    fn test_flat_eq_coefficients_are_unity() {
        let eq = Equalizer::new(44100, 2);
        for coeff in eq.coefficients() {
            assert!((coeff.b0 - 1.0).abs() < 1e-5, "b0={}", coeff.b0);
            assert!((coeff.b1 - coeff.a1).abs() < 1e-5);
            assert!((coeff.b2 - coeff.a2).abs() < 1e-5);
        }
    }

    #[test]
    fn test_peaking_eq_coefficients_change_with_gain() {
        let mut eq = Equalizer::new(48000, 2);
        let flat_b0 = eq.coefficients()[4].b0;
        eq.set_band_gain(4, 6.0);
        let boosted_b0 = eq.coefficients()[4].b0;
        assert!(boosted_b0 > flat_b0);

        eq.set_band_gain(4, -6.0);
        let cut_b0 = eq.coefficients()[4].b0;
        assert!(cut_b0 < flat_b0);
    }

    #[test]
    fn test_eq_high_band_above_nyquist_falls_back_safely() {
        // At 22050 Hz sample rate, Nyquist is 11025 Hz, so 12k/14k/16k should use unity coefficients.
        let mut eq = Equalizer::new(22050, 1);
        eq.set_band_gain(9, 10.0);
        let c = eq.coefficients()[9];
        assert!((c.b0 - 1.0).abs() < 1e-6);
        assert!((c.a1).abs() < 1e-6);
        assert!((c.a2).abs() < 1e-6);
    }

    #[test]
    fn test_all_ten_presets() {
        assert_eq!(EqPreset::ALL.len(), 10);
        for preset in EqPreset::ALL {
            assert_eq!(EqPreset::from_name(preset.name()), Some(preset));
            let preamp = preset.preamp_db();
            assert!((PREAMP_MIN_DB..=PREAMP_MAX_DB).contains(&preamp));
            for &g in &preset.gains() {
                assert!((BAND_GAIN_MIN_DB..=BAND_GAIN_MAX_DB).contains(&g));
            }
        }
        assert_eq!(EqPreset::Flat.gains(), [0.0; 10]);
        assert_eq!(EqPreset::Flat.preamp_db(), 0.0);
    }

    #[test]
    fn test_equalizer_preamp_and_bypass() {
        let mut eq = Equalizer::new(44100, 2);
        eq.set_preamp_db(6.0);
        let mut buf = [0.25_f32, -0.25_f32, 0.5_f32, -0.5_f32];
        let expected_scale = db_to_linear(6.0);
        eq.process_interleaved(&mut buf);
        assert!((buf[0] - 0.25 * expected_scale).abs() < 1e-4);
        assert!((buf[1] + 0.25 * expected_scale).abs() < 1e-4);

        eq.set_enabled(false);
        let mut raw = [0.1, 0.2, 0.3, 0.4];
        eq.process_interleaved(&mut raw);
        assert_eq!(raw, [0.1, 0.2, 0.3, 0.4]);
    }

    #[test]
    fn test_crossfade_math_linear_and_equal_power() {
        assert_eq!(CrossfadeMath::clamp_duration_secs(-1.0), 0.0);
        assert_eq!(CrossfadeMath::clamp_duration_secs(15.0), 12.0);
        assert_eq!(CrossfadeMath::clamp_duration_secs(4.5), 4.5);

        // Linear
        assert_eq!(CrossfadeMath::gains(CrossfadeCurve::Linear, 0.0), (1.0, 0.0));
        assert_eq!(CrossfadeMath::gains(CrossfadeCurve::Linear, 0.5), (0.5, 0.5));
        assert_eq!(CrossfadeMath::gains(CrossfadeCurve::Linear, 1.0), (0.0, 1.0));

        // EqualPower: cos^2 + sin^2 == 1 at every point
        for step in 0..=10 {
            let t = step as f32 / 10.0;
            let (out_g, in_g) = CrossfadeMath::gains(CrossfadeCurve::EqualPower, t);
            let power = out_g * out_g + in_g * in_g;
            assert!((power - 1.0).abs() < 1e-5, "t={t}, power={power}");
        }
        let (mid_out, mid_in) = CrossfadeMath::gains(CrossfadeCurve::EqualPower, 0.5);
        assert!((mid_out - FRAC_1_SQRT_2).abs() < 1e-5);
        assert!((mid_in - FRAC_1_SQRT_2).abs() < 1e-5);
    }
}
