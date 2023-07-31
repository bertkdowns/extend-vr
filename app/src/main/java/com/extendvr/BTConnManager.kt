package com.extendvr

/*
* This class manages the bluetooth setup and inital connection to both devices
* It scans for previously found bluetooth devices, and handles the bluetooth device picker intent to allow users to initially choose devices,
* and storing the resulting mac address so that it can automagically reconnect.
* */

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences

import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothConfiguration
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService
import com.github.douglasjunior.bluetoothlowenergylibrary.BluetoothLeService

import java.util.UUID

open class BTConnManager(private val context: Context) {
    internal var preferences: SharedPreferences

    init {
        preferences = context.getSharedPreferences("BT_PREFS", Context.MODE_PRIVATE)

        //------------------ CONFIGURATION -------------------------
        val config = BluetoothConfiguration()
        config.context = context
        config.bluetoothServiceClass = BluetoothLeService::class.java
        config.bufferSize = 1024
        config.characterDelimiter = '\n'
        config.deviceName = "Your App Name"
        config.callListenersInMainThread = true

        config.uuidService = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb") // Required
        config.uuidCharacteristic = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb") // Required
        config.transport = BluetoothDevice.TRANSPORT_LE // Required for dual-mode devices
        config.uuid = null // Used to filter found devices. Set null to find all devices.
        BluetoothService.init(config)


        //------------------------- EVENT HANDLING -----------------------------

        val service = BluetoothService.getDefaultInstance()
        service.setOnScanCallback(object : BluetoothService.OnBluetoothScanCallback {
            // scan, and list the devices
            override fun onDeviceDiscovered(device: BluetoothDevice, rssi: Int) {
                // do something like: check if it is one of the 2 devices with the appropriate mac address, if not stop
            }

            override fun onStartScan() {}

            override fun onStopScan() {}
        })
        service.startScan() // See also service.stopScan();

    }


    fun chooseDevice(side: Int) {
        context.registerReceiver(BluetoothChoiceReciever(side), IntentFilter("android.bluetooth.devicepicker.action.DEVICE_SELECTED"))
        val bluetoothPicker = Intent("android.bluetooth.devicepicker.action.LAUNCH")
        bluetoothPicker.putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 0)// show all device
        bluetoothPicker.putExtra("android.bluetooth.devicepicker.extra.NEED_AUTH", false)
        bluetoothPicker.putExtra("android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE", "com.extendvr")
        context.startActivity(bluetoothPicker)// start the bluetooth picker
    }

    private inner class BluetoothChoiceReciever(// this class is used by connectBTDevice to get the device from the list

            private val buttonID: Int) : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            context.unregisterReceiver(this)

            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            // now we can finally set the device
            val editor = preferences.edit()
            if (buttonID == RIGHT_CONTROLLER) {
                editor.putString(RIGHT_SP_NAME, device.address)
            } else { // buttonID == LEFT_CONTROLLER
                editor.putString(LEFT_SP_NAME, device.address)
            }
            editor.apply()
            onDeviceFound(device, buttonID)
        }
    }

    open fun onDeviceFound(device: BluetoothDevice, id: Int) {
        // to be overriden by MainActivity later on
    }

    companion object { // this is equivalant to java's static variables
        var RIGHT_CONTROLLER = 0
        var LEFT_CONTROLLER = 1
        var LEFT_SP_NAME = "Left_Controller" // left and right shared preferences names
        var RIGHT_SP_NAME = "Right_Controller"
    }
}
