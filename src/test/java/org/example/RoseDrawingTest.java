package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class RoseDrawingTest {
    @TempDir Path temp;
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    private static BufferedImage paint(RoseDrawing panel, int w, int h) {
        panel.setSize(w,h);
        BufferedImage image=new BufferedImage(Math.max(1,w),Math.max(1,h),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {panel.paintComponent(g);} finally {g.dispose();}
        return image;
    }
    @Test void originalHasRecognizableRoseAndDarkBackground() {
        BufferedImage image=RoseDrawing.renderScene(400,400);
        int red=0,dark=0;
        for(int rgb:pixels(image)) {
            int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
            if(r>90 && r>g*1.6 && r>b*1.3) red++;
            if(r<70 && g<80 && b<80) dark++;
        }
        assertTrue(red>12000,"Red flower is absent");
        assertTrue(dark>60000,"Dark composition is absent");
        assertArrayEquals(pixels(image),pixels(RoseDrawing.renderScene(400,400)));
        assertArrayEquals(pixels(image),pixels(RoseDrawing.renderScene(400,400,RoseDrawing.ORIGINAL)));
    }
    @ParameterizedTest @CsvSource({"0,10","10,0","-1,10","10,-1"})
    void rejectsInvalidDimensions(int w,int h) {
        assertThrows(IllegalArgumentException.class,()->RoseDrawing.renderScene(w,h));
    }
    @Test void rejectsNullAppearance() {
        assertThrows(NullPointerException.class,()->RoseDrawing.renderScene(10,10,null));
    }
    @ParameterizedTest @CsvSource({"NaN,1,1","Infinity,1,1","0,NaN,1","0,Infinity,1","0,1,NaN","0,1,Infinity","0,0,1","0,-1,1","0,1,0","0,1,-1"})
    void rejectsInvalidAppearance(float hue,float saturation,float exposure) {
        assertThrows(IllegalArgumentException.class,()->new RoseDrawing.Appearance(1,hue,saturation,exposure,RoseDrawing.Style.VELVET));
    }
    @Test void rejectsNullStyle() {
        assertThrows(IllegalArgumentException.class,()->new RoseDrawing.Appearance(1,0,1,1,null));
    }
    @ParameterizedTest @EnumSource(RoseDrawing.Style.class)
    void eachStyleIsDeterministicAndDistinct(RoseDrawing.Style style) {
        var a=new RoseDrawing.Appearance(777,.3f,.8f,1.2f,style);
        BufferedImage first=RoseDrawing.renderScene(260,260,a);
        assertArrayEquals(pixels(first),pixels(RoseDrawing.renderScene(260,260,a)));
        assertFalse(Arrays.equals(pixels(first),pixels(RoseDrawing.renderScene(260,260))));
        for(var other:RoseDrawing.Style.values()) if(other!=style) {
            var b=new RoseDrawing.Appearance(777,.3f,.8f,1.2f,other);
            assertFalse(Arrays.equals(pixels(first),pixels(RoseDrawing.renderScene(260,260,b))),"Styles should differ: "+other);
        }
    }
    @Test void paletteToneAndSeedEachAffectPixels() {
        int[] baseline=pixels(RoseDrawing.renderScene(220,220));
        for(var a:new RoseDrawing.Appearance[]{
                new RoseDrawing.Appearance(1913,.4f,1,1,RoseDrawing.Style.VELVET),
                new RoseDrawing.Appearance(1913,0,.5f,1,RoseDrawing.Style.VELVET),
                new RoseDrawing.Appearance(1913,0,1,1.2f,RoseDrawing.Style.VELVET),
                new RoseDrawing.Appearance(567,0,1,1,RoseDrawing.Style.VELVET),
                new RoseDrawing.Appearance(1913,.9f,2,2,RoseDrawing.Style.VELVET)})
            assertFalse(Arrays.equals(baseline,pixels(RoseDrawing.renderScene(220,220,a))));
    }
    @ParameterizedTest @CsvSource({"600,300","300,600","301,457"})
    void centersSquareWithoutStretching(int w,int h) {
        int side=Math.min(w,h),dx=(w-side)/2,dy=(h-side)/2;
        BufferedImage actual=RoseDrawing.renderScene(w,h),square=RoseDrawing.renderScene(side,side);
        long error=0;
        for(int y=0;y<side;y++) for(int x=0;x<side;x++) {
            int a=actual.getRGB(x+dx,y+dy),b=square.getRGB(x,y);
            for(int shift=0;shift<=16;shift+=8) error+=Math.abs(((a>>>shift)&255)-((b>>>shift)&255));
        }
        assertTrue(error/(side*(double)side*3)<1,"Composition is stretched or off-center");
        assertEquals(new Color(17,22,23).getRGB(),actual.getRGB(0,0));
    }
    @Test void smallestImageRenders() {
        BufferedImage image=RoseDrawing.renderScene(1,1);
        assertEquals(1,image.getWidth());assertEquals(1,image.getHeight());
    }
    @Test void panelCachesAndResizesWithoutChangingAppearance() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RoseDrawing panel=new RoseDrawing();
            assertEquals(new Dimension(1000,1000),panel.getPreferredSize());
            assertEquals(RoseDrawing.ORIGINAL,panel.getAppearance());
            assertDoesNotThrow(()->paint(panel,0,10));
            assertDoesNotThrow(()->paint(panel,10,0));
            assertArrayEquals(pixels(paint(panel,240,240)),pixels(paint(panel,240,240)));
            assertArrayEquals(pixels(RoseDrawing.renderScene(280,240)),pixels(paint(panel,280,240)));
            assertArrayEquals(pixels(RoseDrawing.renderScene(280,300)),pixels(paint(panel,280,300)));
            assertEquals(RoseDrawing.ORIGINAL,panel.getAppearance());
        });
    }
    @Test void navigationRequiresEdt() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        RoseDrawing panel = new RoseDrawing();
        assertThrows(IllegalStateException.class, panel::redraw);
        assertThrows(IllegalStateException.class, panel::back);
        assertThrows(IllegalStateException.class, panel::forward);
    }
    @Test void backForwardAndRedrawManagePreviewHistory() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RoseDrawing panel = new RoseDrawing();
            assertFalse(panel.canGoBack());
            assertFalse(panel.canGoForward());
            assertFalse(panel.back());
            assertFalse(panel.forward());

            panel.redraw();
            RoseDrawing.Appearance first = panel.getAppearance();
            panel.redraw();
            RoseDrawing.Appearance second = panel.getAppearance();
            assertNotEquals(first, second);
            assertTrue(panel.canGoBack());
            assertFalse(panel.canGoForward());

            assertTrue(panel.back());
            assertEquals(first, panel.getAppearance());
            assertTrue(panel.canGoForward());
            assertTrue(panel.back());
            assertEquals(RoseDrawing.ORIGINAL, panel.getAppearance());
            assertFalse(panel.canGoBack());
            assertTrue(panel.canGoForward());
            assertFalse(panel.back());

            assertTrue(panel.forward());
            assertEquals(first, panel.getAppearance(), "FORWARD must restore the exact saved preview");
            assertTrue(panel.forward());
            assertEquals(second, panel.getAppearance(), "FORWARD must restore the next saved preview");
            assertFalse(panel.canGoForward());
            assertFalse(panel.forward());

            assertTrue(panel.back());
            assertEquals(first, panel.getAppearance());
            panel.redraw();
            RoseDrawing.Appearance branch = panel.getAppearance();
            assertNotEquals(second, branch, "REDRAW after BACK must create a new branch");
            assertFalse(panel.canGoForward(), "A fresh REDRAW discards the abandoned forward branch");
        });
    }
    @Test void redrawButtonChangesImageAndKeepsItOnResize() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JPanel content=RoseDrawing.createContent();
            BorderLayout layout=(BorderLayout)content.getLayout();
            RoseDrawing canvas=(RoseDrawing)layout.getLayoutComponent(BorderLayout.CENTER);
            JPanel toolbar=(JPanel)layout.getLayoutComponent(BorderLayout.SOUTH);
            JPanel buttonRow=(JPanel)toolbar.getComponent(0);
            JPanel backColumn=(JPanel)buttonRow.getComponent(0);
            JPanel redrawColumn=(JPanel)buttonRow.getComponent(2);
            JPanel forwardColumn=(JPanel)buttonRow.getComponent(4);
            JButton back=(JButton)backColumn.getComponent(0);
            JButton redraw=(JButton)redrawColumn.getComponent(0);
            JButton forward=(JButton)forwardColumn.getComponent(0);
            JLabel label=(JLabel)redrawColumn.getComponent(1);

            assertEquals("BACK",back.getText());
            assertFalse(back.isEnabled());
            assertNotNull(back.getIcon());
            assertNotNull(back.getRolloverIcon());
            assertNotNull(back.getPressedIcon());
            assertNotNull(back.getDisabledIcon());
            assertEquals(new Dimension(136,143),back.getPreferredSize());
            assertEquals("BACK",back.getAccessibleContext().getAccessibleName());

            assertEquals("REDRAW",redraw.getText());assertTrue(redraw.isEnabled());
            assertNotNull(redraw.getIcon());
            assertNotNull(redraw.getRolloverIcon());
            assertNotNull(redraw.getPressedIcon());
            assertEquals(new Dimension(300,143),redraw.getPreferredSize());
            assertEquals("REDRAW",redraw.getAccessibleContext().getAccessibleName());

            assertEquals("FORWARD",forward.getText());
            assertFalse(forward.isEnabled());
            assertNotNull(forward.getIcon());
            assertNotNull(forward.getRolloverIcon());
            assertNotNull(forward.getPressedIcon());
            assertNotNull(forward.getDisabledIcon());
            assertEquals(new Dimension(136,143),forward.getPreferredSize());
            assertEquals("FORWARD",forward.getAccessibleContext().getAccessibleName());

            int[] before=pixels(paint(canvas,260,260));
            redraw.doClick(0);
            var first=canvas.getAppearance();
            assertTrue(back.isEnabled());
            assertEquals(first.style().name().replace('_',' '),label.getText());
            int[] firstPixels=pixels(paint(canvas,260,260));
            assertFalse(Arrays.equals(before,firstPixels),"Stale image after redraw");

            redraw.doClick(0);
            var second=canvas.getAppearance();
            assertNotEquals(first,second);
            int[] secondPixels=pixels(paint(canvas,260,260));

            back.doClick(0);
            assertEquals(first,canvas.getAppearance());
            assertArrayEquals(firstPixels,pixels(paint(canvas,260,260)));
            assertEquals(first.style().name().replace('_',' '),label.getText());
            assertTrue(forward.isEnabled());

            forward.doClick(0);
            assertEquals(second,canvas.getAppearance(),"FORWARD must restore the saved preview after BACK");
            assertArrayEquals(secondPixels,pixels(paint(canvas,260,260)));
            assertFalse(forward.isEnabled());

            back.doClick(0);
            assertEquals(first,canvas.getAppearance());
            assertTrue(forward.isEnabled());
            redraw.doClick(0);
            assertNotEquals(second,canvas.getAppearance(),"REDRAW after BACK must create a new preview, not navigate forward");
            assertFalse(forward.isEnabled(),"New REDRAW branch must clear saved forward navigation");

            for(int i=0;i<10;i++) {
                var old=canvas.getAppearance();redraw.doClick(0);var next=canvas.getAppearance();
                assertNotEquals(old.style(),next.style());assertNotEquals(old.hueShift(),next.hueShift());
                assertTrue(next.saturation()>=.55f && next.saturation()<1.21f);
                assertTrue(next.exposure()>=.85f && next.exposure()<1.26f);
                assertEquals(next.style().name().replace('_',' '),label.getText());
            }
            var saved=canvas.getAppearance();int[] after=pixels(paint(canvas,260,260));
            assertArrayEquals(pixels(RoseDrawing.renderScene(260,260,saved)),after);
            assertArrayEquals(after,pixels(paint(canvas,260,260)));
            paint(canvas,340,220);assertEquals(saved,canvas.getAppearance());
        });
    }
    @Test void flowerOptionsValidateBoundsAndNulls() {
        assertEquals(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.CLASSIC, 5, false),
                RoseDrawing.DEFAULT_FLOWER);
        assertThrows(NullPointerException.class,
                () -> new RoseDrawing.FlowerOptions(null, 5, false));
        for (int layers : new int[]{-1, 0, 2, 9, 100})
            assertThrows(IllegalArgumentException.class,
                    () -> new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.CLASSIC, layers, false));
        for (int layers : new int[]{3, 5, 8})
            assertEquals(layers, new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, layers, true).petalLayers());
        assertThrows(NullPointerException.class,
                () -> RoseDrawing.renderScene(20, 20, RoseDrawing.ORIGINAL, null));
    }

    @Test void bloomStagePetalLayersAndDewIndependentlyChangeTheRose() {
        int[] original = pixels(RoseDrawing.renderScene(280, 280));
        assertArrayEquals(original, pixels(RoseDrawing.renderScene(280, 280,
                RoseDrawing.ORIGINAL, RoseDrawing.DEFAULT_FLOWER)));
        for (RoseDrawing.BloomStage stage : RoseDrawing.BloomStage.values()) {
            var options = new RoseDrawing.FlowerOptions(stage, 5, false);
            int[] first = pixels(RoseDrawing.renderScene(280, 280, RoseDrawing.ORIGINAL, options));
            assertArrayEquals(first, pixels(RoseDrawing.renderScene(280, 280, RoseDrawing.ORIGINAL, options)));
            if (stage != RoseDrawing.BloomStage.CLASSIC)
                assertFalse(Arrays.equals(original, first), "Bloom stage did not affect the image");
        }
        for (int layers : new int[]{3, 8}) {
            var options = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.CLASSIC, layers, false);
            assertFalse(Arrays.equals(original, pixels(RoseDrawing.renderScene(280, 280,
                    RoseDrawing.ORIGINAL, options))), "Petal ring count did not affect the image");
        }
        var dew = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.CLASSIC, 5, true);
        int[] dewy = pixels(RoseDrawing.renderScene(280, 280, RoseDrawing.ORIGINAL, dew));
        assertFalse(Arrays.equals(original, dewy), "Dew did not affect the image");
        assertArrayEquals(dewy, pixels(RoseDrawing.renderScene(280, 280, RoseDrawing.ORIGINAL, dew)));
    }

    @Test void flowerOptionsCreateReversibleHistoryWithoutChangingPalette() throws Exception {
        RoseDrawing panel = new RoseDrawing();
        var bud = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 4, false);
        var dewyBud = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 4, true);
        assertThrows(IllegalStateException.class, () -> panel.setFlowerOptions(bud));
        SwingUtilities.invokeAndWait(() -> {
            assertThrows(NullPointerException.class, () -> panel.setFlowerOptions(null));
            panel.setFlowerOptions(RoseDrawing.DEFAULT_FLOWER);
            assertFalse(panel.canGoBack());
            panel.setFlowerOptions(bud);
            assertEquals(RoseDrawing.ORIGINAL, panel.getAppearance());
            assertEquals(bud, panel.getFlowerOptions());
            int[] budPixels = pixels(paint(panel, 260, 260));
            panel.setFlowerOptions(bud);
            assertFalse(panel.canGoForward(), "No-op change must preserve history");
            panel.setFlowerOptions(dewyBud);
            assertArrayEquals(pixels(RoseDrawing.renderScene(260, 260, RoseDrawing.ORIGINAL, dewyBud)),
                    pixels(paint(panel, 260, 260)));
            assertTrue(panel.back());
            assertEquals(bud, panel.getFlowerOptions());
            assertArrayEquals(budPixels, pixels(paint(panel, 260, 260)));
            assertTrue(panel.forward());
            assertEquals(dewyBud, panel.getFlowerOptions());
            assertTrue(panel.back());
            panel.setFlowerOptions(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 8, false));
            assertFalse(panel.canGoForward(), "Editing after BACK must discard forward branch");
            panel.redraw();
            assertEquals(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 8, false),
                    panel.getFlowerOptions());
        });
    }

    @Test void guiFlowerControlsRestoreTheExactHistoryState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel content = RoseDrawing.createContent();
            JPanel toolbar = (JPanel) ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
            JPanel row = (JPanel) toolbar.getComponent(1);
            @SuppressWarnings("unchecked")
            JComboBox<RoseDrawing.BloomStage> bloom = (JComboBox<RoseDrawing.BloomStage>) row.getComponent(1);
            JSpinner layers = (JSpinner) row.getComponent(3);
            JCheckBox dew = (JCheckBox) row.getComponent(4);
            JPanel buttons = (JPanel) toolbar.getComponent(0);
            JButton back = (JButton) ((JPanel) buttons.getComponent(0)).getComponent(0);
            JButton forward = (JButton) ((JPanel) buttons.getComponent(4)).getComponent(0);
            RoseDrawing canvas = (RoseDrawing) ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.CENTER);

            bloom.setSelectedItem(RoseDrawing.BloomStage.BUD);
            layers.setValue(7);
            dew.doClick();
            assertEquals(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 7, true), canvas.getFlowerOptions());
            back.doClick();
            assertFalse(dew.isSelected());
            assertEquals(7, layers.getValue());
            back.doClick();
            assertEquals(5, layers.getValue());
            back.doClick();
            assertEquals(RoseDrawing.BloomStage.CLASSIC, bloom.getSelectedItem());
            assertFalse(back.isEnabled());
            for (int i = 0; i < 3; i++) forward.doClick();
            assertEquals(new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.BUD, 7, true), canvas.getFlowerOptions());
            assertFalse(forward.isEnabled());
        });
    }

    @Test void cliCanExportAllFlowerOptionsAndRejectsInvalidFlags() throws Exception {
        Path file = temp.resolve("dewy full rose.png");
        var flower = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 6, true);
        RoseDrawing.main(new String[]{"--export", file.toString(), "--bloom", "full", "--layers", "6", "--dew"});
        assertArrayEquals(pixels(RoseDrawing.renderScene(1600, 1600, RoseDrawing.ORIGINAL, flower)),
                pixels(ImageIO.read(file.toFile())));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--bloom"}));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--layers"}));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--layers", "abc"}));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--layers", "9"}));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--bloom", "unknown"}));
        assertThrows(IllegalArgumentException.class,
                () -> RoseDrawing.main(new String[]{"--export", file.toString(), "--dew", "unexpected"}));
    }

    @Test void exportsReadablePngAndReplacesExistingFile() throws Exception {
        Path file=temp.resolve("rose with spaces.png");
        java.nio.file.Files.writeString(file,"previous content");
        RoseDrawing.main(new String[]{"--export",file.toString()});
        BufferedImage image=ImageIO.read(file.toFile());
        assertNotNull(image);assertEquals(1600,image.getWidth());assertEquals(1600,image.getHeight());
        assertArrayEquals(pixels(RoseDrawing.renderScene(1600,1600)),pixels(image));
    }
    @Test void legacyLauncherExportsSameArtwork() throws Exception {
        Path file=temp.resolve("legacy.png");
        RoseDrawing_2.main(new String[]{"--export",file.toString()});
        assertArrayEquals(pixels(RoseDrawing.renderScene(1600,1600)),pixels(ImageIO.read(file.toFile())));
    }
    @Test
    void rejectsUnknownCommand() {
        assertThrows(IllegalArgumentException.class, () ->
                RoseDrawing.main(new String[]{"--unknown", "rose.png"})
        );
    }

    @Test
    void rejectsMissingOutputDirectory() {
        Path destination = temp.resolve("missing-directory").resolve("rose.png");

        PrintStream originalError = System.err;
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(capturedError)) {
            System.setErr(capture);

            assertThrows(IOException.class, () ->
                    RoseDrawing.main(new String[]{
                            "--export",
                            destination.toString()
                    })
            );

            assertFalse(java.nio.file.Files.exists(destination));
        } finally {
            System.setErr(originalError);
        }
    }
    @Test void rejectsMissingAndExcessArguments() {
        assertThrows(IllegalArgumentException.class,()->RoseDrawing.main(new String[]{"--export"}));
        assertThrows(IllegalArgumentException.class,()->RoseDrawing.main(new String[]{"--export","a","b"}));
    }
    @Test void noDisplayGivesActionableMessage() throws Exception {
        assertTrue(GraphicsEnvironment.isHeadless());
        PrintStream original=System.err;ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(PrintStream capture=new PrintStream(bytes)) {
            System.setErr(capture);RoseDrawing.main(new String[0]);
        } finally {System.setErr(original);}
        assertTrue(bytes.toString().contains("--export rose.png"));
    }
}
