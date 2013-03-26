package com.p944.blend;

import java.awt.BorderLayout;
import java.awt.Component;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Main extends JFrame {

    public static String version = "0.1";
    private static AtomicLong recalcImageTS = new AtomicLong(0);
    private static Timer timer;
    private BufferedImage baseImage;
    private BufferedImage layerImage;

    public void loadImages(File file, File file2) {
        try {
            baseImage = ImageIO.read(file);
            layerImage = ImageIO.read(file2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BufferedImage mergeImages(int windowWidth, int windowHeight) throws IOException {
        long start = System.currentTimeMillis();
        int imageWidth = baseImage.getWidth();
        int imageHeight = baseImage.getHeight();

        // Get the smallest ratio
        int width, height;
        if (windowWidth == 0 && windowHeight == 0) {
            width = imageWidth;
            height = imageHeight;
        } else if ((double) windowWidth / imageWidth < (double) windowHeight / imageHeight) {
            width = windowWidth;
            height = (int) ((double) imageHeight / imageWidth * width);
        } else {
            height = windowHeight;
            width = (int) ((double) imageWidth / imageHeight * height);
        }

        BufferedImage image = baseImage;
        BufferedImage image2 = layerImage;

        for (int pass = 0; pass < 2; pass++) {
            // Resize first
            if (windowWidth != 0) {
                image = resizeImage(image, width, height, BufferedImage.TYPE_INT_RGB);
                image2 = resizeImage(image2, width, height, BufferedImage.TYPE_INT_RGB);
            }
            if ( pass == 1 ) {
                break; // Only do rotation once
            }
            // Rotations early, as change shape of image1
            image = rotateImage(rotateCombo1.getSelectedItem(), image);
            image2 = rotateImage(rotateCombo2.getSelectedItem(), image2);
            if ( image.getWidth() != image2.getWidth() || image.getHeight() != image2.getHeight()) {
                // Resize again?  FIXME: Or just crop?
            } else {
                break;
            }
        }

        
        BufferedImage ans = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        System.out.println("Size is " + image.getWidth() + ", " + image.getHeight());
        int brightness1Value = brightness1.getValue();
        int brightness2Value = brightness2.getValue();
        boolean darknessWinsV = darknessWins.isSelected();

        // B&W etc styles
        image = styleImage(style1, image);
        image2 = styleImage(style2, image2);

        // Flips
        image = flipImage(flipCombo1.getSelectedItem(), image);
        image2 = flipImage(flipCombo2.getSelectedItem(), image2);

        int p, q;
        int r, g, b;
        int origR, origG, origB;
        int layerR, layerG, layerB;
        for (int x = 0; x < image.getWidth(); ++x) {
            for (int y = 0; y < image.getHeight(); ++y) {
                p = image.getRGB(x, y); // & 0xff00;
                q = image2.getRGB(x, y); // & 0xff;

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
    }

    private BufferedImage rotateImage(Object selectedItem, BufferedImage image) {
        if ( "Left".equals(selectedItem) ) {
            return rotate(image, 90);
        }
        if ( "Right".equals(selectedItem) ) {
            return rotate(image, -90);
        }
        if ( "Full".equals(selectedItem) ) {
            return rotate(image, 180);
        }
        return image;
    }
    
    public BufferedImage rotate(BufferedImage image, int thetaInDegrees)
    {
      /*
       * Affline transform only works with perfect squares. The following
       *   code is used to take any rectangle image and rotate it correctly.
       *   To do this it chooses a center point that is half the greater
       *   length and tricks the library to think the image is a perfect
       *   square, then it does the rotation and tells the library where
       *   to find the correct top left point. The special cases in each
       *   orientation happen when the extra image that doesn't exist is
       *   either on the left or on top of the image being rotated. In
       *   both cases the point is adjusted by the difference in the
       *   longer side and the shorter side to get the point at the
       *   correct top left corner of the image. NOTE: the x and y
       *   axes also rotate with the image so where width > height
       *   the adjustments always happen on the y axis and where
       *   the height > width the adjustments happen on the x axis.
       *  
       */
      AffineTransform xform = new AffineTransform();
     
      if (image.getWidth() > image.getHeight())
      {
        xform.setToTranslation(0.5 * image.getWidth(), 0.5 * image.getWidth());
        xform.rotate(Math.toRadians(thetaInDegrees));
     
        int diff = image.getWidth() - image.getHeight();
     
        switch (thetaInDegrees)
        {
        case 90:
          xform.translate(-0.5 * image.getWidth(), -0.5 * image.getWidth() + diff);
          break;
        case 180:
          xform.translate(-0.5 * image.getWidth(), -0.5 * image.getWidth() + diff);
          break;
        default:
          xform.translate(-0.5 * image.getWidth(), -0.5 * image.getWidth());
          break;
        }
      }
      else if (image.getHeight() > image.getWidth())
      {
        xform.setToTranslation(0.5 * image.getHeight(), 0.5 * image.getHeight());
        xform.rotate(Math.toRadians(thetaInDegrees));
     
        int diff = image.getHeight() - image.getWidth();
     
        switch (thetaInDegrees)
        {
        case 180:
          xform.translate(-0.5 * image.getHeight() + diff, -0.5 * image.getHeight());
          break;
        case 270:
          xform.translate(-0.5 * image.getHeight() + diff, -0.5 * image.getHeight());
          break;
        default:
          xform.translate(-0.5 * image.getHeight(), -0.5 * image.getHeight());
          break;
        }
      }
      else
      {
        xform.setToTranslation(0.5 * image.getWidth(), 0.5 * image.getHeight());
        xform.rotate(Math.toRadians(thetaInDegrees));
        xform.translate(-0.5 * image.getHeight(), -0.5 * image.getWidth());
      }
     
      AffineTransformOp op = new AffineTransformOp(xform, AffineTransformOp.TYPE_BILINEAR);
     
      return op.filter(image, null);
    }

    private BufferedImage flipImage(Object selectedItem, BufferedImage image) {
        if ("Horizontal".equals(selectedItem) || "Both".equals(selectedItem)) {
            BufferedImage imageCopy = deepCopy(image);

            // Flip the image horizontally
            AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-image.getWidth(null), 0);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            op.filter(image, imageCopy);
            image = imageCopy;
        }
        if ("Vertical".equals(selectedItem) || "Both".equals(selectedItem)) {
            BufferedImage imageCopy = deepCopy(image);

            // Flip the image vertically
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
            tx.translate(0, -image.getHeight(null));
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            op.filter(image, imageCopy);
            image = imageCopy;
        }
        return image;
    }

    private BufferedImage styleImage(JComboBox styleCombo, BufferedImage image) {
        if ("B&W".equals(styleCombo.getSelectedItem())) {
            ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
            BufferedImage imageCopy = deepCopy(image);
            op.filter(image, imageCopy);
            return imageCopy;
        } else if ("Sepia".equals(styleCombo.getSelectedItem())) {
            BufferedImage imageCopy = deepCopy(image);
            applySepiaFilter(imageCopy, 20);
            return imageCopy;
        } else if ("Negative".equals(styleCombo.getSelectedItem())) {
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

    static BufferedImage deepCopy(BufferedImage bi) {
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
            BufferedImage mergedImage = mergeImages(0, 0);
            ImageIO.write(mergedImage, "jpg", file);
            prevSaveFile = file;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImagePanel imagePanel;
    private JSlider brightness1;
    private JSlider brightness2;
    private JCheckBox darknessWins;
    private JComboBox style1;
    private JComboBox style2;
    private JComboBox flipCombo1;
    private JComboBox flipCombo2;
    private JComboBox rotateCombo1;
    private JComboBox rotateCombo2;
    private static File filename1 = new File("/Users/dclark/public/face1.jpg");
    private static File filename2 = new File("/Users/dclark/public/layer1.jpg");
    private static File prevSaveFile = new File("/Users/dclark/tmp/mergedImage.jpg");

    public Main(String title) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        imagePanel = new ImagePanel();
        imagePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent arg0) {
                // Want to recompute our picture, but not all of the time cos
                // too expensive
                // Only after a little while
                redoMerge();
            }
        });
        loadImages(filename1, filename2);
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
                File file = getImageFile(Main.this, filename1);
                if (file != null) {
                    try {
                        baseImage = ImageIO.read(file);
                        filename1 = file;
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
                File file = getImageFile(Main.this, filename2);
                if (file != null) {
                    try {
                        layerImage = ImageIO.read(file);
                        filename2 = file;
                        // layerImage = ImageIO.read(new File(file2));
                        redoMerge();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        String[] styleStrings = { "Normal", "B&W", "Sepia", "Negative" };

        ActionListener alRedo = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                redoMerge();
            }
        };
        style1 = new JComboBox(styleStrings);
        style1.setSelectedIndex(0);
        style1.addActionListener(alRedo);

        style2 = new JComboBox(styleStrings);
        style2.setSelectedIndex(0);
        style2.addActionListener(alRedo);

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

        String[] flipChoices = new String[] { "No-flip", "Vertical", "Horizontal", "Both" };
        flipCombo1 = new JComboBox(flipChoices);
        flipCombo1.setSelectedIndex(0);
        flipCombo1.addActionListener(alRedo);

        flipCombo2 = new JComboBox(flipChoices);
        flipCombo2.setSelectedIndex(0);
        flipCombo2.addActionListener(alRedo);

        String [] rotateChoice = new String[] { "No-rotate", "Left", "Right", "Full" };
        rotateCombo1 = new JComboBox(rotateChoice);
        rotateCombo1.setSelectedIndex(0);
        rotateCombo1.addActionListener(alRedo);
        
        rotateCombo2 = new JComboBox(rotateChoice);
        rotateCombo2.setSelectedIndex(0);
        rotateCombo2.addActionListener(alRedo);
        
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

        controls.add(load1);
        controls.add(style1);
        controls.add(flipCombo1);
        controls.add(rotateCombo1);
        controls.add(brightness1);
        controls.add(load2);
        controls.add(style2);
        controls.add(flipCombo2);
        controls.add(rotateCombo2);
        controls.add(brightness2);
        controls.add(darknessWins);
        controls.add(reset);

        getContentPane().add(controls, BorderLayout.EAST);

        System.out.println("Shown window");
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

    public static void main(String[] args) {
        JFrame frame = new Main("Blend " + version);
        frame.setVisible(true);
    }
}
