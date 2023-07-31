/*
* This class is an image/container class for easy retrevial and manipulation of the YCbCr image data
* */
package com.extendvr

import android.support.annotation.ColorInt
import android.util.Log
import com.extendvr.Pixel.YCbCr

class TrackingImage(var Y: UByteArray, var Cb: UByteArray, var Cr: UByteArray) {
    var width = 320
    var height = 240

    /* YCbCr images often have less color data compared to brightness data. I.E 1 color pixel for every 4 brightness pixels.
    * hence we need to store the color pixel stride and row stride, because there is gonna be a quarter the amount of data for color
    * */
    var ColorPixelStride = 2
    var ColorRowStride = 2

    @ColorInt
    fun getPixel(x: Int, y: Int): Int { // cb and cr have half the width and x/2 and y/2 because their pixel stride is 2, ie 1 pixel for every 4 Y pixels
        if (x < width && y < height && x >= 0 && y >= 0) return YCbCr(Y[x + y * 320].toInt(),
                Cb[x / ColorPixelStride + y / ColorRowStride * 320 / ColorPixelStride].toInt(),
                Cr[x / ColorPixelStride + y / ColorRowStride * 320 / ColorPixelStride].toInt()) else Log.e("ERROR", "x or y outiside bounds: x is " + x + "y is " + y)
        return -1 // pixel does not exist
    }

}