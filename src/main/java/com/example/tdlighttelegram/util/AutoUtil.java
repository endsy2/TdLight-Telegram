package com.example.tdlighttelegram.util;

import org.springframework.stereotype.Component;

@Component
public class AutoUtil {
    public static int getDurationSeconds(String filePath) {
        try {
            Process process = new ProcessBuilder(
                    "ffprobe",
                    "-i", filePath,
                    "-show_entries", "format=duration",
                    "-v", "quiet",
                    "-of", "csv=p=0"
            ).start();

            java.util.Scanner s = new java.util.Scanner(process.getInputStream())
                    .useDelimiter("\\A");

            String result = s.hasNext() ? s.next().trim() : "0";

            process.waitFor();

            return (int) Math.round(Double.parseDouble(result));

        } catch (Exception e) {
            return 0;
        }
    }

    // =========================
    // WAVEFORM (Telegram style)
    // =========================
    public static byte[] getWaveform(String filePath) {
        try {
            double[] amplitudes = extractAmplitudes(filePath);
            return buildWaveform(amplitudes, 96);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // =========================
    // INTERNAL: AMPLITUDES
    // =========================
    private static double[] extractAmplitudes(String filePath) throws Exception {
        Process process = new ProcessBuilder(
                "ffmpeg",
                "-i", filePath,
                "-f", "s16le",
                "-ac", "1",
                "-ar", "8000",
                "-"
        ).redirectErrorStream(true).start();

        java.io.InputStream is = process.getInputStream();
        java.util.List<Double> amplitudes = new java.util.ArrayList<>();

        byte[] buffer = new byte[2048];
        int read;

        while ((read = is.read(buffer)) != -1) {
            for (int i = 0; i < read - 1; i += 2) {
                short sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
                amplitudes.add((double) Math.abs(sample));
            }
        }

        process.waitFor();

        return amplitudes.stream().mapToDouble(d -> d).toArray();
    }

    // =========================
    // INTERNAL: WAVEFORM BUILDER
    // =========================
    private static byte[] buildWaveform(double[] amplitudes, int size) {
        byte[] waveform = new byte[size];

        double max = 1;
        for (double a : amplitudes) {
            if (a > max) max = a;
        }

        int blockSize = Math.max(1, amplitudes.length / size);

        for (int i = 0; i < size; i++) {
            int start = i * blockSize;
            int end = Math.min(start + blockSize, amplitudes.length);

            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += amplitudes[j];
            }

            double avg = sum / Math.max(1, (end - start));

            int value = (int) ((avg / max) * 31);
            waveform[i] = (byte) Math.min(31, value);
        }

        return waveform;
    }
}
