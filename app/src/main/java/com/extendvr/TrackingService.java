package com.extendvr;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothStatus;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.StringTokenizer;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

public class TrackingService extends Service {
    private Button btnCapture;
    private TextureView textureView;
    // check state  orientation of output image
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
    private Size imageDimensions;
    private ImageReader imageReader;
    // save to file
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Button btnExpose;
    private byte[] JPEG;
    private String TAG = "CAMERA_SERVICE";
    private boolean cameraExposureLocked = false;
    private boolean processing = false;
    private volatile byte[] Y = new byte[76800];
    private volatile byte[] Cb= new byte[19200];
    private volatile byte[] Cr= new byte[19200];
    private volatile int CbCrRowStride;
    private volatile int CbCrPixelStride;
    private int imgHeight = 240;
    private int imgWidth = 320;
    public TrackingServer trackingServer;

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

    // websocket server class
    public class TrackingServer extends WebSocketServer {
        // initialiser
        public TrackingServer( int port ) {
            super( new InetSocketAddress( port ) );
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake ) {
            System.out.println( conn.getRemoteSocketAddress().getAddress().getHostAddress() + " joined the tracking feed" );
            trackingLoop();
        }

        @Override
        public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
            System.out.println( conn + " left the tracking feed" );
        }

        @Override
        public void onMessage( WebSocket conn, String message ) {

        }

        @Override
        public void onStart() {
            System.out.println("Server started!");
            setConnectionLostTimeout(0);
            setConnectionLostTimeout(100);
        }

        @Override
        public void onError( WebSocket conn, Exception ex ) {
            ex.printStackTrace();
            if( conn != null ) {
                // some errors like port binding failed may not be assignable to a specific websocket
            }
        }
    }

    boolean isTracking = false;
    public void trackingLoop(){
        // don't run this if already tracking
        if(isTracking) return;
        isTracking = true;
        long startTime;
        long endTime;
        long duration;
        String msg;
        while(trackingServer.getConnections().size() > 0){
            startTime = System.nanoTime();
            processImage();
            msg = "";
            for(int i =0; i < tracking.get(0).object.size();i++){
                msg+=     tracking.get(0).object.get(i).x + ","
                        + tracking.get(0).object.get(i).y + ","
                        + tracking.get(0).object.get(i).width + ","
                        + tracking.get(0).object.get(i).height
                        + "\n";
            }
            trackingServer.broadcast(msg);
            endTime = System.nanoTime();
            duration = (endTime - startTime)/1000000;//get milliseconds operating time
            if(duration < 33){
                // track 30 times per second -30fps
                try {
                    Thread.sleep(33 - duration);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }


        }
        isTracking = false;

    }


    //////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("ExtendVR Tracking Service")
                .setContentText("Currently running")
                .setContentIntent(pendingIntent).build();

        startForeground(1337, notification);
        startBackgroundThread();
        // start the websocket server
        int port = 8887; // 843 flash policy port
        trackingServer = new TrackingServer( port );
        trackingServer.start();
        System.out.println( "ChatServer started on port: " + trackingServer.getPort() );
        // initalise the bluetooth service stuff:
        onCreate_Bluetooth();

        // add a starting color
        tracking.add(new ColorObject(){
            public boolean check(int color){
                // remember color.red is Y , color.green is U (cr), color.blue is v (cb)
                if(Color.red(color) > 120 && Color.green(color) < 90 && Color.blue(color) < 60) return true;
                return false;
            }
        });


    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flags " + flags + " startId " + startId);
        openCamera();
        return super.onStartCommand(intent, flags, startId);
    }

    // open the camera instance
    private void openCamera(){
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraID = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert  map != null;
            imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];
            // check realtime  permission if api level 23 or higher
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(),"No permission. Bad boy!", Toast.LENGTH_LONG).show();
                return;
            }
            manager.openCamera(cameraID,stateCallBack,null);
        } catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void createCameraPreview(){
        try{
            //SurfaceTexture texture = textureView.getSurfaceTexture();
            //assert texture != null;
            //texture.setDefaultBufferSize(imageDimensions.getWidth(),imageDimensions.getHeight());
            //Surface surface = new Surface(texture);

            imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if(image != null) {
                        if (!processing) {
                            //get the image, and store the Y, Cb (U), and Cr (V) data in byte arrays
                            // Y plane
                            ByteBuffer YBuffer = image.getPlanes()[0].getBuffer();
                            YBuffer.get(Y);
                            // Y plane
                            ByteBuffer CbBuffer = image.getPlanes()[1].getBuffer();
                            CbBuffer.get(Cb);
                            // Y plane
                            ByteBuffer CrBuffer = image.getPlanes()[2].getBuffer();
                            CrBuffer.get(Cr);
                            CbCrPixelStride = image.getPlanes()[1].getPixelStride();
                            CbCrRowStride = image.getPlanes()[1].getRowStride();
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
                    Toast.makeText(getApplicationContext(), "changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void updatePreview(){
        if(cameraDevice == null){
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    public int lockExposure(){
        if(!cameraExposureLocked){
            if( (Y[1]&0xff) > 95 && (Cb[1]&0xff) < 90 && (Cr[1]&0xff) < 70) {
                // lock the exposure
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                try{
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                }
                return 0; // exposure locked successfully
            } else {
                Log.i(TAG,Integer.toString(Y[1]& 0xff) + " " + Integer.toString(Cb[1] & 0xff)+ " " + Integer.toString( Cr[1] & 0xff));
                return 1; // color not right
            }
        } else {return 2;}//exposure already locked
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
    @Override
    public void onDestroy() {
        try {
            cameraCaptureSessions.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e(TAG,"OnDestroy");
            Log.e(TAG, e.getMessage());
        }
        cameraCaptureSessions.close();
        stopBackgroundThread();
    }
    // start and stop background thread


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

    /////////////////////////////////////////////////////////////////////////////////////////////////////

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
    public volatile ArrayList<ColorObject> tracking = new ArrayList<>();

    // processImage
    // called by the HTTP server, when someone wants some image data
    private void processImage() {


        int color;
        // remove any previous objects
        for(int c = 0; c < tracking.size();c++){
            tracking.get(c).object.clear();
        }
        processing = true;

        // ITERATE THROUGH ALL THE PIXELS IN THE IMAGEVERSE!!!
        // iterate down(s)
        for(int down = 0; down < imgHeight; down+=5  ){
            checkNextPixelForObject:
            // iterate across boi
            for(int across = 0; across < imgWidth; across+=5){

                color = getTrackingColor(across,down,-1);// -1 means all colors
                if(color == -1) {continue;}
                Log.i(TAG,"Got something...");
                // else color is detected
                // first check if you've found an object that you have already been tracking
                // iterate through all tracked objects (of each color),
                // and if one of them takes up the space you are in, skip past it
                for(int colorGroup = 0; colorGroup < tracking.size();colorGroup++){
                    for( int item = 0; item < tracking.get(colorGroup).object.size();item++){
                        if(
                                tracking.get(colorGroup).object.get(item).x <= across &&
                                        tracking.get(colorGroup).object.get(item).farX >= across &&
                                        tracking.get(colorGroup).object.get(item).y <= down &&
                                        tracking.get(colorGroup).object.get(item).farY >= down
                        ){
                            // if true, then this pixel has a object here already, move on
                            // do we need to skip across by the width of the object???
                            continue checkNextPixelForObject;
                        }
                    }
                }
                // now we found the object, time to iterate through everything in the object and
                // find the thing we wanted to find and its dimensions boi
                int objectIndex = tracking.get(color).object.size();
                tracking.get(color).object.add(new TrackingObject(across,down));
                // move down a few rows from here, and scan across each row to see if you can get a new
                // max width and height
                for(int subDown = down+3; subDown < imgHeight;subDown+=3){
                    // go down 3 rows, scan left and right to determine the width at this point, and if off the object, break.
                    if(getTrackingColor(across,subDown,color) == -1)break; // cause we are off the object
                    tracking.get(color).object.get(objectIndex).farY = subDown;
                    // scan left
                    for(int subAcross = across; subAcross >= 0; subAcross--){
                        // if this is still the correct color, and it is the furtherest left that we have got so far,
                        // set the new x value to that
                        if(getTrackingColor(subAcross,subDown,color) != -1){
                            if(subAcross < tracking.get(color).object.get(objectIndex).x) {
                                tracking.get(color).object.get(objectIndex).x = subAcross;
                            }
                        } else {
                            // stop moving across the line if we have got to the edge of the thing
                            break;
                        }
                    }
                    // scan right
                    for(int subAcross = across; subAcross < imgWidth ;subAcross++){
                        // if this is still the correct color, and it is the furtherest left that we have got so far,
                        // set the new x value to that
                        if(getTrackingColor(subAcross,subDown,color) != -1){
                            if(subAcross < tracking.get(color).object.get(objectIndex).farX) {
                                tracking.get(color).object.get(objectIndex).farX = subAcross;
                            }
                        } else {
                            // stop moving across the line if we have got to the edge of the thing
                            break;
                        }
                    }
                }
                // now that we've mapped the object, check if its super close to any other objects of the same color.
                // if so, merge them
                for(int object = 0; object < objectIndex/*don't need to compare to itself*/;object++){
                    if(
                            (
                                    (
                                            tracking.get(color).object.get(objectIndex).x > tracking.get(color).object.get(object).x -5
                                                    && tracking.get(color).object.get(objectIndex).x < tracking.get(color).object.get(object).farX +5
                                    ) ||
                                            (
                                                    tracking.get(color).object.get(objectIndex).farX > tracking.get(color).object.get(object).x -5
                                                            && tracking.get(color).object.get(objectIndex).farX < tracking.get(color).object.get(object).farX +5
                                            )
                            )&&(
                                    (
                                            tracking.get(color).object.get(objectIndex).y > tracking.get(color).object.get(object).y -5
                                                    && tracking.get(color).object.get(objectIndex).y < tracking.get(color).object.get(object).farY +5
                                    ) ||
                                            (
                                                    tracking.get(color).object.get(objectIndex).farY > tracking.get(color).object.get(object).y -5
                                                            && tracking.get(color).object.get(objectIndex).farY < tracking.get(color).object.get(object).farY +5
                                            )
                            )
                    ){
                        // they are close enough together to be assumed tracking the same object, so combine them - furthest values stay!
                        if(tracking.get(color).object.get(objectIndex).x < tracking.get(color).object.get(object).x){
                            tracking.get(color).object.get(object).x = tracking.get(color).object.get(objectIndex).x;
                        }
                        if(tracking.get(color).object.get(objectIndex).y < tracking.get(color).object.get(object).y){
                            tracking.get(color).object.get(object).y = tracking.get(color).object.get(objectIndex).y;
                        }
                        if(tracking.get(color).object.get(objectIndex).farX > tracking.get(color).object.get(object).farX){
                            tracking.get(color).object.get(object).farX = tracking.get(color).object.get(objectIndex).farX;
                        }
                        if(tracking.get(color).object.get(objectIndex).farY > tracking.get(color).object.get(object).farY){
                            tracking.get(color).object.get(object).farY = tracking.get(color).object.get(objectIndex).farY;
                        }
                        // remove the old object
                        tracking.get(color).object.remove(objectIndex);
                        // for now this doesnt check for multiple close objects
                        // recursive functions may be needed for that?!?
                        // so just continue onwards, ever onwards
                        continue checkNextPixelForObject;
                    }
                }
            }
        }
        processing = false;

        // now we can calculate all the heightses and widthses, my precioussss!
        for(int colorGroup = 0; colorGroup < tracking.size();colorGroup++){
            for( int item = 0; item < tracking.get(colorGroup).object.size();item++){
                tracking.get(colorGroup).object.get(item).width = tracking.get(colorGroup).object.get(item).farX - tracking.get(colorGroup).object.get(item).x;
                tracking.get(colorGroup).object.get(item).height = tracking.get(colorGroup).object.get(item).farY - tracking.get(colorGroup).object.get(item).y;
            }
        }
    }



    private int getTrackingColor(int x,int y, int colorToCheck){
        // returns index of color we are tracking if its present, else it returns -1

        if(colorToCheck == -1){ // then we have to check all the colors
            for(int c = 0; c < tracking.size();c++) {// c++ hehehe!
                if(tracking.get(c).check(getColor(x,y))){
                    return c;
                }
            }
        } else{
            if(tracking.get(colorToCheck).check(getColor(x,y)))
                return colorToCheck;
        }
        // if they didnt work, no color found, return -1
        return -1;
    }

    @ColorInt
    public int getColor(int x, int y){
        // will store it as a Color int, but the r is actually the Y, the G is actually the Cb, and
        // the B is actually the Cr

        // cb and cr have half the width and x/2 and y/2 because their pixel stride is 2, ie 1 pixel for every 4 Y pixels
        return Color.rgb(Y[x+y*320] & 0xff,Cb[x/2+(y/2)*320/2]& 0xff,Cr[x/2+(y/2)*320/2]& 0xff);
    }



    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // binding stuff - to bind it to the main activity for gui things
    // Binder given to clients
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        TrackingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return TrackingService.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // bluetooth stuff
    BluetoothService service;
    private void onCreate_Bluetooth(){
        // onCreate routine for bluetooth, seperated out for easy reading
        service = BluetoothService.getDefaultInstance();
        service.setOnEventCallback(new BluetoothService.OnBluetoothEventCallback() {
            @Override
            public void onDataRead(byte[] buffer, int length) {
                Log.i("BLUETOOTH_MSG",buffer.toString());
            }

            @Override
            public void onStatusChange(BluetoothStatus status) { }
            @Override
            public void onDeviceName(String deviceName) { }
            @Override
            public void onToast(String message) { }
            @Override
            public void onDataWrite(byte[] buffer) { }
        });
    }

    public int connectToBTDevice(BluetoothDevice device){

        return 0;
    };


}