package com.wuc.photoview;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.TypedValue;

/**
 * @author : wuchao5
 * @date : 2021/12/17 11:28
 * @desciption :
 */
public class Utils {
  public static float dpToPixel(float dp) {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
        Resources.getSystem().getDisplayMetrics());
  }

  /** 获取bitmap */
  public static Bitmap getPhoto(Resources res, int width) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeResource(res, R.drawable.photo, options);
    options.inJustDecodeBounds = false;
    options.inDensity = options.outWidth;
    options.inTargetDensity = width;
    return BitmapFactory.decodeResource(res, R.drawable.photo, options);
  }
}