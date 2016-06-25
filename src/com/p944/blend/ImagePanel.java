package com.p944.blend;
/*
 * ImagePanel.java
 *
 * Copyright (C) 2007  Scott Carpenter (scottc at movingtofreedom dot org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Created on November 9, 2007, 4:07 PM 
 *
 */

import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import javax.swing.*;
import javax.imageio.*;

public class ImagePanel extends JPanel {
    
    private BufferedImage image;
    public int imageDrawnWidth = 0;
    public int imageDrawnHeight = 0;
    private int imageActualWidth = 0;
    private int imageActualHeight = 0;
    //private long paintCount = 0;
    
    //constructor
    public ImagePanel() {
        super();
        setSize(300, 200);
    }
    
    public void loadImage(String file) throws IOException {
        setImage(ImageIO.read(new File(file)));
    }

    public BufferedImage getImage() { return image; }

    public void setImage(BufferedImage read) {
        image = read;
        //might be a situation where image isn't fully loaded, and
        //  should check for that before setting...
        imageActualWidth = image.getWidth(this);
        imageActualHeight = image.getHeight(this);
        calcDrawSize();
        repaint();
    }
    
    //override paintComponent
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if ( image != null ) {
            //System.out.println("ImagePanel paintComponent " + ++paintCount);
 //           g.drawImage(scaledImage, 0, 0, this);
            // Lock aspect ratio of image
            calcDrawSize();
            
            g.drawImage(image, 0, 0, imageDrawnWidth, imageDrawnHeight, this);
        }
    }

    private void calcDrawSize() {
        int windowWidth = getWidth();
        int windowHeight = getHeight();
        System.out.println("drawing size of "+imageActualWidth+", "+imageActualHeight);
        
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