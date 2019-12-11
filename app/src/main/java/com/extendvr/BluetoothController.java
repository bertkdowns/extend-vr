package com.extendvr;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothStatus;

public class BluetoothController {
    private BluetoothService service;
    // BluetoothController contains hand and rotation information
    public float w;
    public float x;
    public float y;
    public float z;
    int thumb;
    int indexFinger;
    int middleFinger;
    int ringFinger;
    int pinkie;

    public void connect(BluetoothDevice device){
        // setup tracking callback
        service = BluetoothService.getDefaultInstance();
        service.setOnEventCallback(new BluetoothService.OnBluetoothEventCallback() {
            @Override
            public void onDataRead(byte[] buffer, int length) {
                if (buffer != null && buffer.length > 12) {
                    w = ((buffer[0] & 0xff) << 8) | (buffer[1] & 0xff);
                    x = ((buffer[2] & 0xff) << 8) | (buffer[3] & 0xff);
                    y = ((buffer[4] & 0xff) << 8) | (buffer[5] & 0xff);
                    z = ((buffer[6] & 0xff) << 8) | (buffer[7] & 0xff);
                    thumb = buffer[8] & 0xFF * 255/6; // actual readings will be between 0 and 255 in final version, for consistency between controllers of
                    // different accuracy, rather than between 0 and 6
                    indexFinger = buffer[9] & 0xFF * 255/6;
                    middleFinger = buffer[10] & 0xFF * 255/6;
                    ringFinger = buffer[11] & 0xFF * 255/6;
                    pinkie = buffer[12] & 0xFF * 255/6;
                }
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


        service.connect(device);
    }
}
