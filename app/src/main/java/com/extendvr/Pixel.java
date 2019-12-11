// this is basically like ColorInts, but for YCbCr color space
// to make the code more readable
package com.extendvr;

import android.graphics.Color;
import android.support.annotation.ColorInt;

public class Pixel {

    @ColorInt
    public static int Y(int colorInt){ // get Y part of color int
      return (colorInt >> 16) & 0xFF; // equivalent to Color.red
    }

    @ColorInt
    public static int Cb(int colorInt){  // get Cb part of color int
        return (colorInt >> 8) & 0xFF; // equivalent to Color.green
    };

    @ColorInt
    public static int Cr(int colorInt){  // get Cr part of color int
        return colorInt & 0xFF ;// equivalent to Color.blue
    };

    @ColorInt
    public static int YCbCr(int Y, int Cb, int Cr){// create color int
        return Color.rgb(Y,Cb,Cr);
    }
}
