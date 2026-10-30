package org.example;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Turns (appearance, flower, view) into pixels: cached geometry + ambient occlusion, per-view
 * shadows and shading, projection, supersampled rasterization over a Java2D backdrop, then
 * overlays (dew, caption).
 */
final class RoseRenderer {
    /** Background colour outside the square artwork (letterbox). */
    static final Color LETTERBOX = new Color(17, 22, 23);
    /** Art space is 1000 x 1000; the flower centre projects here. */
    static final double CENTER_X = 485, CENTER_Y = 352;
    /** Art units per world unit at the flower, and camera distance. */
    static final double ZOOM = 226, DISTANCE = 10;
    /** Camera elevation for the default view, degrees. */
    static final double ELEVATION = 15;

    /** Preview trades supersampling and tessellation for speed while the rose is being turned. */
    enum Quality { FINAL, PREVIEW }

    /** Geometry + its ambient occlusion: the expensive, view-independent part of a frame. */
    record Prepared(RoseGeometry.Model model, float[] ambient, int petals) { }

    private record Key(long seed, RoseDrawing.FlowerOptions flower, int detail) { }

    private static final Map<Key, Prepared> CACHE = new LinkedHashMap<>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Prepared> eldest) { return size() > 6; }
    };

    private RoseRenderer() { }

    static int detailFor(int side, Quality quality) {
        if (quality == Quality.PREVIEW) return 0;
        return side < 560 ? 0 : side < 1250 ? 1 : 2;
    }

    static int supersamplingFor(int side, Quality quality) {
        if (quality == Quality.PREVIEW) return 1;
        return side <= 760 ? 3 : 2;
    }

    static Prepared prepare(long seed, RoseDrawing.FlowerOptions flower, int detail) {
        Key key = new Key(seed, flower, detail);
        synchronized (CACHE) {
            Prepared hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        RoseGeometry.Model model = RoseGeometry.build(seed, flower, detail);
        float[] ambient = RoseLighting.ambient(model.mesh, 192 + 32 * detail);
        Prepared prepared = new Prepared(model, ambient, RoseGeometry.petalCount(flower));
        synchronized (CACHE) {
            CACHE.put(key, prepared);
        }
        return prepared;
    }

    static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** Camera + model turn for one view; maps world points to art space (0..1000). */
    static final class Camera {
        final double cosYaw, sinYaw, cosE, sinE;
        final double[] position;

        Camera(RoseDrawing.View view) {
            double yaw = Math.toRadians(view.yaw()), e = Math.toRadians(ELEVATION + view.pitch());
            cosYaw = StrictMath.cos(yaw); sinYaw = StrictMath.sin(yaw);
            cosE = StrictMath.cos(e); sinE = StrictMath.sin(e);
            position = new double[]{0, DISTANCE * sinE, DISTANCE * cosE};
        }

        /** Model (turntable) rotation about the vertical axis. */
        double turnX(double x, double z) { return cosYaw * x + sinYaw * z; }

        double turnZ(double x, double z) { return -sinYaw * x + cosYaw * z; }

        /** View-space light/vector to world. */
        double[] toWorld(double[] v) {
            return new double[]{v[0], v[1] * cosE + v[2] * sinE, -v[1] * sinE + v[2] * cosE};
        }

        /** World point to {art x, art y, depth}. */
        double[] project(double x, double y, double z) {
            double yv = y * cosE - z * sinE, zv = y * sinE + z * cosE;
            double depth = DISTANCE - zv, f = ZOOM * DISTANCE / depth;
            return new double[]{CENTER_X + f * x, CENTER_Y - f * yv, depth};
        }
    }

    static BufferedImage render(int width, int height, RoseDrawing.Appearance look, RoseDrawing.FlowerOptions flower,
                                RoseDrawing.View view, Quality quality) {
        Objects.requireNonNull(look, "appearance");
        Objects.requireNonNull(flower, "flower");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(quality, "quality");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Positive dimensions required");
        int side = Math.min(width, height);
        Prepared prepared = prepare(look.seed(), flower, detailFor(side, quality));
        Camera camera = new Camera(view);
        BufferedImage square = renderSquare(side, look, flower, prepared, camera, quality);
        if (width == side && height == side) return square;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(look.style() == RoseDrawing.Style.FADED_INK ? new Color(35, 38, 38) : LETTERBOX);
            g.fillRect(0, 0, width, height);
            g.drawImage(square, (width - side) / 2, (height - side) / 2, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage renderSquare(int side, RoseDrawing.Appearance look, RoseDrawing.FlowerOptions flower,
                                              Prepared prepared, Camera camera, Quality quality) {
        RoseGeometry.Model model = prepared.model();
        RoseMesh m = model.mesh;
        int n = m.vertexCount;
        double scale = side / 1000.0;
        int ss = supersamplingFor(side, quality);

        // Turn the model, light it, project it.
        float[] wx = new float[n], wy = m.y, wz = new float[n], wnx = new float[n], wnz = new float[n];
        for (int i = 0; i < n; i++) {
            wx[i] = (float) camera.turnX(m.x[i], m.z[i]); wz[i] = (float) camera.turnZ(m.x[i], m.z[i]);
            wnx[i] = (float) camera.turnX(m.nx[i], m.nz[i]); wnz[i] = (float) camera.turnZ(m.nx[i], m.nz[i]);
        }
        double ambientGain = look.style() == RoseDrawing.Style.ENGRAVING ? 2.2 : 1.0;
        RoseLighting.Shaded lit = RoseLighting.shade(m, wx, wy, wz, wnx, m.ny, wnz,
                RosePalette.albedo(m, look, prepared.petals()), prepared.ambient(),
                camera.toWorld(RoseLighting.KEY_VIEW), camera.toWorld(RoseLighting.RIM_VIEW),
                camera.toWorld(RoseLighting.FILL_VIEW), camera.position, look.exposure(), ambientGain,
                quality == Quality.PREVIEW ? 1024 : 2048);

        RoseRasterizer.Frame frame = new RoseRasterizer.Frame();
        frame.side = side; frame.ss = ss; frame.style = look.style();
        frame.sx = new float[n]; frame.sy = new float[n]; frame.sz = new float[n];
        double toSamples = scale * ss;
        for (int i = 0; i < n; i++) {
            double[] p = camera.project(wx[i], wy[i], wz[i]);
            frame.sx[i] = (float) (p[0] * toSamples);
            frame.sy[i] = (float) (p[1] * toSamples);
            frame.sz[i] = (float) p[2];
        }
        frame.base = lit.base(); frame.key = lit.key(); frame.shadowPos = lit.shadowPos(); frame.keyMap = lit.keyMap();
        boolean metric = look.style() == RoseDrawing.Style.ENGRAVING;
        frame.ta = metric ? m.hu : m.u;
        frame.tb = metric ? m.hv : m.v;
        frame.material = m.material; frame.part = m.part;
        frame.tri = m.tri; frame.triangles = m.triangleCount;
        double pixelsPerUnit = ZOOM * scale;
        frame.hatch = (float) (1 / Math.max(.0165, 3.1 / pixelsPerUnit));

        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) square.getRaster().getDataBuffer()).getData();
        RoseBackdrop.paintBase(square, look, model, camera);
        frame.background = pixels;
        if (flower.dew()) { frame.depthOut = new float[side * side]; frame.idOut = new int[side * side]; }
        RoseRasterizer.render(frame);

        Graphics2D g = square.createGraphics();
        try {
            if (flower.dew()) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoseBackdrop.paintDew(g, square, look, prepared, wx, wy, wz, wnx, wnz, camera, frame, scale);
            }
        } finally {
            g.dispose();
        }
        g = artGraphics(square);
        try {
            RoseBackdrop.paintOverlay(g, look);
        } finally {
            g.dispose();
        }
        return square;
    }

    /** Antialiased graphics scaled so that the square image spans the 1000 x 1000 art space. */
    static Graphics2D artGraphics(BufferedImage square) {
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(square.getWidth() / 1000.0, square.getWidth() / 1000.0);
        return g;
    }
}
