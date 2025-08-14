package org.example;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Graphics;

import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RoseDrawingMockitoTest {

    @Mock
    Graphics g;

    @Test
    void paintComponent_doesNotExplode() {
        var panel = new RoseDrawing();
        // просто вызываем paint — цель: убедиться, что NPE нет и мок не требует специфики
        panel.paint(g);
        verifyNoMoreInteractions(g);
    }
}
