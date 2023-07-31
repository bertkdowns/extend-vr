package com.extendvr

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService.OnBluetoothEventCallback
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothStatus

class BluetoothController {
    private var service: BluetoothService? = null

    // BluetoothController contains hand and rotation information, and
    // handles updating based on latest values from the controller
    var w = 0f
    var x = 0f
    var y = 0f
    var z = 0f
    var thumb = 0
    var indexFinger = 0
    var middleFinger = 0
    var ringFinger = 0
    var pinkie = 0
    fun connect(device: BluetoothDevice?) {
        // listen for updates from the controller
        service = BluetoothService.getDefaultInstance()
        service?.setOnEventCallback(object : OnBluetoothEventCallback {
            override fun onDataRead(buffer: ByteArray, length: Int) {
                if (buffer != null && buffer.size > 12) {
                    var data = buffer.toUByteArray()
                    // Get quaternion coordinates from IMU
                    // controller passes them as 2 bit integer 1000 times their actual value
                    // so we convert it to a float with 3dp of precision here
                    w = (data[0] * 256u + data[1]).toFloat()/ 1000f
                    x = (data[2] * 256u + data[3]).toFloat()/ 1000f
                    y = (data[4] * 256u + data[5]).toFloat()/ 1000f
                    z = (data[6] * 256u + data[7]).toFloat()/ 1000f

                    // Get finger readings
                    // Each finger reading is a unsigned byte between 0 and 255
                    // 0 == finger straight up, 255 = finger bent
                    thumb = data[8].toInt()
                    indexFinger = data[9].toInt()
                    middleFinger = data[9].toInt()
                    ringFinger = data[9].toInt()
                    pinkie = data[9].toInt()
                }
                Log.i("BLUETOOTH_MSG", buffer.toString())
            }

            override fun onStatusChange(status: BluetoothStatus) {}
            override fun onDeviceName(deviceName: String) {}
            override fun onToast(message: String) {}
            override fun onDataWrite(buffer: ByteArray) {}
        })
        service?.connect(device)
    }
}

