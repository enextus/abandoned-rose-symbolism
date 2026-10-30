package org.example;

import java.util.Arrays;

/**
 * Growable indexed triangle mesh used by the procedural 3D renderer.
 *
 * <p>Every vertex keeps two kinds of surface coordinates: normalized {@code (u, v)} used for
 * colour gradients and fine vein texture, and metric {@code (hu, hv)} (world units along and across
 * the surface) used for engraving hatches. The vertex normal always points to the "front" side of
 * the surface; the rasterizer decides per triangle which side the camera actually sees.
 */
final class RoseMesh {
    static final byte PETAL = 0, SEPAL = 1, RECEPTACLE = 2, STEM = 3, THORN = 4, LEAF = 5, FALLEN = 6;

    /** Evaluates one grid point into {@code out = {x, y, z, u, v, hu, hv, edge}}. */
    @FunctionalInterface
    interface Surface {
        void eval(int i, int j, double[] out);
    }

    int vertexCount;
    int triangleCount;
    float[] x = new float[4096], y = new float[4096], z = new float[4096];
    float[] nx = new float[4096], ny = new float[4096], nz = new float[4096];
    float[] u = new float[4096], v = new float[4096], hu = new float[4096], hv = new float[4096];
    /** 0 in the body of a surface, 1 at its free margin (thin, translucent, sometimes tinted). */
    float[] edge = new float[4096];
    byte[] material = new byte[4096];
    /** Logical part (petal / leaflet / thorn index); used for colour variation and outlines. */
    int[] part = new int[4096];
    int[] tri = new int[3 * 8192];

    private void ensureVertices(int extra) {
        int need = vertexCount + extra;
        if (need <= x.length) return;
        int n = Math.max(need, x.length * 2);
        x = Arrays.copyOf(x, n); y = Arrays.copyOf(y, n); z = Arrays.copyOf(z, n);
        nx = Arrays.copyOf(nx, n); ny = Arrays.copyOf(ny, n); nz = Arrays.copyOf(nz, n);
        u = Arrays.copyOf(u, n); v = Arrays.copyOf(v, n); hu = Arrays.copyOf(hu, n); hv = Arrays.copyOf(hv, n);
        edge = Arrays.copyOf(edge, n); material = Arrays.copyOf(material, n); part = Arrays.copyOf(part, n);
    }

    private void ensureTriangles(int extra) {
        int need = 3 * (triangleCount + extra);
        if (need > tri.length) tri = Arrays.copyOf(tri, Math.max(need, tri.length * 2));
    }

    /**
     * Adds an {@code nu x nv} grid. With {@code wrapV} the last column connects back to the first
     * (closed tubes). Normals come from central differences, oriented as {@code dP/dv x dP/du}.
     */
    void addGrid(int nu, int nv, byte mat, int partId, boolean wrapV, Surface surface) {
        ensureVertices(nu * nv);
        int base = vertexCount;
        double[] out = new double[8];
        for (int i = 0; i < nu; i++) {
            for (int j = 0; j < nv; j++) {
                surface.eval(i, j, out);
                int k = base + i * nv + j;
                x[k] = (float) out[0]; y[k] = (float) out[1]; z[k] = (float) out[2];
                u[k] = (float) out[3]; v[k] = (float) out[4];
                hu[k] = (float) out[5]; hv[k] = (float) out[6]; edge[k] = (float) out[7];
                material[k] = mat; part[k] = partId;
            }
        }
        vertexCount += nu * nv;
        for (int i = 0; i < nu; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(nu - 1, i + 1);
            for (int j = 0; j < nv; j++) {
                int j0 = wrapV ? (j + nv - 1) % nv : Math.max(0, j - 1);
                int j1 = wrapV ? (j + 1) % nv : Math.min(nv - 1, j + 1);
                int a = base + i0 * nv + j, b = base + i1 * nv + j;
                int c = base + i * nv + j0, d = base + i * nv + j1;
                double ux = x[b] - x[a], uy = y[b] - y[a], uz = z[b] - z[a];
                double vx = x[d] - x[c], vy = y[d] - y[c], vz = z[d] - z[c];
                double cx = vy * uz - vz * uy, cy = vz * ux - vx * uz, cz = vx * uy - vy * ux;
                double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
                int k = base + i * nv + j;
                if (len < 1e-12) { cx = 0; cy = 1; cz = 0; len = 1; }
                nx[k] = (float) (cx / len); ny[k] = (float) (cy / len); nz[k] = (float) (cz / len);
            }
        }
        int columns = wrapV ? nv : nv - 1;
        ensureTriangles(2 * (nu - 1) * columns);
        for (int i = 0; i < nu - 1; i++) {
            for (int j = 0; j < columns; j++) {
                int jn = (j + 1) % nv;
                int p00 = base + i * nv + j, p01 = base + i * nv + jn;
                int p10 = base + (i + 1) * nv + j, p11 = base + (i + 1) * nv + jn;
                int t = 3 * triangleCount;
                tri[t] = p00; tri[t + 1] = p01; tri[t + 2] = p10;
                tri[t + 3] = p01; tri[t + 4] = p11; tri[t + 5] = p10;
                triangleCount += 2;
            }
        }
    }
}
