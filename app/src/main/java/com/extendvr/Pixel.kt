// this is basically like ColorInts, but for YCbCr color space
// to make the code more readable
package com.extendvr

import android.graphics.Color
import android.support.annotation.ColorInt

object Pixel {

    @ColorInt
    fun Y(colorInt: Int): Int { // get Y part of color int
        return Color.red(colorInt) // equivalent to Color.red
    }

    @ColorInt
    fun Cb(colorInt: Int): Int {  // get Cb part of color int
        return Color.green(colorInt) // equivalent to Color.green
    }

    @ColorInt
    fun Cr(colorInt: Int): Int {  // get Cr part of color int
        return Color.blue(colorInt)// equivalent to Color.blue
    }

    @ColorInt
    fun YCbCr(Y: Int, Cb: Int, Cr: Int): Int {// create color int
        return Color.rgb(Y, Cb, Cr)
    }
}
