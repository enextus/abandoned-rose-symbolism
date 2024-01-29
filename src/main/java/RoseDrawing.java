package main.java;

import javax.swing.*;
import java.awt.*;

public class RoseDrawing extends JPanel {

/*    // Метод paintComponent переопределен для кастомной отрисовки.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.RED);
        g.fillOval(100, 100, 50, 50);
        g.fillOval(120, 80, 50, 50);
        g.fillOval(140, 100, 50, 50);
        g.fillOval(120, 120, 50, 50);
        g.setColor(Color.GREEN);
        g.fillRect(135, 150, 10, 50);
    }*/

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Установка цветов
        Color redColor = new Color(255, 0, 0);
        Color blackColor = new Color(0, 0, 0);
        Color blueColor = new Color(0, 0, 255);
        Color yellowColor = new Color(255, 255, 0);

        // Фоновые блоки
        g.setColor(blueColor);
        g.fillRect(50, 50, 100, 100); // Синий фоновый квадрат

        g.setColor(yellowColor);
        g.fillRect(150, 50, 100, 100); // Желтый фоновый квадрат

        g.setColor(blackColor);
        g.fillRect(50, 150, 200, 50); // Черный фоновый прямоугольник

        // Рисование розы
        // Лепестки розы
        g.setColor(redColor);
        g.fillArc(100, 100, 100, 100, 0, 180); // Верхний лепесток
        g.fillArc(75, 125, 100, 100, 270, 180); // Левый лепесток
        g.fillArc(125, 125, 100, 100, 90, 180); // Правый лепесток
        g.fillArc(100, 150, 100, 100, 180, 180); // Нижний лепесток

        // Контур розы
        g.setColor(blackColor);
        g.drawArc(100, 100, 100, 100, 0, 180); // Верхний контур лепестка
        g.drawArc(75, 125, 100, 100, 270, 180); // Левый контур лепестка
        g.drawArc(125, 125, 100, 100, 90, 180); // Правый контур лепестка
        g.drawArc(100, 150, 100, 100, 180, 180); // Нижний контур лепестка

        // Стебель розы
        g.fillRect(145, 250, 10, 50); // Стебель

        // Листья розы
        g.fillOval(130, 275, 30, 15); // Левый лист
        g.fillOval(140, 275, 30, 15); // Правый лист
    }



    public static void main(String[] args) {
        JFrame frame = new JFrame("Роза для тебя");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new RoseDrawing());
        frame.setSize(300, 300);
        frame.setVisible(true);
    }

}
