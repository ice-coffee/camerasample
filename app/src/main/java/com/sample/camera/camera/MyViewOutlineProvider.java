package com.sample.camera.camera;

import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.RequiresApi;

/**
 * @author mzp
 * date : 2020/9/25
 * desc :
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MyViewOutlineProvider extends ViewOutlineProvider {

    private boolean isCircle;
    private int radius;

    public MyViewOutlineProvider(boolean isCircle, int radius) {
        this.isCircle = isCircle;
        this.radius = radius;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        Rect rect = new Rect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        if (isCircle) {
            outline.setOval(rect);
        } else if (radius != 0) {
            outline.setRoundRect(rect, radius);
        }
    }
}
