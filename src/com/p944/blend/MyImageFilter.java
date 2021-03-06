package com.p944.blend;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class MyImageFilter extends FileFilter {

    @Override
    public boolean accept(File arg0) {
        if ( arg0.getName().endsWith(".jpg") ) {
            return true;
        }
        return false;
    }

    @Override
    public String getDescription() {
        return ".jpg files only";
    }

}
