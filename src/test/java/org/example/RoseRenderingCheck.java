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
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                javax.swing.JPanel content=RoseDrawing.createContent();
                RoseDrawing canvas=(RoseDrawing)content.getComponent(0);
                javax.swing.JPanel toolbar=(javax.swing.JPanel)content.getComponent(1);
                javax.swing.JPanel buttonRow=(javax.swing.JPanel)toolbar.getComponent(0);
                javax.swing.JPanel backColumn=(javax.swing.JPanel)buttonRow.getComponent(0);
                javax.swing.JPanel redrawColumn=(javax.swing.JPanel)buttonRow.getComponent(2);
                javax.swing.JButton back=(javax.swing.JButton)backColumn.getComponent(0);
                javax.swing.JButton redraw=(javax.swing.JButton)redrawColumn.getComponent(0);
                if(back.isEnabled()) throw new AssertionError("BACK must start disabled");
                canvas.setSize(300,300);
                BufferedImage before=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g=before.createGraphics();canvas.paint(g);g.dispose();
                redraw.doClick(0);
                RoseDrawing.Appearance first=canvas.getAppearance();
                if(!back.isEnabled()) throw new AssertionError("BACK was not enabled after REDRAW");
                redraw.doClick(0);
                RoseDrawing.Appearance second=canvas.getAppearance();
                back.doClick(0);
                if(!first.equals(canvas.getAppearance())) throw new AssertionError("BACK did not restore saved preview");
                redraw.doClick(0);
                if(!second.equals(canvas.getAppearance())) throw new AssertionError("REDRAW did not move forward to saved preview");
                for(int n=0;n<10;n++) {
                    RoseDrawing.Appearance old=canvas.getAppearance();
                    redraw.doClick(0);
                    RoseDrawing.Appearance next=canvas.getAppearance();
                    if(next.style()==old.style() || next.hueShift()==old.hueShift())
                        throw new AssertionError("Redraw did not change appearance");
                }
                BufferedImage after=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
                g=after.createGraphics();canvas.paint(g);g.dispose();
                if(java.util.Arrays.equals(before.getRGB(0,0,300,300,null,0,300),after.getRGB(0,0,300,300,null,0,300)))
                    throw new AssertionError("Stale render cache after REDRAW");
                RoseDrawing.Appearance saved=canvas.getAppearance();
                canvas.setSize(400,300);g=after.createGraphics();canvas.paint(g);g.dispose();
                if(!saved.equals(canvas.getAppearance())) throw new AssertionError("Resize randomized art");
            });
        } catch(Exception e) { throw new AssertionError("REDRAW control failed",e); }
        for(RoseDrawing.Style style:RoseDrawing.Style.values()) {
            RoseDrawing.Appearance look=new RoseDrawing.Appearance(123,.4f,.9f,1.1f,style);
            BufferedImage first=RoseDrawing.renderScene(240,240,look),second=RoseDrawing.renderScene(240,240,look);
            if(!java.util.Arrays.equals(first.getRGB(0,0,240,240,null,0,240),second.getRGB(0,0,240,240,null,0,240)))
                throw new AssertionError("Appearance not deterministic: "+style);
        }
        System.out.println("PASS: BACK/REDRAW history, new style/palette, cache invalidation, stable resize, all style renders");
        System.out.println("PASS: deterministic rendering, rose/background, aspect ratio, portrait, Swing paint/resize, invalid dimensions");
    }
}
