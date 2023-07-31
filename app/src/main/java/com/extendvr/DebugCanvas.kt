package com.extendvr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.ArrayList


class DebugCanvas(context: Context?) : View(context) {
    var debugEnabled = false
    private var trackingData :ArrayList<VisionTracker.ColorGroup>? = null;
    var red = Paint();
    var green = Paint()
    init{
        red.color = Color.RED
        green.color = Color.GREEN
    }
    override fun onDraw(canvas: Canvas) {
        if(trackingData == null) return;
        for(color in trackingData!!) {
            var paint = if (color.name == "green") green else red
            for (blob in color.blobs) {
                //TODO: dont hardcode width and height values.
                canvas.drawRect((blob.x*640/width).toFloat(), (blob.y*480/height).toFloat(), (blob.width*640/width).toFloat(), (blob.height*480/height).toFloat(),paint)
            }
        }

        trackingData = null; // free up memory?
    }

    fun update(data: ArrayList<VisionTracker.ColorGroup>){
        // get the onDraw method to be called with the latest tracking data
        trackingData = data;
        invalidate();
    }
}