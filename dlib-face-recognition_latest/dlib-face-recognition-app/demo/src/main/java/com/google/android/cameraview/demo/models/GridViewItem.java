package com.google.android.cameraview.demo.models;

import android.graphics.Bitmap;

public class GridViewItem {
    private String path;
    private Bitmap image;


    public GridViewItem(String path, Bitmap image) {
        this.path = path;
        this.image = image;
    }


    public String getPath() {
        return path;
    }

    public Bitmap getImage() {
        return image;
    }
}
