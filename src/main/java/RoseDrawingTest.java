package main.java;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

import java.awt.Graphics;

public class RoseDrawingTest {

    @Test
    public void testRoseDrawingCreation() {
        RoseDrawing roseDrawing = new RoseDrawing();
        assertNotEquals(null, roseDrawing);
    }

    @Test
    public void testPaintComponent() {
        RoseDrawing roseDrawing = new RoseDrawing();
        Graphics graphics = mock(Graphics.class);
        roseDrawing.paintComponent(graphics);

        // Проверяем, были ли вызваны методы объекта Graphics
        verify(graphics, atLeastOnce()).setColor(any());
        verify(graphics, atLeastOnce()).fillRect(anyInt(), anyInt(), anyInt(), anyInt());
        verify(graphics, atLeastOnce()).fillOval(anyInt(), anyInt(), anyInt(), anyInt());
        verify(graphics, atLeastOnce()).drawOval(anyInt(), anyInt(), anyInt(), anyInt());
    }
}
