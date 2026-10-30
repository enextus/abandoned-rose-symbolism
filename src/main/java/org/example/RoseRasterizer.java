package org.example;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Supersampled, band-parallel, deferred triangle rasterizer.
 *
 * <p>The output square is cut into horizontal bands rendered independently on the common
 * fork-join pool. A band first rasterizes its binned triangles in a fixed order with exact
 * fixed-point edge functions (top-left fill rule), keeping only depth, triangle and barycentric
 * weights per sample. Only the visible samples are then shaded: attributes are interpolated, the
 * key-light shadow map is filtered per sample, the result is tone-mapped and stylised. Each pixel
 * averages {@code ss x ss} samples in linear light and is composited over the Java2D background.
 * Because bands never share mutable state the image is bit-identical on any number of cores.
 */
final class RoseRasterizer {
    private static final int SUB = 16;
    private static final int BAND_ROWS = 16;
    private static final float[] SRGB_TO_LINEAR = new float[256];
    private static final float[] LINEAR_TO_SRGB = new float[8193];
    private static final float[] BAYER = new float[16];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4));
        }
        for (int i = 0; i < LINEAR_TO_SRGB.length; i++) {
            double l = i / 8192.0;
            LINEAR_TO_SRGB[i] = (float) (255 * (l <= .0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - .055));
        }
        int[] m = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        for (int i = 0; i < 16; i++) BAYER[i] = (m[i] + .5f) / 16f - .5f;
    }

    /** Everything the rasterizer needs about one frame. Arrays are indexed by vertex. */
    static final class Frame {
        int side, ss;
        RoseDrawing.Style style;
        float[] sx, sy, sz;
        /** Lighting per vertex and side (6 floats per vertex), see {@link RoseLighting.Shaded}. */
        float[] base, key, shadowPos;
        RoseLighting.ShadowMap keyMap;
        /** Two texture coordinates per vertex: (u, v), or metric (hu, hv) for engraving. */
        float[] ta, tb;
        byte[] material;
        int[] part;
        int[] tri;
        int triangles;
        /** Engraving lines per world unit. */
        float hatch;
        /** Background in sRGB, {@code side * side}; also the destination. */
        int[] background;
        /** Optional per-pixel nearest depth / surface id (for dew placement). */
        float[] depthOut;
        int[] idOut;
    }

    private static final class Buffers {
        float[] depth = new float[0], w1 = new float[0], w2 = new float[0];
        int[] tid = new int[0];

        void ensure(int n) {
            if (depth.length >= n) return;
            depth = new float[n]; w1 = new float[n]; w2 = new float[n]; tid = new int[n];
        }
    }

    private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

    private RoseRasterizer() { }

    static int outlineRadius(RoseDrawing.Style style, int ss) {
        return switch (style) {
            case ART_NOUVEAU -> Math.max(2, Math.round(1.25f * ss));
            case ENGRAVING -> Math.max(1, Math.round(.75f * ss));
            case FADED_INK -> Math.max(1, ss / 2);
            case VELVET -> 0;
        };
    }

    /** Renders the frame over (and into) {@code f.background}. */
    static void render(Frame f) {
        int side = f.side, ss = f.ss, width = side * ss;
        int bandSamples = BAND_ROWS * ss;
        int guard = outlineRadius(f.style, ss) + 1;
        int bands = (side + BAND_ROWS - 1) / BAND_ROWS;
        int[] fx = new int[f.sx.length], fy = new int[f.sy.length];
        for (int i = 0; i < fx.length; i++) {
            fx[i] = Math.round(f.sx[i] * SUB);
            fy[i] = Math.round(f.sy[i] * SUB);
        }
        // Bin triangles to bands (two passes keep a stable order).
        int[] count = new int[bands + 1];
        int[] first = new int[f.triangles], last = new int[f.triangles];
        for (int t = 0; t < f.triangles; t++) {
            int a = f.tri[3 * t], b = f.tri[3 * t + 1], c = f.tri[3 * t + 2];
            int minY = Math.min(fy[a], Math.min(fy[b], fy[c])), maxY = Math.max(fy[a], Math.max(fy[b], fy[c]));
            int minX = Math.min(fx[a], Math.min(fx[b], fx[c])), maxX = Math.max(fx[a], Math.max(fx[b], fx[c]));
            int top = Math.floorDiv(Math.floorDiv(minY, SUB) - guard, bandSamples);
            int bottom = Math.floorDiv(Math.floorDiv(maxY, SUB) + guard, bandSamples);
            if (maxX < 0 || minX >= width * SUB || bottom < 0 || top >= bands) { first[t] = 1; continue; }
            first[t] = Math.max(0, top);
            last[t] = Math.min(bands - 1, bottom);
            for (int k = first[t]; k <= last[t]; k++) count[k + 1]++;
        }
        for (int k = 0; k < bands; k++) count[k + 1] += count[k];
        int[] bins = new int[count[bands]], fill = Arrays.copyOf(count, bands);
        for (int t = 0; t < f.triangles; t++)
            for (int k = first[t]; k <= last[t]; k++) bins[fill[k]++] = t;

        IntStream.range(0, bands).parallel().forEach(band ->
                renderBand(f, band, fx, fy, bins, count[band], count[band + 1], guard));
    }

    private static void renderBand(Frame f, int band, int[] fx, int[] fy, int[] bins, int from, int to, int guard) {
        int ss = f.ss, side = f.side, width = side * ss;
        int y0 = band * BAND_ROWS * ss - guard;
        int outRows = Math.min(BAND_ROWS, side - band * BAND_ROWS);
        int rows = outRows * ss + 2 * guard;
        Buffers buf = BUFFERS.get();
        buf.ensure(rows * width);
        int n = rows * width;
        Arrays.fill(buf.depth, 0, n, Float.POSITIVE_INFINITY);
        Arrays.fill(buf.tid, 0, n, -1);
        for (int k = from; k < to; k++) rasterTriangle(f, bins[k], fx, fy, buf, y0, rows, width);
        resolve(f, band, buf, y0, outRows, width);
    }

    private static void rasterTriangle(Frame f, int t, int[] fx, int[] fy, Buffers buf, int y0, int rows, int width) {
        int i0 = f.tri[3 * t], i1 = f.tri[3 * t + 1], i2 = f.tri[3 * t + 2];
        long area = (long) (fx[i1] - fx[i0]) * (fy[i2] - fy[i0]) - (long) (fy[i1] - fy[i0]) * (fx[i2] - fx[i0]);
        if (area == 0) return;
        boolean front = area < 0;
        int j1 = front ? i2 : i1, j2 = front ? i1 : i2;
        int x0 = fx[i0], yA = fy[i0], x1 = fx[j1], yB = fy[j1], x2 = fx[j2], yC = fy[j2];
        int minX = Math.max(0, Math.floorDiv(Math.min(x0, Math.min(x1, x2)) - SUB / 2 + SUB - 1, SUB));
        int maxX = Math.min(width - 1, Math.floorDiv(Math.max(x0, Math.max(x1, x2)) - SUB / 2, SUB));
        int minY = Math.max(y0, Math.floorDiv(Math.min(yA, Math.min(yB, yC)) - SUB / 2 + SUB - 1, SUB));
        int maxY = Math.min(y0 + rows - 1, Math.floorDiv(Math.max(yA, Math.max(yB, yC)) - SUB / 2, SUB));
        if (minX > maxX || minY > maxY) return;

        // Edge functions E_ab(p) = (xb-xa)(py-ya) - (yb-ya)(px-xa); inside when all >= 0 (+ top-left bias).
        long e0dx = -(long) (yB - yA) * SUB, e0dy = (long) (x1 - x0) * SUB;
        long e1dx = -(long) (yC - yB) * SUB, e1dy = (long) (x2 - x1) * SUB;
        long e2dx = -(long) (yA - yC) * SUB, e2dy = (long) (x0 - x2) * SUB;
        long px = (long) minX * SUB + SUB / 2, py = (long) minY * SUB + SUB / 2;
        long row0 = (long) (x1 - x0) * (py - yA) - (long) (yB - yA) * (px - x0) + (topLeft(x1 - x0, yB - yA) ? 0 : -1);
        long row1 = (long) (x2 - x1) * (py - yB) - (long) (yC - yB) * (px - x1) + (topLeft(x2 - x1, yC - yB) ? 0 : -1);
        long row2 = (long) (x0 - x2) * (py - yC) - (long) (yA - yC) * (px - x2) + (topLeft(x0 - x2, yA - yC) ? 0 : -1);
        float invArea = 1f / Math.abs(area);

        // Depth plane in sample units, relative to vertex 0.
        float ax = x0 / (float) SUB, ay = yA / (float) SUB;
        float dx1 = x1 / (float) SUB - ax, dy1 = yB / (float) SUB - ay, dx2 = x2 / (float) SUB - ax, dy2 = yC / (float) SUB - ay;
        float inv = 1f / (dx1 * dy2 - dx2 * dy1);
        float zA = f.sz[i0], z1 = f.sz[j1] - zA, z2 = f.sz[j2] - zA;
        float zdx = (z1 * dy2 - z2 * dy1) * inv, zdy = (z2 * dx1 - z1 * dx2) * inv;
        int tid = (t << 1) | (front ? 0 : 1);
        float[] depth = buf.depth, bw1 = buf.w1, bw2 = buf.w2;
        int[] ids = buf.tid;

        for (int y = minY; y <= maxY; y++, row0 += e0dy, row1 += e1dy, row2 += e2dy) {
            long w0 = row0, w1 = row1, w2 = row2;
            float zRow = zA + zdy * (y + .5f - ay) + zdx * (minX + .5f - ax);
            int base = (y - y0) * width;
            for (int x = minX; x <= maxX; x++, w0 += e0dx, w1 += e1dx, w2 += e2dx, zRow += zdx) {
                if ((w0 | w1 | w2) < 0) continue;
                int k = base + x;
                if (zRow >= depth[k]) continue;
                depth[k] = zRow;
                ids[k] = tid;
                // Weights of the triangle's original vertices i1 and i2.
                float l1 = w2 * invArea, l2 = w0 * invArea;
                bw1[k] = front ? l2 : l1;
                bw2[k] = front ? l1 : l2;
            }
        }
    }

    private static boolean topLeft(int dx, int dy) {
        return dy < 0 || (dy == 0 && dx > 0);
    }

    private static float tri(float x) {
        float fr = x - (float) Math.floor(x);
        return Math.abs(2 * fr - 1);
    }

    private static int surfaceId(Frame f, int tid) {
        return tid < 0 ? -1 : (f.part[f.tri[3 * (tid >> 1)]] << 1) | (tid & 1);
    }

    private static boolean isOutline(Frame f, Buffers buf, int k, int width, int radius, float tolerance) {
        int id = surfaceId(f, buf.tid[k]);
        float z = buf.depth[k] + tolerance;
        for (int d = 1; d <= radius; d++) {
            if (differs(f, buf, k - d, id, z) || differs(f, buf, k + d, id, z)
                    || differs(f, buf, k - d * width, id, z) || differs(f, buf, k + d * width, id, z)) return true;
        }
        return false;
    }

    private static boolean differs(Frame f, Buffers buf, int p, int id, float z) {
        int other = buf.tid[p];
        return other < 0 || (surfaceId(f, other) != id && buf.depth[p] > z);
    }

    private static void resolve(Frame f, int band, Buffers buf, int y0, int outRows, int width) {
        int ss = f.ss, side = f.side;
        int radius = outlineRadius(f.style, ss);
        float invSamples = 1f / (ss * ss);
        float[] sample = new float[3];
        boolean faded = f.style == RoseDrawing.Style.FADED_INK;
        int[] dst = f.background;
        for (int oy = 0; oy < outRows; oy++) {
            int py = band * BAND_ROWS + oy;
            for (int px = 0; px < side; px++) {
                float sr = 0, sg = 0, sb = 0, nearest = Float.POSITIVE_INFINITY;
                int covered = 0, nearestTid = -1;
                for (int sy = 0; sy < ss; sy++) {
                    int k = (py * ss + sy - y0) * width + px * ss;
                    for (int sx = 0; sx < ss; sx++, k++) {
                        if (buf.tid[k] < 0) continue;
                        covered++;
                        shadeSample(f, buf, k, width, radius, sample);
                        sr += sample[0]; sg += sample[1]; sb += sample[2];
                        if (buf.depth[k] < nearest) { nearest = buf.depth[k]; nearestTid = buf.tid[k]; }
                    }
                }
                int o = py * side + px;
                if (f.depthOut != null) { f.depthOut[o] = nearest; f.idOut[o] = surfaceId(f, nearestTid); }
                if (covered == 0 && !faded) continue;
                int bg = dst[o];
                float keep = 1 - covered * invSamples;
                float lr = sr * invSamples + SRGB_TO_LINEAR[(bg >>> 16) & 255] * keep;
                float lg = sg * invSamples + SRGB_TO_LINEAR[(bg >>> 8) & 255] * keep;
                float lb = sb * invSamples + SRGB_TO_LINEAR[bg & 255] * keep;
                float dither = BAYER[(py & 3) * 4 + (px & 3)];
                float r = encode(lr), g = encode(lg), b = encode(lb);
                if (faded) {
                    float l = .299f * r + .587f * g + .114f * b;
                    r = 20.4f + .86f * 1.03f * (l + (r - l) * .45f);
                    g = 20.4f + .86f * (l + (g - l) * .45f);
                    b = 20.4f + .86f * .93f * (l + (b - l) * .45f);
                }
                dst[o] = 0xFF000000 | (clamp255(r + dither) << 16) | (clamp255(g + dither) << 8) | clamp255(b + dither);
            }
        }
    }

    private static float encode(float linear) {
        float l = linear <= 0 ? 0 : linear >= 1 ? 1 : linear;
        return LINEAR_TO_SRGB[(int) (l * 8192 + .5f)];
    }

    private static int clamp255(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : Math.min(255, i);
    }

    /** Interpolates, shadows, tone-maps and stylises one visible sample into display-linear rgb. */
    private static void shadeSample(Frame f, Buffers buf, int k, int width, int radius, float[] out) {
        int tid = buf.tid[k], t = tid >> 1, side = 3 * (tid & 1);
        int i0 = f.tri[3 * t], i1 = f.tri[3 * t + 1], i2 = f.tri[3 * t + 2];
        float l1 = buf.w1[k], l2 = buf.w2[k], l0 = 1 - l1 - l2;
        int a0 = 6 * i0 + side, a1 = 6 * i1 + side, a2 = 6 * i2 + side;
        float[] p = f.shadowPos, base = f.base, key = f.key;
        float vis = f.keyMap.visibility4(l0 * p[a0] + l1 * p[a1] + l2 * p[a2],
                l0 * p[a0 + 1] + l1 * p[a1 + 1] + l2 * p[a2 + 1], l0 * p[a0 + 2] + l1 * p[a1 + 2] + l2 * p[a2 + 2]);
        float r = l0 * base[a0] + l1 * base[a1] + l2 * base[a2] + vis * (l0 * key[a0] + l1 * key[a1] + l2 * key[a2]);
        float g = l0 * base[a0 + 1] + l1 * base[a1 + 1] + l2 * base[a2 + 1]
                + vis * (l0 * key[a0 + 1] + l1 * key[a1 + 1] + l2 * key[a2 + 1]);
        float b = l0 * base[a0 + 2] + l1 * base[a1 + 2] + l2 * base[a2 + 2]
                + vis * (l0 * key[a0 + 2] + l1 * key[a1 + 2] + l2 * key[a2 + 2]);
        float ta = l0 * f.ta[i0] + l1 * f.ta[i1] + l2 * f.ta[i2], tb = l0 * f.tb[i0] + l1 * f.tb[i1] + l2 * f.tb[i2];
        byte mat = f.material[i0];
        if (f.style != RoseDrawing.Style.ENGRAVING) {
            float mod = texture(mat, ta, tb);
            r *= mod; g *= mod; b *= mod;
        }
        out[0] = r; out[1] = g; out[2] = b;
        RoseLighting.toneMap(out);
        r = out[0]; g = out[1]; b = out[2];
        switch (f.style) {
            case VELVET -> { }
            case ENGRAVING -> engraving(f, buf, k, width, radius, r, g, b, ta, tb, out);
            case ART_NOUVEAU -> {
                if (isOutline(f, buf, k, width, radius, .035f)) { out[0] = .028f; out[1] = .010f; out[2] = .022f; return; }
                float lum = .2126f * r + .7152f * g + .0722f * b;
                float tone = (float) Math.sqrt(lum);
                float level = tone < .17f ? .09f : tone < .36f ? .27f : tone < .58f ? .47f : .74f;
                float gain = Math.min(9f, level * level / Math.max(lum, 1e-4f));
                out[0] = Math.min(1, r * gain); out[1] = Math.min(1, g * gain); out[2] = Math.min(1, b * gain);
            }
            case FADED_INK -> {
                float ink = isOutline(f, buf, k, width, radius, .035f) ? .55f : 1f;
                out[0] = r * ink; out[1] = g * ink; out[2] = b * ink;
            }
        }
    }

    /** Fine surface texture: fanning petal veins, leaf midrib and lateral veins. */
    private static float texture(byte mat, float u, float v) {
        if (mat == RoseMesh.PETAL || mat == RoseMesh.FALLEN) {
            float vein = Math.min(tri(v * 11f + u * .9f), tri(v * 23f - u * 1.7f + .37f) + .08f);
            return 1 - .055f * Math.max(0, .16f - vein) / .16f * (.25f + .75f * u);
        }
        if (mat == RoseMesh.LEAF) {
            float av = Math.abs(v);
            if (av < .035f) return 1.35f;
            float lateral = tri(u * 9f - av * 1.1f);
            return lateral < .07f ? .80f : 1f;
        }
        return 1f;
    }

    private static void engraving(Frame f, Buffers buf, int k, int width, int radius, float r, float g, float b,
                                  float along, float across, float[] out) {
        if (isOutline(f, buf, k, width, radius, .035f)) { out[0] = .045f; out[1] = .034f; out[2] = .030f; return; }
        // Engravers read value, not luminance: a lit red petal is light, so judge by the strongest channel.
        float value = .6f * Math.max(r, Math.max(g, b)) + .4f * (.2126f * r + .7152f * g + .0722f * b);
        float tone = (float) Math.sqrt(value);
        float dark = Math.max(0, Math.min(1, (.70f - tone) / .55f));
        boolean ink = dark > .03f && tri(across * f.hatch) < .02f + .66f * dark;
        if (!ink && dark > .55f) ink = tri(along * f.hatch * .9f) < (dark - .55f) * 1.2f;
        if (ink) { out[0] = .060f; out[1] = .045f; out[2] = .040f; return; }
        float wash = .62f;
        out[0] = .90f * (1 - wash) + wash * Math.min(1, .20f + 1.15f * r);
        out[1] = .85f * (1 - wash) + wash * Math.min(1, .20f + 1.15f * g);
        out[2] = .74f * (1 - wash) + wash * Math.min(1, .20f + 1.15f * b);
    }
}
