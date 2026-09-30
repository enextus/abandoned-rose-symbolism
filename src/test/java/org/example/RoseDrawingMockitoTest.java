package org.example;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
class RoseDrawingMockitoTest {
    @Test void paintsRoseWithoutErrors() {
        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            RoseDrawing panel = new RoseDrawing(); panel.setSize(600,600);
            BufferedImage image = new BufferedImage(600,600,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();
            try {panel.paint(g);} finally {g.dispose();}
        }));
    }
}
