package com.p944.blend;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.p944.blend.Main.ResizeMod;

public class Main extends JFrame {

    public static abstract class Modification {
        public final int imageIndex;

        public Modification(int imageIndex) {
            this.imageIndex = imageIndex;
        }

        public abstract BufferedImage modify(BufferedImage image);

        public String getName() {
            return this.getClass().getSimpleName();
        }
    }

    public static class ResizeMod extends Modification {
        public final double zoom;
        public final double offsetXPc;
        public final double offsetYPc;

        public ResizeMod(int imageIndex,
                         double zoom,
                         double offsetXPc,
                         double offsetYPc) {
            super(imageIndex);
            this.zoom = zoom;
            this.offsetXPc = offsetXPc;
            this.offsetYPc = offsetYPc;
        }

        public BufferedImage modify(BufferedImage image) {
            // Zoom and shift by % of width and height
            int w = image.getWidth();
            int h = image.getHeight();
            int x = (int) (offsetXPc * w);
            int y = (int) (offsetYPc * h);
            BufferedImage resized1 = new BufferedImage(w, h, image.getType());
            Graphics2D g = resized1.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, (int) (w * zoom), (int) (h * zoom), x, y, x+w, y+h, null);
            g.dispose();
            return resized1;
        }
    }

    public static class RotateLeft extends Modification {
        public RotateLeft(int i) {
            super(i);
        }

        public BufferedImage modify(BufferedImage image) {
            image = rotateCw(image); // , -90);
            image = rotateCw(image); // , -90);
            image = rotateCw(image); // , -90);
            return image;
        }
    }

    public static class RotateRight extends Modification {
        public RotateRight(int i) {
            super(i);
        }

        public BufferedImage modify(BufferedImage image) {
            return rotateCw(image); // , -90);
        }
    }

    public static class FlipHoriz extends Modification {
        public FlipHoriz(int i) {
            super(i);
        }

        public BufferedImage modify(BufferedImage image) {
            BufferedImage imageCopy = deepCopy(image);

            // Flip the image horizontally
            AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-image.getWidth(null), 0);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            op.filter(image, imageCopy);
            image = imageCopy;
            return image;
        }
    }

    public static class FlipVert extends Modification {
        public FlipVert(int i) {
            super(i);
        }

        public BufferedImage modify(BufferedImage image) {
            BufferedImage imageCopy = deepCopy(image);

            // Flip the image vertically
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
            tx.translate(0, -image.getHeight(null));
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            op.filter(image, imageCopy);
            image = imageCopy;
            return image;
        }
    }

    private static AtomicLong recalcImageTS = new AtomicLong(0);
    private static Timer timer;
    private BufferedImage baseImage;
    private BufferedImage layerImage;

    private List<Modification> modifications = new LinkedList<Modification>();

    public void loadImages(File file, File file2) {
        try {
            baseImage = ImageIO.read(file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to load image file of [" + file.getAbsolutePath() + "]", "Error loading image",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        try {
            layerImage = ImageIO.read(file2);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to load image file of [" + file2.getAbsolutePath() + "]", "Error loading image",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public BufferedImage mergeImages(int windowWidth, int windowHeight) throws IOException {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            long start = System.currentTimeMillis();

            BufferedImage[] images = new BufferedImage[] { baseImage, layerImage };

            final int imageWidth;
            final int imageHeight;

            imageWidth = images[0].getWidth();
            imageHeight = images[0].getHeight();

            // Get the smallest ratio
            int width, height;
            if (windowWidth == 0 && windowHeight == 0) {
                width = imageWidth;
                height = imageHeight;
                // // Images should start off matching sizes, even if we are not
                // // changing the size
                // if (image2.getWidth() != width || image2.getHeight() !=
                // height) {
                // image2 = resizeImage(image2, width, height,
                // BufferedImage.TYPE_INT_RGB);
                // }
            } else if ((double) windowWidth / imageWidth < (double) windowHeight / imageHeight) {
                width = windowWidth;
                height = (int) ((double) imageHeight / imageWidth * width);
            } else {
                height = windowHeight;
                width = (int) ((double) imageWidth / imageHeight * height);
            }

            // Resize first so everything happens a lot quicker and takes less
            // memory
            if (windowWidth != 0) {
                for (int i = 0; i < images.length; i++) {
                    images[i] = resizeImage(images[i], width, height, BufferedImage.TYPE_INT_RGB);
                }
            }

            // Zoom and Pan
            if ( resizeAction1 != null ) {
                images[0] = resizeAction1.modify(images[0]);
            }
            if ( resizeAction2 != null ) {
                images[1] = resizeAction2.modify(images[1]);
            }

            // --- Image modifications...
            String str = "";
            for (Modification modification : modifications) {
                str += modification.getName() + ", ";
            }
            System.out.println("Applying: " + str);
            for (Modification modification : modifications) {
                images[modification.imageIndex] = modification.modify(images[modification.imageIndex]);
            }

            width = images[0].getWidth();
            height = images[0].getHeight();
            if (width != images[1].getWidth() || height != images[1].getHeight()) {
                // Resize again?
                System.out.println("Extra resize to " + width + "," + height);
                images[1] = resizeImage(images[1], width, height, BufferedImage.TYPE_INT_RGB);
            }

            // --- End modifications

            // B&W etc styles
            images[0] = styleImage(style1.getValue(), images[0]);
            images[1] = styleImage(style2.getValue(), images[1]);

            System.out
                    .println("Size is " + images[0].getWidth() + ", " + images[0].getHeight() + " and " + images[1].getWidth() + ", " + images[1].getHeight());

            // Merge
            final BufferedImage ans = new BufferedImage(images[0].getWidth(), images[0].getHeight(), BufferedImage.TYPE_INT_RGB);
            final int brightness1Value = brightness1.getValue();
            final int brightness2Value = brightness2.getValue();
            final boolean darknessWinsV = darknessWins.isSelected();
            int p, q;
            int r, g, b;
            int origR, origG, origB;
            int layerR, layerG, layerB;
            for (int x = 0; x < images[0].getWidth(); ++x) {
                for (int y = 0; y < images[0].getHeight(); ++y) {
                    p = images[0].getRGB(x, y); // & 0xff00;
                    q = images[1].getRGB(x, y); // & 0xff;

                    // --- Change brightnesses
                    if (brightness1Value != 0) {
                        origR = (p & 0xff0000) >> 16;
                        origG = (p & 0xff00) >> 8;
                        origB = p & 0xff;
                        origR = Math.max(0, Math.min(0xff, origR + brightness1Value) << 16);
                        origG = Math.max(0, Math.min(0xff, origG + brightness1Value) << 8);
                        origB = Math.max(0, Math.min(0xff, origB + brightness1Value));
                    } else {
                        origR = p & 0xff0000;
                        origG = p & 0xff00;
                        origB = p & 0xff;
                    }

                    if (brightness2Value != 0) {
                        layerR = (q & 0xff0000) >> 16;
                        layerG = (q & 0xff00) >> 8;
                        layerB = q & 0xff;
                        layerR = Math.max(0, Math.min(0xff, layerR + brightness2Value) << 16);
                        layerG = Math.max(0, Math.min(0xff, layerG + brightness2Value) << 8);
                        layerB = Math.max(0, Math.min(0xff, layerB + brightness2Value));
                    } else {
                        layerR = q & 0xff0000;
                        layerG = q & 0xff00;
                        layerB = q & 0xff;
                    }

                    // Merge operations
                    if (darknessWinsV) {
                        // Least brightess wins
                        int origY = (origR + origR + origR + origB + origG + origG + origG + origG) >> 3;
                        int layerY = (layerR + layerR + layerR + layerB + layerG + layerG + layerG + layerG) >> 3;
                        if (origY < layerY) {
                            r = origR;
                            g = origG;
                            b = origB;
                        } else {
                            r = layerR;
                            g = layerG;
                            b = layerB;
                        }
                    } else {
                        // Simple merge/addition
                        r = Math.max(origR, layerR);
                        g = Math.max(origG, layerG);
                        b = Math.max(origB, layerB);
                    }

                    // Choose combination operation, plus brightness shifts
                    // (multiply and max)
                    // ans.setRGB(x, y, p | q);
                    ans.setRGB(x, y, r + g + b);
                }
            }
            System.out.println("Done in " + (System.currentTimeMillis() - start));
            return ans;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public static BufferedImage rotateCw(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage newImage = new BufferedImage(height, width, img.getType());

        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                newImage.setRGB(height - 1 - j, i, img.getRGB(i, j));

        return newImage;
    }

    private BufferedImage styleImage(String style, BufferedImage image) {
        if ("B&W".equals(style)) {
            ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
            BufferedImage imageCopy = deepCopy(image);
            op.filter(image, imageCopy);
            return imageCopy;
        } else if ("Sepia".equals(style)) {
            BufferedImage imageCopy = deepCopy(image);
            applySepiaFilter(imageCopy, 20);
            return imageCopy;
        } else if ("Negative".equals(style)) {
            BufferedImage imageCopy = deepCopy(image);
            applyNegativeFilter(imageCopy);
            return imageCopy;
        }
        return image; // no change
    }

    /**
     * 
     * @param img
     *            Image to modify
     * @param sepiaIntensity
     *            From 0-255, 30 produces nice results
     * @throws Exception
     */
    public static void applySepiaFilter(BufferedImage img, int sepiaIntensity) {
        // Play around with this. 20 works well and was recommended
        // by another developer. 0 produces black/white image
        int sepiaDepth = 20;

        int w = img.getWidth();
        int h = img.getHeight();

        WritableRaster raster = img.getRaster();

        // We need 3 integers (for R,G,B color values) per pixel.
        int[] pixels = new int[w * h * 3];
        raster.getPixels(0, 0, w, h, pixels);

        // Process 3 ints at a time for each pixel.
        // Each pixel has 3 RGB colors in array
        for (int i = 0; i < pixels.length; i += 3) {
            int r = pixels[i];
            int g = pixels[i + 1];
            int b = pixels[i + 2];

            int gry = (r + g + b) / 3;
            r = g = b = gry;
            r = r + (sepiaDepth * 2);
            g = g + sepiaDepth;

            if (r > 255)
                r = 255;
            if (g > 255)
                g = 255;
            if (b > 255)
                b = 255;

            // Darken blue color to increase sepia effect
            b -= sepiaIntensity;

            // normalize if out of bounds
            if (b < 0)
                b = 0;
            if (b > 255)
                b = 255;

            pixels[i] = r;
            pixels[i + 1] = g;
            pixels[i + 2] = b;
        }
        raster.setPixels(0, 0, w, h, pixels);
    }

    public static void applyNegativeFilter(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        WritableRaster raster = img.getRaster();

        // We need 3 integers (for R,G,B color values) per pixel.
        int[] pixels = new int[w * h * 3];
        raster.getPixels(0, 0, w, h, pixels);

        // Process 3 ints at a time for each pixel.
        // Each pixel has 3 RGB colors in array
        for (int i = 0; i < pixels.length; i += 3) {
            int r = pixels[i];
            int g = pixels[i + 1];
            int b = pixels[i + 2];

            pixels[i] = 255 - r;
            pixels[i + 1] = 255 - g;
            pixels[i + 2] = 255 - b;
        }
        raster.setPixels(0, 0, w, h, pixels);
    }

    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    private static BufferedImage resizeImage(BufferedImage origImage, int width, int height, int typeIntRgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // ImageObserver imgObs = new ImageObserver() {
        // @Override
        // public boolean imageUpdate(Image arg0, int arg1, int arg2, int arg3,
        // int arg4, int arg5) {
        // return true;
        // }
        // };
        image.getGraphics().drawImage(origImage, 0, 0, width, height, null);
        return image;
    }

    public void save() {
        try {
            File file = getSaveAsFile(this, prevSaveFile);
            if (file != null) {
                BufferedImage mergedImage = mergeImages(0, 0);
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    ImageIO.write(mergedImage, "jpg", file);
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
                prevSaveFile = file;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImagePanel imagePanel;
    private JSlider brightness1;
    private JSlider brightness2;
    private JCheckBox darknessWins;
    private RadioChoice style1;
    private RadioChoice style2;

    public class FileLabel extends JTextField {
        public FileLabel(String filename) {
            setText(filename);
            setEditable(false);
        }

        public File getFile() {
            return new File(getText());
        }

        public void setFile(File file) {
            setText(file.getAbsolutePath());
        }
    }

    private String username = System.getProperty("user.name");
    private FileLabel filename1 = new FileLabel("/Users/" + username + "/public/face1.jpg");
    private FileLabel filename2 = new FileLabel("/Users/" + username + "/public/layer1.jpg");
    private File prevSaveFile = new File("/Users/" + username + "/tmp/mergedImage.jpg");
    protected ResizeMod resizeAction1;
    protected ResizeMod resizeAction2;

    public Main(String title) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        imagePanel = new ImagePanel();
        imagePanel.setPreferredSize(new Dimension(300, 300));
        imagePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent arg0) {
                // Want to recompute our picture, but not all of the time cos
                // too expensive
                // Only after a little while
                redoMerge();
            }
        });
        if (!filename1.getFile().exists()) {
            filename1.setFile(new File("/Users/" + username + "/Pictures"));
        }
        if (!filename2.getFile().exists()) {
            filename2.setFile(new File("/Users/" + username + "/Pictures"));
        }
        loadImages(filename1.getFile(), filename2.getFile());
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(imagePanel);
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                save();
            }
        });
        getContentPane().add(saveButton, BorderLayout.SOUTH);
        setSize(600, 400);
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        }, 200, 200);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.PAGE_AXIS));

        JButton load1 = new JButton("Load-1");
        load1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                File file = getImageFile(Main.this, filename1.getFile());
                if (file != null) {
                    try {
                        baseImage = ImageIO.read(file);
                        filename1.setFile(file);
                        // layerImage = ImageIO.read(new File(file2));
                        redoMerge();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        JButton load2 = new JButton("Load-2");
        load2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                File file = getImageFile(Main.this, filename2.getFile());
                if (file != null) {
                    try {
                        layerImage = ImageIO.read(file);
                        filename2.setFile(file);
                        // layerImage = ImageIO.read(new File(file2));
                        redoMerge();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        String[] styleStrings = { "Normal", "B&W", "Sepia", "Negative" };

        style1 = new RadioChoice(styleStrings);
        style2 = new RadioChoice(styleStrings);

        ActionListener alRedo = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                redoMerge();
            }
        };

        brightness1 = new JSlider(JSlider.HORIZONTAL, -256, 256, 0);
        brightness1.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                redoMerge();
            }
        });

        // Turn on labels at major tick marks.
        brightness1.setMajorTickSpacing(64);
        brightness1.setMinorTickSpacing(8);
        brightness1.setPaintTicks(true);

        brightness2 = new JSlider(JSlider.HORIZONTAL, -256, 256, 0);
        ChangeListener onChangeRedo = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                redoMerge();
            }
        };
        brightness2.addChangeListener(onChangeRedo);

        // Turn on labels at major tick marks.
        brightness2.setMajorTickSpacing(64);
        brightness2.setMinorTickSpacing(8);
        brightness2.setPaintTicks(true);

        darknessWins = new JCheckBox("Darkness", false);
        darknessWins.addChangeListener(onChangeRedo);

        JButton reset = new JButton("Reset");
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                brightness1.setValue(0);
                brightness2.setValue(0);
            }
        });

        JButton resize1 = new JButton("Resize");
        resize1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                ImageResizerFrame f = new ImageResizerFrame(baseImage, resizeAction1);
                resizeAction1 = f.result;
                redoMerge();
            }
        });
        JButton resize2 = new JButton("Resize");
        resize2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                ImageResizerFrame f = new ImageResizerFrame(layerImage, resizeAction2);
                resizeAction2 = f.result;
                redoMerge();
            }
        });

        controls.add(flow(filename1, load1));
        controls.add(resize1);
        controls.add(style1);
        controls.add(flow(makeButton(new FlipVert(0)), makeButton(new FlipHoriz(0))));
        controls.add(flow(makeButton(new RotateLeft(0)), makeButton(new RotateRight(0))));
        controls.add(brightness1);

        controls.add(flow(filename2, load2));
        controls.add(resize2);
        controls.add(style2);
        controls.add(flow(makeButton(new FlipVert(1)), makeButton(new FlipHoriz(1))));
        controls.add(flow(makeButton(new RotateLeft(1)), makeButton(new RotateRight(1))));
        controls.add(brightness2);
        controls.add(darknessWins);
        controls.add(reset);

        getContentPane().add(controls, BorderLayout.EAST);
        pack();

        System.out.println("Shown window");
    }

    public class RadioChoice extends JPanel {
        private volatile String value;

        public RadioChoice(String... labels) {
            setLayout(new FlowLayout());
            boolean selected = true;
            ButtonGroup grp = new ButtonGroup();
            for (String label : labels) {
                JRadioButton b = makeRadioButton(label, selected);
                grp.add(b);
                add(b);
                selected = false;
            }
        }

        private JRadioButton makeRadioButton(final String label, boolean selected) {
            JRadioButton b = new JRadioButton(label);
            b.setSelected(selected);
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    value = label;
                    redoMerge();
                }
            });
            return b;
        }

        public String getValue() {
            return value;
        }
    }

    public static Component flow(JComponent... buttons) {
        JPanel p = new JPanel(new FlowLayout());
        for (JComponent button : buttons) {
            p.add(button);
        }
        return p;
    }

    private JButton makeButton(final Modification mod) {
        JButton but = new JButton(mod.getName());
        but.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                modifications.add(mod);
                redoMerge();
            }
        });
        return but;
    }

    private File getImageFile(Component parent, File prevFile) {
        JFileChooser chooser = new JFileChooser(prevFile);
        ImagePreviewPanel preview = new ImagePreviewPanel();
        chooser.setAccessory(preview);
        chooser.addPropertyChangeListener(preview);
        int rc = chooser.showOpenDialog(parent);
        if (rc == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private File getSaveAsFile(Component parent, File prevSaveFile) {
        JFileChooser chooser = new JFileChooser(prevSaveFile);
        chooser.addChoosableFileFilter(new MyImageFilter());
        int rc = chooser.showSaveDialog(parent);
        if (rc == JFileChooser.APPROVE_OPTION) {
            File ans = chooser.getSelectedFile();
            if (ans.getName().endsWith(".jpg")) {
                return ans;
            } else {
                return new File(ans.getAbsoluteFile() + ".jpg");
            }
        }
        return null;
    }

    protected void redoMerge() {
        recalcImageTS.set(System.currentTimeMillis() + 100);
    }

    protected void tick() {
        if (recalcImageTS.get() != 0 && System.currentTimeMillis() > recalcImageTS.get()) {
            recalcImageTS.set(0);
            if (imagePanel.getWidth() < 1 || imagePanel.getHeight() < 1) {
                System.out.println("Image too small?");
            }
            try {
                // imagePanel.loadImage("/Users/dclark/public/face1.jpg");
                BufferedImage mergedImage = mergeImages(imagePanel.getWidth(), imagePanel.getHeight());
                imagePanel.setImage(mergedImage);
            } catch (Exception e) {
                System.out.println("Got ex of " + e);
                e.printStackTrace();
            }
        }
    }

    public static String version = "0.4";

    public static void main(String[] args) {
        JFrame frame = new Main("Blend " + version);
        frame.setVisible(true);
    }
}
