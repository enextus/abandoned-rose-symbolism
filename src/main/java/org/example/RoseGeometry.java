package org.example;

import java.util.Random;

/**
 * Builds the 3D model of the rose: spirally arranged cupped petals with rolled lips, sepals,
 * receptacle, an S-curved stem with hooked thorns, compound serrated leaves and fallen petals.
 *
 * <p>All randomness comes from the appearance seed, so a given seed + {@link RoseDrawing.FlowerOptions}
 * always produces the same shape. {@code detail} only changes tessellation density, never the shape.
 */
final class RoseGeometry {
    /** World height of the implied ground on which fallen petals rest and the stem disappears. */
    static final double FLOOR_Y = -2.72;
    private static final double GOLDEN_ANGLE = Math.toRadians(137.50776);

    /** Built model plus the metadata other stages need. */
    static final class Model {
        final RoseMesh mesh;
        /** Vertex ranges of upright flower petals (start, count, nu, nv) for dew placement. */
        final int[][] petalGrids;
        /** Fallen petals: {x, z, radius} on the floor, for soft contact shadows. */
        final double[][] fallen;
        /** Where the stem meets the floor: {x, z}. */
        final double[] stemFoot;

        Model(RoseMesh mesh, int[][] petalGrids, double[][] fallen, double[] stemFoot) {
            this.mesh = mesh; this.petalGrids = petalGrids; this.fallen = fallen; this.stemFoot = stemFoot;
        }
    }

    private RoseGeometry() { }

    static int petalCount(RoseDrawing.FlowerOptions flower) {
        return 6 * flower.petalLayers() + 5;
    }

    static Model build(long seed, RoseDrawing.FlowerOptions flower, int detail) {
        RoseMesh mesh = new RoseMesh();
        Random random = new Random(seed * 0x9E3779B97F4A7C15L + 0x5DEECE66DL);
        RoseDrawing.BloomStage bloom = flower.bloom();
        double tilt = bloom == RoseDrawing.BloomStage.BUD ? .30 : bloom == RoseDrawing.BloomStage.FULL ? .58 : .50;
        double[] frame = RoseMath.multiply(RoseMath.rotationZ(.07 + (random.nextDouble() - .5) * .08),
                RoseMath.multiply(RoseMath.rotationX(tilt), RoseMath.rotationY(random.nextDouble() * Math.PI * 2)));

        int n = petalCount(flower);
        int[][] grids = new int[n][];
        int nu = 13 + 6 * detail, nv = 15 + 7 * detail;
        double angle = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double t = Math.pow(i / (double) (n - 1), .92);
            Blade shape = petalShape(bloom, t, angle, random);
            angle += GOLDEN_ANGLE + (random.nextDouble() - .5) * .10;
            grids[i] = new int[]{mesh.vertexCount, nu * nv, nu, nv};
            addBlade(mesh, shape, frame, null, nu, nv, RoseMesh.PETAL, i);
        }
        addSepals(mesh, bloom, frame, random, detail);
        addReceptacle(mesh, frame, detail);
        double[] stem = stemCurve(frame, random);
        double[] foot = addStem(mesh, stem, random, detail);
        addLeaf(mesh, stem, random, detail, .40, new double[]{-.92, .10, .36}, new double[]{-.18, .93, .32}, 1.0, 900);
        addLeaf(mesh, stem, random, detail, .63, new double[]{.88, .05, .30}, new double[]{.22, .92, .30}, .82, 920);
        double[][] fallen = addFallenPetals(mesh, random, detail);
        return new Model(mesh, grids, fallen, foot);
    }

    // ------------------------------------------------------------------ blades (petals, sepals)

    /** Shape of a petal-like blade in the flower's local frame (Y = flower axis). */
    static final class Blade {
        double theta, phi0, bend, lip, length, halfWidth, rho0, z0, cup, roll, notch, asym, twist;
        /** Margin waviness: amplitude (radians of extra curl), frequency and phases. */
        double ruffle, ruffleFreq, phaseA, phaseB;
        boolean pointed;     // sepal-like pointed tip instead of a rounded petal top
        int beard;           // sepal "beard": feathery lobes on one side when > 0
    }

    static Blade petalShape(RoseDrawing.BloomStage bloom, double t, double theta, Random r) {
        Blade b = new Blade();
        double d = Math.toRadians(1);
        switch (bloom) {
            case BUD -> {
                b.phi0 = RoseMath.lerp(15, 1.5, Math.pow(t, .6)) * d;
                b.bend = RoseMath.lerp(22, 3, t) * d;
                b.lip = RoseMath.lerp(14, 5, t) * d;
                b.length = RoseMath.lerp(.92, .70, t);
                b.halfWidth = RoseMath.lerp(.44, .33, t);
                b.roll = .015;
            }
            case FULL -> {
                b.phi0 = RoseMath.lerp(66, 10, Math.pow(t, .75)) * d;
                b.bend = RoseMath.lerp(50, 24, t) * d;
                b.lip = RoseMath.lerp(40, 30, t) * d;
                b.length = RoseMath.lerp(.98, .60, t);
                b.halfWidth = RoseMath.lerp(.53, .30, t);
                b.roll = RoseMath.lerp(.10, .03, t);
            }
            default -> {
                b.phi0 = RoseMath.lerp(36, 3, Math.pow(t, .65)) * d;
                b.bend = RoseMath.lerp(50, 7, t) * d;
                b.lip = RoseMath.lerp(34, 20, t) * d;
                b.length = RoseMath.lerp(1.0, .66, t);
                b.halfWidth = RoseMath.lerp(.50, .30, t);
                b.roll = RoseMath.lerp(.13, .02, t);
            }
        }
        b.theta = theta;
        b.rho0 = RoseMath.lerp(.19, .02, Math.pow(t, .8));
        b.z0 = RoseMath.lerp(-.05, .06, t);
        b.cup = RoseMath.lerp(.16, .03, t);
        b.phi0 = Math.max(0, b.phi0 + r.nextGaussian() * Math.toRadians(2.5) * (1 - .7 * t));
        b.length *= 1 + .055 * r.nextGaussian();
        b.halfWidth *= 1 + .07 * r.nextGaussian();
        b.lip *= 1 + .18 * r.nextGaussian();
        b.roll *= .7 + .6 * r.nextDouble();
        b.asym = .10 * r.nextGaussian();
        b.twist = .07 * r.nextGaussian();
        b.notch = .02 + .035 * r.nextDouble();
        b.ruffle = Math.toRadians(1.5 + 3 * r.nextDouble()) * (1 - .5 * t);
        b.ruffleFreq = 1.4 + 1.2 * r.nextDouble();
        b.phaseA = r.nextDouble() * Math.PI * 2;
        b.phaseB = r.nextDouble() * Math.PI * 2;
        return b;
    }

    /**
     * Petal/sepal surface. A meridian curve is integrated along the blade (bending outward, lips
     * rolling back), then the blade is spread around the flower axis by arc length so that the
     * small inner petals wrap the heart while the large outer ones only cup.
     */
    static void addBlade(RoseMesh mesh, Blade b, double[] frame, double[] offset,
                         int nu, int nv, byte material, int part) {
        double[][] pts = new double[nu * nv][8];
        for (int j = 0; j < nv; j++) {
            double v = -1 + 2.0 * j / (nv - 1), av = Math.abs(v);
            double lenF = b.pointed ? 1 - .10 * av * av
                    : (1 - .20 * Math.pow(av, 2.2) - b.notch * Math.exp(-(v / .16) * (v / .16)))
                    * (1 + .035 * StrictMath.sin(v * 5.1 + b.phaseA) + .018 * StrictMath.sin(v * 12.7 + b.phaseB));
            double len = b.length * lenF, ds = len / (nu - 1);
            double rho = b.rho0, h = b.z0;
            for (int i = 0; i < nu; i++) {
                double s = i / (double) (nu - 1);
                if (i > 0) {
                    double phiMid = bladePhi(b, (i - .5) / (nu - 1), v);
                    rho += StrictMath.sin(phiMid) * ds;
                    h += StrictMath.cos(phiMid) * ds;
                }
                double phi = bladePhi(b, s, v);
                double halfW = b.halfWidth * bladeWidth(b, s, v) * (1 + b.asym * v);
                double w = v * halfW;
                double cupOff = b.cup * v * v * (.3 + s) * (1 - .8 * RoseMath.smooth(.7, 1, s))
                        - b.roll * Math.pow(RoseMath.smooth(.5, 1, s), 1.6) * Math.pow(av, 1.4);
                double r2 = rho - cupOff * StrictMath.cos(phi);
                double h2 = h + cupOff * StrictMath.sin(phi);
                double alpha = b.theta + RoseMath.clamp(w / Math.max(rho, .035), -2.6, 2.6) + b.twist * s;
                double lx = r2 * StrictMath.cos(alpha), lz = r2 * StrictMath.sin(alpha);
                double[] p = pts[i * nv + j];
                p[0] = frame[0] * lx + frame[1] * h2 + frame[2] * lz;
                p[1] = frame[3] * lx + frame[4] * h2 + frame[5] * lz;
                p[2] = frame[6] * lx + frame[7] * h2 + frame[8] * lz;
                if (offset != null) { p[0] += offset[0]; p[1] += offset[1]; p[2] += offset[2]; }
                p[3] = s; p[4] = v; p[5] = s * len; p[6] = w;
                p[7] = Math.max(RoseMath.smooth(.72, 1, s), RoseMath.smooth(.70, 1, av) * (.4 + .6 * s));
            }
        }
        mesh.addGrid(nu, nv, material, part, false, (i, j, out) -> System.arraycopy(pts[i * nv + j], 0, out, 0, 8));
    }

    private static double bladePhi(Blade b, double s, double v) {
        double lip = RoseMath.smooth(.62, 1, s);
        double wave = b.ruffle * RoseMath.smooth(.55, 1, s) * StrictMath.sin(v * Math.PI * b.ruffleFreq + b.phaseA);
        return b.phi0 + b.bend * Math.pow(s, 1.5) + b.lip * lip * lip * (1 + .6 * v * v) + wave;
    }

    private static double bladeWidth(Blade b, double s, double v) {
        if (b.pointed) {
            double body = Math.pow(1 - s, .85) * (.55 + .45 * StrictMath.sin(Math.PI * Math.min(1, s * 1.25)));
            boolean bearded = b.beard > 0 && v > 0;
            double lobes = bearded && s > .18 && s < .82
                    ? .35 * Math.pow(Math.max(0, StrictMath.sin(s * Math.PI * 9)), 8) : 0;
            return body + lobes;
        }
        return .24 + .76 * Math.pow(StrictMath.sin(Math.min(1, s / .78) * Math.PI / 2), .65);
    }

    private static void addSepals(RoseMesh mesh, RoseDrawing.BloomStage bloom, double[] frame, Random r, int detail) {
        double open = bloom == RoseDrawing.BloomStage.BUD ? 42 : bloom == RoseDrawing.BloomStage.FULL ? 112 : 100;
        double start = r.nextDouble() * Math.PI * 2;
        int[] beards = {1, 0, 0, 0, 0};
        for (int k = 0; k < 5; k++) {
            Blade b = new Blade();
            b.pointed = true;
            b.beard = beards[k];
            b.theta = start + k * Math.PI * 2 / 5 + (r.nextDouble() - .5) * .25;
            b.phi0 = Math.toRadians(open + r.nextGaussian() * 6);
            b.bend = Math.toRadians(38 + r.nextGaussian() * 8);
            b.length = .62 + .06 * r.nextGaussian();
            b.halfWidth = .07;
            b.rho0 = .12; b.z0 = -.11; b.cup = .02; b.roll = 0;
            b.twist = .15 * r.nextGaussian();
            addBlade(mesh, b, frame, null, 10 + 4 * detail, 9 + 2 * detail, RoseMesh.SEPAL, 600 + k);
        }
    }

    private static void addReceptacle(RoseMesh mesh, double[] frame, int detail) {
        int nu = 10 + 4 * detail, nv = 14 + 4 * detail;
        double[] ys = {-.34, -.29, -.21, -.13, -.07, -.035};
        double[] rs = {.052, .075, .125, .150, .140, .100};
        mesh.addGrid(nu, nv, RoseMesh.RECEPTACLE, 700, true, (i, j, out) -> {
            double t = i / (double) (nu - 1) * (ys.length - 1);
            int k = Math.min(ys.length - 2, (int) t);
            double f = RoseMath.smoothstep(t - k);
            double yy = RoseMath.lerp(ys[k], ys[k + 1], f), rr = RoseMath.lerp(rs[k], rs[k + 1], f);
            double a = -2 * Math.PI * j / nv;
            double lx = rr * StrictMath.cos(a), lz = rr * StrictMath.sin(a);
            out[0] = frame[0] * lx + frame[1] * yy + frame[2] * lz;
            out[1] = frame[3] * lx + frame[4] * yy + frame[5] * lz;
            out[2] = frame[6] * lx + frame[7] * yy + frame[8] * lz;
            out[3] = i / (double) (nu - 1); out[4] = j / (double) nv;
            out[5] = yy; out[6] = a * rr; out[7] = 0;
        });
    }

    // ------------------------------------------------------------------ stem, thorns, leaves

    private static double[] stemPoint(double[] c, double t) {
        double a = 1 - t;
        double[] p = new double[3];
        for (int k = 0; k < 3; k++)
            p[k] = a * a * a * c[k] + 3 * a * a * t * c[3 + k] + 3 * a * t * t * c[6 + k] + t * t * t * c[9 + k];
        return p;
    }

    /** Cubic Bezier control points of the stem: leaves the receptacle along the flower axis, S-curves to the ground. */
    private static double[] stemCurve(double[] frame, Random r) {
        double[] base = RoseMath.apply(frame, 0, -.33, 0);
        double[] down = RoseMath.apply(frame, 0, -1, 0);
        double sway = (r.nextDouble() - .5) * .25;
        return new double[]{
                base[0], base[1], base[2],
                base[0] + down[0] * .75, base[1] + down[1] * .75, base[2] + down[2] * .75,
                .38 + sway, -1.42, .05,
                -.16 - sway * .5, FLOOR_Y - .12, -.08};
    }

    private static double[] addStem(RoseMesh mesh, double[] c, Random r, int detail) {
        int nu = 40 + 25 * detail, nv = 10 + 4 * detail;
        Tube tube = new Tube(nu, t -> stemPoint(c, t), t -> .050 + .009 * t);
        tube.addTo(mesh, nv, RoseMesh.STEM, 800);
        double[] thornT = {.27, .41, .55, .70, .84};
        for (int k = 0; k < thornT.length; k++) {
            double t = thornT[k] + (r.nextDouble() - .5) * .04;
            double around = r.nextDouble() * Math.PI * 2;
            addThorn(mesh, tube, t, around, .8 + .4 * r.nextDouble(), detail, 820 + k);
        }
        double[] foot = stemPoint(c, 1);
        return new double[]{foot[0], foot[2]};
    }

    /** A curve with a frame (parallel transport) that can be skinned into a tube. */
    static final class Tube {
        final int nu;
        final double[][] p, tangent, n1, n2;
        final double[] radius;

        Tube(int nu, java.util.function.DoubleFunction<double[]> curve, java.util.function.DoubleUnaryOperator r) {
            this.nu = nu;
            p = new double[nu][]; tangent = new double[nu][]; n1 = new double[nu][]; n2 = new double[nu][];
            radius = new double[nu];
            for (int i = 0; i < nu; i++) { p[i] = curve.apply(i / (double) (nu - 1)); radius[i] = r.applyAsDouble(i / (double) (nu - 1)); }
            for (int i = 0; i < nu; i++) {
                double[] a = p[Math.max(0, i - 1)], b = p[Math.min(nu - 1, i + 1)];
                tangent[i] = RoseMath.normalize(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            }
            double[] ref = Math.abs(tangent[0][1]) < .9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
            double[] first = RoseMath.cross(ref, tangent[0]);
            n1[0] = RoseMath.normalize(first[0], first[1], first[2]);
            for (int i = 0; i < nu; i++) {
                if (i > 0) {
                    double[] prev = n1[i - 1], t = tangent[i];
                    double dot = prev[0] * t[0] + prev[1] * t[1] + prev[2] * t[2];
                    n1[i] = RoseMath.normalize(prev[0] - dot * t[0], prev[1] - dot * t[1], prev[2] - dot * t[2]);
                }
                n2[i] = RoseMath.cross(tangent[i], n1[i]);
            }
        }

        double[] surface(int i, double angle, double scale) {
            double ca = StrictMath.cos(angle) * radius[i] * scale, sa = StrictMath.sin(angle) * radius[i] * scale;
            return new double[]{p[i][0] + ca * n1[i][0] + sa * n2[i][0],
                    p[i][1] + ca * n1[i][1] + sa * n2[i][1],
                    p[i][2] + ca * n1[i][2] + sa * n2[i][2]};
        }

        void addTo(RoseMesh mesh, int nv, byte material, int part) {
            mesh.addGrid(nu, nv, material, part, true, (i, j, out) -> {
                double a = 2 * Math.PI * j / nv;
                double[] q = surface(i, a, 1);
                out[0] = q[0]; out[1] = q[1]; out[2] = q[2];
                out[3] = i / (double) (nu - 1); out[4] = j / (double) nv;
                out[5] = i * 3.4 / (nu - 1); out[6] = a * radius[i]; out[7] = 0;
            });
        }
    }

    private static void addThorn(RoseMesh mesh, Tube stem, double t, double around, double size, int detail, int part) {
        int i = (int) Math.round(t * (stem.nu - 1));
        double[] root = stem.surface(i, around, .55);
        double ca = StrictMath.cos(around), sa = StrictMath.sin(around);
        double[] out = {ca * stem.n1[i][0] + sa * stem.n2[i][0], ca * stem.n1[i][1] + sa * stem.n2[i][1],
                ca * stem.n1[i][2] + sa * stem.n2[i][2]};
        double[] along = stem.tangent[i];
        double len = .13 * size;
        Tube thorn = new Tube(6 + 2 * detail, s -> new double[]{
                root[0] + (out[0] * s + along[0] * .55 * s * s) * len,
                root[1] + (out[1] * s + along[1] * .55 * s * s) * len,
                root[2] + (out[2] * s + along[2] * .55 * s * s) * len},
                s -> .034 * size * Math.pow(1 - s, 1.25) + .0008);
        thorn.addTo(mesh, 7 + detail, RoseMesh.THORN, part);
    }

    /**
     * Compound rose leaf: petiole + rachis with two pairs of lateral leaflets and a terminal one.
     */
    private static void addLeaf(RoseMesh mesh, double[] stem, Random r, int detail, double stemT, double[] direction,
                                double[] up, double size, int part) {
        double[] node = stemPoint(stem, stemT);
        double[] d = RoseMath.normalize(direction[0], direction[1], direction[2]);
        double[] upN = RoseMath.normalize(up[0], up[1], up[2]);
        double rachisLen = 1.05 * size;
        java.util.function.DoubleFunction<double[]> rachis = s -> new double[]{
                node[0] + d[0] * rachisLen * s,
                node[1] + d[1] * rachisLen * s + .22 * size * StrictMath.sin(Math.PI * s * .8) - .18 * size * s * s,
                node[2] + d[2] * rachisLen * s};
        new Tube(8 + 4 * detail, rachis, s -> .016 * (1 - .5 * s)).addTo(mesh, 6, RoseMesh.STEM, part);
        double[] side = RoseMath.normalize(RoseMath.cross(d, upN));
        double[][] leaflets = {{.36, -1, .70}, {.36, 1, .72}, {.70, -1, .86}, {.70, 1, .88}, {1.0, 0, 1.0}};
        for (int k = 0; k < leaflets.length; k++) {
            double s = leaflets[k][0], sideSign = leaflets[k][1], scale = leaflets[k][2] * size;
            double[] at = rachis.apply(s);
            double spread = sideSign * Math.toRadians(52 + 8 * r.nextGaussian());
            double[] dir = RoseMath.normalize(
                    d[0] * StrictMath.cos(spread) + side[0] * StrictMath.sin(spread) - .12 * upN[0],
                    d[1] * StrictMath.cos(spread) + side[1] * StrictMath.sin(spread) - .12 * upN[1],
                    d[2] * StrictMath.cos(spread) + side[2] * StrictMath.sin(spread) - .12 * upN[2]);
            double[] tiltUp = RoseMath.normalize(upN[0] + side[0] * sideSign * .25, upN[1], upN[2] + side[2] * sideSign * .25);
            addLeaflet(mesh, at, dir, tiltUp, .56 * scale, .21 * scale, r, detail, part + 1 + k);
        }
    }

    private static void addLeaflet(RoseMesh mesh, double[] base, double[] d, double[] upHint, double length,
                                   double width, Random r, int detail, int part) {
        double[] side = RoseMath.normalize(RoseMath.cross(d, upHint));
        double[] up = RoseMath.cross(side, d);
        double teeth = 19 + 4 * r.nextDouble(), arch = .08 + .03 * r.nextGaussian(), fold = .26, phase = r.nextDouble();
        int nu = 14 + 6 * detail, nv = 9 + 4 * detail;
        mesh.addGrid(nu, nv, RoseMesh.LEAF, part, false, (i, j, out) -> {
            double u = i / (double) (nu - 1), v = -1 + 2.0 * j / (nv - 1);
            double hw = width * Math.pow(StrictMath.sin(Math.PI * Math.pow(u, .92)), .62) * (1 - .25 * u * u);
            double tooth = 1 - (u * teeth + phase) % 1.0;
            hw *= 1 - .045 * tooth * RoseMath.smooth(.08, .22, u) * (1 - RoseMath.smooth(.88, 1, u));
            double along = u * length;
            double lift = arch * StrictMath.sin(Math.PI * u) * length - .10 * u * u * length;
            double lat = v * hw, rise = fold * Math.abs(v) * hw - .18 * v * v * v * v * hw;
            for (int k = 0; k < 3; k++)
                out[k] = base[k] + d[k] * along + up[k] * (lift + rise) + side[k] * lat;
            out[3] = u; out[4] = v; out[5] = along; out[6] = lat;
            out[7] = RoseMath.smooth(.75, 1, Math.abs(v));
        });
    }

    // ------------------------------------------------------------------ fallen petals

    private static double[][] addFallenPetals(RoseMesh mesh, Random r, int detail) {
        double[][] spots = {{-1.10, .10, .62}, {1.20, .38, .52}, {-.48, .62, .40}};
        double[][] result = new double[spots.length][];
        for (int k = 0; k < spots.length; k++) {
            double scale = spots[k][2];
            Blade b = new Blade();
            b.theta = r.nextDouble() * Math.PI * 2;
            b.phi0 = Math.toRadians(76); b.bend = Math.toRadians(10); b.lip = Math.toRadians(-24);
            b.length = .78 * scale; b.halfWidth = .40 * scale; b.rho0 = 0; b.z0 = 0;
            b.cup = .22 * scale; b.roll = .10 * scale; b.notch = .03;
            b.ruffle = Math.toRadians(4); b.ruffleFreq = 1.7; b.phaseA = r.nextDouble() * 6; b.phaseB = r.nextDouble() * 6; b.asym = .1 * r.nextGaussian(); b.twist = .2 * r.nextGaussian();
            double cx = spots[k][0], cz = spots[k][1];
            double[] offset = {cx - StrictMath.cos(b.theta) * .36 * scale, FLOOR_Y + .012, cz - StrictMath.sin(b.theta) * .36 * scale};
            addBlade(mesh, b, RoseMath.IDENTITY, offset, 10 + 4 * detail, 12 + 5 * detail, RoseMesh.FALLEN, 950 + k);
            result[k] = new double[]{cx, cz, .42 * scale};
        }
        return result;
    }
}
