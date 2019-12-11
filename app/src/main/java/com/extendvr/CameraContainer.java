/*
* CameraContainer class- contains all the required camera classes and objects
* Returns Y Cb Cr data from the camera
* Used by the Vision tracker class, which implements the algorithm
* */

package com.extendvr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class CameraContainer {
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
    private TrackingImage trackingImage = new TrackingImage(new byte[76800],new byte[19200],new byte[19200]);
    private static String TAG = "CameraContainer";
    private Context context;

    public CameraContainer(Context _context){
        context = _context;
    }


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
        }
    };


    private void openCamera(){
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraID = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert  map != null;
            // check realtime  permission if api level 23 or higher
            if(ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
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
                        trackingImage.ColorPixelStride = image.getPlanes()[1].getPixelStride();
                        trackingImage.ColorRowStride = image.getPlanes()[1].getRowStride();
                        image.close();
                        // run the onNewFrame callback
                        onNewFrame.run(trackingImage);
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

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

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

    public void start(){
        startBackgroundThread();
        openCamera();
    }


    public void stop() {
        try {
            cameraCaptureSessions.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e(TAG,"OnDestroy");
            Log.e(TAG, e.getMessage());
        }
        cameraCaptureSessions.close();
        stopBackgroundThread();
    }

    public int lockExposure(){
        if(!cameraExposureLocked){
            int firstPixel = trackingImage.getPixel(1,1);
            if(Pixel.Y(firstPixel) > 95 && Pixel.Cb(firstPixel) < 90 && Pixel.Cr(firstPixel) < 70){
                // then we have the right green, so we can lock the exposure
                // lock the exposure
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                try{
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                }
                return 0; // exposure locked successfully
            } else {
                Log.i(TAG,"invalid color, could not lock exposure");
                return 1; // color not right
            }
        } else {return 2;}//exposure already locked
    }



    public TrackingListener onNewFrame = new TrackingListener();


}
