package org.example;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoseDrawingTest {
    @Test
    void preferredSize_isVirtualCanvas() {
        var panel = new RoseDrawing();
        assertEquals(new Dimension(1000, 1000), panel.getPreferredSize());
    }
}
