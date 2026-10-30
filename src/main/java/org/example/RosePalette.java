package org.example;

/**
 * Natural colours of the plant in linear RGB.
 *
 * <p>The appearance's hue shift no longer rotates the whole picture through every hue (which used to
 * produce green roses with purple leaves). It walks a ring of real garden-rose colours instead:
 * crimson, coral, apricot, yellow, ivory, blush, magenta and lavender, while leaves stay green.
 */
final class RosePalette {
    /** Stops: hue position, then heart / body / margin / reverse-side colours (linear RGB). */
    private static final double[][] STOPS = {
            {.000, .070, .001, .006, .300, .0045, .012, .400, .010, .020, .360, .016, .026},   // crimson
            {.125, .520, .060, .040, .780, .170, .095, .900, .330, .200, .820, .260, .170},   // coral
            {.250, .740, .220, .060, .890, .420, .160, .950, .620, .350, .900, .520, .260},   // apricot
            {.375, .820, .400, .020, .880, .610, .080, .950, .800, .300, .900, .700, .180},   // yellow
            {.500, .760, .600, .300, .820, .760, .580, .880, .850, .750, .860, .800, .680},   // ivory
            {.625, .680, .190, .260, .850, .420, .450, .920, .660, .660, .880, .560, .580},   // blush
            {.750, .330, .010, .110, .600, .050, .210, .740, .150, .350, .680, .120, .300},   // magenta
            {.875, .270, .120, .290, .450, .280, .480, .600, .480, .650, .520, .380, .560},   // lavender
            {1.00, .070, .001, .006, .300, .0045, .012, .400, .010, .020, .360, .016, .026},   // crimson
    };

    private RosePalette() { }

    /** Rose colours for a hue position: {@code [heart rgb, body rgb, margin rgb, reverse rgb]}. */
    static double[] roseColours(double hue, double saturation) {
        double h = hue - Math.floor(hue);
        int k = 0;
        while (k < STOPS.length - 2 && h >= STOPS[k + 1][0]) k++;
        double t = (h - STOPS[k][0]) / (STOPS[k + 1][0] - STOPS[k][0]);
        double[] out = new double[12];
        for (int i = 0; i < 12; i++) out[i] = RoseMath.lerp(STOPS[k][i + 1], STOPS[k + 1][i + 1], t);
        for (int c = 0; c < 4; c++) {
            double lum = .2126 * out[3 * c] + .7152 * out[3 * c + 1] + .0722 * out[3 * c + 2];
            for (int i = 0; i < 3; i++) out[3 * c + i] = Math.max(0, lum + (out[3 * c + i] - lum) * saturation);
        }
        return out;
    }

    private static double hash(long seed, int part) {
        long h = seed * 0x9E3779B97F4A7C15L + part * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Albedo of both sides for every vertex (6 floats per vertex, linear RGB). */
    static float[] albedo(RoseMesh m, RoseDrawing.Appearance look, int petalCount) {
        double[] rose = roseColours(look.hueShift(), look.saturation());
        float[] out = new float[6 * m.vertexCount];
        double[] f = new double[3], b = new double[3];
        for (int v = 0; v < m.vertexCount; v++) {
            double u = m.u[v], e = m.edge[v];
            double vary = hash(look.seed(), m.part[v]) - .5;
            switch (m.material[v]) {
                case RoseMesh.PETAL, RoseMesh.FALLEN -> {
                    double inner = m.material[v] == RoseMesh.PETAL ? m.part[v] / (double) Math.max(1, petalCount - 1) : .3;
                    double body = RoseMath.smooth(0, .58, u);
                    for (int c = 0; c < 3; c++) {
                        double heart = rose[c], mid = rose[3 + c], margin = rose[6 + c], reverse = rose[9 + c];
                        double front = RoseMath.lerp(heart, mid, body);
                        front = RoseMath.lerp(front, margin, .45 * e);
                        front = RoseMath.lerp(front, heart, .45 * inner * inner);
                        double rear = RoseMath.lerp(RoseMath.lerp(heart, reverse, .35 + .65 * body), margin, .3 * e);
                        f[c] = front * (1 + .16 * vary);
                        b[c] = rear * (1 + .12 * vary);
                    }
                    if (m.material[v] == RoseMesh.FALLEN) {
                        double age = .30 + .62 * e;
                        double[] brown = {.045, .018, .011};
                        for (int c = 0; c < 3; c++) {
                            f[c] = RoseMath.lerp(f[c] * .42, brown[c], age);
                            b[c] = RoseMath.lerp(b[c] * .42, brown[c], age);
                        }
                    }
                }
                case RoseMesh.LEAF -> {
                    double[] top = {.020, .052, .016}, under = {.058, .092, .044}, blush = {.120, .030, .014};
                    double tint = .55 * e * (.6 + vary);
                    for (int c = 0; c < 3; c++) {
                        f[c] = RoseMath.lerp(top[c] * (1 + .25 * vary), blush[c], tint);
                        b[c] = RoseMath.lerp(under[c], blush[c], .5 * tint);
                    }
                }
                case RoseMesh.SEPAL -> {
                    double tip = RoseMath.smooth(.6, 1, u);
                    double[] in = {.040, .070, .032}, out2 = {.032, .072, .024}, dry = {.060, .046, .020};
                    for (int c = 0; c < 3; c++) {
                        f[c] = RoseMath.lerp(in[c], dry[c], tip * .7);
                        b[c] = RoseMath.lerp(out2[c], dry[c], tip * .6);
                    }
                }
                case RoseMesh.THORN -> {
                    double[] root = {.13, .045, .022}, point = {.28, .17, .085};
                    for (int c = 0; c < 3; c++) f[c] = b[c] = RoseMath.lerp(root[c], point[c], u);
                }
                default -> {
                    double[] green = {.040, .095, .028}, red = {.11, .040, .018};
                    double ruddy = .22 + .2 * StrictMath.sin(m.hu[v] * 2.3 + vary * 6);
                    for (int c = 0; c < 3; c++) f[c] = b[c] = RoseMath.lerp(green[c], red[c], ruddy * .5);
                }
            }
            for (int c = 0; c < 3; c++) {
                out[6 * v + c] = (float) f[c];
                out[6 * v + 3 + c] = (float) b[c];
            }
        }
        return out;
    }
}
