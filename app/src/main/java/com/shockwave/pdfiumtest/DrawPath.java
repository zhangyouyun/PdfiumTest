package com.shockwave.pdfiumtest;

import android.graphics.Paint;
import android.graphics.Path;

/**
 * Created by Auser on 2016/3/11.
 */
public class DrawPath {
    //画笔和路径
    public Path path;
    public Paint paint;

    @Override
    public String toString() {
        return "DrawPath{" +
                "path=" + path +
                ", paint=" + paint +
                '}';
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public Paint getPaint() {
        return paint;
    }

    public void setPaint(Paint paint) {
        this.paint = paint;
    }
}
