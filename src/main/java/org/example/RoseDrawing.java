package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class RoseDrawing extends JPanel {
    private static final int VW = 1000, VH = 1000;

    // единые хинты (не создаём каждый раз)
    private static final RenderingHints HINTS = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    static {
        HINTS.put(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
        HINTS.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private final BufferedImage image;

    public RoseDrawing() {
        setOpaque(true);
        setBackground(Color.WHITE);

        // 1) Буфер, совместимый с текущей ГПУ-конфигурацией + OPAQUE (быстрее, чем ARGB для белого фона)
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        image = gc.createCompatibleImage(VW, VH, Transparency.OPAQUE);

        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHints(HINTS);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, VW, VH);

            final int squareSize = 333;
            final int centerX = 500, centerY = 500, petal = 300;
            final int p2 = petal / 2, p4 = petal / 4, p6 = petal / 6;

            // фон
            g2.setColor(Color.BLUE);
            g2.fillRect(0, 0, squareSize, squareSize);
            g2.setColor(Color.YELLOW);
            g2.fillRect(squareSize, squareSize, squareSize, squareSize);
            g2.setColor(Color.BLACK);
            g2.fillRect(2 * squareSize, 0, squareSize, 2 * squareSize);

            // стебель и листья (остаются за бутоном)
            g2.setColor(Color.BLACK);
            g2.fillRect(centerX - p6, centerY + p2, petal / 3, petal);
            g2.fillOval(centerX - p2, centerY + petal, p2, petal / 4);
            g2.fillOval(centerX,       centerY + petal, p2, petal / 4);

            // лепестки
            g2.setColor(Color.RED);
            g2.fillOval(centerX - p2, centerY - petal, petal, petal);     // верхний
            g2.fillOval(centerX - p2, centerY,         petal, petal);     // нижний
            g2.fillOval(centerX - petal, centerY - p2, petal, petal);     // левый
            g2.fillOval(centerX,         centerY - p2, petal, petal);     // правый

            // центр
            g2.setColor(Color.WHITE);
            g2.fillOval(centerX - p4, centerY - p4, p2, p2);

            // 2) контур — ПОСЛЕ лепестков, чтобы он был виден
            g2.setColor(Color.BLACK);
            g2.drawOval(centerX - p4, centerY - p4, p2, p2);
        } finally {
            g2.dispose();
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(VW, VH); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // 3) Явная интерполяция при масштабировании (bilinear — компромисс скорость/качество)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            double sx = w / (double) VW, sy = h / (double) VH;
            double s  = Math.min(sx, sy);
            int dw = (int) Math.round(VW * s), dh = (int) Math.round(VH * s);
            int dx = (w - dw) / 2, dy = (h - dh) / 2;

            // быстрее, чем transform+drawImage в некоторых пайплайнах
            g2.drawImage(image, dx, dy, dw, dh, null);
        } finally {
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Роза для тебя");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new RoseDrawing());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
