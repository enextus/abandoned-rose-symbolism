package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/** Procedural Java2D painting. No downloaded images, fonts or runtime dependencies. */
public final class RoseDrawing extends JPanel {
    public enum Style { VELVET, ENGRAVING, ART_NOUVEAU, FADED_INK }
    public record Appearance(long seed, float hueShift, float saturation, float exposure, Style style) {
        public Appearance {
            if (style == null || !Float.isFinite(hueShift) || !Float.isFinite(saturation)
                    || !Float.isFinite(exposure) || saturation <= 0 || exposure <= 0)
                throw new IllegalArgumentException("Invalid appearance");
        }
    }
    public static final Appearance ORIGINAL = new Appearance(1913, 0, 1, 1, Style.VELVET);
    private final Random choices = new Random();
    private Appearance appearance = ORIGINAL;
    private BufferedImage cached;

    public Appearance getAppearance() { return appearance; }

    /** Call on the Swing event dispatch thread. Resizing never randomizes the art. */
    public void redraw() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("redraw must run on the EDT");
        Style[] styles = Style.values();
        Style next = styles[(appearance.style().ordinal() + 1 + choices.nextInt(styles.length - 1)) % styles.length];
        float hue = (appearance.hueShift() + .12f + choices.nextFloat() * .76f) % 1f;
        appearance = new Appearance(choices.nextLong(), hue,
                .55f + choices.nextFloat() * .65f, .85f + choices.nextFloat() * .40f, next);
        cached = null;
        repaint();
    }

    public static JPanel createContent() {
        RoseDrawing canvas = new RoseDrawing();
        JPanel content = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 10));
        toolbar.setBackground(new Color(17,22,23));
        JLabel label = new JLabel("VELVET"); label.setForeground(new Color(214,197,159));
        JButton button = new JButton("REDRAW");
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setToolTipText("Random color, tone and drawing style");
        button.getAccessibleContext().setAccessibleDescription("Generate a new rose variation");
        button.addActionListener(event -> {
            canvas.redraw(); label.setText(canvas.getAppearance().style().name().replace('_', ' '));
        });
        toolbar.add(button); toolbar.add(label);
        content.add(canvas, BorderLayout.CENTER); content.add(toolbar, BorderLayout.SOUTH);
        return content;
    }
    public RoseDrawing() { setBackground(new Color(17, 22, 23)); }
    @Override public Dimension getPreferredSize() { return new Dimension(1000, 1000); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        int w = getWidth(), h = getHeight();
        if (w < 1 || h < 1) return;
        if (cached == null || cached.getWidth() != w || cached.getHeight() != h)
            cached = renderScene(w, h, appearance);
        graphics.drawImage(cached, 0, 0, null);
    }
    public static BufferedImage renderScene(int width, int height) {
        return renderScene(width, height, ORIGINAL);
    }
    public static BufferedImage renderScene(int width, int height, Appearance appearance) {
        java.util.Objects.requireNonNull(appearance, "appearance");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Positive dimensions required");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(new Color(17,22,23)); g.fillRect(0,0,width,height);
            double scale = Math.min(width, height) / 1000.0;
            g.translate((width-1000*scale)/2, (height-1000*scale)/2); g.scale(scale,scale);
            paintArtwork(g, appearance);
        } finally { g.dispose(); }
        if (!appearance.equals(ORIGINAL)) tone(image, appearance);
        return image;
    }
    private static void tone(BufferedImage image, Appearance a) {
        float[] hsb = new float[3];
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            Color.RGBtoHSB((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, hsb);
            float saturation = Math.min(1, hsb[1] * a.saturation());
            float brightness = Math.min(1, hsb[2] * a.exposure());
            if (a.style() == Style.FADED_INK) { saturation *= .45f; brightness = .08f + brightness * .86f; }
            pixels[i] = Color.HSBtoRGB((hsb[0] + a.hueShift()) % 1f, saturation, brightness);
        }
    }
    private static Path2D path(double... p) {
        Path2D q = new Path2D.Double(); q.moveTo(p[0],p[1]);
        for (int i=2; i<p.length; i+=6) q.curveTo(p[i],p[i+1],p[i+2],p[i+3],p[i+4],p[i+5]);
        return q;
    }
    private static void paintArtwork(Graphics2D g, Appearance appearance) {
        Random random = new Random(appearance.seed());
        g.setPaint(new RadialGradientPaint(470,360,740,new float[]{0,.65f,1},
                new Color[]{new Color(65,66,51),new Color(27,36,35),new Color(9,15,19)}));
        g.fillRect(0,0,1000,1000);
        // Faded architectural remnants and a broken halo: time, absence, memory.
        g.setColor(new Color(158,145,104,22)); g.setStroke(new BasicStroke(22));
        g.draw(new Arc2D.Double(154,83,692,950,0,180,Arc2D.OPEN));
        g.drawLine(154,558,154,906); g.drawLine(846,558,846,906);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(191,156,93,100));
        g.draw(new Arc2D.Double(218,129,550,550,18,278,Arc2D.OPEN));
        g.setColor(new Color(191,156,93,37));
        g.draw(new Arc2D.Double(204,115,578,578,69,235,Arc2D.OPEN));
        for(int i=0;i<10500;i++) {
            int x=random.nextInt(1000), y=random.nextInt(1000);
            g.setColor(new Color(202,185,138,random.nextInt(15)+2));
            g.fillRect(x,y,1+random.nextInt(3),1+random.nextInt(3));
        }
        for(int i=0;i<24;i++) {
            double x=random.nextInt(1000), y=random.nextInt(1000);
            g.setColor(new Color(6,13,15,50)); g.setStroke(new BasicStroke(.7f));
            g.draw(path(x,y,x-20,y+26,x+22,y+45,x-8,y+95));
        }
        g.setPaint(new GradientPaint(0,790,new Color(10,17,19,0),0,1000,new Color(5,9,12,210)));
        g.fillRect(0,790,1000,210);
        Path2D stem=path(476,463,589,575,402,690,539,904);
        g.setStroke(new BasicStroke(13,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(11,23,20)); g.draw(stem);
        g.setStroke(new BasicStroke(6,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(455,500,new Color(109,119,66),545,890,new Color(36,51,41)));
        g.draw(stem);
        leaf(g,498,638,-2.75,1.05); leaf(g,488,714,-.65,.83);
        for(int i=0;i<4;i++) {
            double y=595+i*64, x= i==0?508:(i==1?494:(i==2?488:495));
            Path2D thorn=path(x,y,x+7,y-10,x+20,y-12,x+24,y-20,x+20,y-2,x+9,y+7,x,y+9);
            g.setColor(new Color(85,93,53)); g.fill(thorn);
        }
        // Calyx behind the flower.
        for(int i=0;i<5;i++) {
            Graphics2D c=(Graphics2D)g.create(); c.translate(475,473); c.rotate((i-2)*.34);
            c.setColor(new Color(58+i*5,72+i*4,43));
            c.fill(path(0,35,-34,1,-50,-37,-38,-86,-15,-34,7,-20,0,35)); c.dispose();
        }
        Graphics2D bloom=(Graphics2D)g.create(); bloom.translate(473,382); bloom.rotate(-.20 + (appearance.equals(ORIGINAL) ? 0 : (random.nextDouble()-.5)*.3));
        // Unequal overlapping cupped petals, outside first, tightly folded heart last.
        for(int ring=0;ring<5;ring++) {
            int count=appearance.style()==Style.ART_NOUVEAU ? (ring==0?7:5) : (ring==0?9:7);
            double radius=172-ring*31;
            for(int i=0;i<count;i++) {
                double angle=2*Math.PI*i/count+ring*.71;
                petal(bloom,angle,radius,.91+random.nextDouble()*.20,ring,appearance.style());
            }
        }
        bloom.setPaint(new RadialGradientPaint(0,0,25,new float[]{0,1},
            new Color[]{new Color(35,6,18),new Color(112,22,45)}));
        bloom.fill(new Ellipse2D.Double(-20,-17,40,34));
        bloom.setColor(new Color(227,113,115,160)); bloom.setStroke(new BasicStroke(1.8f));
        bloom.draw(path(-15,8,-30,-17,24,-25,15,2,8,16,-9,10,-3,-2)); bloom.dispose();
        // Fallen petals on the ground; the flower is still alive.
        fallen(g,343,864,-.4,1); fallen(g,650,913,.4,.78); fallen(g,399,935,2.8,.48);
        g.setColor(new Color(186,157,104,95)); g.setStroke(new BasicStroke(1));
        g.drawLine(74,77,74,136); g.drawLine(74,77,132,77);
        g.drawLine(926,923,868,923); g.drawLine(926,923,926,864);
        g.setFont(new Font(Font.SERIF,Font.PLAIN,15));
        g.setColor(new Color(199,184,151,150)); g.drawString("R O S A   /   R E L I C T A",74,958);
        g.setFont(new Font(Font.SERIF,Font.ITALIC,13));
        g.setColor(new Color(199,184,151,100)); g.drawString("what remains of tenderness",74,979);
    }
    private static void petal(Graphics2D parent,double angle,double radius,double variation,int ring,Style style) {
        Graphics2D g=(Graphics2D)parent.create();
        try {
            g.rotate(angle); g.translate(0,-radius*.40); g.scale(variation,1);
            double w=radius*(style==Style.ART_NOUVEAU?.58:.73), h=radius;
            Path2D p=path(-w*.78,5,-w*1.22,-h*.35,-w*.82,-h*.92,-w*.26,-h,
                w*.10,-h*1.10,w*.57,-h*.82,w*.92,-h*.65,
                w*1.17,-h*.08,w*.50,h*.54,0,h*.52,
                -w*.32,h*.40,-w*.64,h*.21,-w*.78,5);
            p.closePath();
            g.setPaint(new LinearGradientPaint(0,(float)-h,0,(float)(h*.52),new float[]{0,.22f,.62f,1},
                new Color[]{new Color(193+ring*6,67+ring*5,78+ring*5),new Color(139+ring*9,29+ring*3,52+ring*3),
                    new Color(80+ring*7,13,34),new Color(34,9,23)}));
            if (style == Style.ENGRAVING) g.setPaint(new Color(127+ring*9,47,61));
            g.fill(p); g.setStroke(new BasicStroke(style==Style.ART_NOUVEAU?2.8f:1.2f)); g.setColor(new Color(33,8,20,170)); g.draw(p);
            g.setColor(new Color(244,147,135,145)); g.setStroke(new BasicStroke(1.7f));
            g.draw(path(-w*.96,-h*.38,-w*.9,-h*.85,-w*.46,-h*1.08,-w*.26,-h,
                w*.1,-h*1.1,w*.57,-h*.82,w*.92,-h*.65));
            Shape clip=g.getClip(); g.clip(p);
            for(int k=0;k<18;k++) {
                double x=(k/17.0-.5)*w*1.9;
                g.setColor(new Color(238,140,130,16)); g.setStroke(new BasicStroke(.65f));
                g.draw(path(x,-h,x*.95,-h*.6,x*.4,-h*.1,0,h*.48));
            }
            if (style == Style.ENGRAVING || style == Style.FADED_INK) {
                g.setColor(new Color(27,8,22,style == Style.ENGRAVING?95:40));
                g.setStroke(new BasicStroke(.8f));
                for (double y=-h; y<h; y+=style==Style.ENGRAVING?5:9)
                    g.draw(new Line2D.Double(-w*1.3,y,w*1.3,y+w*.6));
            }
            g.setClip(clip);
        } finally { g.dispose(); }
    }
    private static void leaf(Graphics2D parent,double x,double y,double angle,double size) {
        Graphics2D g=(Graphics2D)parent.create(); g.translate(x,y); g.rotate(angle); g.scale(size,size);
        Path2D p=path(0,0,34,-53,91,-56,148,0,104,4,64,48,0,0);
        g.setPaint(new GradientPaint(0,-30,new Color(101,110,66),30,37,new Color(20,39,33)));
        g.fill(p); g.setColor(new Color(164,157,95,115)); g.setStroke(new BasicStroke(1));
        g.draw(path(0,0,41,-7,98,-7,148,0));
        for(int i=1;i<7;i++) {int a=i*18; g.drawLine(a,-4,a+15,-23+(i/2));g.drawLine(a,-4,a+10,15);}
        g.dispose();
    }
    private static void fallen(Graphics2D parent,double x,double y,double angle,double size) {
        Graphics2D g=(Graphics2D)parent.create();g.translate(x,y);g.rotate(angle);g.scale(size,size);
        g.setColor(new Color(0,0,0,85));g.fill(new Ellipse2D.Double(-45,3,105,19));
        Path2D p=path(-42,0,-34,-40,28,-31,59,6,24,2,4,39,-42,0);
        g.setPaint(new GradientPaint(0,-26,new Color(165,55,66),0,25,new Color(50,13,28)));g.fill(p);
        g.setColor(new Color(211,112,111,150));g.draw(path(-42,0,-20,-14,28,20,59,6));g.dispose();
    }
    public static void main(String[] args) throws IOException {
        if(args.length>0) {
            if(args.length!=2 || !"--export".equals(args[0])) throw new IllegalArgumentException("Usage: java -jar abandoned-rose-symbolism.jar [--export rose.png]");
            ImageIO.write(renderScene(1600,1600),"png",new File(args[1])); return;
        }
        if(GraphicsEnvironment.isHeadless()) {System.err.println("No display. Use --export rose.png");return;}
        SwingUtilities.invokeLater(()->{
            JFrame frame=new JFrame("Rosa relicta — Abandoned symbolism");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setContentPane(createContent());
            Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
            int size=Math.min(1000,Math.min(screen.width-80,screen.height-100));
            frame.setSize(Math.max(320,size),Math.max(320,size));frame.setLocationRelativeTo(null);frame.setVisible(true);
        });
    }
}
