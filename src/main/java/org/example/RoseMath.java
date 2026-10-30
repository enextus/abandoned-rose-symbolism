package org.example;

/** Small, allocation-light math helpers. Matrices are row-major {@code double[9]}. */
final class RoseMath {
    static final double[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    private RoseMath() { }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double clamp(double x, double lo, double hi) { return x < lo ? lo : Math.min(x, hi); }

    static double smoothstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Hermite step from {@code a} to {@code b}; works for descending edges too. */
    static double smooth(double a, double b, double x) { return smoothstep((x - a) / (b - a)); }

    static double[] rotationX(double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        return new double[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    static double[] rotationY(double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        return new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    static double[] rotationZ(double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    static double[] multiply(double[] a, double[] b) {
        double[] m = new double[9];
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                m[3 * r + c] = a[3 * r] * b[c] + a[3 * r + 1] * b[3 + c] + a[3 * r + 2] * b[6 + c];
        return m;
    }

    static double[] apply(double[] m, double x, double y, double z) {
        return new double[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1e-15 ? new double[]{0, 1, 0} : new double[]{x / len, y / len, z / len};
    }

    static double[] normalize(double[] a) { return normalize(a[0], a[1], a[2]); }

    /** ACES filmic curve (Narkowicz fit): HDR scene value to display-linear [0, 1]. */
    static double filmic(double x) {
        if (x <= 0) return 0;
        double y = x * (2.51 * x + .03) / (x * (2.43 * x + .59) + .14);
        return Math.min(1, y);
    }
}
