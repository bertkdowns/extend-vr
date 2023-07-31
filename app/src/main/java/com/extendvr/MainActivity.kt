package com.extendvr

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.customtabs.CustomTabsIntent
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import org.java_websocket.WebSocket
import java.util.*


//TODO:Ask for camera and BluetoothAdapter permissions at start
class MainActivity : AppCompatActivity() {
    internal var visionTracker = VisionTracker();
    internal var webSocket = TrackingSocket(8887)
    internal var activityContext: Context = this
    internal var exposureLocked = false
    internal var visionTrackerBound = false
    internal var rightHand = BluetoothController()
    internal var leftHand = BluetoothController()
    private var bluetoothManager: BTConnManager? = null;
    private val TAG = "Main"
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    var latestRotationVals:SensorEvent? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_main)
        bluetoothManager = myBTConnManager(this)
        class myThread : Thread() {
            override fun run() {
                val intent = Intent(applicationContext, VisionTracker::class.java)
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
        myThread().run()

        webSocket.start()
        // bluetooth scanning stuff
        bluetoothManager = myBTConnManager(activityContext)


        webSocket.messageListener = object : TrackingSocket.OnMessageListener() {
            override fun onMessage(conn: WebSocket, message: String) {
                // when someone connects to our websocket and sends us a message, handle it here
            }
        }


    }
    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
    override fun onStart(){
        super.onStart()
        //get 30ms updates for rotation data
        sensorManager.registerListener(object:SensorEventListener{
            override fun onSensorChanged(event : SensorEvent){
                latestRotationVals = event;
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // not gonna worry about this?
            }
        },rotationSensor,30000)//30ms

    }



    /** Defines callbacks for service binding, passed to bindService()  */
    // Note: a service is needed for the camera, because the camera needs to run in the background even when
    // another app is open.
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName,
                                        service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as VisionTracker.VisionBinder
            visionTracker = binder.service
            visionTrackerBound = true
            visionTracker.dataProcessRoutine =  VisionTracker.onDataListener() {
                fun onData(data: ArrayList<VisionTracker.ColorGroup>) {
                    sendNewTrackingData(data)
                }
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            visionTrackerBound = false
        }
    }


    private inner class myBTConnManager(cnt: Context) : BTConnManager(cnt) {
        override fun onDeviceFound(device: BluetoothDevice, id: Int) {
            if (id == BTConnManager.LEFT_CONTROLLER) {
                leftHand.connect(device)// TODO: add support to show names
            } else {
                rightHand.connect(device)
                Log.i("BTDEVICE", "right hand connected")
            }
        }
    }

    fun connectController(v: View) {
        if (v.id == R.id.rightControllerbtn) {
            bluetoothManager?.chooseDevice(BTConnManager.RIGHT_CONTROLLER)
        } else {
            bluetoothManager?.chooseDevice(BTConnManager.LEFT_CONTROLLER)
        }
    }


    // The camera's exposure needs to be locked on long exposure for the app to work.
    // This allows the PSVR style colored balls to not be washed out on the camera.
    // to do this, put the lens of the camera on top of the glowing green tracker.
    // the camera will auto adjust to long exposure, so click the lock exposure button
    // to ensure it stays locked at this dark exposure.

    fun onLockExposureClick(v: View) {
        // Call a method from the localService. This method locks the exposure of the camera.
        if (visionTrackerBound) {
            val code = visionTracker.lockExposure()
            if (code == 0) {
                Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
                exposureLocked = true
            } else if (code == 1)
                Toast.makeText(this, "Invalid color!", Toast.LENGTH_SHORT).show()
            else if (code == 2)
                Toast.makeText(this, "Already locked", Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(this, "error?!?", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "error:Vision tracker class not bound", Toast.LENGTH_SHORT).show()
        }


    }

    // this function sends the latest tracking data across the websocket to any connected clients.
    // called by: visionTracker class
    fun sendNewTrackingData(data: ArrayList<VisionTracker.ColorGroup>) {
        // find largest object the tracker picked up of each color
        var bestObjects: ArrayList<VisionTracker.Blob?> = ArrayList(2);
        for(color in data){
            var largestAreaSoFar = 0
            var bestSoFar: VisionTracker.Blob? = null;
            for (b in color.blobs) {
                if (b.width * b.height > largestAreaSoFar) {
                    bestSoFar = b
                    largestAreaSoFar = b.width * b.height
                }
            }
            bestObjects.add(bestSoFar);
        }

        // send the data over the websocket in JSON format.



        var msg = """
            {
                "right":{
                    "q_w":${rightHand.w},
                    "q_x":${rightHand.x},
                    "q_y":${rightHand.y},
                    "q_z":${rightHand.z},
                    "thumb":${rightHand.thumb},
                    "index":${rightHand.indexFinger},
                    "middle":${rightHand.middleFinger},
                    "ring":${rightHand.ringFinger},
                    "pinkie:${rightHand.pinkie},
                    "pos":{
                        ${ if (bestObjects[0] != null)  """
                            "x":${bestObjects[0]?.x}
                            "y":${bestObjects[0]?.y}
                            "w":${bestObjects[0]?.width}
                            "h":${bestObjects[0]?.height}
                            
                        """.trimIndent() else ""}
                    }
                },
                "left":{
                    "q_w":${leftHand.w},
                    "q_x":${leftHand.x},
                    "q_y":${leftHand.y},
                    "q_z":${leftHand.z},
                    "thumb":${leftHand.thumb},
                    "index":${leftHand.indexFinger},
                    "middle":${leftHand.middleFinger},
                    "ring":${leftHand.ringFinger},
                    "pinkie:${leftHand.pinkie},
                    "pos":{
                        ${ if (bestObjects[0] != null)  """
                            "x":${bestObjects[0]?.x}
                            "y":${bestObjects[0]?.y}
                            "w":${bestObjects[0]?.width}
                            "h":${bestObjects[0]?.height}
                            
                        """.trimIndent() else ""}
                    }
                },
                "hmd":{
                    "q_w":${latestRotationVals?.values?.get(0)},
                    "q_x":${latestRotationVals?.values?.get(1)},
                    "q_y":${latestRotationVals?.values?.get(2)},
                    "q_z":${latestRotationVals?.values?.get(3)},
                }
            }""".trimIndent()

        // show all objects found (for debug purporses)
        /*for(int i =0; i < data.get(0).object.size();i++){
            msg+=     data.get(0).object.get(i).x + ","
                    + data.get(0).object.get(i).y + ","
                    + data.get(0).object.get(i).width + ","
                    + data.get(0).object.get(i).height
                    + "\n";
            Log.i(TAG, Integer.toString(data.get(0).object.get(i).x));
        }*/
        // line break, then next left hand information

        webSocket.broadcast(msg)

    }

    // launch the browser so that you can enter vr!
    fun onLaunchBrowserClick(v: View) {
        if (!exposureLocked) {
            Toast.makeText(this, "exposure must be locked first", Toast.LENGTH_SHORT).show()
            return
        }
        //else, good to launch browser
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse("http://localhost:1234/extendvr-test.html"))
    }

}
