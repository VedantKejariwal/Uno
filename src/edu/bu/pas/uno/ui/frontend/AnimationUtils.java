package edu.bu.pas.uno.ui.frontend;

/**
 * Small math helpers for UI motion: pulses, fades, and easing.
 * <p>
 * Animations read wall-clock time here so multiple widgets can share the same rhythm without
 * duplicating trigonometry.
 */
public final class AnimationUtils {

    private AnimationUtils() {
    }

    /**
     * @return a value oscillating roughly between {@code min} and {@code max} using a sine wave
     */
    public static double sinePulse(long millis, double periodMillis, double min, double max) {
        double t = millis / Math.max(1.0, periodMillis) * Math.PI * 2.0;
        double mid = (min + max) / 2.0;
        double amp = (max - min) / 2.0;
        return mid + amp * Math.sin(t);
    }

    /**
     * Normalized pulse in {@code [0,1]} for glow strength.
     */
    public static double pulse01(long millis, double periodMillis) {
        double v = (Math.sin(millis / Math.max(1.0, periodMillis) * Math.PI * 2.0) + 1.0) * 0.5;
        return clamp01(v);
    }

    /**
     * Linear fade from 1 at the start of an effect to 0 at the end.
     *
     * @param progress fraction of animation time elapsed in {@code [0,1]}
     */
    public static double fadeOut(double progress) {
        return clamp01(1.0 - progress);
    }

    /**
     * Maps progress to an alpha multiplier for a 1 second PRD-style overlay.
     */
    public static int alphaForFade(double progress, int maxAlpha) {
        return (int) (fadeOut(progress) * maxAlpha);
    }

    public static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    /**
     * Bounce offset upward in pixels for penalty text (progress 0→1).
     */
    public static int bounceUpPixels(double progress, int maxRise) {
        double t = clamp01(progress);
        // Upward then hold: use inverted parabola peak early
        return (int) (maxRise * (1.0 - (2.0 * t - 1.0) * (2.0 * t - 1.0)));
    }
}
