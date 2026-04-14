package com.mad.sensorapp.utils;

/**
 * Feature 15 – FFT Frequency Analysis
 * Pure Java Cooley-Tukey radix-2 in-place FFT.
 * No external libs needed.
 *
 * Usage:
 *   float[] magnitudes = FFTAnalyzer.computeMagnitudes(samples, sampleRateHz);
 *   float dominantHz   = FFTAnalyzer.dominantFrequency(magnitudes, sampleRateHz);
 */
public class FFTAnalyzer {

    /** Compute FFT magnitude spectrum from real input samples.
     *  Input length must be a power of 2. Pads/truncates automatically.
     *  @param samples      raw float samples (e.g. accelerometer magnitude)
     *  @param sampleRateHz sensor sampling rate
     *  @return magnitude array of length N/2 (positive frequencies only)
     */
    public static float[] computeMagnitudes(float[] samples, float sampleRateHz) {
        int N = nextPow2(Math.min(samples.length, 2048));

        double[] re = new double[N];
        double[] im = new double[N];
        for (int i = 0; i < N && i < samples.length; i++) re[i] = samples[i];

        fft(re, im);

        float[] mag = new float[N / 2];
        for (int i = 0; i < N / 2; i++) {
            mag[i] = (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]) / N;
        }
        return mag;
    }

    /** Return the frequency bin (Hz) with the highest magnitude, ignoring DC (bin 0). */
    public static float dominantFrequency(float[] magnitudes, float sampleRateHz) {
        int N  = magnitudes.length * 2;
        int maxIdx = 1;
        float maxVal = 0f;
        for (int i = 1; i < magnitudes.length; i++) {
            if (magnitudes[i] > maxVal) { maxVal = magnitudes[i]; maxIdx = i; }
        }
        return maxIdx * sampleRateHz / N;
    }

    /** Frequency of each magnitude bin in Hz. */
    public static float binFrequency(int binIndex, int totalBins, float sampleRateHz) {
        return binIndex * sampleRateHz / (totalBins * 2);
    }

    // ── Cooley-Tukey in-place FFT ────────────────────────────────────────────
    private static void fft(double[] re, double[] im) {
        int N = re.length;
        // Bit reversal
        for (int i = 1, j = 0; i < N; i++) {
            int bit = N >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tmpR = re[i]; re[i] = re[j]; re[j] = tmpR;
                double tmpI = im[i]; im[i] = im[j]; im[j] = tmpI;
            }
        }
        // Butterfly
        for (int len = 2; len <= N; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < N; i += len) {
                double uRe = 1.0, uIm = 0.0;
                for (int j = 0; j < len / 2; j++) {
                    double evenR = re[i+j],     evenI = im[i+j];
                    double oddR  = re[i+j+len/2], oddI = im[i+j+len/2];
                    double tR    = uRe * oddR - uIm * oddI;
                    double tI    = uRe * oddI + uIm * oddR;
                    re[i+j]         = evenR + tR; im[i+j]         = evenI + tI;
                    re[i+j+len/2]   = evenR - tR; im[i+j+len/2]   = evenI - tI;
                    double newURe = uRe * wRe - uIm * wIm;
                    uIm = uRe * wIm + uIm * wRe;
                    uRe = newURe;
                }
            }
        }
    }

    private static int nextPow2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }
}
