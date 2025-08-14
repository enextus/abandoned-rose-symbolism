import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class RoseDrawing extends JPanel {
    // Логический «виртуальный» размер холста (в этих координатах описана сцена)
    private static final int VW = 1000;
    private static final int VH = 1000;

    public RoseDrawing() {
        setBackground(Color.WHITE);
    }

    @Override
    public Dimension getPreferredSize() {
        // Окно по умолчанию ~1000×1000, чтобы всё было видно без ресайза
        return new Dimension(VW, VH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Мягкие края фигур
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Масштабирование сцены VW×VH -> фактический размер панели, с сохранением пропорций и центрированием
            double sx = getWidth() / (double) VW;
            double sy = getHeight() / (double) VH;
            double s = Math.min(sx, sy);
            double tx = (getWidth() - VW * s) / 2.0;
            double ty = (getHeight() - VH * s) / 2.0;

            AffineTransform old = g2.getTransform();
            g2.translate(tx, ty);
            g2.scale(s, s);

            int squareSize = 333;

            // Фон
            g2.setColor(Color.BLUE);
            g2.fillRect(0, 0, squareSize, squareSize); // верхний левый квадрат

            g2.setColor(Color.YELLOW);
            g2.fillRect(squareSize, squareSize, squareSize, squareSize); // нижний правый из центрального блока 2×2

            g2.setColor(Color.BLACK);
            g2.fillRect(2 * squareSize, 0, squareSize, 2 * squareSize); // правая «полоса»

            // Роза
            int centerX = 500; // центр розы по X
            int centerY = 500; // центр розы по Y
            int petalSize = 300; // размер лепестка

            // Стебель и листья
            g2.setColor(Color.BLACK);
            g2.fillRect(centerX - petalSize / 6, centerY + petalSize / 2, petalSize / 3, petalSize); // стебель
            g2.fillOval(centerX - petalSize / 2, centerY + petalSize, petalSize / 2, petalSize / 4); // лист слева
            g2.fillOval(centerX, centerY + petalSize, petalSize / 2, petalSize / 4); // лист справа

            g2.setColor(Color.BLACK);
            g2.drawOval(centerX - petalSize / 4, centerY - petalSize / 4, petalSize / 2, petalSize / 2); // контур

            g2.setColor(Color.RED);
            g2.fillOval(centerX - petalSize / 2, centerY - petalSize, petalSize, petalSize); // верхний лепесток
            g2.fillOval(centerX - petalSize / 2, centerY, petalSize, petalSize); // нижний лепесток
            g2.fillOval(centerX - petalSize, centerY - petalSize / 2, petalSize, petalSize); // левый лепесток
            g2.fillOval(centerX, centerY - petalSize / 2, petalSize, petalSize); // правый лепесток

            // Внутренние детали
            g2.setColor(Color.WHITE);
            g2.fillOval(centerX - petalSize / 4, centerY - petalSize / 4, petalSize / 2, petalSize / 2); // центральный круг

            g2.setTransform(old);
        } finally {
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Роза для тебя");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new RoseDrawing());
            frame.pack(); // подгоняем под preferred size панели
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

}
