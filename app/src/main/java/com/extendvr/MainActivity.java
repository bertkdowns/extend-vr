package com.extendvr;


import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
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

import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    TrackingService trackingService;
    boolean trackingServiceBound = false;
    Context activityContext = this;

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


        final LinearLayout btDeviceList = (LinearLayout) findViewById(R.id.btdevicelist);
        // bluetooth scanning stuff
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
                        trackingService.connectToBTDevice(btTextView.device);
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

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, TrackingService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
        trackingServiceBound = false;
    }

    /** Called when a button is clicked (the button in the layout file attaches to
     * this method with the android:onClick attribute) */
    public void onLockExposureClick(View v) {
        if (trackingServiceBound) {
            // Call a method from the LocalService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
            int code = trackingService.lockExposure();
            if(code == 0) Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show();
            else if(code==1)Toast.makeText(this,"Invalid color!",Toast.LENGTH_SHORT).show();
            else if(code==2)Toast.makeText(this,"Already locked",Toast.LENGTH_SHORT).show();
            else Toast.makeText(this,"error?!?", Toast.LENGTH_SHORT).show();

        }
    }

    /** Defines callbacks for service binding, passed to bindService() so that we can bind to TrackingService */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TrackingService.LocalBinder binder = (TrackingService.LocalBinder) service;
            trackingService = binder.getService();
            trackingServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            trackingServiceBound = false;
        }
    };


}
