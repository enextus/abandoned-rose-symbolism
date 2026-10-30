package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rosa relicta: a procedurally modelled, physically lit 3D rose painted with pure Java2D/Swing.
 * No downloaded images, fonts or runtime dependencies.
 *
 * <p>On screen the panel renders off the event dispatch thread (the window never freezes on
 * resize or REDRAW) and can be turned with the mouse or the arrow keys. Painting into an
 * off-screen graphics context (printing, tests, exports) stays synchronous and pixel-identical
 * to {@link #renderScene}.
 */
public final class RoseDrawing extends JPanel {
    private static final long serialVersionUID = 3L;

    public enum Style { VELVET, ENGRAVING, ART_NOUVEAU, FADED_INK }

    public record Appearance(long seed, float hueShift, float saturation, float exposure, Style style) {
        public Appearance {
            if (style == null || !Float.isFinite(hueShift) || !Float.isFinite(saturation)
                    || !Float.isFinite(exposure) || saturation <= 0 || exposure <= 0)
                throw new IllegalArgumentException("Invalid appearance");
        }
    }

    /** Independent, geometry-level controls for the flower. */
    public enum BloomStage { BUD, CLASSIC, FULL }

    public record FlowerOptions(BloomStage bloom, int petalLayers, boolean dew) {
        public FlowerOptions {
            Objects.requireNonNull(bloom, "bloom");
            if (petalLayers < 3 || petalLayers > 8)
                throw new IllegalArgumentException("Petal layers must be between 3 and 8");
        }
    }

    /** Camera around the rose in degrees: yaw turns the plant, pitch raises (+) or lowers (-) the eye. */
    public record View(double yaw, double pitch) {
        public static final View DEFAULT = new View(0, 0);
        public static final double MIN_PITCH = -30, MAX_PITCH = 45;

        public View {
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || pitch < MIN_PITCH || pitch > MAX_PITCH)
                throw new IllegalArgumentException("View needs a finite yaw and a pitch in [-30, 45]");
        }

        /** This view turned by the given amounts, pitch clamped to the allowed range, yaw kept in [0, 360). */
        public View turned(double deltaYaw, double deltaPitch) {
            double yaw2 = (yaw + deltaYaw) % 360;
            return new View(yaw2 < 0 ? yaw2 + 360 : yaw2,
                    Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + deltaPitch)));
        }
    }

    private record RoseState(Appearance appearance, FlowerOptions flower) { }

    /** What a rendered image shows; equal keys mean pixel-identical images. */
    private record FrameKey(int width, int height, Appearance appearance, FlowerOptions flower, View view,
                            RoseRenderer.Quality quality) { }

    public static final Appearance ORIGINAL = new Appearance(1913, 0, 1, 1, Style.VELVET);
    public static final FlowerOptions DEFAULT_FLOWER = new FlowerOptions(BloomStage.CLASSIC, 5, false);
    static final Color CANVAS_BACKGROUND = new Color(17, 22, 23);
    private static final Color TOOLBAR_BACKGROUND = new Color(20, 29, 27);
    private static final Color TEXT = new Color(214, 197, 159);

    /** One background thread renders frames for every on-screen panel; only the latest request matters. */
    private static final ExecutorService RENDER_THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "rose-render");
        thread.setDaemon(true);
        return thread;
    });

    private final transient Random choices = new Random();
    private final transient List<RoseState> history = new ArrayList<>();
    private int historyIndex;
    private transient Appearance appearance = ORIGINAL;
    private transient FlowerOptions flowerOptions = DEFAULT_FLOWER;
    private transient View view = View.DEFAULT;
    private transient BufferedImage shown;
    private transient FrameKey shownKey, inFlight, wanted;
    private boolean dragging, turntable;
    private final Timer spin = new Timer(33, event -> stepTurntable());

    public RoseDrawing() {
        setBackground(CANVAS_BACKGROUND);
        setFocusable(true);
        setToolTipText("Drag to turn the rose · arrow keys to nudge · double-click to reset");
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        getAccessibleContext().setAccessibleName("Rose artwork");
        getAccessibleContext().setAccessibleDescription(
                "Three-dimensional rose; drag or use arrow keys to turn it, Home to reset the view");
        history.add(new RoseState(ORIGINAL, DEFAULT_FLOWER));
        installTurning();
    }

    public Appearance getAppearance() { return appearance; }
    public FlowerOptions getFlowerOptions() { return flowerOptions; }
    public View getView() { return view; }
    public boolean canGoBack() { return historyIndex > 0; }
    public boolean canGoForward() { return historyIndex + 1 < history.size(); }
    /** Compatibility name retained for callers from 2.3.x. */
    public boolean hasSavedForwardPreview() { return canGoForward(); }

    /**
     * Generate a fresh variation from the currently displayed preview.
     * If the user previously navigated BACK, the abandoned forward branch is discarded
     * before the new preview is appended, exactly like starting a new browser-history branch.
     * Call on the Swing event dispatch thread. Resizing or turning never randomizes the art.
     */
    public void redraw() {
        requireEdt("redraw");
        commitState(createNextAppearance(), flowerOptions);
    }

    /** Restore the previously shown preview, if one exists. */
    public boolean back() {
        requireEdt("back");
        if (!canGoBack()) return false;
        restoreState(history.get(--historyIndex));
        return true;
    }

    /** Restore the next already-saved preview, if one exists. */
    public boolean forward() {
        requireEdt("forward");
        if (!canGoForward()) return false;
        restoreState(history.get(++historyIndex));
        return true;
    }

    /** Changing a flower control creates a navigable preview without randomizing the palette. */
    public void setFlowerOptions(FlowerOptions options) {
        requireEdt("setFlowerOptions");
        Objects.requireNonNull(options, "options");
        if (!flowerOptions.equals(options)) commitState(appearance, options);
    }

    /** Turns the camera. The view is not part of the BACK/FORWARD history, like the window size. */
    public void setView(View newView) {
        requireEdt("setView");
        Objects.requireNonNull(newView, "view");
        if (!view.equals(newView)) {
            view = newView;
            repaint();
        }
    }

    /** Back to the composed default angle. */
    public void resetView() { setView(View.DEFAULT); }

    public boolean isTurntable() { return turntable; }

    /** Slowly spins the rose (preview quality while moving, full quality once stopped). */
    public void setTurntable(boolean on) {
        requireEdt("setTurntable");
        if (turntable == on) return;
        turntable = on;
        if (on) spin.start(); else spin.stop();
        repaint();
    }

    private void stepTurntable() {
        if (inFlight == null) setView(view.turned(1.5, 0));
    }

    private void commitState(Appearance look, FlowerOptions flower) {
        if (canGoForward()) history.subList(historyIndex + 1, history.size()).clear();
        history.add(new RoseState(look, flower));
        historyIndex++;
        restoreState(history.get(historyIndex));
    }

    private void restoreState(RoseState state) {
        appearance = state.appearance();
        flowerOptions = state.flower();
        repaint();
    }

    private Appearance createNextAppearance() {
        Style[] styles = Style.values();
        Style next = styles[(appearance.style().ordinal() + 1 + choices.nextInt(styles.length - 1)) % styles.length];
        float hue = (appearance.hueShift() + .12f + choices.nextFloat() * .76f) % 1f;
        return new Appearance(choices.nextLong(), hue,
                .55f + choices.nextFloat() * .65f, .85f + choices.nextFloat() * .40f, next);
    }

    private static void requireEdt(String operation) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException(operation + " must run on the EDT");
    }

    // ------------------------------------------------------------------ turning with mouse and keys

    private void installTurning() {
        MouseAdapter mouse = new MouseAdapter() {
            private Point last;

            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                last = e.getPoint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (last == null) return;
                dragging = true;
                double unit = 360.0 / Math.max(300, Math.min(getWidth(), getHeight()));
                setView(view.turned((e.getX() - last.x) * unit, (e.getY() - last.y) * unit * .5));
                last = e.getPoint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                last = null;
                if (dragging) {
                    dragging = false;
                    repaint();
                }
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) resetView();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        bindKey(KeyEvent.VK_LEFT, "turnLeft", () -> setView(view.turned(-12, 0)));
        bindKey(KeyEvent.VK_RIGHT, "turnRight", () -> setView(view.turned(12, 0)));
        bindKey(KeyEvent.VK_UP, "tiltUp", () -> setView(view.turned(0, 5)));
        bindKey(KeyEvent.VK_DOWN, "tiltDown", () -> setView(view.turned(0, -5)));
        bindKey(KeyEvent.VK_HOME, "resetView", this::resetView);
    }

    private void bindKey(int key, String name, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction(name) {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
    }

    // ------------------------------------------------------------------ toolbar

    private static ImageIcon loadButtonIcon(String resourceName) {
        java.net.URL resource = RoseDrawing.class.getResource("/ui/" + resourceName);
        if (resource == null) throw new IllegalStateException("Missing UI resource: " + resourceName);
        return new ImageIcon(resource);
    }

    private static JButton configureArtworkButton(JButton button, String normal, String hover, String pressed,
                                                     Dimension size, String tooltip, String accessibleDescription) {
        button.setIcon(loadButtonIcon(normal));
        button.setRolloverIcon(loadButtonIcon(hover));
        button.setPressedIcon(loadButtonIcon(pressed));
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setForeground(new Color(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(button.getText());
        button.getAccessibleContext().setAccessibleDescription(accessibleDescription);
        return button;
    }

    private static JButton createBackButton() {
        JButton button = configureArtworkButton(new JButton("BACK"),
                "back-art-nouveau.png", "back-art-nouveau-hover.png", "back-art-nouveau-pressed.png",
                new Dimension(136, 143), "Previous saved preview", "Show the previously viewed rose preview");
        button.setDisabledIcon(loadButtonIcon("back-art-nouveau-disabled.png"));
        return button;
    }

    private static JButton createRedrawButton() {
        return configureArtworkButton(new JButton("REDRAW"),
                "redraw-art-nouveau.png", "redraw-art-nouveau-hover.png", "redraw-art-nouveau-pressed.png",
                new Dimension(300, 143), "Generate a new variation", "Generate a new rose variation from the current preview");
    }

    private static JButton createForwardButton() {
        JButton button = configureArtworkButton(new JButton("FORWARD"),
                "forward-art-nouveau.png", "forward-art-nouveau-hover.png", "forward-art-nouveau-pressed.png",
                new Dimension(136, 143), "Next saved preview", "Show the next already-viewed rose preview");
        button.setDisabledIcon(loadButtonIcon("forward-art-nouveau-disabled.png"));
        return button;
    }

    private static JLabel optionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SERIF, Font.PLAIN, 13));
        label.setForeground(TEXT);
        return label;
    }

    private static JCheckBox optionCheckBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(TEXT);
        return box;
    }

    private static JPanel createOptionsRow(JComboBox<BloomStage> bloom, JSpinner layers, JCheckBox dew,
                                           JCheckBox spinBox, JButton save) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 13, 3));
        row.setOpaque(false);
        row.add(optionLabel("Bloom"));
        row.add(bloom);
        row.add(optionLabel("Petal layers"));
        row.add(layers);
        row.add(dew);
        row.add(spinBox);
        row.add(save);
        bloom.setToolTipText("Bud / classic rose / fully opened bloom");
        layers.setToolTipText("Number of petal rings (3–8)");
        dew.setToolTipText("Add reproducible water droplets to the petals");
        spinBox.setToolTipText("Slowly turn the rose on a turntable");
        save.setToolTipText("Save the current view as a 1600 × 1600 PNG");
        bloom.getAccessibleContext().setAccessibleName("Bloom stage");
        layers.getAccessibleContext().setAccessibleName("Petal layers");
        dew.getAccessibleContext().setAccessibleName("Morning dew");
        spinBox.getAccessibleContext().setAccessibleName("Turntable");
        save.getAccessibleContext().setAccessibleName("Save PNG");
        return row;
    }

    public static JPanel createContent() {
        RoseDrawing canvas = new RoseDrawing();
        JPanel content = new JPanel(new BorderLayout());
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setBackground(TOOLBAR_BACKGROUND);
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 0, 7, 0));

        JButton backButton = createBackButton();
        JButton redrawButton = createRedrawButton();
        JButton forwardButton = createForwardButton();
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);

        JLabel label = new JLabel("VELVET");
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(new Font(Font.SERIF, Font.PLAIN, 12));
        label.setForeground(TEXT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(150, 18));
        label.setMinimumSize(new Dimension(150, 18));
        label.setMaximumSize(new Dimension(150, 18));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        JComboBox<BloomStage> bloomControl = new JComboBox<>(BloomStage.values());
        bloomControl.setSelectedItem(DEFAULT_FLOWER.bloom());
        JSpinner layersControl = new JSpinner(new SpinnerNumberModel(DEFAULT_FLOWER.petalLayers(), 3, 8, 1));
        JCheckBox dewControl = optionCheckBox("Morning dew");
        JCheckBox spinControl = optionCheckBox("Turntable");
        JButton saveButton = new JButton("Save PNG…");
        JPanel optionsRow = createOptionsRow(bloomControl, layersControl, dewControl, spinControl, saveButton);

        JPanel redrawColumn = new JPanel();
        redrawColumn.setLayout(new BoxLayout(redrawColumn, BoxLayout.Y_AXIS));
        redrawColumn.setOpaque(false);
        redrawColumn.add(redrawButton);
        redrawColumn.add(label);

        JPanel backColumn = new JPanel();
        backColumn.setLayout(new BoxLayout(backColumn, BoxLayout.Y_AXIS));
        backColumn.setOpaque(false);
        backColumn.add(backButton);
        backColumn.add(Box.createRigidArea(new Dimension(0, 18)));

        JPanel forwardColumn = new JPanel();
        forwardColumn.setLayout(new BoxLayout(forwardColumn, BoxLayout.Y_AXIS));
        forwardColumn.setOpaque(false);
        forwardColumn.add(forwardButton);
        forwardColumn.add(Box.createRigidArea(new Dimension(0, 18)));

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonRow.add(backColumn);
        buttonRow.add(Box.createRigidArea(new Dimension(8, 0)));
        buttonRow.add(redrawColumn);
        buttonRow.add(Box.createRigidArea(new Dimension(8, 0)));
        buttonRow.add(forwardColumn);

        boolean[] updatingControls = {false};
        Runnable refreshControls = () -> {
            label.setText(canvas.getAppearance().style().name().replace('_', ' '));
            backButton.setEnabled(canvas.canGoBack());
            forwardButton.setEnabled(canvas.canGoForward());
            updatingControls[0] = true;
            try {
                FlowerOptions options = canvas.getFlowerOptions();
                bloomControl.setSelectedItem(options.bloom());
                layersControl.setValue(options.petalLayers());
                dewControl.setSelected(options.dew());
            } finally {
                updatingControls[0] = false;
            }
        };
        Runnable applyFlowerOptions = () -> {
            if (updatingControls[0]) return;
            canvas.setFlowerOptions(new FlowerOptions((BloomStage) bloomControl.getSelectedItem(),
                    (Integer) layersControl.getValue(), dewControl.isSelected()));
            refreshControls.run();
        };
        bloomControl.addActionListener(event -> applyFlowerOptions.run());
        layersControl.addChangeListener(event -> applyFlowerOptions.run());
        dewControl.addActionListener(event -> applyFlowerOptions.run());
        spinControl.addActionListener(event -> canvas.setTurntable(spinControl.isSelected()));
        saveButton.addActionListener(event -> canvas.chooseAndSave());
        backButton.addActionListener(event -> {
            canvas.back();
            refreshControls.run();
        });
        redrawButton.addActionListener(event -> {
            canvas.redraw();
            refreshControls.run();
        });
        forwardButton.addActionListener(event -> {
            canvas.forward();
            refreshControls.run();
        });

        toolbar.add(buttonRow);
        toolbar.add(optionsRow);
        content.add(canvas, BorderLayout.CENTER);
        content.add(toolbar, BorderLayout.SOUTH);
        return content;
    }

    // ------------------------------------------------------------------ saving

    /** Renders the current appearance, flower and view at {@code size x size} and writes a PNG. */
    public void saveImage(File file, int size) throws IOException {
        Objects.requireNonNull(file, "file");
        if (size < 1) throw new IllegalArgumentException("Positive size required");
        writePng(renderScene(size, size, appearance, flowerOptions, view), file);
    }

    private static void writePng(BufferedImage image, File file) throws IOException {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(file, "file");

        File absolute = file.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent != null && !parent.isDirectory())
            throw new IOException("Output directory does not exist: " + parent);
        if (absolute.isDirectory())
            throw new IOException("Output path is a directory: " + absolute);

        if (!ImageIO.write(image, "png", absolute))
            throw new IOException("No PNG writer available");
    }

    private void chooseAndSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("rosa-relicta.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) saveInBackground(chooser.getSelectedFile());
    }

    /** Where save failures are reported (a dialog on screen; replaceable for tests). */
    transient java.util.function.Consumer<String> saveErrors =
            message -> JOptionPane.showMessageDialog(this, message, "Save PNG", JOptionPane.ERROR_MESSAGE);

    /** Renders the current state at 1600 px off the EDT and writes it; failures go to {@link #saveErrors}. */
    java.util.concurrent.Future<?> saveInBackground(File file) {
        Appearance look = appearance;
        FlowerOptions flower = flowerOptions;
        View angle = view;
        return RENDER_THREAD.submit(() -> {
            try {
                writePng(renderScene(1600, 1600, look, flower, angle), file);
            } catch (IOException | RuntimeException failure) {
                String message = "Could not save " + file + ":\n" + failure.getMessage();
                SwingUtilities.invokeLater(() -> saveErrors.accept(message));
            }
        });
    }

    // ------------------------------------------------------------------ painting

    @Override public Dimension getPreferredSize() { return new Dimension(1000, 1000); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        int w = getWidth(), h = getHeight();
        if (w < 1 || h < 1) return;
        boolean moving = dragging || turntable;
        FrameKey want = new FrameKey(w, h, appearance, flowerOptions, view,
                moving ? RoseRenderer.Quality.PREVIEW : RoseRenderer.Quality.FINAL);
        if (!want.equals(shownKey)) {
            if (onScreen()) {
                request(want);
            } else {
                shown = render(want);
                shownKey = want;
            }
        }
        if (shown == null) return;
        if (shown.getWidth() == w && shown.getHeight() == h) {
            graphics.drawImage(shown, 0, 0, null);
            return;
        }
        // A newer frame is on its way: show the last one scaled to fit, so resizing never blanks.
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(w / (double) shown.getWidth(), h / (double) shown.getHeight());
            int sw = (int) Math.round(shown.getWidth() * scale), sh = (int) Math.round(shown.getHeight() * scale);
            g.drawImage(shown, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        } finally {
            g.dispose();
        }
    }

    private static BufferedImage render(FrameKey key) {
        return RoseRenderer.render(key.width(), key.height(), key.appearance(), key.flower(), key.view(), key.quality());
    }

    /** Latest-wins scheduling: at most one frame renders at a time; stale requests are dropped. */
    private void request(FrameKey key) {
        wanted = key;
        if (inFlight == null) start(key);
    }

    private void start(FrameKey key) {
        inFlight = key;
        RENDER_THREAD.submit(() -> {
            BufferedImage image = null;
            try {
                image = render(key);
            } finally {
                BufferedImage result = image;
                SwingUtilities.invokeLater(() -> finished(key, result));
            }
        });
    }

    private void finished(FrameKey key, BufferedImage image) {
        inFlight = null;
        if (image != null) {
            shown = image;
            shownKey = key;
            repaint();
        }
        if (!wanted.equals(key) && onScreen()) start(wanted);
    }

    /**
     * Visible panels render asynchronously; off-screen painting (printing, tests, exports) is
     * synchronous. Tests can force the on-screen behaviour without a window.
     */
    Boolean asyncOverride;

    private boolean onScreen() {
        return asyncOverride != null ? asyncOverride : isShowing();
    }

    /** For tests: whether the latest requested frame is the one on display. */
    boolean isUpToDate() {
        return inFlight == null && shownKey != null && shownKey.equals(wanted == null ? shownKey : wanted);
    }

    // ------------------------------------------------------------------ static API

    public static BufferedImage renderScene(int width, int height) {
        return renderScene(width, height, ORIGINAL, DEFAULT_FLOWER);
    }

    public static BufferedImage renderScene(int width, int height, Appearance appearance) {
        return renderScene(width, height, appearance, DEFAULT_FLOWER);
    }

    public static BufferedImage renderScene(int width, int height, Appearance appearance, FlowerOptions flower) {
        return renderScene(width, height, appearance, flower, View.DEFAULT);
    }

    /** Full-quality render of the rose turned to {@code view}; deterministic for equal arguments. */
    public static BufferedImage renderScene(int width, int height, Appearance appearance, FlowerOptions flower, View view) {
        return RoseRenderer.render(width, height, appearance, flower, view, RoseRenderer.Quality.FINAL);
    }

    // ------------------------------------------------------------------ command line

    /** Headless PNG export. Existing two-argument usage remains backward compatible. */
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            if (args.length < 2 || !"--export".equals(args[0]))
                throw new IllegalArgumentException(exportUsage());
            Export export = Export.parse(args);
            writePng(renderScene(export.size, export.size, export.look, export.flower, export.view), new File(args[1]));
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No display. Use --export rose.png");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Rosa relicta — Abandoned symbolism");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(createContent());
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int size = Math.min(1000, Math.min(screen.width - 80, screen.height - 245));
            int width = Math.max(460, size);
            int height = Math.min(screen.height - 60, Math.max(510, size + 215));
            frame.setSize(width, height);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /** Parsed {@code --export} options. */
    static final class Export {
        int size = 1600;
        FlowerOptions flower = DEFAULT_FLOWER;
        Appearance look = ORIGINAL;
        View view = View.DEFAULT;

        static Export parse(String[] args) {
            Export e = new Export();
            for (int i = 2; i < args.length; i++) {
                String flag = args[i];
                if ("--dew".equals(flag)) {
                    e.flower = new FlowerOptions(e.flower.bloom(), e.flower.petalLayers(), true);
                    continue;
                }
                if (++i >= args.length) throw new IllegalArgumentException(exportUsage());
                String value = args[i];
                switch (flag) {
                    case "--bloom" -> e.flower = new FlowerOptions(
                            enumValue(BloomStage.class, value, "Bloom must be bud, classic, or full"),
                            e.flower.petalLayers(), e.flower.dew());
                    case "--layers" -> e.flower = new FlowerOptions(e.flower.bloom(),
                            (int) integer(value, 3, 8, "Petal layers must be an integer from 3 to 8"), e.flower.dew());
                    case "--style" -> e.look = new Appearance(e.look.seed(), e.look.hueShift(), e.look.saturation(),
                            e.look.exposure(), enumValue(Style.class, value,
                            "Style must be velvet, engraving, art_nouveau, or faded_ink"));
                    case "--hue" -> e.look = new Appearance(e.look.seed(), (float) range(value, 0, 1,
                            "Hue must be a number from 0 to 1"), e.look.saturation(), e.look.exposure(), e.look.style());
                    case "--seed" -> e.look = new Appearance(integer(value, Long.MIN_VALUE, Long.MAX_VALUE,
                            "Seed must be an integer"), e.look.hueShift(), e.look.saturation(), e.look.exposure(), e.look.style());
                    case "--size" -> e.size = (int) integer(value, 16, 8192, "Size must be an integer from 16 to 8192");
                    case "--yaw" -> e.view = new View(range(value, -360, 360,
                            "Yaw must be a number of degrees from -360 to 360"), e.view.pitch());
                    case "--pitch" -> e.view = new View(e.view.yaw(), range(value, View.MIN_PITCH, View.MAX_PITCH,
                            "Pitch must be a number of degrees from -30 to 45"));
                    default -> throw new IllegalArgumentException(exportUsage());
                }
            }
            return e;
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, String text, String message) {
            try {
                return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(message, invalid);
            }
        }

        private static long integer(String text, long min, long max, String message) {
            long value;
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(message, invalid);
            }
            if (value < min || value > max) throw new IllegalArgumentException(message);
            return value;
        }

        private static double range(String text, double min, double max, String message) {
            double value;
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(message, invalid);
            }
            if (!(value >= min && value <= max)) throw new IllegalArgumentException(message);
            return value;
        }
    }

    private static String exportUsage() {
        return "Usage: java -jar abandoned-rose-symbolism.jar [--export rose.png [--bloom bud|classic|full]"
                + " [--layers 3..8] [--dew] [--style velvet|engraving|art_nouveau|faded_ink] [--hue 0..1]"
                + " [--seed n] [--size 16..8192] [--yaw degrees] [--pitch -30..45]]";
    }
}
