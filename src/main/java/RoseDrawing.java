package main.java;

import javax.swing.*;
import java.awt.*;

public class RoseDrawing extends JPanel {

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
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Роза для тебя");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new RoseDrawing());
        frame.setSize(300, 300);
        frame.setVisible(true);
    }

}
