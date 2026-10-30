package org.example;

import java.util.stream.IntStream;

/**
 * Lighting for the 3D rose: directional shadow maps, sky ambient occlusion and per-vertex shading
 * of both sides of every thin surface (petals are lit from the front, glow when lit from behind).
 *
 * <p>Ambient occlusion only depends on the geometry and is cached with it; shadow maps depend on
 * the light direction relative to the (possibly turned) model and are rebuilt per view.
 */
final class RoseLighting {
    private RoseLighting() { }

    /** Orthographic depth map seen from a directional light. */
    static final class ShadowMap {
        final int size;
        final double[] dir, r1, r2;
        final double minA, minB, scale, texel;
        final float[] depth;

        ShadowMap(float[] px, float[] py, float[] pz, int count, int[] tri, int triangles, double[] lightDir, int size) {
            this.size = size;
            dir = RoseMath.normalize(lightDir);
            double[] ref = Math.abs(dir[1]) < .95 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
            r1 = RoseMath.normalize(RoseMath.cross(ref, dir));
            r2 = RoseMath.cross(dir, r1);
            float[] a = new float[count], b = new float[count], d = new float[count];
            double loA = Double.MAX_VALUE, hiA = -Double.MAX_VALUE, loB = Double.MAX_VALUE, hiB = -Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                double x = px[i], y = py[i], z = pz[i];
                double aa = x * r1[0] + y * r1[1] + z * r1[2], bb = x * r2[0] + y * r2[1] + z * r2[2];
                a[i] = (float) aa; b[i] = (float) bb; d[i] = (float) (x * dir[0] + y * dir[1] + z * dir[2]);
                loA = Math.min(loA, aa); hiA = Math.max(hiA, aa); loB = Math.min(loB, bb); hiB = Math.max(hiB, bb);
            }
            scale = (size - 4) / Math.max(1e-6, Math.max(hiA - loA, hiB - loB));
            texel = 1 / scale;
            minA = loA - 2 * texel; minB = loB - 2 * texel;
            depth = new float[size * size];
            java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);
            float[] tx = new float[count], ty = new float[count];
            for (int i = 0; i < count; i++) {
                tx[i] = (float) ((a[i] - minA) * scale);
                ty[i] = (float) ((b[i] - minB) * scale);
                int ix = (int) tx[i], iy = (int) ty[i], k = iy * size + ix;
                if (d[i] > depth[k]) depth[k] = d[i];
            }
            for (int t = 0; t < triangles; t++) raster(tx, ty, d, tri[3 * t], tri[3 * t + 1], tri[3 * t + 2]);
        }

        /** Conservative depth rasterization keeping the surface nearest to the light (max depth). */
        private void raster(float[] tx, float[] ty, float[] d, int i0, int i1, int i2) {
            float x0 = tx[i0], y0 = ty[i0], x1 = tx[i1], y1 = ty[i1], x2 = tx[i2], y2 = ty[i2];
            float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            if (area > -1e-9f && area < 1e-9f) return;
            float inv = 1f / area;
            int minX = Math.max(0, (int) Math.min(x0, Math.min(x1, x2))), maxX = Math.min(size - 1, (int) Math.max(x0, Math.max(x1, x2)));
            int minY = Math.max(0, (int) Math.min(y0, Math.min(y1, y2))), maxY = Math.min(size - 1, (int) Math.max(y0, Math.max(y1, y2)));
            float d0 = d[i0], d1 = d[i1], d2 = d[i2];
            // Normalised barycentrics at texel centres; l1 weighs vertex 1, l2 vertex 2.
            float l1dx = (y2 - y0) * inv, l1dy = -(x2 - x0) * inv;
            float l2dx = -(y1 - y0) * inv, l2dy = (x1 - x0) * inv;
            float cx = minX + .5f - x0, cy = minY + .5f - y0;
            float l1Row = (cx * (y2 - y0) - (x2 - x0) * cy) * inv;
            float l2Row = ((x1 - x0) * cy - (y1 - y0) * cx) * inv;
            float e1 = d1 - d0, e2 = d2 - d0, eps = -1e-4f;
            float[] z = depth;
            for (int y = minY; y <= maxY; y++, l1Row += l1dy, l2Row += l2dy) {
                float l1 = l1Row, l2 = l2Row;
                int k = y * size + minX;
                for (int x = minX; x <= maxX; x++, k++, l1 += l1dx, l2 += l2dx) {
                    if (l1 < eps || l2 < eps || l1 + l2 > 1 - eps) continue;
                    float value = d0 + l1 * e1 + l2 * e2;
                    if (value > z[k]) z[k] = value;
                }
            }
        }

        /** Per-sample variant of {@link #visibility}: 4 x 4 texel tent filter, float math. */
        float visibility4(float x, float y, float z) {
            float fx = (float) ((x * r1[0] + y * r1[1] + z * r1[2] - minA) * scale) - .5f;
            float fy = (float) ((x * r2[0] + y * r2[1] + z * r2[2] - minB) * scale) - .5f;
            float d = (float) (x * dir[0] + y * dir[1] + z * dir[2] + texel);
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
            if (ix < 1 || iy < 1 || ix + 2 >= size || iy + 2 >= size) return 1;
            float ax = fx - ix, ay = fy - iy;
            float wx0 = 1 - ax, wx1 = 2 - ax, wx2 = 1 + ax, wx3 = ax;
            float wy0 = 1 - ay, wy1 = 2 - ay, wy2 = 1 + ay, wy3 = ay;
            int k = (iy - 1) * size + ix - 1;
            float sum = wy0 * row(k, d, wx0, wx1, wx2, wx3) + wy1 * row(k + size, d, wx0, wx1, wx2, wx3)
                    + wy2 * row(k + 2 * size, d, wx0, wx1, wx2, wx3) + wy3 * row(k + 3 * size, d, wx0, wx1, wx2, wx3);
            return sum * (1f / 16f);
        }

        private float row(int k, float d, float w0, float w1, float w2, float w3) {
            float[] z = depth;
            return (d >= z[k] ? w0 : 0) + (d >= z[k + 1] ? w1 : 0) + (d >= z[k + 2] ? w2 : 0) + (d >= z[k + 3] ? w3 : 0);
        }

        /** Fraction of light reaching a world point, filtered over a (2r+2)^2 texel tent. */
        double visibility(double x, double y, double z, int r, double bias) {
            double fx = (x * r1[0] + y * r1[1] + z * r1[2] - minA) * scale - .5;
            double fy = (x * r2[0] + y * r2[1] + z * r2[2] - minB) * scale - .5;
            double d = x * dir[0] + y * dir[1] + z * dir[2] + bias;
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
            double sum = 0, weight = 0, reach = r + 1;
            for (int yy = iy - r; yy <= iy + 1 + r; yy++) {
                double wy = Math.max(0, reach - Math.abs(yy - fy));
                for (int xx = ix - r; xx <= ix + 1 + r; xx++) {
                    double w = wy * Math.max(0, reach - Math.abs(xx - fx));
                    if (w <= 0) continue;
                    weight += w;
                    int cx = Math.max(0, Math.min(size - 1, xx)), cy = Math.max(0, Math.min(size - 1, yy));
                    if (d >= depth[cy * size + cx]) sum += w;
                }
            }
            return sum / weight;
        }
    }

    // ------------------------------------------------------------------ ambient occlusion

    static final int AO_DIRECTIONS = 32;

    /** Sky radiance (linear RGB) arriving from world direction d: dim olive sky, dark ground. */
    static void sky(double dx, double dy, double dz, double[] out) {
        if (dy >= 0) {
            double t = Math.pow(dy, .7);
            out[0] = RoseMath.lerp(.115, .19, t); out[1] = RoseMath.lerp(.125, .20, t); out[2] = RoseMath.lerp(.105, .17, t);
        } else {
            double t = Math.min(1, -dy * 1.5);
            out[0] = RoseMath.lerp(.06, .018, t); out[1] = RoseMath.lerp(.065, .02, t); out[2] = RoseMath.lerp(.055, .018, t);
        }
    }

    /**
     * Cosine-weighted, sky-coloured ambient irradiance for both sides of every vertex
     * ({@code 6 floats per vertex: front rgb, back rgb}).
     */
    static float[] ambient(RoseMesh m, int mapSize) {
        int k = AO_DIRECTIONS;
        double[][] dirs = new double[k][];
        double[][] radiance = new double[k][3];
        for (int i = 0; i < k; i++) {
            double y = 1 - 2 * (i + .5) / k, r = Math.sqrt(1 - y * y), phi = i * Math.PI * (3 - Math.sqrt(5));
            dirs[i] = new double[]{r * StrictMath.cos(phi), y, r * StrictMath.sin(phi)};
            sky(dirs[i][0], dirs[i][1], dirs[i][2], radiance[i]);
        }
        ShadowMap[] maps = new ShadowMap[k];
        IntStream.range(0, k).parallel().forEach(i ->
                maps[i] = new ShadowMap(m.x, m.y, m.z, m.vertexCount, m.tri, m.triangleCount, dirs[i], mapSize));
        float[] out = new float[6 * m.vertexCount];
        double eps = 2.0 * maps[0].texel;
        IntStream.range(0, m.vertexCount).parallel().forEach(v -> {
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                double nx = sign * m.nx[v], ny = sign * m.ny[v], nz = sign * m.nz[v];
                double px = m.x[v] + nx * eps, py = m.y[v] + ny * eps, pz = m.z[v] + nz * eps;
                double r = 0, g = 0, b = 0;
                for (int i = 0; i < k; i++) {
                    double[] d = dirs[i];
                    double cos = nx * d[0] + ny * d[1] + nz * d[2];
                    if (cos <= 0) continue;
                    double vis = maps[i].visibility(px, py, pz, 0, maps[i].texel);
                    r += radiance[i][0] * vis * cos; g += radiance[i][1] * vis * cos; b += radiance[i][2] * vis * cos;
                }
                out[6 * v + 3 * side] = (float) (4.0 / k * r);
                out[6 * v + 3 * side + 1] = (float) (4.0 / k * g);
                out[6 * v + 3 * side + 2] = (float) (4.0 / k * b);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ direct light + shading

    /** Camera-fixed studio lights, given in view space (x right, y up, z toward the viewer). */
    static final double[] KEY_VIEW = RoseMath.normalize(-.62, .70, .50);
    static final double[] RIM_VIEW = RoseMath.normalize(.72, .38, -.60);
    static final double[] FILL_VIEW = RoseMath.normalize(.55, -.05, .85);
    static final double[] KEY_COLOR = {2.35, 2.12, 1.86};
    static final double[] RIM_COLOR = {.78, .84, .98};
    static final double[] FILL_COLOR = {.08, .09, .10};

    /** Per-material response: wrap, translucency, sheen, specular strength, shininess. */
    private static final double[][] MATERIAL = {
            {.26, .55, .16, .008, 7},   // PETAL
            {.18, .10, .05, .06, 16},   // SEPAL
            {.15, .02, .03, .10, 18},   // RECEPTACLE
            {.12, .00, .04, .12, 24},   // STEM
            {.10, .00, .02, .22, 40},   // THORN
            {.16, .22, .03, .10, 28},   // LEAF
            {.24, .30, .08, .006, 7},   // FALLEN
    };

    /**
     * Hue-preserving filmic tone mapping: the brightest channel follows the ACES curve and the
     * others scale with it, so saturated reds stay red instead of drifting to pink; only very
     * strong highlights desaturate towards white, like film.
     */
    static void toneMap(float[] rgb) {
        float m = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (m <= 1e-9f) { rgb[0] = rgb[1] = rgb[2] = 0; return; }
        float mapped = (float) RoseMath.filmic(m), k = mapped / m, white = .55f * (float) RoseMath.smooth(1.1, 4.5, m);
        for (int c = 0; c < 3; c++) rgb[c] = Math.min(1, rgb[c] * k + (mapped - rgb[c] * k) * white);
    }

    /**
     * Per-vertex lighting split into what the rasterizer needs for per-sample key-light shadows:
     * {@code base} (everything except direct key light), {@code key} (unshadowed key light incl.
     * its specular), and {@code shadowPos} (world point offset off the surface, per side) at which
     * the key shadow map is sampled. All arrays hold 6 floats per vertex: front rgb/xyz, back rgb/xyz.
     */
    record Shaded(float[] base, float[] key, float[] shadowPos, ShadowMap keyMap) { }

    /**
     * Shades every vertex for both sides (HDR linear, exposure and ground fog applied).
     *
     * @param wx world positions (model turned by the view yaw), {@code wn} world normals
     * @param camera world position of the camera
     */
    static Shaded shade(RoseMesh m, float[] wx, float[] wy, float[] wz, float[] wnx, float[] wny, float[] wnz,
                        float[] albedo, float[] ambient, double[] key, double[] rim, double[] fill, double[] camera,
                        double exposure, double ambientGain, int keyMapSize) {
        ShadowMap[] maps = new ShadowMap[2];
        double[][] lights = {key, rim};
        int[] sizes = {keyMapSize, 512};
        IntStream.range(0, 2).parallel().forEach(i ->
                maps[i] = new ShadowMap(wx, wy, wz, m.vertexCount, m.tri, m.triangleCount, lights[i], sizes[i]));
        int n = m.vertexCount;
        float[] base = new float[6 * n], direct = new float[6 * n], shadowPos = new float[6 * n];
        double eps = 1.5 * maps[0].texel, epsRim = 1.5 * maps[1].texel;
        IntStream.range(0, n).parallel().forEach(v -> {
            double[] mat = MATERIAL[m.material[v]];
            double wrap = mat[0], trans = mat[1], sheen = mat[2], specK = mat[3], shin = mat[4];
            double px = wx[v], py = wy[v], pz = wz[v];
            double vx = camera[0] - px, vy = camera[1] - py, vz = camera[2] - pz;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            vx /= vl; vy /= vl; vz /= vl;
            double fog = m.material[v] == RoseMesh.FALLEN ? 0
                    : .88 * RoseMath.smooth(RoseGeometry.FLOOR_Y + .95, RoseGeometry.FLOOR_Y - .05, py);
            double gain = exposure * (1 - fog);
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                double nx = sign * wnx[v], ny = sign * wny[v], nz = sign * wnz[v];
                double dK = nx * key[0] + ny * key[1] + nz * key[2];
                double dR = nx * rim[0] + ny * rim[1] + nz * rim[2];
                double dF = nx * fill[0] + ny * fill[1] + nz * fill[2];
                double back = 0, frontR = 0, backR = 0;
                if (dK < 0 && trans > 0) back = maps[0].visibility(px - nx * eps, py - ny * eps, pz - nz * eps, 1, maps[0].texel);
                if (dR > -wrap) frontR = maps[1].visibility(px + nx * epsRim, py + ny * epsRim, pz + nz * epsRim, 1, maps[1].texel);
                if (dR < 0 && trans > 0) backR = maps[1].visibility(px - nx * epsRim, py - ny * epsRim, pz - nz * epsRim, 1, maps[1].texel);
                double diffK = Math.max(0, (dK + wrap) / (1 + wrap));
                double diffR = Math.max(0, (dR + wrap) / (1 + wrap)) * frontR;
                double diffF = Math.max(0, (dF + .5) / 1.5);
                double thin = .45 + .55 * m.edge[v];
                double transK = trans * thin * Math.max(0, -dK) * back, transR = trans * thin * Math.max(0, -dR) * backR;
                double nv = Math.abs(nx * vx + ny * vy + nz * vz);
                double rimGlow = sheen * Math.pow(1 - nv, 3) * 1.7;
                double hx = key[0] + vx, hy = key[1] + vy, hz = key[2] + vz;
                double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
                double nh = Math.max(0, (nx * hx + ny * hy + nz * hz) / hl);
                double spec = dK > 0 ? specK * Math.pow(nh, shin) * (side == 0 || m.material[v] != RoseMesh.LEAF ? 1 : .3) : 0;
                int a = 6 * v + 3 * side;
                for (int c = 0; c < 3; c++) {
                    double alb = albedo[a + c];
                    double light = RIM_COLOR[c] * diffR + FILL_COLOR[c] * diffF + ambientGain * ambient[a + c];
                    double glow = alb * alb * 2.4 * (KEY_COLOR[c] * transK + RIM_COLOR[c] * transR);
                    base[a + c] = (float) ((alb * light + glow + rimGlow * alb * (.35 + .6 * diffR)) * gain);
                    direct[a + c] = (float) ((alb * KEY_COLOR[c] * diffK * (1 + rimGlow) + spec * KEY_COLOR[c]) * gain);
                }
                shadowPos[a] = (float) (px + nx * eps);
                shadowPos[a + 1] = (float) (py + ny * eps);
                shadowPos[a + 2] = (float) (pz + nz * eps);
            }
        });
        return new Shaded(base, direct, shadowPos, maps[0]);
    }
}
