package org.example;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Java2D layers around the 3D rose: the faded arch, broken halo, dust and cracks behind it, soft
 * ground contact shadows, and the dew + caption drawn on top. All coordinates are in the
 * 1000 x 1000 art space unless noted.
 */
final class RoseBackdrop {
    private static final Color[] DUST = new Color[15];
    private static final Color[] PAPER_DUST = new Color[15];

    static {
        for (int i = 0; i < DUST.length; i++) {
            DUST[i] = new Color(202, 185, 138, i + 2);
            PAPER_DUST[i] = new Color(120, 92, 52, i + 3);
        }
    }

    private RoseBackdrop() { }

    private static Path2D curve(double... p) {
        Path2D q = new Path2D.Double();
        q.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 6) q.curveTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5]);
        return q;
    }

    private record BaseKey(int side, RoseDrawing.Style style, long seed) { }

    /** The static backdrop only depends on size, style and seed; re-renders (turning, flower edits) reuse it. */
    private static final Map<BaseKey, int[]> BASE_CACHE = new LinkedHashMap<>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BaseKey, int[]> eldest) { return size() > 3; }
    };

    /**
     * Paints the backdrop into {@code square}: cached static layers, then the view-dependent soft
     * contact shadows of the fallen petals and the stem.
     */
    static void paintBase(BufferedImage square, RoseDrawing.Appearance look, RoseGeometry.Model model, RoseRenderer.Camera camera) {
        int side = square.getWidth();
        int[] pixels = ((DataBufferInt) square.getRaster().getDataBuffer()).getData();
        BaseKey key = new BaseKey(side, look.style(), look.seed());
        int[] cached;
        synchronized (BASE_CACHE) {
            cached = BASE_CACHE.get(key);
        }
        if (cached != null) {
            System.arraycopy(cached, 0, pixels, 0, pixels.length);
        } else {
            Graphics2D g = RoseRenderer.artGraphics(square);
            try {
                paintStatic(g, look);
            } finally {
                g.dispose();
            }
            if (side <= 2048) {
                synchronized (BASE_CACHE) {
                    BASE_CACHE.put(key, pixels.clone());
                }
            }
        }
        Graphics2D g = RoseRenderer.artGraphics(square);
        try {
            contactShadows(g, model, camera, look.style() == RoseDrawing.Style.ENGRAVING);
        } finally {
            g.dispose();
        }
    }

    private static void paintStatic(Graphics2D g, RoseDrawing.Appearance look) {
        Random random = new Random(look.seed());
        RoseDrawing.Style style = look.style();
        double hx = RoseRenderer.CENTER_X + 6, hy = RoseRenderer.CENTER_Y + 12;
        switch (style) {
            case ENGRAVING -> paper(g, random);
            case ART_NOUVEAU -> {
                g.setPaint(new RadialGradientPaint(480, 380, 760, new float[]{0, .6f, 1},
                        new Color[]{new Color(31, 58, 52), new Color(14, 33, 31), new Color(5, 13, 14)}));
                g.fillRect(0, 0, 1000, 1000);
            }
            default -> {
                boolean faded = style == RoseDrawing.Style.FADED_INK;
                g.setPaint(new RadialGradientPaint(470, 360, 740, new float[]{0, .65f, 1}, faded
                        ? new Color[]{new Color(92, 90, 76), new Color(52, 56, 52), new Color(24, 27, 28)}
                        : new Color[]{new Color(65, 66, 51), new Color(27, 36, 35), new Color(9, 15, 19)}));
                g.fillRect(0, 0, 1000, 1000);
            }
        }
        boolean paper = style == RoseDrawing.Style.ENGRAVING;
        // Faded architectural remnants and a broken halo: time, absence, memory.
        g.setStroke(new BasicStroke(paper ? 1.3f : style == RoseDrawing.Style.ART_NOUVEAU ? 15 : 22));
        g.setColor(paper ? new Color(120, 96, 64, 90) : style == RoseDrawing.Style.ART_NOUVEAU
                ? new Color(184, 146, 74, 46) : new Color(158, 145, 104, 22));
        g.draw(new Arc2D.Double(154, 83, 692, 950, 0, 180, Arc2D.OPEN));
        g.drawLine(154, 558, 154, 906);
        g.drawLine(846, 558, 846, 906);
        if (paper) {
            g.draw(new Arc2D.Double(166, 95, 668, 926, 0, 180, Arc2D.OPEN));
            g.drawLine(166, 558, 166, 906);
            g.drawLine(834, 558, 834, 906);
        }
        Color haloLine = paper ? new Color(130, 100, 60, 110) : new Color(191, 156, 93, 100);
        Color haloFaint = paper ? new Color(130, 100, 60, 50) : new Color(191, 156, 93, 37);
        g.setStroke(new BasicStroke(style == RoseDrawing.Style.ART_NOUVEAU ? 2.4f : 1.2f));
        g.setColor(haloLine);
        g.draw(new Arc2D.Double(hx - 275, hy - 275, 550, 550, 18, 278, Arc2D.OPEN));
        g.setColor(haloFaint);
        g.draw(new Arc2D.Double(hx - 289, hy - 289, 578, 578, 69, 235, Arc2D.OPEN));
        if (style == RoseDrawing.Style.ART_NOUVEAU) whiplash(g);
        Color[] dust = paper ? PAPER_DUST : DUST;
        for (int i = 0; i < 10500; i++) {
            int x = random.nextInt(1000), y = random.nextInt(1000);
            g.setColor(dust[random.nextInt(dust.length)]);
            g.fillRect(x, y, 1 + random.nextInt(3), 1 + random.nextInt(3));
        }
        g.setStroke(new BasicStroke(.7f));
        g.setColor(paper ? new Color(110, 84, 50, 36) : new Color(6, 13, 15, 50));
        for (int i = 0; i < 24; i++) {
            double x = random.nextInt(1000), y = random.nextInt(1000);
            g.draw(curve(x, y, x - 20, y + 26, x + 22, y + 45, x - 8, y + 95));
        }
        if (!paper) {
            g.setPaint(new GradientPaint(0, 790, new Color(10, 17, 19, 0), 0, 1000, new Color(5, 9, 12, 210)));
            g.fillRect(0, 790, 1000, 210);
        }
    }

    private static void paper(Graphics2D g, Random random) {
        g.setPaint(new RadialGradientPaint(500, 470, 760, new float[]{0, .7f, 1},
                new Color[]{new Color(238, 229, 207), new Color(226, 212, 183), new Color(196, 176, 140)}));
        g.fillRect(0, 0, 1000, 1000);
        for (int i = 0; i < 46; i++) {
            double x = random.nextDouble() * 1000, y = random.nextDouble() * 1000, r = 3 + random.nextDouble() * 16;
            g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) r, new float[]{0, 1},
                    new Color[]{new Color(150, 110, 60, 26), new Color(150, 110, 60, 0)}));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        g.setColor(new Color(120, 96, 64, 120));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new Rectangle2D.Double(40, 40, 920, 920));
        g.setStroke(new BasicStroke(.6f));
        g.draw(new Rectangle2D.Double(48, 48, 904, 904));
    }

    /** Art Nouveau "coup de fouet" ornaments behind the bloom. */
    private static void whiplash(Graphics2D g) {
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(196, 158, 82, 52));
        g.draw(curve(150, 900, 210, 760, 120, 640, 230, 560, 330, 490, 260, 380, 330, 300));
        g.draw(curve(850, 900, 790, 760, 880, 640, 770, 560, 670, 490, 740, 380, 670, 300));
        g.setColor(new Color(196, 158, 82, 30));
        g.draw(curve(175, 900, 240, 780, 160, 660, 262, 580));
        g.draw(curve(825, 900, 760, 780, 840, 660, 738, 580));
    }

    private static void contactShadows(Graphics2D g, RoseGeometry.Model model, RoseRenderer.Camera camera, boolean paper) {
        int alpha = paper ? 70 : 150;
        for (double[] petal : model.fallen) shadowAt(g, camera, petal[0], petal[1], petal[2] * 1.15, alpha);
        shadowAt(g, camera, model.stemFoot[0], model.stemFoot[1], .26, alpha);
    }

    private static void shadowAt(Graphics2D g, RoseRenderer.Camera camera, double x, double z, double radius, int alpha) {
        double wx = camera.turnX(x, z), wz = camera.turnZ(x, z);
        double[] c = camera.project(wx, RoseGeometry.FLOOR_Y, wz);
        double[] e = camera.project(wx + radius, RoseGeometry.FLOOR_Y, wz);
        double[] d = camera.project(wx, RoseGeometry.FLOOR_Y, wz + radius);
        double rx = Math.max(4, Math.abs(e[0] - c[0])), ry = Math.max(2, Math.abs(d[1] - c[1]));
        Graphics2D s = (Graphics2D) g.create();
        try {
            s.translate(c[0] + rx * .12, c[1] + ry * .2);
            s.scale(1, ry / rx);
            s.setPaint(new RadialGradientPaint(0, 0, (float) rx, new float[]{0, .55f, 1},
                    new Color[]{new Color(0, 0, 0, alpha), new Color(0, 0, 0, alpha / 3), new Color(0, 0, 0, 0)}));
            s.fill(new Ellipse2D.Double(-rx, -rx, 2 * rx, 2 * rx));
        } finally {
            s.dispose();
        }
    }

    static void paintOverlay(Graphics2D g, RoseDrawing.Appearance look) {
        RoseDrawing.Style style = look.style();
        boolean paper = style == RoseDrawing.Style.ENGRAVING;
        Color ink = paper ? new Color(70, 52, 34, 200) : style == RoseDrawing.Style.ART_NOUVEAU
                ? new Color(214, 178, 104, 190) : new Color(199, 184, 151, 150);
        g.setColor(paper ? new Color(110, 86, 56, 130) : new Color(186, 157, 104, 95));
        g.setStroke(new BasicStroke(1));
        g.drawLine(74, 77, 74, 136); g.drawLine(74, 77, 132, 77);
        g.drawLine(926, 923, 868, 923); g.drawLine(926, 923, 926, 864);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 15));
        g.setColor(ink);
        g.drawString("R O S A   /   R E L I C T A", 74, 958);
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 13));
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), ink.getAlpha() * 2 / 3));
        g.drawString("what remains of tenderness", 74, 979);
    }

    /**
     * Dew: droplets sit on upward-facing, visible petal surface points. Each one is a little lens:
     * contact shadow, the petal colour magnified and brightened, a dark rim and a sharp highlight
     * towards the key light. Drawn in pixel space of {@code image}.
     */
    static void paintDew(Graphics2D g, BufferedImage image, RoseDrawing.Appearance look, RoseRenderer.Prepared prepared,
                         float[] wx, float[] wy, float[] wz, float[] wnx, float[] wnz,
                         RoseRenderer.Camera camera, RoseRasterizer.Frame frame, double scale) {
        RoseMesh m = prepared.model().mesh;
        int[][] grids = prepared.model().petalGrids;
        Random random = new Random(look.seed() ^ 0x5AD0_5EEDL);
        int side = image.getWidth(), placed = 0;
        for (int attempt = 0; attempt < 600 && placed < 28; attempt++) {
            int[] grid = grids[random.nextInt(Math.max(1, grids.length * 3 / 4))];
            int nu = grid[2], nv = grid[3];
            int i = (int) Math.round(nu * (.35 + .55 * random.nextDouble()));
            int j = (int) Math.round((nv - 1) * (.15 + .7 * random.nextDouble()));
            int v = grid[0] + Math.min(nu - 1, i) * nv + j;
            double vx = camera.position[0] - wx[v], vy = camera.position[1] - wy[v], vz = camera.position[2] - wz[v];
            double facing = wnx[v] * vx + m.ny[v] * vy + wnz[v] * vz;
            int sideBit = facing >= 0 ? 0 : 1;
            double sign = facing >= 0 ? 1 : -1;
            double upness = sign * m.ny[v];
            if (upness < .18) continue;
            double[] p = camera.project(wx[v], wy[v], wz[v]);
            int px = Math.max(0, Math.min(side - 1, (int) (p[0] * scale)));
            int py = Math.max(0, Math.min(side - 1, (int) (p[1] * scale)));
            int o = py * side + px;
            if (frame.idOut[o] != ((m.part[v] << 1) | sideBit) || Math.abs(frame.depthOut[o] - p[2]) > .05) continue;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double cosView = Math.abs(facing) / vl;
            double radius = (.010 + .022 * random.nextDouble() * random.nextDouble() + .006) * RoseRenderer.ZOOM
                    * RoseRenderer.DISTANCE / p[2] * scale;
            drop(g, new Color(image.getRGB(px, py)), p[0] * scale, p[1] * scale, radius, Math.max(.55, cosView));
            placed++;
        }
    }

    private static void drop(Graphics2D g, Color base, double x, double y, double r, double squash) {
        Color bright = mix(base, 1.45, 34, 235), body = mix(base, 1.08, 6, 215), rim = mix(base, .42, 0, 220);
        double ry = r * squash;
        g.setColor(new Color(0, 0, 0, 62));
        g.fill(new Ellipse2D.Double(x - r + r * .28, y - ry + ry * .42, 2 * r, 2 * ry));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x + r * .2, y + ry * .28), (float) (r * 1.15),
                new float[]{0, .55f, 1}, new Color[]{bright, body, rim}));
        g.fill(new Ellipse2D.Double(x - r, y - ry, 2 * r, 2 * ry));
        g.setStroke(new BasicStroke((float) Math.max(.5, r * .08)));
        g.setColor(new Color(0, 0, 0, 55));
        g.draw(new Ellipse2D.Double(x - r, y - ry, 2 * r, 2 * ry));
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(new Ellipse2D.Double(x - r * .58, y - ry * .62, r * .42, ry * .30));
        g.setColor(new Color(255, 250, 240, 110));
        g.fill(new Ellipse2D.Double(x + r * .25, y + ry * .38, r * .22, ry * .14));
    }

    private static Color mix(Color c, double gain, int lift, int alpha) {
        return new Color((int) Math.min(255, c.getRed() * gain + lift), (int) Math.min(255, c.getGreen() * gain + lift),
                (int) Math.min(255, c.getBlue() * gain + lift), alpha);
    }
}
