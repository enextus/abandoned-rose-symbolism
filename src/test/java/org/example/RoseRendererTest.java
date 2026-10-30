package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.*;

/** Focused tests for the 3D pipeline: geometry, lighting, palette, rasterizer and views. */
class RoseRendererTest {
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    // ------------------------------------------------------------------ views

    @Test void viewValidatesAndTurnsWithinLimits() {
        assertEquals(new RoseDrawing.View(0, 0), RoseDrawing.View.DEFAULT);
        for (double[] bad : new double[][]{{Double.NaN, 0}, {Double.POSITIVE_INFINITY, 0}, {0, Double.NaN},
                {0, Double.NEGATIVE_INFINITY}, {0, -30.5}, {0, 45.5}})
            assertThrows(IllegalArgumentException.class, () -> new RoseDrawing.View(bad[0], bad[1]));
        RoseDrawing.View v = RoseDrawing.View.DEFAULT.turned(-30, 100);
        assertEquals(330, v.yaw(), 1e-9);
        assertEquals(RoseDrawing.View.MAX_PITCH, v.pitch(), 1e-9);
        assertEquals(RoseDrawing.View.MIN_PITCH, v.turned(0, -500).pitch(), 1e-9);
        assertEquals(10, new RoseDrawing.View(350, 0).turned(20, 0).yaw(), 1e-9);
        assertEquals(0, new RoseDrawing.View(90, 0).turned(270, 0).yaw(), 1e-9);
    }

    @Test void viewsAreDeterministicAndActuallyTurnTheRose() {
        var flower = RoseDrawing.DEFAULT_FLOWER;
        int[] front = pixels(RoseDrawing.renderScene(240, 240, RoseDrawing.ORIGINAL, flower));
        assertArrayEquals(front, pixels(RoseDrawing.renderScene(240, 240, RoseDrawing.ORIGINAL, flower, RoseDrawing.View.DEFAULT)));
        var turned = new RoseDrawing.View(120, 20);
        int[] side = pixels(RoseDrawing.renderScene(240, 240, RoseDrawing.ORIGINAL, flower, turned));
        assertFalse(Arrays.equals(front, side), "Turning must change the picture");
        assertArrayEquals(side, pixels(RoseDrawing.renderScene(240, 240, RoseDrawing.ORIGINAL, flower, turned)));
        assertThrows(NullPointerException.class, () -> RoseDrawing.renderScene(10, 10, RoseDrawing.ORIGINAL, flower, null));
    }

    @Test void previewQualityIsCheaperButStillARose() {
        var look = RoseDrawing.ORIGINAL;
        BufferedImage preview = RoseRenderer.render(300, 300, look, RoseDrawing.DEFAULT_FLOWER,
                RoseDrawing.View.DEFAULT, RoseRenderer.Quality.PREVIEW);
        BufferedImage fine = RoseDrawing.renderScene(300, 300);
        assertFalse(Arrays.equals(pixels(preview), pixels(fine)));
        int red = 0;
        for (int rgb : pixels(preview)) {
            int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255;
            if (r > 90 && r > 1.6 * g) red++;
        }
        assertTrue(red > 5000, "Preview lost the flower: " + red);
        assertEquals(1, RoseRenderer.supersamplingFor(300, RoseRenderer.Quality.PREVIEW));
        assertEquals(3, RoseRenderer.supersamplingFor(300, RoseRenderer.Quality.FINAL));
        assertEquals(2, RoseRenderer.supersamplingFor(1600, RoseRenderer.Quality.FINAL));
        assertEquals(0, RoseRenderer.detailFor(1600, RoseRenderer.Quality.PREVIEW));
        assertEquals(2, RoseRenderer.detailFor(1600, RoseRenderer.Quality.FINAL));
        assertThrows(NullPointerException.class, () -> RoseRenderer.render(10, 10, look,
                RoseDrawing.DEFAULT_FLOWER, RoseDrawing.View.DEFAULT, null));
    }

    @Test void renderingIsBitIdenticalOnOneCoreAndOnMany() throws Exception {
        var look = new RoseDrawing.Appearance(42, .3f, .9f, 1.1f, RoseDrawing.Style.ART_NOUVEAU);
        var flower = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 6, true);
        RoseRenderer.clearCache();
        int[] many = pixels(RoseDrawing.renderScene(260, 260, look, flower));
        RoseRenderer.clearCache();
        ForkJoinPool single = new ForkJoinPool(1);
        try {
            int[] one = pixels(single.submit(() -> RoseDrawing.renderScene(260, 260, look, flower)).get());
            assertArrayEquals(many, one, "Parallel bands must not change a single pixel");
        } finally {
            single.shutdown();
        }
    }

    @ParameterizedTest @EnumSource(RoseDrawing.Style.class)
    void everyStyleRendersEveryBloomWithDew(RoseDrawing.Style style) {
        var look = new RoseDrawing.Appearance(7, .62f, 1, 1, style);
        for (RoseDrawing.BloomStage stage : RoseDrawing.BloomStage.values()) {
            var dry = new RoseDrawing.FlowerOptions(stage, 4, false);
            var wet = new RoseDrawing.FlowerOptions(stage, 4, true);
            assertFalse(Arrays.equals(pixels(RoseDrawing.renderScene(220, 220, look, dry)),
                    pixels(RoseDrawing.renderScene(220, 220, look, wet))), style + " " + stage + ": dew invisible");
        }
    }

    @Test void largeImagesBypassTheBackdropCacheButRenderTheSame() {
        BufferedImage big = RoseRenderer.render(2060, 2060, RoseDrawing.ORIGINAL, RoseDrawing.DEFAULT_FLOWER,
                RoseDrawing.View.DEFAULT, RoseRenderer.Quality.PREVIEW);
        BufferedImage again = RoseRenderer.render(2060, 2060, RoseDrawing.ORIGINAL, RoseDrawing.DEFAULT_FLOWER,
                RoseDrawing.View.DEFAULT, RoseRenderer.Quality.PREVIEW);
        assertArrayEquals(pixels(big), pixels(again));
    }

    @Test void fadedInkLetterboxMatchesTheFadedGrade() {
        var faded = new RoseDrawing.Appearance(1, 0, 1, 1, RoseDrawing.Style.FADED_INK);
        BufferedImage wide = RoseDrawing.renderScene(300, 200, faded);
        assertEquals(new java.awt.Color(35, 38, 38).getRGB(), wide.getRGB(0, 0));
    }

    // ------------------------------------------------------------------ palette & lighting

    @Test void hueWalksNaturalRoseColoursAndKeepsLeavesGreen() {
        double[] crimson = RosePalette.roseColours(0, 1), ivory = RosePalette.roseColours(.5, 1);
        assertTrue(crimson[3] > 20 * crimson[4], "Crimson body must be deep red");
        assertTrue(ivory[4] > .6 && ivory[5] > .4, "Ivory must be light in every channel");
        assertArrayEquals(crimson, RosePalette.roseColours(1, 1), 1e-12);
        assertArrayEquals(crimson, RosePalette.roseColours(-1, 1), 1e-12);
        double[] grey = RosePalette.roseColours(.2, 1e-6);
        assertEquals(grey[3], grey[4], 1e-4);
        var mesh = RoseGeometry.build(5, RoseDrawing.DEFAULT_FLOWER, 0).mesh;
        float[] red = RosePalette.albedo(mesh, new RoseDrawing.Appearance(5, 0, 1, 1, RoseDrawing.Style.VELVET), 35);
        float[] yellow = RosePalette.albedo(mesh, new RoseDrawing.Appearance(5, .375f, 1, 1, RoseDrawing.Style.VELVET), 35);
        for (int v = 0; v < mesh.vertexCount; v++) {
            boolean flower = mesh.material[v] == RoseMesh.PETAL || mesh.material[v] == RoseMesh.FALLEN;
            if (mesh.material[v] == RoseMesh.LEAF) {
                assertEquals(red[6 * v + 1], yellow[6 * v + 1], 0f, "Leaves must not follow the rose colour");
                assertTrue(red[6 * v + 1] > red[6 * v + 2], "Leaves must stay green");
            }
            if (flower && v % 97 == 0) assertNotEquals(red[6 * v + 1], yellow[6 * v + 1]);
        }
    }

    @Test void toneMappingKeepsHueAndClamps() {
        float[] rgb = {.5f, .05f, .02f};
        RoseLighting.toneMap(rgb);
        assertEquals(.1, rgb[1] / rgb[0], 1e-6, "Below the shoulder hue ratios are preserved");
        float[] hot = {40, 30, 20};
        RoseLighting.toneMap(hot);
        assertEquals(1, hot[0], 1e-6, "Brightest channel saturates");
        for (float c : hot) assertTrue(c > .7 && c <= 1, "Strong highlights roll off towards white");
        float[] black = {0, 0, 0};
        RoseLighting.toneMap(black);
        assertArrayEquals(new float[]{0, 0, 0}, black, 0f);
        assertEquals(0, RoseMath.filmic(-1), 0);
        assertEquals(1, RoseMath.filmic(1e9), 1e-6);
    }

    @Test void shadowMapOccludesWhatIsBehindASurface() {
        // One big triangle in the plane y = 0, light from straight above.
        float[] x = {-1, 1, 0}, y = {0, 0, 0}, z = {-1, -1, 1};
        var map = new RoseLighting.ShadowMap(x, y, z, 3, new int[]{0, 1, 2}, 1, new double[]{0, 1, 0}, 64);
        assertEquals(1, map.visibility(0, .2, -.2, 1, map.texel), 1e-9, "Above the roof is lit");
        assertEquals(0, map.visibility(0, -.2, -.2, 1, map.texel), 1e-9, "Below the roof is shadowed");
        assertEquals(1f, map.visibility4(0, .2f, -.2f), 1e-6f);
        assertEquals(0f, map.visibility4(0, -.2f, -.2f), 1e-6f);
        assertEquals(1f, map.visibility4(50, -5, 50), 0f, "Outside the map nothing occludes");
        var degenerate = new RoseLighting.ShadowMap(new float[]{0, 0, 0}, new float[]{0, 0, 0}, new float[]{0, 0, 0},
                3, new int[]{0, 1, 2}, 1, new double[]{0, 1, 0}, 16);
        assertEquals(1, degenerate.visibility(0, 1, 0, 0, 0), 1e-9);
        var sideways = new RoseLighting.ShadowMap(x, y, z, 3, new int[]{0, 1, 2}, 1, new double[]{0, 0, 1}, 32);
        assertTrue(sideways.visibility(0, 0, 2, 0, sideways.texel) > .99);
    }

    @Test void ambientOcclusionDarkensTheHeartOfTheRose() {
        var model = RoseGeometry.build(1913, RoseDrawing.DEFAULT_FLOWER, 0);
        float[] ambient = RoseLighting.ambient(model.mesh, 160);
        RoseMesh m = model.mesh;
        double outer = 0, inner = 0;
        int[] first = model.petalGrids[0], last = model.petalGrids[model.petalGrids.length - 1];
        for (int k = 0; k < first[1]; k++) outer += ambient[6 * (first[0] + k)];
        for (int k = 0; k < last[1]; k++) inner += ambient[6 * (last[0] + k)];
        assertTrue(outer / first[1] > 1.5 * inner / last[1], "Inner petals must receive less sky light");
        assertEquals(6 * m.vertexCount, ambient.length);
    }

    // ------------------------------------------------------------------ geometry

    @Test void geometryIsDeterministicAndDetailOnlyChangesTessellation() {
        var flower = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.CLASSIC, 3, false);
        var a = RoseGeometry.build(99, flower, 0).mesh;
        var b = RoseGeometry.build(99, flower, 0).mesh;
        assertEquals(a.vertexCount, b.vertexCount);
        assertArrayEquals(Arrays.copyOf(a.x, a.vertexCount), Arrays.copyOf(b.x, b.vertexCount), 0f);
        var fine = RoseGeometry.build(99, flower, 2).mesh;
        assertTrue(fine.triangleCount > 2 * a.triangleCount);
        assertEquals(23, RoseGeometry.petalCount(flower));
        assertEquals(53, RoseGeometry.petalCount(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 8, true)));
        for (int v = 0; v < a.vertexCount; v++) {
            double len = Math.sqrt(a.nx[v] * a.nx[v] + a.ny[v] * a.ny[v] + a.nz[v] * a.nz[v]);
            assertEquals(1, len, 1e-4, "Normals must be unit length");
        }
        var other = RoseGeometry.build(100, flower, 0).mesh;
        assertFalse(Arrays.equals(Arrays.copyOf(a.x, 500), Arrays.copyOf(other.x, 500)), "Seed must shape the petals");
    }

    @Test void stemNormalsPointOutwardAndFallenPetalsLieOnTheGround() {
        var model = RoseGeometry.build(3, RoseDrawing.DEFAULT_FLOWER, 1);
        RoseMesh m = model.mesh;
        int fallen = 0;
        for (int v = 0; v < m.vertexCount; v++) {
            if (m.material[v] == RoseMesh.FALLEN) {
                fallen++;
                assertTrue(m.y[v] > RoseGeometry.FLOOR_Y - .05 && m.y[v] < RoseGeometry.FLOOR_Y + .4);
            }
        }
        assertTrue(fallen > 100);
        assertEquals(3, model.fallen.length);
        // A straight tube along -y: every normal must point away from its axis.
        RoseMesh tube = new RoseMesh();
        new RoseGeometry.Tube(5, t -> new double[]{0, -t, 0}, t -> .1).addTo(tube, 8, RoseMesh.STEM, 1);
        for (int v = 0; v < tube.vertexCount; v++)
            assertTrue(tube.nx[v] * tube.x[v] + tube.nz[v] * tube.z[v] > 0, "Tube normal points inward");
        var horizontal = new RoseGeometry.Tube(4, t -> new double[]{0, 1, 0}, t -> .05);
        assertEquals(4, horizontal.nu);
    }

    @Test void meshGrowsAndHandlesDegenerateGrids() {
        RoseMesh mesh = new RoseMesh();
        mesh.addGrid(70, 70, RoseMesh.LEAF, 1, false, (i, j, out) -> {
            out[0] = i; out[1] = 0; out[2] = -j;
        });
        assertEquals(4900, mesh.vertexCount);
        assertEquals(2 * 69 * 69, mesh.triangleCount);
        assertEquals(-1, mesh.ny[0], 1e-6, "dP/dv x dP/du with v along -z points down");
        mesh.addGrid(3, 3, RoseMesh.LEAF, 2, false, (i, j, out) -> { });
        assertEquals(1, mesh.ny[mesh.vertexCount - 1], 0, "Degenerate patch falls back to +Y");
    }

}
