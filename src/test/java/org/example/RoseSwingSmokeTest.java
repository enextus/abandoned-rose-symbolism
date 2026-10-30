package org.example;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Minimal Swing smoke test: constructing and painting the component must work on the EDT. */
class RoseSwingSmokeTest {

    @Test
    void componentCanBePaintedOffScreenOnTheEdt() {
        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            RoseDrawing panel = new RoseDrawing();
            panel.setSize(600, 600);

            BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }

            assertNotEquals(RoseDrawing.CANVAS_BACKGROUND.getRGB(), image.getRGB(300, 260),
                    "Painting should produce artwork, not only the canvas background");
        }));
    }
}
