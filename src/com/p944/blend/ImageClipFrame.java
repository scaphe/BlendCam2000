package com.p944.blend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;


public class ImageClipFrame extends JDialog {

    private BufferedImage origImage;
    private ClipImagePanel imagePanel;
    public Main.Modification result;
    private boolean clipRight;

    public ImageClipFrame(BufferedImage origImage, final int imageIndex, boolean _clipRight) {
        super();
        clipRight = _clipRight;
        result = null;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModal(true);
        setTitle("Clip image - Drag points, ok when done");
        this.origImage = origImage;
        getContentPane().setLayout(new BorderLayout());
        imagePanel = new ClipImagePanel(clipRight);
        imagePanel.setImage(origImage);
        getContentPane().add(imagePanel);
        JButton okButton = new JButton("Ok");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (clipRight) {
                    result = new Main.ClipRight(imageIndex, imagePanel.Bx, imagePanel.By, imagePanel.Ax, imagePanel.Ay,
                            imagePanel.imageActualWidth, imagePanel.imageActualHeight);
                } else {
                    result = new Main.ClipLeft(imageIndex, imagePanel.Ax, imagePanel.Ay, imagePanel.Bx, imagePanel.By,
                            imagePanel.imageActualWidth, imagePanel.imageActualHeight);
                }
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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent arg0) {
            }
        });

        setVisible(true);
    }


    public static class ClipImagePanel extends JPanel {
        private final int IDLE = 0;
        private final int DRAGGING_A = 1;
        private final int DRAGGING_B = 2;
        private final int OVER_A = 3;
        private final int OVER_B = 4;
        private int state = IDLE;

        boolean clipRight;
        public int Ax = 0;
        public int Ay = 0;
        public int Bx = 0;
        public int By = 0;

        private int boxSize = 3;

        private BufferedImage image;
        public int imageDrawnWidth = 0;
        public int imageDrawnHeight = 0;
        private int imageActualWidth = 0;
        private int imageActualHeight = 0;

        private boolean setState(int s) {
            if (state != s) {
                System.out.println("State changed to "+s);
                state = s;
                return true;
            } else {
                return false;
            }
        }

        //constructor
        public ClipImagePanel(boolean _clipRight) {
            super();
            clipRight = _clipRight;
            setSize(300, 200);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent ev) {
                    System.out.println("MouseMove at "+ev.getX()+", "+ev.getY());
                    if (ev.getButton() == 1) {
                        if (isOver(ev.getX(), ev.getY(), Ax, Ay)) {
                            setState(DRAGGING_A);
                        } else if (isOver(ev.getX(), ev.getY(), Bx, By)) {
                            setState(DRAGGING_B);
                        } else {
                            setState(IDLE);
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent ev) {
                    if (ev.getButton() == 1) {
                        checkHighlight(ev);
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent ev) {
                    if (ev.getButton() == 1) {
                        int x = (int)((double)imageActualWidth / imageDrawnWidth * (ev.getX()-4));
                        int y = (int)((double)imageActualHeight / imageDrawnHeight * (ev.getY()-4));
                        if (state == DRAGGING_A) {
                            Ax = x;
                            Ay = y;
                            // Lock to an edge
                            // Are we nearer to the left or the top or the bottom
                            int toLeft = Ax;
                            int toTop = Ay;
                            int toBottom = imageActualHeight-Ay;
                            int m = Math.min(toLeft, Math.min(toTop, toBottom));
                            if (m == toLeft) {
                                Ax = 0;
                            } else if (m == toTop) {
                                Ay = 0;
                            } else {
                                // To bottom
                                Ay = imageActualHeight;
                            }

                        } else if (state == DRAGGING_B) {
                            Bx = x;
                            By = y;
                            // Lock to an edge
                            // Are we nearer to the left or the top or the bottom
                            int toRight = imageActualWidth-Bx;
                            int toTop = By;
                            int toBottom = imageActualHeight-By;
                            int m = Math.min(toRight, Math.min(toTop, toBottom));
                            if (m == toRight) {
                                Bx = imageActualWidth;
                            } else if (m == toTop) {
                                By = 0;
                            } else {
                                // To bottom
                                By = imageActualHeight;
                            }
                        }

                        repaint();
                    }
                }
                @Override
                public void mouseMoved(MouseEvent ev) {
                    checkHighlight(ev);
                }
            });
        }

        private void checkHighlight(MouseEvent ev) {
            if (isOver(ev.getX(), ev.getY(), Ax, Ay)) {
                if (setState(OVER_A)) repaint();
            } else if (isOver(ev.getX(), ev.getY(), Bx, By)) {
                if (setState(OVER_B)) repaint();
            } else {
                if (setState(IDLE)) repaint();
            }
        }

        private boolean isOver(int _evX, int _evY, int Cx, int Cy) {
            int evX = _evX-4;
            int evY = _evY-4;
            int Sx = (int)((double)imageDrawnWidth / imageActualWidth * Cx);
            int Sy = (int)((double)imageDrawnHeight / imageActualHeight * Cy);
            return evX >= (Sx-boxSize) && evX <= (Sx+boxSize) && evY >= (Sy-boxSize) && evY <= (Sy+boxSize);
        }

        public void setImage(BufferedImage read) {
            image = read;
            //might be a situation where image isn't fully loaded, and
            //  should check for that before setting...
            imageActualWidth = image.getWidth(this);
            imageActualHeight = image.getHeight(this);
            this.Ay = imageActualHeight/2;
            this.Bx = imageActualWidth;
            this.By = imageActualHeight/2;
            calcDrawSize();
            repaint();
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if ( image != null ) {
                calcDrawSize();
                BufferedImage bi = Main.deepCopy(image);
                if (clipRight) {
                    System.out.println("Clipping right");
                    Main.clip(bi, Bx, By, Ax, Ay);
                } else {
                    Main.clip(bi, Ax, Ay, Bx, By);
                }
                g.drawImage(bi, 4, 4, imageDrawnWidth, imageDrawnHeight, this);

                // Draw A and B points
                drawPoint(g, Ax, Ay, state == OVER_A);
                drawPoint(g, Bx, By, state == OVER_B);
            }
        }

        private void drawPoint(Graphics g, int _x, int _y, boolean highlight) {
            if (highlight) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.CYAN);
            }
            int x = 4+(int)((double)imageDrawnWidth / imageActualWidth * _x);
            int y = 4+(int)((double)imageDrawnHeight / imageActualHeight * _y);

            g.fillRect(x-boxSize, y-boxSize, boxSize*2, boxSize*2);
//            System.out.println("Draw point at "+x+", "+y);
        }

        private void calcDrawSize() {
            // Margin of 4 px around picture
            int windowWidth = getWidth()-8;
            int windowHeight = getHeight()-8;
//            System.out.println("drawing size of "+imageActualWidth+", "+imageActualHeight);

            // Get the smallest ratio
            final int w, h;
            if ( (double)windowWidth/imageActualWidth < (double)windowHeight/imageActualHeight ) {
                w = windowWidth;
                h = (int)((double)imageActualHeight / imageActualWidth * w);
            } else {
                h = windowHeight;
                w = (int)((double)imageActualWidth / imageActualHeight * h);
            }
            imageDrawnWidth = w;
            imageDrawnHeight = h;
        }
    }
}
