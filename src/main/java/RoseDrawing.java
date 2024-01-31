

import javax.swing.*;
import java.awt.*;

public class RoseDrawing extends JPanel {

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Определяем размеры и положение для фона
        int squareSize = 333; // Размер квадрата для фона
        // Рисуем фон
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, squareSize, squareSize); // верхний левый квадрат
        g.setColor(Color.YELLOW);
        g.fillRect(squareSize, squareSize, squareSize, squareSize); // нижний правый квадрат
        g.setColor(Color.BLACK);
        g.fillRect(2 * squareSize, 0, squareSize, 2 * squareSize); // правая часть

        // Рисуем лепестки розы
        int centerX = 500; // центр розы по горизонтали
        int centerY = 500; // центр розы по вертикали
        int petalSize = 300; // размер лепестка
        g.setColor(Color.RED);
        g.fillOval(centerX - petalSize / 2, centerY - petalSize, petalSize, petalSize); // верхний лепесток
        g.fillOval(centerX - petalSize / 2, centerY, petalSize, petalSize); // нижний лепесток
        g.fillOval(centerX - petalSize, centerY - petalSize / 2, petalSize, petalSize); // левый лепесток
        g.fillOval(centerX, centerY - petalSize / 2, petalSize, petalSize); // правый лепесток

        // Рисуем стебель и листья
        g.setColor(Color.BLACK);
        g.fillRect(centerX - petalSize / 6, centerY + petalSize / 2, petalSize / 3, petalSize); // стебель
        g.fillOval(centerX - petalSize / 2, centerY + petalSize, petalSize / 2, petalSize / 4); // нижний лист
        g.fillOval(centerX, centerY + petalSize, petalSize / 2, petalSize / 4); // нижний лист

        // Рисуем внутренние детали розы
        g.setColor(Color.WHITE);
        g.fillOval(centerX - petalSize / 4, centerY - petalSize / 4, petalSize / 2, petalSize / 2); // центральный круг
        g.setColor(Color.BLACK);
        g.drawOval(centerX - petalSize / 4, centerY - petalSize / 4, petalSize / 2, petalSize / 2); // контур центрального круга
    }




    public static void main(String[] args) {
        JFrame frame = new JFrame("Роза для тебя");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new RoseDrawing());
        frame.setSize(300, 300);
        frame.setVisible(true);
    }

}
