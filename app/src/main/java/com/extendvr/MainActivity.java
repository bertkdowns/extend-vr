package com.extendvr;


import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothConfiguration;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService;
import com.github.douglasjunior.bluetoothlowenergylibrary.BluetoothLeService;

import java.util.ArrayList;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    VisionTracker visionTracker;
    TrackingSocket socket = new TrackingSocket(8887);
    Context activityContext = this;
    boolean exposureLocked = false;
    BluetoothController rightHand = new BluetoothController();
    BluetoothController leftHand = new BluetoothController();

    // bluetooth text view class - for showing + storing information about the bluetooth devices.
    public class BTTextView extends AppCompatTextView {
        public BTTextView(Context context){
            super(context);
        }
        public BluetoothDevice device = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        visionTracker = new VisionTracker(this);
        visionTracker.dataProcessRoutine = new VisionTracker.onDataListener(){
            @Override
            public void onData(ArrayList<VisionTracker.ColorObject> data){
                sendNewTrackingData(data);
            }
        };

        socket.start();
        visionTracker.cameraContainer.start();
        // bluetooth scanning stuff
        _bluetooth_onCreate();


    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onStop() {
        super.onStop();
        //visionTracker.cameraContainer.stop();

    }

    private void _bluetooth_onCreate(){

        //------------------ CONFIGURATION -------------------------
        BluetoothConfiguration config = new BluetoothConfiguration();
        config.context = getApplicationContext();
        config.bluetoothServiceClass = BluetoothLeService.class;
        config.bufferSize = 1024;
        config.characterDelimiter = '\n';
        config.deviceName = "Your App Name";
        config.callListenersInMainThread = true;

        config.uuidService = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"); // Required
        config.uuidCharacteristic = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"); // Required
        config.transport = BluetoothDevice.TRANSPORT_LE; // Required for dual-mode devices
        config.uuid = null; // Used to filter found devices. Set null to find all devices.
        BluetoothService.init(config);


        //------------------------- EVENT HANDLING -----------------------------
        final LinearLayout btDeviceList = (LinearLayout) findViewById(R.id.btdevicelist);

        BluetoothService service = BluetoothService.getDefaultInstance();
        service.setOnScanCallback(new BluetoothService.OnBluetoothScanCallback() {
            // scan, and list the devices
            @Override
            public void onDeviceDiscovered(BluetoothDevice device, int rssi) {
                BTTextView btDevice = new BTTextView(activityContext);
                btDevice.setText(device.getName());
                btDevice.device = device;
                btDevice.setOnClickListener(new View.OnClickListener() {
                    // when the user clicks on a device, send it to the trackingService to connect to in background.
                    @Override
                    public void onClick(View v) {

                        BTTextView btTextView = (BTTextView) v;
                        rightHand.connect(btTextView.device);
                    }
                });
                btDeviceList.addView(btDevice);
            }

            @Override
            public void onStartScan() {
            }

            @Override
            public void onStopScan() {
            }
        });
        service.startScan(); // See also service.stopScan();

    }

    /** Called when a button is clicked (the button in the layout file attaches to
     * this method with the android:onClick attribute) */
    public void onLockExposureClick(View v) {
        // Call a method from the LocalService.
        // However, if this call were something that might hang, then this request should
        // occur in a separate thread to avoid slowing down the activity performance.
        int code = visionTracker.cameraContainer.lockExposure();
        if(code == 0) {Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show();exposureLocked=true;}
        else if(code==1)Toast.makeText(this,"Invalid color!",Toast.LENGTH_SHORT).show();
        else if(code==2)Toast.makeText(this,"Already locked",Toast.LENGTH_SHORT).show();
        else Toast.makeText(this,"error?!?", Toast.LENGTH_SHORT).show();

    }


    public void sendNewTrackingData(ArrayList<VisionTracker.ColorObject> data){
        String msg = "";
        msg += rightHand.w + "," + rightHand.x + "," + rightHand.y + "," + rightHand.z + ","
                + rightHand.thumb + "," + rightHand.indexFinger + "," + rightHand.middleFinger + ","
                + rightHand.ringFinger + "," + rightHand.pinkie + "\n";
        msg+= "\n"; // left hand controller data
        msg+= "\n"; // break
        // right hand information

        for(int i =0; i < data.get(0).object.size();i++){
            msg+=     data.get(0).object.get(i).x + ","
                    + data.get(0).object.get(i).y + ","
                    + data.get(0).object.get(i).width + ","
                    + data.get(0).object.get(i).height
                    + "\n";
        }
        // line break, then next left hand information

        socket.broadcast(msg);

    }

    public void onLaunchBrowserClick(View v){
        if(!exposureLocked){
            Toast.makeText(this,"exposure must be locked first",Toast.LENGTH_SHORT);
            return;
        }
        //else, good to launch browser
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(this, Uri.parse("http://localhost:12345/"));
    }

}
