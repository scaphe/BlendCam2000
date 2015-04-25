package com.p944.blend;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.p944.blend.Main.Modification;

public class Main extends JFrame {

    public static abstract class Modification {
        public final int imageIndex;

        public Modification(int imageIndex) {
            this.imageIndex = imageIndex;
        }

        public abstract BufferedImage modify(BufferedImage image);
        
        public List<Modification> getModifications() {
            List<Modification> tmp =  new ArrayList<Modification>();
            tmp.add(this);
            return tmp;
        }

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
            g.drawImage(image, 0, 0, (int) (w * zoom), (int) (h * zoom), x, y, x + w, y + h, null);
            g.dispose();
            return resized1;
        }
    }

    public static class RotateLeft extends Modification {
        public RotateLeft(int i) {
            super(i);
        }
        
        public List<Modification> getModifications() {
            List<Modification> tmp =  new ArrayList<Modification>();
            tmp.add(new RotateRight(imageIndex));
            tmp.add(new RotateRight(imageIndex));
            tmp.add(new RotateRight(imageIndex));
            return tmp;
        }

        public BufferedImage modify(BufferedImage image) {
            throw new RuntimeException("Should never be called!!!");
            
//            image = rotateCw(image); // , -90);
//            image = rotateCw(image); // , -90);
//            image = rotateCw(image); // , -90);
//            return image;
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
    BufferedImage[] images = new BufferedImage[] { null, null };

    public void loadImages(boolean showError) {
        try {
            images[0] = null;
            images[0] = ImageIO.read(filename1.getFile());
        } catch (Throwable e) {
            if (showError) {
                JOptionPane.showMessageDialog(null, "Failed to load image file of [" + filename1.getFile().getAbsolutePath() + "]", "Error loading image",
                        JOptionPane.ERROR_MESSAGE);
            }
            e.printStackTrace();
        }
        try {
            images[1] = null;
            images[1] = ImageIO.read(filename2.getFile());
        } catch (Throwable e) {
            if (showError) {
                JOptionPane.showMessageDialog(null, "Failed to load image file of [" + filename2.getFile().getAbsolutePath() + "]", "Error loading image",
                        JOptionPane.ERROR_MESSAGE);
            }
            e.printStackTrace();
        }
    }

    public String modificationsToString() {
        String str = "";
        for (List<Modification> mods: modifications) {
            for (Modification modification : mods) {
                str += modification.getName() + ", ";
            }
        }
        return str;
    }
    
    class BIHolder {
        public BufferedImage ans = null;
    }

    public BufferedImage mergeImages(final int windowWidth, final int windowHeight) throws IOException {
        final BIHolder ansHolder = new BIHolder();
        Runnable mergeTask = new Runnable() {
            @Override
            public void run() {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    long start = System.currentTimeMillis();

                    final int imageWidth;
                    final int imageHeight;

                    BufferedImage[] copyOfImages = new BufferedImage[] { images[0], images[1] };
                    if (copyOfImages[0] != null) {
                        imageWidth = copyOfImages[0].getWidth();
                        imageHeight = copyOfImages[0].getHeight();
                    } else if (copyOfImages[1] != null) {
                        imageWidth = copyOfImages[1].getWidth();
                        imageHeight = copyOfImages[1].getHeight();
                    } else {
                        imageWidth = 200;
                        imageHeight = 200;
                    }

                    // Get the smallest ratio
                    int width, height;
                    if (windowWidth == 0 && windowHeight == 0) {

                        setStatus("Starting long merge");
                        width = imageWidth;
                        height = imageHeight;
                        // // Images should start off matching sizes, even if we
                        // are not
                        // // changing the size
                        // if (image2.getWidth() != width || image2.getHeight()
                        // !=
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

                    // Resize first so everything happens a lot quicker and
                    // takes less
                    // memory
                    if (windowWidth != 0) {
                        for (int i = 0; i < copyOfImages.length; i++) {
                            if (copyOfImages[i] != null) {
                                copyOfImages[i] = resizeImage(copyOfImages[i], width, height, BufferedImage.TYPE_INT_RGB);
                            }
                        }
                    }

                    // Zoom and Pan
                    setStatus("Resizing");
                    if (resizeAction1 != null) {
                        copyOfImages[0] = resizeAction1.modify(copyOfImages[0]);
                    }
                    if (resizeAction2 != null) {
                        copyOfImages[1] = resizeAction2.modify(copyOfImages[1]);
                    }
                    setStatus("Resizing done");

                    // --- Image modifications...
                    System.out.println("Applying modifications of: " + modificationsToString());
                    int total = modifications.size();
                    int count = 1;
                    for (List<Modification> mods : modifications) {
                        for (Modification modification : mods) {
                            setStatus("Applying modification " + count + " of " + total + " of " + modification.getName() + " on " + modification.imageIndex);
                            copyOfImages[modification.imageIndex] = modification.modify(copyOfImages[modification.imageIndex]);
                            setStatus("Modification done");
                            count += 1;
                        }
                    }

                    if (copyOfImages[0] != null) {
                        width = copyOfImages[0].getWidth();
                        height = copyOfImages[0].getHeight();
                        if (copyOfImages[1] != null) {
                            if (width != copyOfImages[1].getWidth() || height != copyOfImages[1].getHeight()) {
                                // Resize again?
                                System.out.println("Extra resize to " + width + "," + height);
                                setStatus("Extra resize");
                                copyOfImages[1] = resizeImage(copyOfImages[1], width, height, BufferedImage.TYPE_INT_RGB);
                                setStatus("Extra resize done");
                            }
                        }
                    }

                    // --- End modifications

                    // B&W etc styles
                    setStatus("Applying B&W etc");
                    copyOfImages[0] = styleImage(style1.getValue(), copyOfImages[0]);
                    copyOfImages[1] = styleImage(style2.getValue(), copyOfImages[1]);
                    setStatus("Applying B&W etc done");

                    // System.out.println("Size is " + width + ", " + height +
                    // " and " + images[1].getWidth() + ", " +
                    // images[1].getHeight());

                    // Merge
                    setStatus("Merging...");
                    final BufferedImage ans = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    final int brightness1Value = brightness1.getValue();
                    final int brightness2Value = brightness2.getValue();
                    final boolean darknessWinsV = darknessWins.isSelected();
                    int p, q;
                    int r, g, b;
                    int origR, origG, origB;
                    int layerR, layerG, layerB;
                    for (int x = 0; x < width; ++x) {
                        for (int y = 0; y < height; ++y) {
                            if (copyOfImages[0] == null) {
                                p = 0x0;
                            } else {
                                p = copyOfImages[0].getRGB(x, y); // & 0xff00;
                            }
                            if (copyOfImages[1] == null) {
                                q = 0x0;
                            } else {
                                q = copyOfImages[1].getRGB(x, y); // & 0xff;
                            }

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

                            // Choose combination operation, plus brightness
                            // shifts
                            // (multiply and max)
                            // ans.setRGB(x, y, p | q);
                            ans.setRGB(x, y, r + g + b);
                        }
                    }
                    System.out.println("Done in " + (System.currentTimeMillis() - start));
                    setStatus("Done in " + (System.currentTimeMillis() - start) + "ms");
                    ansHolder.ans = ans;
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };

        mergeTask.run();
//        try {
//            SwingUtilities.invokeAndWait(mergeTask);
//        } catch (Exception e) {
//            setStatus("Merge failed with: " + e);
//            e.printStackTrace();
//        }
        return ansHolder.ans;
    }

    long lastStatusCall = System.currentTimeMillis();
    public void setStatus(String status) {
        long now = System.currentTimeMillis();
        String msg = "Status ("+(now-lastStatusCall)+"ms): "+status;
        System.out.println(msg);
        statusBar.setText(msg);
        lastStatusCall = now;
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
        if (image == null)
            return null;
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

    public static BufferedImage resizeImage(BufferedImage origImage, int width, int height, int typeIntRgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().drawImage(origImage, 0, 0, width, height, null);
        return image;
    }

    public void save() {
        try {
            File file = getSaveAsFile(this, prevSaveFile);
            if (file != null) {
                if (!file.exists() || isUserSure("Overwrite file " + file.getName() + "?")) {
                    // Are you sure?
                    BufferedImage mergedImage = mergeImages(0, 0);
                    try {
                        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        ImageIO.write(mergedImage, "jpg", file);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                    prevSaveFile = file;
                    JOptionPane.showMessageDialog(Main.this, "Saved to " + file);
                } else {
                    JOptionPane.showMessageDialog(Main.this, "Save aborted!");
                }
            } else {
                JOptionPane.showMessageDialog(Main.this, "Save cancelled!");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            String trace = "";
            for (StackTraceElement st : e.getStackTrace()) {
                trace += st.getClassName()+":"+st.getMethodName()+"."+st.getLineNumber()+"\n";
            }
            JOptionPane.showMessageDialog(Main.this, "Failed save with " + e+"\n"+trace);
        }
    }

    private boolean isUserSure(String message) {
        return JOptionPane.showConfirmDialog(Main.this, message, "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public class FileLabel extends JTextField {
        private Color origBg;

        public FileLabel(String filename) {
            super(filename, 15);
            origBg = getBackground();
            setEditable(false);
        }

        public File getFile() {
            return new File(getText());
        }

        public void setFile(File file) {
            setText(file.getAbsolutePath());
            if (file.exists() && file.isFile()) {
                setBackground(origBg);
            } else {
                setBackground(Color.PINK);
            }
        }

        public void clear() {
            setFile(new File("/Users/" + username + "/Pictures"));
        }
    }

    private String username = System.getProperty("user.name");
    private FileLabel filename1 = new FileLabel("/Users/" + username + "/public/face1.jpg");
    private FileLabel filename2 = new FileLabel("/Users/" + username + "/public/layer1.jpg");
    private File prevSaveFile = new File("/Users/" + username + "/Pictures/Lightroom/project/project2013/mergedImage.jpg");

    private ImagePanel imagePanel;

    protected ResizeMod resizeAction1;
    protected ResizeMod resizeAction2;
    private JSlider brightness1;
    private JSlider brightness2;
    private JCheckBox darknessWins;
    private JLabel statusBar;
    private RadioChoice style1;
    private RadioChoice style2;
    private LinkedList<List<Modification>> modifications = new LinkedList<List<Modification>>();

    public Main(String title) {
        super(title);
        modifications.add(new LinkedList<Modification>());
        modifications.add(new LinkedList<Modification>());
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
            filename1.clear();
        }
        if (!filename2.getFile().exists()) {
            filename2.clear();
        }
        loadImages(false);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(imagePanel);

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                save();
            }
        });

        JButton clearButton = new JButton("Reset-All");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (JOptionPane.showConfirmDialog(Main.this, "Are you sure you want to Reset All?", "Confirm Reset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    resetAll();
                    redoMerge();
                }
            }
        });

        JButton promoteButton = new JButton("Promote");
        promoteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (JOptionPane.showConfirmDialog(Main.this, "Promote merged to image 1?", "Confirm Promote", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    try {
                        // TODO: If we just did a save, then no need to do
                        // another merge!
                        BufferedImage mergedImage = mergeImages(0, 0);
                        resetAll();
                        filename1.setFile(new File("--promoted--"));
                        filename2.clear();
                        images[0] = mergedImage;
                        images[1] = null;
                        redoMerge();
                    } catch (Throwable e) {
                        JOptionPane.showMessageDialog(Main.this, "Failed merge with " + e);
                        e.printStackTrace();
                    }
                }
            }
        });

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

        JButton load1 = new JButton("Load");
        load1.addActionListener(new LoadFileActionListener(filename1) {
            @Override
            protected void setImage(BufferedImage image) {
                images[0] = image;
            }
        });

        JButton load2 = new JButton("Load");
        load2.addActionListener(new LoadFileActionListener(filename2) {
            @Override
            protected void setImage(BufferedImage image) {
                images[1] = image;
            }
        });

        String[] styleStrings = { "Normal", "B&W", "Sepia", "Negative" };

        style1 = new RadioChoice(styleStrings);
        style2 = new RadioChoice(styleStrings);

        brightness1 = makeSlider(-256, 256, 64, 8);
        brightness2 = makeSlider(-256, 256, 64, 8);

        darknessWins = new JCheckBox("Darkness Wins", false);
        darknessWins.addChangeListener(onChangeRedo);

        JButton reset1 = new JButton("Reset");
        reset1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                brightness1.setValue(0);
            }
        });
        JButton reset2 = new JButton("Reset");
        reset2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                brightness2.setValue(0);
            }
        });

        JButton resize1 = new JButton("Resize");
        resize1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                ImageResizerFrame f = new ImageResizerFrame(images[0], resizeAction1);
                resizeAction1 = f.result;
                redoMerge();
            }
        });
        JButton resize2 = new JButton("Resize");
        resize2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                ImageResizerFrame f = new ImageResizerFrame(images[1], resizeAction2);
                resizeAction2 = f.result;
                redoMerge();
            }
        });

        controls.add(flow(new JLabel("Image 1:"), filename1, load1));
        controls.add(resize1);
        controls.add(style1);
        controls.add(flow(makeButton(new FlipVert(0)), makeButton(new FlipHoriz(0)), makeButton(new RotateLeft(0)), makeButton(new RotateRight(0))));
        controls.add(borderHoriz(null, brightness1, panel(reset1)));

        controls.add(new JSeparator());
        controls.add(borderHoriz(new JLabel("Image 2:"), filename2, load2));
        controls.add(resize2);
        controls.add(style2);
        controls.add(flow(makeButton(new FlipVert(1)), makeButton(new FlipHoriz(1)), makeButton(new RotateLeft(1)), makeButton(new RotateRight(1))));
        controls.add(borderHoriz(null, brightness2, panel(reset2)));
        controls.add(new JSeparator());
        controls.add(darknessWins);

        getContentPane().add(panel(controls), BorderLayout.EAST);
        Component buttons = flow(clearButton, promoteButton, saveButton);
        statusBar = new JLabel("Status");
        getContentPane().add(borderVert(null, buttons, statusBar), BorderLayout.SOUTH);
        pack();

        System.out.println("Shown window");
    }
    
    public void resetAll() {
        filename1.clear();
        filename2.clear();
        modifications.get(0).clear();
        modifications.get(1).clear();
        resetBrightnesses();
        images[0] = null;
        images[1] = null;
        darknessWins.setSelected(false);
        style1.reset();
        style2.reset();
        resizeAction1 = null;
        resizeAction2 = null;
    }

    ChangeListener onChangeRedo = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent arg0) {
            redoMerge();
        }
    };

    private JSlider makeSlider(int min, int max, int major, int minor) {
        JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, 0);
        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                redoMerge();
            }
        });

        // Turn on labels at major tick marks.
        slider.setMajorTickSpacing(major);
        slider.setMinorTickSpacing(minor);
        slider.setPaintTicks(true);
        return slider;
    }

    private Component borderHoriz(JComponent left, JComponent middle, JComponent right) {
        JPanel p = new JPanel(new BorderLayout());
        if (left != null)
            p.add(left, BorderLayout.WEST);
        if (middle != null)
            p.add(middle);
        if (right != null)
            p.add(right, BorderLayout.EAST);
        return p;
    }

    private Component borderVert(Component top, Component middle, Component bottom) {
        JPanel p = new JPanel(new BorderLayout());
        if (top != null)
            p.add(top, BorderLayout.NORTH);
        if (middle != null)
            p.add(middle);
        if (bottom != null)
            p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent panel(JComponent right) {
        JPanel p = new JPanel();
        p.add(right);
        return p;
    }

    abstract public class LoadFileActionListener implements ActionListener {
        private final FileLabel filename;

        public LoadFileActionListener(FileLabel filename) {
            this.filename = filename;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            File file = getImageFile(Main.this, filename.getFile());
            if (file != null) {
                try {
                    BufferedImage image = ImageIO.read(file);
                    filename.setFile(file);
                    // layerImage = ImageIO.read(new File(file2));
                    redoMerge();
                    setImage(image);
                } catch (Throwable e) {
                    JOptionPane.showMessageDialog(Main.this, "Failed to load file [" + filename1.getFile().getAbsolutePath() + "] with " + e);
                    e.printStackTrace();
                }
            }
        }

        abstract protected void setImage(BufferedImage image);
    }

    private void resetBrightnesses() {
        brightness1.setValue(0);
        brightness2.setValue(0);
    }

    public class RadioChoice extends JPanel {
        private volatile String value;
        private volatile String firstValue;
        private JRadioButton firstButton = null;

        public RadioChoice(String... labels) {
            firstValue = labels[0];
            setLayout(new FlowLayout());
            boolean selected = true;
            ButtonGroup grp = new ButtonGroup();
            for (String label : labels) {
                JRadioButton b = makeRadioButton(label, selected);
                if (firstButton == null) {
                    firstButton = b;
                }
                grp.add(b);
                add(b);
                selected = false;
            }
        }

        public void reset() {
            firstButton.setSelected(true);
            value = firstValue;
            redoMerge();
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
                modifications.get(mod.imageIndex).addAll(mod.getModifications());
                compactModifications();
                redoMerge();
            }
        });
        return but;
    }

    public void compactModifications() {
        // Can remove 4x RotateRight
        for (List<Modification> mods: modifications) {
            boolean removedSomething = false;
            String before = modificationsToString();
            int rrCount = 0;
            int fvCount = 0;
            int fhCount = 0;
            int index = 0;
            while (index < mods.size()) {
                Modification m = mods.get(index);
                if (m instanceof RotateRight) {
                    rrCount += 1;
                    if (rrCount == 4) {
                        // Remove the last 4
                        mods.remove(index - 3);
                        mods.remove(index - 3);
                        mods.remove(index - 3);
                        mods.remove(index - 3);
                        index -= 4;
                        removedSomething = true;
                    }
                } else {
                    rrCount = 0;
                }
                if (m instanceof FlipHoriz) {
                    fhCount += 1;
                    if (fhCount == 2) {
                        // Remove the last 2
                        mods.remove(index - 1);
                        mods.remove(index - 1);
                        index -= 2;
                        removedSomething = true;
                    }
                } else {
                    fhCount = 0;
                }
                if (m instanceof FlipVert) {
                    fvCount += 1;
                    if (fvCount == 2) {
                        // Remove the last 2
                        mods.remove(index - 1);
                        mods.remove(index - 1);
                        index -= 2;
                        removedSomething = true;
                    }
                } else {
                    fvCount = 0;
                }
                index += 1;
            }
            if ( removedSomething ) {
                System.out.println("Before: "+before);
                System.out.println("After: "+modificationsToString());
            }
        }
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
            if (ans.getName().toLowerCase().endsWith(".jpg")) {
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
            } catch (Throwable e) {
                System.out.println("Got ex of " + e);
                e.printStackTrace();
            }
        }
    }

    public static String version = "0.6e";

    public static void main(String[] args) {
        JFrame frame = new Main("Blend " + version);
        frame.setVisible(true);
    }
}
