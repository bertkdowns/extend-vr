package com.extendvr;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class VisionTracker extends Service {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    private String cameraID;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageReader imageReader;
    private boolean cameraExposureLocked = false;
    public TrackingImage trackingImage = new TrackingImage(new byte[76800],new byte[19200],new byte[19200]);
    public AtomicBoolean processing = new AtomicBoolean(false);
    // tracking classes
    static class TrackingObject{
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
    static class ColorObject{
        String name;
        // overridable check function
        public boolean check(int color){
            return false;
        }
        public ArrayList<TrackingObject> object = new ArrayList<TrackingObject>();
        // check function
    }
    public ArrayList<ColorObject> trackingTemplate = new ArrayList<>();

    private String TAG = "VisionTracker";
    private final IBinder binder = new VisionBinder();
    public class VisionBinder extends Binder {
        VisionTracker getService() {
            // Return this instance of LocalService so clients can call public methods
            return VisionTracker.this;
        }
    }




    @Override
    public void onCreate() {
        // add a starting color
        trackingTemplate.add(new ColorObject(){
            public boolean check(int color){
                // remember color.red is Y , color.green is U (cr), color.blue is v (cb)
                if(Pixel.Y(color) > 95 && Pixel.Y(color) < 200 && Pixel.Cb(color) < 115 && Pixel.Cr(color) < 100) return true;
                return false;
            }
        });
        // start the video feed
        startBackgroundThread();
        openCamera();
    }
    @Override
    public void onDestroy(){
        try {
            cameraCaptureSessions.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e(TAG,"OnDestroy");
            Log.e(TAG, e.getMessage());
        }
        cameraCaptureSessions.close();
        stopBackgroundThread();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }






    private int getTrackingColor(int pixel, int colorToCheck){
        // returns index of color we are tracking if its present, else it returns -1

        if(colorToCheck == -1){ // then we have to check all the colors
            for(int c = 0; c < trackingTemplate.size();c++) {// c++ hehehe!
                if(trackingTemplate.get(c).check(pixel)){
                    return c;
                }
            }
        } else{
            if(trackingTemplate.get(colorToCheck).check(pixel))
                return colorToCheck;
        }
        // if they didnt work, no color found, return -1
        return -1;
    }
    private ArrayList<ColorObject> data = trackingTemplate;

    public class ProcessImage implements Runnable
    {
        // in the future, will we have problems with these running at the same time and messing up eachothers tracking results?
        @Override
        public void run() {

        data = trackingTemplate;
        // remove any previous objects
        for (int c = 0; c < data.size(); c++) {
            data.get(c).object.clear();
        }

        // ACTUAL TRACKING ALGORITHM
        for (int y = 0; y < trackingImage.height; y += 5)
            for (int x = 0; x < trackingImage.width; x += 5) {
                int color = getTrackingColor(trackingImage.getPixel(x, y), -1);
                if (color == -1) continue; // nothing here
                //else
                // check if this location has already been tracked before
                boolean tracked = false;
                for (TrackingObject obj : data.get(color).object) {
                    if (obj.x < x && obj.farX > x && obj.y < y && obj.farY > y) {
                        // if so, we are in a place that has already been tracked, so move on
                        tracked = true;
                        break;
                    }
                }
                if (tracked) continue;
                // okay, we got a lock on the object! move up and down to find it's vertical center, then left and right to find its width + horizontal centre, then up and down again to find it's height
                TrackingObject newObject = new TrackingObject(x, y);
                int x1 = x;
                int y1 = y;
                if (y1 > 0) do {
                    y1 = y1 - 1;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && y1 > 0);
                newObject.y = y1;
                x1 = x;
                y1 = y;
                if (y1 < trackingImage.height) do {
                    y1++;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && y1 < trackingImage.height);
                newObject.farY = y1;
                // find the centre y coordinate from the average of the 2 y values
                int ycentre = (newObject.farY + newObject.y) / 2;
                y1 = ycentre;
                x1 = x;
                // now do the same thing except on the other axis (x axis)
                if (x1 > 0) do {
                    x1 = x1 - 1;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && x1 > 0);
                newObject.x = x1;
                x1 = x;
                if (x1 < trackingImage.width) do {
                    x1++;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && x1 < trackingImage.width);
                newObject.farX = x1;
                // now find the centre  x coordinate
                int xcentre = (newObject.farX + newObject.x) / 2;
                // now find the actual height, now that we are measuring along the centre of the ball
                x1 = xcentre;
                y1 = ycentre;
                if (y1 > 0) do {
                    y1 = y1 - 1;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && y1 > 0);
                newObject.y = y1;
                x1 = xcentre;
                y1 = ycentre;
                if (y1 < trackingImage.width) do {
                    y1++;
                } while (getTrackingColor(trackingImage.getPixel(x1, y1), color) == color && y1 < trackingImage.height);
                newObject.farY = y1;
                // all done!
                data.get(color).object.add(newObject);
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
        dataProcessRoutine.onData(data);
        processing.set(false);
    }
    }









    static class onDataListener{
        public void onData(ArrayList<VisionTracker.ColorObject> data){
            // do something
        }
    }
    public onDataListener dataProcessRoutine = new onDataListener();








    private CameraDevice.StateCallback stateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
            Log.e(TAG,"cameraDevice.statecallback onerror");
        }
    };


    private void openCamera(){
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraID = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert  map != null;
            // check realtime  permission if api level 23 or higher
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                Log.e(TAG,"camera permission has not been granted");
                return;
            }
            manager.openCamera(cameraID,stateCallBack,null);
        } catch(CameraAccessException e){
            e.printStackTrace();
        }
    }



    private void createCameraPreview(){
        try{
            trackingImage.width = 320;
            trackingImage.height = 240;
            imageReader = ImageReader.newInstance(trackingImage.width, trackingImage.height, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if(image != null) {
                        if(!processing.get()){
                            processing.set(true);
                            //get the image, and store the Y, Cb (U), and Cr (V) data in byte arrays
                            // Y plane
                            ByteBuffer YBuffer = image.getPlanes()[0].getBuffer();
                            YBuffer.get(trackingImage.Y);
                            // Y plane
                            ByteBuffer CbBuffer = image.getPlanes()[1].getBuffer();
                            CbBuffer.get(trackingImage.Cb);
                            // Y plane
                            ByteBuffer CrBuffer = image.getPlanes()[2].getBuffer();
                            CrBuffer.get(trackingImage.Cr);
                            //trackingImage.ColorPixelStride = image.getPlanes()[1].getPixelStride();
                            //trackingImage.ColorRowStride = image.getPlanes()[1].getRowStride();

                            // process the resultant image data
                            new Thread(new ProcessImage()).start();
                        }
                        image.close();

                    }
                }
            }, null);

            captureRequestBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_PREVIEW);
            //captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageReader.getSurface());



            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if(cameraDevice == null){
                        return;
                    }
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            },null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }


    private void updatePreview(){
        if(cameraDevice == null){
            Log.i(TAG,"cameraDevice == null");
        }
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }


    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////
    //                     PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////////////////////

    public int lockExposure(){
        if(!cameraExposureLocked){
            int firstPixel = trackingImage.getPixel(300,200);
            if(getTrackingColor(firstPixel,0) == 0){
                Log.i("PIXELDATA_Y",Integer.toString(Pixel.Y(firstPixel)));
                Log.i("PIXELDATA_Cb",Integer.toString(Pixel.Cb(firstPixel)));
                Log.i("PIXELDATA_Cr",Integer.toString(Pixel.Cr(firstPixel)));
                // then we have the right green, so we can lock the exposure
                // lock the exposure
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                try{
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                    return 3;
                }
                return 0; // exposure locked successfully
            } else {
                Log.i(TAG,"invalid color, could not lock exposure");
                Log.i(TAG,Integer.toString(Integer.MAX_VALUE));
                return 1; // color not right
            }
        } else {return 2;}//exposure already locked
    }



}
