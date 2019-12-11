package com.extendvr;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;

public class VisionTracker {
    public CameraContainer cameraContainer;
    private String TAG = "VisionTracker";

    public VisionTracker(Context context){
        // add a starting color
        tracking.add(new ColorObject(){
            public boolean check(int color){
            // remember color.red is Y , color.green is U (cr), color.blue is v (cb)
            if(Color.red(color) > 120 && Color.green(color) < 90 && Color.blue(color) < 60) return true;
            return false;
            }
        });
        // start the video feed
        cameraContainer = new CameraContainer(context);
        cameraContainer.start();
        cameraContainer.onNewFrame = trackingRoutine;
    }

    // tracking classes
    class TrackingObject{
        public TrackingObject(int X,int Y){
            x = X;
            y = Y;
            farX = X;
            farY = Y;
            width = 1;
            height = 1;
        }
        public int x;
        public int y;
        public int farX;
        public int farY;
        public int width;
        public int height;
    }
    class ColorObject{
        String name;
        // overridable check function
        public boolean check(int color){
            return false;
        }
        public ArrayList<TrackingObject> object = new ArrayList<TrackingObject>();
        // check function
    }
    public ArrayList<ColorObject> tracking = new ArrayList<>();

    private int getTrackingColor(int pixel, int colorToCheck){
        // returns index of color we are tracking if its present, else it returns -1

        if(colorToCheck == -1){ // then we have to check all the colors
            for(int c = 0; c < tracking.size();c++) {// c++ hehehe!
                if(tracking.get(c).check(pixel)){
                    return c;
                }
            }
        } else{
            if(tracking.get(colorToCheck).check(pixel))
                return colorToCheck;
        }
        // if they didnt work, no color found, return -1
        return -1;
    }
    public boolean processing = false;
    public TrackingListener trackingRoutine = new TrackingListener(){
        // in the future, will we have problems with these running at the same time and messing up eachothers tracking results?
        public void run(final TrackingImage trackingImage) {
            if(!processing){
                processing = true;
                class TrackingRoutine extends Thread {
                    ArrayList<ColorObject> data = tracking;
                    public void run() {
                        int color;


                        // remove any previous objects
                        for (
                                int c = 0; c < data.size(); c++) {
                            data.get(c).object.clear();
                        }


                        // Iterate through all the pixels in the image
                        // iterate down(s)
                        for (
                                int down = 0;
                                down < trackingImage.height; down += 5) {
                            checkNextPixelForObject:
                            // iterate across boi
                            for (int across = 0; across < trackingImage.width; across += 5) {

                                color = getTrackingColor(trackingImage.getPixel(across, down), -1);// -1 means all colors
                                if (color == -1) {
                                    continue;
                                }
                                Log.i(TAG, "Got something...");
                                // else color is detected
                                // first check if you've found an object that you have already been tracking
                                // iterate through all tracked objects (of each color),
                                // and if one of them takes up the space you are in, skip past it
                                for (int colorGroup = 0; colorGroup < data.size(); colorGroup++) {
                                    for (int item = 0; item < data.get(colorGroup).object.size(); item++) {
                                        if (
                                                data.get(colorGroup).object.get(item).x <= across &&
                                                        data.get(colorGroup).object.get(item).farX >= across &&
                                                        data.get(colorGroup).object.get(item).y <= down &&
                                                        data.get(colorGroup).object.get(item).farY >= down
                                        ) {
                                            // if true, then this pixel has a object here already, move on
                                            // do we need to skip across by the width of the object???
                                            continue checkNextPixelForObject;
                                        }
                                    }
                                }


                                // now we found the object, time to iterate through everything in the object and
                                // find the thing we wanted to find and its dimensions boi
                                int objectIndex = data.get(color).object.size();
                                data.get(color).object.add(new TrackingObject(across, down));
                                // move down a few rows from here, and scan across each row to see if you can get a new
                                // max width and height
                                for (int subDown = down + 3; subDown < trackingImage.height; subDown += 3) {
                                    // go down 3 rows, scan left and right to determine the width at this point, and if off the object, break.
                                    if (getTrackingColor(trackingImage.getPixel(across, subDown), color) == -1)
                                        break; // cause we are off the object
                                    data.get(color).object.get(objectIndex).farY = subDown;
                                    // scan left
                                    for (int subAcross = across; subAcross >= 0; subAcross--) {
                                        // if this is still the correct color, and it is the furtherest left that we have got so far,
                                        // set the new x value to that
                                        if (getTrackingColor(trackingImage.getPixel(subAcross, subDown), color) != -1) {
                                            if (subAcross < data.get(color).object.get(objectIndex).x) {
                                                data.get(color).object.get(objectIndex).x = subAcross;
                                            }
                                        } else {
                                            // stop moving across the line if we have got to the edge of the thing
                                            break;
                                        }
                                    }
                                    // scan right
                                    for (int subAcross = across; subAcross < trackingImage.width; subAcross++) {
                                        // if this is still the correct color, and it is the furtherest left that we have got so far,
                                        // set the new x value to that
                                        if (getTrackingColor(trackingImage.getPixel(subAcross, subDown), color) != -1) {
                                            if (subAcross < data.get(color).object.get(objectIndex).farX) {
                                                data.get(color).object.get(objectIndex).farX = subAcross;
                                            }
                                        } else {
                                            // stop moving across the line if we have got to the edge of the thing
                                            break;
                                        }
                                    }
                                }
                                // now that we've mapped the object, check if its super close to any other objects of the same color.
                                // if so, merge them
                                for (int object = 0; object < objectIndex/*don't need to compare to itself*/; object++) {
                                    if (
                                            (
                                                    (
                                                            data.get(color).object.get(objectIndex).x > data.get(color).object.get(object).x - 5
                                                                    && data.get(color).object.get(objectIndex).x < data.get(color).object.get(object).farX + 5
                                                    ) ||
                                                            (
                                                                    data.get(color).object.get(objectIndex).farX > data.get(color).object.get(object).x - 5
                                                                            && data.get(color).object.get(objectIndex).farX < data.get(color).object.get(object).farX + 5
                                                            )
                                            ) && (
                                                    (
                                                            data.get(color).object.get(objectIndex).y > data.get(color).object.get(object).y - 5
                                                                    && data.get(color).object.get(objectIndex).y < data.get(color).object.get(object).farY + 5
                                                    ) ||
                                                            (
                                                                    data.get(color).object.get(objectIndex).farY > data.get(color).object.get(object).y - 5
                                                                            && data.get(color).object.get(objectIndex).farY < data.get(color).object.get(object).farY + 5
                                                            )
                                            )
                                    ) {
                                        // they are close enough together to be assumed tracking the same object, so combine them - furthest values stay!
                                        if (data.get(color).object.get(objectIndex).x < data.get(color).object.get(object).x) {
                                            data.get(color).object.get(object).x = data.get(color).object.get(objectIndex).x;
                                        }
                                        if (data.get(color).object.get(objectIndex).y < data.get(color).object.get(object).y) {
                                            data.get(color).object.get(object).y = data.get(color).object.get(objectIndex).y;
                                        }
                                        if (data.get(color).object.get(objectIndex).farX > data.get(color).object.get(object).farX) {
                                            data.get(color).object.get(object).farX = data.get(color).object.get(objectIndex).farX;
                                        }
                                        if (data.get(color).object.get(objectIndex).farY > data.get(color).object.get(object).farY) {
                                            data.get(color).object.get(object).farY = data.get(color).object.get(objectIndex).farY;
                                        }
                                        // remove the old object
                                        data.get(color).object.remove(objectIndex);
                                        // for now this doesnt check for multiple close objects
                                        // recursive functions may be needed for that?!?
                                        // so just continue onwards, ever onwards
                                        continue checkNextPixelForObject;
                                    }
                                }
                            }
                        }


                        // now we can calculate all the heights and widths from the start and end co-ordinates
                        for (
                                int colorGroup = 0; colorGroup < data.size(); colorGroup++) {
                            for (int item = 0; item < data.get(colorGroup).object.size(); item++) {
                                data.get(colorGroup).object.get(item).width = data.get(colorGroup).object.get(item).farX - data.get(colorGroup).object.get(item).x;
                                data.get(colorGroup).object.get(item).height = data.get(colorGroup).object.get(item).farY - data.get(colorGroup).object.get(item).y;
                            }
                        }

                        // call the listener
                        dataProcessRoutine.onData(tracking);
                        processing = false;
                    }
                }
                new TrackingRoutine().start();
            }


        }
    };



    static class onDataListener{
        public void onData(ArrayList<ColorObject> data){
            // do something
        }
    }
    public onDataListener dataProcessRoutine = new onDataListener();


}
