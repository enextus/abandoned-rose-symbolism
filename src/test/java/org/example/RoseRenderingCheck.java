package org.example;
import java.awt.image.BufferedImage;
/** Run explicitly using test.sh/test.bat; no external test framework needed. */
public final class RoseRenderingCheck {
    public static void main(String[] args) {
        BufferedImage a=RoseDrawing.renderScene(400,400), b=RoseDrawing.renderScene(400,400);
        int red=0, dark=0;
        for(int y=0;y<400;y++) for(int x=0;x<400;x++) {
            int rgb=a.getRGB(x,y);
            if(rgb!=b.getRGB(x,y)) throw new AssertionError("Non-deterministic render");
            int r=(rgb>>16)&255, g=(rgb>>8)&255, blue=rgb&255;
            if(r>90 && r>g*1.6 && r>blue*1.3) red++;
            if(r<70 && g<80 && blue<80) dark++;
        }
        if(red<12000 || dark<60000) throw new AssertionError("Missing rose/background: "+red+"/"+dark);
        BufferedImage wide=RoseDrawing.renderScene(800,400);
        long error=0;
        for(int y=0;y<400;y++) for(int x=0;x<400;x++) {
            int u=a.getRGB(x,y),v=wide.getRGB(x+200,y);
            for(int shift=0;shift<=16;shift+=8) error+=Math.abs(((u>>shift)&255)-((v>>shift)&255));
        }
        // Translating an antialiased path can change boundary rounding by a pixel.
        if(error/(400.0*400*3)>1.0) throw new AssertionError("Aspect ratio/centering: "+error);
        BufferedImage tall=RoseDrawing.renderScene(400,800);
        if(tall.getRGB(200,0)!=wide.getRGB(0,200)) throw new AssertionError("Letterbox color");
        RoseDrawing panel=new RoseDrawing(); panel.setSize(320,480);
        BufferedImage ui=new BufferedImage(320,480,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics=ui.createGraphics();
        try { panel.paint(graphics); panel.paint(graphics); panel.setSize(480,320); panel.paint(graphics); }
        finally { graphics.dispose(); }
        try {RoseDrawing.renderScene(0,10);throw new AssertionError("Missing validation");}
        catch(IllegalArgumentException expected) { }
        System.out.println("PASS: deterministic rendering, rose/background, aspect ratio, portrait, Swing paint/resize, invalid dimensions");
    }
}
