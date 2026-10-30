package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Turning, turntable, asynchronous on-screen painting and saving. */
class RoseInteractionTest {
    @TempDir Path temp;

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static BufferedImage paint(RoseDrawing panel, int w, int h) {
        panel.setSize(w, h);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paintComponent(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void awaitFrame(RoseDrawing panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!onEdt(panel::isUpToDate)) {
            assertTrue(System.nanoTime() < deadline, "Background frame never arrived");
            Thread.sleep(15);
        }
    }

    private static void mouse(RoseDrawing panel, int id, int x, int y, int clicks) {
        panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, clicks, false, MouseEvent.BUTTON1));
    }

    @Test void viewChangesRequireTheEdtAndStayOutOfHistory() throws Exception {
        RoseDrawing panel = new RoseDrawing();
        assertThrows(IllegalStateException.class, () -> panel.setView(new RoseDrawing.View(10, 0)));
        assertThrows(IllegalStateException.class, () -> panel.setTurntable(true));
        onEdt(() -> {
            assertEquals(RoseDrawing.View.DEFAULT, panel.getView());
            assertThrows(NullPointerException.class, () -> panel.setView(null));
            panel.setView(new RoseDrawing.View(40, 10));
            panel.setView(new RoseDrawing.View(40, 10));
            assertEquals(new RoseDrawing.View(40, 10), panel.getView());
            assertFalse(panel.canGoBack(), "Turning is not a history step");
            panel.resetView();
            assertEquals(RoseDrawing.View.DEFAULT, panel.getView());
            return null;
        });
    }

    @Test void arrowKeysAndHomeTurnTheRose() throws Exception {
        onEdt(() -> {
            RoseDrawing panel = new RoseDrawing();
            ActionMap actions = panel.getActionMap();
            ActionEvent event = new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "key");
            actions.get("turnLeft").actionPerformed(event);
            assertEquals(348, panel.getView().yaw(), 1e-9);
            actions.get("turnRight").actionPerformed(event);
            actions.get("turnRight").actionPerformed(event);
            assertEquals(12, panel.getView().yaw(), 1e-9);
            actions.get("tiltUp").actionPerformed(event);
            assertEquals(5, panel.getView().pitch(), 1e-9);
            actions.get("tiltDown").actionPerformed(event);
            actions.get("tiltDown").actionPerformed(event);
            assertEquals(-5, panel.getView().pitch(), 1e-9);
            actions.get("resetView").actionPerformed(event);
            assertEquals(RoseDrawing.View.DEFAULT, panel.getView());
            assertNotNull(panel.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT")));
            return null;
        });
    }

    @Test void draggingTurnsWithPreviewFramesAndDoubleClickResets() throws Exception {
        onEdt(() -> {
            RoseDrawing panel = new RoseDrawing();
            panel.setSize(360, 360);
            mouse(panel, MouseEvent.MOUSE_DRAGGED, 10, 10, 0);
            assertEquals(RoseDrawing.View.DEFAULT, panel.getView(), "A drag without a press does nothing");
            mouse(panel, MouseEvent.MOUSE_PRESSED, 100, 100, 1);
            mouse(panel, MouseEvent.MOUSE_DRAGGED, 130, 80, 0);
            RoseDrawing.View turned = panel.getView();
            assertEquals(30, turned.yaw(), 1e-9);
            assertEquals(-10, turned.pitch(), 1e-9);
            BufferedImage moving = paint(panel, 360, 360);
            assertArrayEquals(pixels(RoseRenderer.render(360, 360, RoseDrawing.ORIGINAL, RoseDrawing.DEFAULT_FLOWER,
                    turned, RoseRenderer.Quality.PREVIEW)), pixels(moving), "Dragging shows fast previews");
            mouse(panel, MouseEvent.MOUSE_RELEASED, 130, 80, 1);
            assertArrayEquals(pixels(RoseDrawing.renderScene(360, 360, RoseDrawing.ORIGINAL, RoseDrawing.DEFAULT_FLOWER, turned)),
                    pixels(paint(panel, 360, 360)), "Releasing settles on the full-quality frame");
            mouse(panel, MouseEvent.MOUSE_RELEASED, 130, 80, 1);
            mouse(panel, MouseEvent.MOUSE_CLICKED, 130, 80, 1);
            assertEquals(turned, panel.getView());
            mouse(panel, MouseEvent.MOUSE_CLICKED, 130, 80, 2);
            assertEquals(RoseDrawing.View.DEFAULT, panel.getView());
            return null;
        });
    }

    @Test void turntableSpinsUntilSwitchedOff() throws Exception {
        RoseDrawing panel = onEdt(RoseDrawing::new);
        onEdt(() -> {
            panel.setTurntable(true);
            panel.setTurntable(true);
            assertTrue(panel.isTurntable());
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (onEdt(() -> panel.getView().yaw()) < 3) {
            assertTrue(System.nanoTime() < deadline, "Turntable did not turn");
            Thread.sleep(20);
        }
        onEdt(() -> {
            panel.setTurntable(false);
            assertFalse(panel.isTurntable());
            return null;
        });
    }

    @Test void onScreenPaintingRendersInTheBackgroundAndScalesWhileWaiting() throws Exception {
        RoseDrawing panel = onEdt(RoseDrawing::new);
        panel.asyncOverride = Boolean.TRUE;
        BufferedImage first = onEdt(() -> paint(panel, 240, 240));
        int background = first.getRGB(120, 120);
        assertEquals(RoseDrawing.CANVAS_BACKGROUND.getRGB(), background, "Nothing to show before the first frame");
        awaitFrame(panel);
        int[] expected = pixels(RoseDrawing.renderScene(240, 240));
        assertArrayEquals(expected, pixels(onEdt(() -> paint(panel, 240, 240))));

        // Resize: the previous frame is shown scaled at once, then replaced by the exact one.
        BufferedImage stale = onEdt(() -> paint(panel, 300, 240));
        assertNotEquals(RoseDrawing.CANVAS_BACKGROUND.getRGB(), stale.getRGB(150, 100), "Resizing must not blank the rose");
        onEdt(() -> paint(panel, 320, 260));
        awaitFrame(panel);
        assertArrayEquals(pixels(RoseDrawing.renderScene(320, 260)), pixels(onEdt(() -> paint(panel, 320, 260))));

        // Latest wins: several changes while a frame renders only lead to the newest frame.
        onEdt(() -> {
            panel.redraw();
            paint(panel, 200, 200);
            panel.redraw();
            paint(panel, 200, 200);
            return null;
        });
        awaitFrame(panel);
        RoseDrawing.Appearance last = onEdt(panel::getAppearance);
        assertArrayEquals(pixels(RoseDrawing.renderScene(200, 200, last)), pixels(onEdt(() -> paint(panel, 200, 200))));
    }

    @Test void saveImageWritesTheCurrentViewAndValidatesArguments() throws Exception {
        RoseDrawing panel = onEdt(RoseDrawing::new);
        onEdt(() -> {
            panel.setFlowerOptions(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 4, true));
            panel.setView(new RoseDrawing.View(200, -10));
            return null;
        });
        File file = temp.resolve("saved.png").toFile();
        panel.saveImage(file, 200);
        assertArrayEquals(pixels(RoseDrawing.renderScene(200, 200, RoseDrawing.ORIGINAL,
                        new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 4, true), new RoseDrawing.View(200, -10))),
                pixels(ImageIO.read(file)));
        assertThrows(NullPointerException.class, () -> panel.saveImage(null, 10));
        assertThrows(IllegalArgumentException.class, () -> panel.saveImage(file, 0));
    }

    @Test
    void backgroundSaveReportsExpectedIoFailureWithoutPollutingStderr() throws Exception {
        RoseDrawing panel = onEdt(RoseDrawing::new);
        List<String> errors = new ArrayList<>();
        panel.saveErrors = errors::add;

        File good = temp.resolve("background.png").toFile();
        panel.saveInBackground(good).get(60, TimeUnit.SECONDS);
        BufferedImage saved = ImageIO.read(good);
        assertNotNull(saved);
        assertEquals(1600, saved.getWidth());
        assertEquals(1600, saved.getHeight());

        File bad = temp.resolve("missing").resolve("x.png").toFile();
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream capturedErr = new java.io.ByteArrayOutputStream();
        try (java.io.PrintStream capture = new java.io.PrintStream(capturedErr)) {
            System.setErr(capture);
            assertDoesNotThrow(() -> panel.saveInBackground(bad).get(60, TimeUnit.SECONDS));
            onEdt(() -> null); // flush the Swing error callback
        } finally {
            System.setErr(originalErr);
        }

        assertAll(
                () -> assertFalse(bad.exists(), "Failed save must not create a partial file"),
                () -> assertEquals(1, errors.size(), "Exactly one user-facing error is expected"),
                () -> assertTrue(errors.get(0).contains("Could not save")),
                () -> assertTrue(errors.get(0).contains("Output directory does not exist")),
                () -> assertEquals("", capturedErr.toString(), "Expected I/O failure must not print a stack trace")
        );
    }

    @Test
    void toolbarOffersAccessibleTurntableAndSaveControls() throws Exception {
        onEdt(() -> {
            JPanel content = RoseDrawing.createContent();
            RoseDrawing canvas = SwingTestSupport.findFirst(content, RoseDrawing.class);
            JCheckBox spin = SwingTestSupport.findByAccessibleName(content, JCheckBox.class, "Turntable");
            JButton save = SwingTestSupport.findByAccessibleName(content, JButton.class, "Save PNG");

            assertAll(
                    () -> assertEquals("Turntable", spin.getText()),
                    () -> assertEquals("Save PNG", save.getAccessibleContext().getAccessibleName()),
                    () -> assertEquals("Rose artwork", canvas.getAccessibleContext().getAccessibleName())
            );

            spin.doClick();
            assertTrue(canvas.isTurntable());
            spin.doClick();
            assertFalse(canvas.isTurntable());
            return null;
        });
    }
}
