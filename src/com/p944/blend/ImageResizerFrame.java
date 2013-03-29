package com.p944.blend;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;

import com.p944.blend.Main.ResizeMod;

public class ImageResizerFrame extends JDialog {

    private final BufferedImage origImage;
    private ImagePanel imagePanel;
    public MouseEvent down;
    public MouseEvent up;
    private double zoom = 1.0;
    protected MouseEvent move;
    protected double offsetX = 0;
    protected double offsetY = 0;
    public ResizeMod result;

    public ImageResizerFrame(BufferedImage origImage, final ResizeMod prev) {
        super();
        result = prev;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModal(true);
        setTitle("Reframe image - Drag to pan, Shift + drag to resize");
        this.origImage = origImage;
        getContentPane().setLayout(new BorderLayout());
        imagePanel = new ImagePanel();
        imagePanel.setImage(origImage);
        getContentPane().add(imagePanel);
        JButton okButton = new JButton("Ok");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                int w = imagePanel.imageDrawnWidth;
                int h = imagePanel.imageDrawnHeight;
                result = new ResizeMod(0, zoom, (double)offsetX/w, (double)offsetY/h);
                setVisible(false);
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                setVisible(false);
            }
        });
        getContentPane().add(Main.flow(cancelButton, okButton), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(400, 300));
        pack();

        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent ev) {
                if (ev.getButton() == 1) {
                    System.out.println("Pressed at " + ev.getY());
                    down = ev;
                    move = ev;
                }
            }

            @Override
            public void mouseReleased(MouseEvent ev) {
                if (ev.getButton() == 1) {
                    System.out.println("Release");
                    up = ev;
                }
            }
        });
        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent ev) {
                if (ev.getButton() == 1) {
                    System.out.println("Dragged at " + ev.getY());
                    int diffY = down.getY()-ev.getY();
                    int diffX = down.getX()-ev.getX();
                    if (ev.isShiftDown()) {
                        // Zoom
                        if ( Math.abs(diffY) > Math.abs(diffX) ) {
                            zoom += (double)diffY / imagePanel.imageDrawnWidth;
                        } else {
                            zoom += (double)diffX / imagePanel.imageDrawnHeight;
                        }
                        zoom = Math.max(zoom, 1);
                        System.out.println("Zoom is "+zoom);
                    } else {
                        // Pan
                        offsetX -= (ev.getX() - move.getX());
                        offsetY -= (ev.getY() - move.getY());
                    }
                    resizeImage();
                }
                move = ev;
            }
        });
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent arg0) {
                if ( prev != null ) {
                    int w = imagePanel.imageDrawnWidth;
                    int h = imagePanel.imageDrawnHeight;
                    zoom = prev.zoom;
                    offsetX = prev.offsetXPc*w;
                    offsetY = prev.offsetYPc*h;
                    resizeImage();
                }
            }
        });
        setVisible(true);
    }
    
    public void resizeImage() {
        if ( zoom > 1 ) {
            int w = imagePanel.imageDrawnWidth;
            int h = imagePanel.imageDrawnHeight;
            BufferedImage resized1 = new BufferedImage(w, h, origImage.getType());
            Graphics2D g = resized1.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(origImage, 0, 0, w, h, 0, 0, origImage.getWidth(), origImage.getHeight(), null);
            g.dispose();
            ResizeMod mod = new ResizeMod(0, zoom, (double)offsetX/w, (double)offsetY/h);
            BufferedImage resized2 = mod.modify(resized1);
//            BufferedImage resized2 = new BufferedImage(w, h, origImage.getType());
//            g = resized2.createGraphics();
//            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//            w *= zoom;
//            h *= zoom;
//            g.drawImage(resized1, 0, 0, w, h, (int)offsetX, (int)offsetY, (int)offsetX+resized1.getWidth(), (int)offsetY+resized1.getHeight(), null);
//            g.dispose();
            imagePanel.setImage(resized2);
        }
    }

    public ResizeMod showDialog() {
        setVisible(true);
        return null;
    }
}
