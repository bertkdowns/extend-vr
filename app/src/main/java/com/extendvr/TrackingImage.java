/*
* This class is an image/container class for easy retrevial and manipulation of the YCbCr image data
* */

package com.extendvr;

import android.support.annotation.ColorInt;
import android.util.Log;

public class TrackingImage {
    public byte[] Y;
    public byte[] Cb;
    public byte[] Cr;
    public int width = 320;
    public int height = 240;
    /* YCbCr images often have less color data compared to brightness data, to
    * take advantage of the fact that our eyes are more sensitive to brightness than
    * color. we store this in the trackingImage so that when we get the pixel data, we
    * can account for this.*/
    public int ColorPixelStride = 2;
    public int ColorRowStride = 2;

    public TrackingImage(byte[] y, byte[] cb, byte[] cr){
        Y = y;
        Cb = cb;
        Cr = cr;
    }

    @ColorInt
    public int getPixel(int x, int y){ // cb and cr have half the width and x/2 and y/2 because their pixel stride is 2, ie 1 pixel for every 4 Y pixels
        if(x < width && y < height)
        return Pixel.YCbCr(Y[x+y*320] & 0xff,
             Cb[x/ColorPixelStride+(y/ColorRowStride)*320/ColorPixelStride]& 0xff,
             Cr[x/ColorPixelStride+(y/ColorRowStride)*320/ColorPixelStride]& 0xff);
        else Log.e("ERROR","x or y outiside bounds: x is " + x + "y is " + y);
        return 0;
    }

}



