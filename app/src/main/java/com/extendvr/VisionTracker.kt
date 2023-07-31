package com.extendvr

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import com.extendvr.Pixel.Cb
import com.extendvr.Pixel.Cr
import com.extendvr.Pixel.Y
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class VisionTracker : Service() {
    // The vision tracking algorithm/service.
    // gets camera data, and uses a basic blob detection algorithm to detect blobs of color; I.E tracking markers
    // sends the result back to the mainActivity

    companion object {
        // orientations of the image
        private val ORIENTATIONS = SparseIntArray()
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private var cameraID: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSessions: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null
    private val cameraExposureLocked = false
    private final val TAG = "VisionTracker"
    var trackingImage = TrackingImage(UByteArray(76800), UByteArray(19200), UByteArray(19200))
    var processing = AtomicBoolean(false)

    // represents a blob we are tracking, with it's coordinates and width and height
    class Blob(var x: Int, var y: Int) {
        var farX: Int
        var farY: Int
        var width: Int
        var height: Int

        init {
            farX = x
            farY = y
            width = 1
            height = 1
        }
    }
    // represents a list of blobs of a single color
    open class ColorGroup {
        open var name = "green" // CSS COLOR NAMES PREFERRED

        // overridable check function
        // this function is designed to be ovverriden with whatever code you want to use to check
        // if the pixel is the color you are looking for
        open fun check(color: Int): Boolean {
            return false
        }

        var blobs = ArrayList<Blob>() // check function
    }

    var colorGroups = ArrayList<ColorGroup>()


    // the Binder is used to allow bound objects (i.e the main activity) to connect to this service
    private val binder: IBinder = VisionBinder()
    inner class VisionBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val service: VisionTracker
            // Return this instance of LocalService so clients can call public methods
            get() = this@VisionTracker
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        // add the color "Green" to be tracked
        // Y: brightness: Cb - chroma blue (green to blue) Cr - Chroma red - (green to red)
        this.colorGroups.add(object : ColorGroup() {
            override var name = "green"
            override fun check(color: Int): Boolean {
                return Y(color) > 95 && Y(color) < 200 && Cb(color) < 115 && Cr(color) < 100
            }
        })
        // add the color "Red"  to be tracked
        this.colorGroups.add(object : ColorGroup() {
            override var name = "red"
            override fun check(color: Int): Boolean {
                return Y(color) > 70 && Y(color) < 200 && Cb(color) < 90 && Cr(color) > 150
            }
        })
        // start the video feed
        startBackgroundThread()
        openCamera()
    }

    override fun onDestroy() {
        try {
            cameraCaptureSessions!!.abortCaptures()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "OnDestroy")
            Log.e(TAG, e.message)
        }
        cameraCaptureSessions!!.close()
        stopBackgroundThread()
    }



    private fun getTrackingColor(pixel: Int, colorToCheck: Int): Int {
        // returns index of color we are tracking if its present, else it returns -1
        if (pixel == -1) {
            // then the pixel does not exist, so no color present return false/-1
            return -1
        }
        if (colorToCheck == -1) { // then we have to check all the colors
            for (c in this.colorGroups.indices) { // c++ hehehe!
                if (this.colorGroups[c].check(pixel)) {
                    return c
                }
            }
        } else {
            if (this.colorGroups[colorToCheck].check(pixel)) return colorToCheck
        }
        // if they didnt work, no color found, return -1
        return -1
    }



    inner class ProcessImage : Runnable {
        // in the future, will we have problems with these running at the same time and messing up eachothers tracking results?
        override fun run() {
            colorGroups = colorGroups
            // remove any previous objects
            for (c in colorGroups.indices) {
                colorGroups[c].blobs.clear()
            }

            // tracking algorithm - check every 15th pixel per row/column and
            var y = 0
            while (y < trackingImage.height) {
                var x = 0
                while (x < trackingImage.width) {
                    val color = getTrackingColor(trackingImage.getPixel(x, y), -1)
                    if (color == -1) {
                        x += 15
                        continue  // nothing here
                    }
                    //else
                    // check if this location has already been tracked before
                    var tracked = false
                    for (obj in colorGroups[color].blobs) {
                        if (obj.x < x && obj.farX > x && obj.y < y && obj.farY > y) {
                            // if so, we are in a place that has already been tracked, so move on
                            tracked = true
                            break
                        }
                    }
                    if (tracked) {
                        x += 15
                        continue
                    }
                    // okay, we got a lock on the object! move up and down to find it's vertical center, then left and right to find its width + horizontal centre, then up and down again to find it's height
                    scoutObject(x, y, color)
                    x += 15
                }
                y += 15
            }
            // finished tracking!
            // now we can calculate all the heights and widths from the start and end co-ordinates
            for (colorGroup in colorGroups.indices) {
                for (item in colorGroups[colorGroup].blobs.indices) {
                    colorGroups[colorGroup].blobs[item].width = colorGroups[colorGroup].blobs[item].farX - colorGroups[colorGroup].blobs[item].x
                    colorGroups[colorGroup].blobs[item].height = colorGroups[colorGroup].blobs[item].farY - colorGroups[colorGroup].blobs[item].y
                }
            }

            // call the listener
            dataProcessRoutine.onData(colorGroups)
            processing.set(false)
        }
    }

    private fun scoutObject(x: Int, y: Int, color: Int) {
        // this routine scouts out a given xy co-ordinate (that we know is the correct color)
        // to find the width, height etc of the object and store it in the trackingobject
        val newBlob = Blob(x, y)
        var x1 = x
        var y1 = y
        while (y1 > 0 && getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
            y1 = y1 - 1
        }
        newBlob.y = y1
        x1 = x
        y1 = y
        while (y1 < trackingImage.height) {
            y1++
            if (getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
                if (getTrackingColor(trackingImage.getPixel(x1 + 2, y1), color) != color
                        && getTrackingColor(trackingImage.getPixel(x1 - 2, y1), color) != color) {
                    break // this also checks if nearby pixels are not the right color, to not rely on a single reading
                }
            } // else contionue
        }
        newBlob.farY = y1
        // find the centre y coordinate from the average of the 2 y values
        val ycentre = (newBlob.farY + newBlob.y) / 2
        y1 = ycentre
        x1 = x
        // now do the same thing except on the other axis (x axis)
        while (x1 > 0) {
            x1 = x1 - 1
            if (getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
                if (getTrackingColor(trackingImage.getPixel(x1, y1 - 2), color) != color
                        && getTrackingColor(trackingImage.getPixel(x1, y1 + 2), color) != color) {
                    break // this also checks if nearby pixels are not the right color, to not rely on a single reading
                }
            } // else contionue
        }
        newBlob.x = x1
        x1 = x
        while (x1 < trackingImage.width) {
            x1 = x1 + 1
            if (getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
                if (getTrackingColor(trackingImage.getPixel(x1, y1 - 2), color) != color
                        && getTrackingColor(trackingImage.getPixel(x1, y1 + 2), color) != color) {
                    break // this also checks if nearby pixels are not the right color, to not rely on a single reading
                }
            } // else contionue
        }
        newBlob.farX = x1
        // now find the centre  x coordinate
        val xcentre = (newBlob.farX + newBlob.x) / 2
        // now find the actual height, now that we are measuring along the centre of the ball
        x1 = xcentre
        y1 = ycentre
        while (y1 > 0) {
            y1 = y1 - 1
            if (getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
                if (getTrackingColor(trackingImage.getPixel(x1 + 2, y1), color) != color
                        && getTrackingColor(trackingImage.getPixel(x1 - 2, y1), color) != color) {
                    break // this also checks if nearby pixels are not the right color, to not rely on a single reading
                }
            } // else contionue
        }
        newBlob.y = y1
        x1 = xcentre
        y1 = ycentre
        while (y1 < trackingImage.height) {
            y1++
            if (getTrackingColor(trackingImage.getPixel(x1, y1), color) != color) {
                if (getTrackingColor(trackingImage.getPixel(x1 + 2, y1), color) != color
                        && getTrackingColor(trackingImage.getPixel(x1 - 2, y1), color) != color) {
                    break // this also checks if nearby pixels are not the right color, to not rely on a single reading
                }
            } // else contionue
        }
        newBlob.farY = y1
        // all done!
        this.colorGroups[color].blobs.add(newBlob)
    }


    // dataProcessRoutine overriden by mainAcitvity, and run when the blob detection algorithm has calculated another frame
    var dataProcessRoutine = onDataListener { fun onData(data: ArrayList<VisionTracker.ColorGroup>) {} };
    class onDataListener(function: () -> Unit) {
        fun onData(data: ArrayList<ColorGroup>?) {
            // do something
        }
    }




    ////////////////////////////////////////////////////////////////////////////////////////\
    //                           CAMERA STUFF
    ///////////////////////////////////////////////////////////////////////////////////////

    private val stateCallBack: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
            Log.e(TAG, "cameraDevice.statecallback onerror")
        }
    }


    // opening a connection to the camera is way too complicated in android

    private fun openCamera() {
        val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraID = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraID)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            // check realtime  permission if api level 23 or higher
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "camera permission has not been granted")
                return
            }
            manager.openCamera(cameraID, stateCallBack, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreview() {
        try {
            trackingImage.width = 320
            trackingImage.height = 240
            imageReader = ImageReader.newInstance(trackingImage.width, trackingImage.height, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener(OnImageAvailableListener { reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    if (!processing.get()) {
                        processing.set(true)
                        //get the image, and store the Y, Cb (U), and Cr (V) data in byte arrays
                        image.planes[0].buffer
                        trackingImage.Cb = image.planes[1].buffer.array().toUByteArray()
                        trackingImage.Cr = image.planes[2].buffer.array().toUByteArray()

                        //trackingImage.ColorPixelStride = image.getPlanes()[1].getPixelStride();
                        //trackingImage.ColorRowStride = image.getPlanes()[1].getRowStride();

                        // process the resultant image data
                        Thread(ProcessImage()).start()
                    }
                    image.close()
                }
            }, null)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            //captureRequestBuilder.addTarget(surface);
            captureRequestBuilder?.addTarget(imageReader?.getSurface())
            cameraDevice!!.createCaptureSession(Arrays.asList(imageReader?.getSurface()), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) {
                        return
                    }
                    cameraCaptureSessions = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) {
            Log.i(TAG, "cameraDevice == null")
        }
        try {
            cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    //////////////////////////////////////////////////////////////////////////////////////
    //                            THREAD STUFF
    //////////////////////////////////////////////////////////////////////////////////////

    // the camera has to be run on a background thread to avoid it hogging the main thread
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    //                     PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////////////////////
    fun lockExposure(): Int {
        return if (!cameraExposureLocked) {
            val firstPixel = trackingImage.getPixel(300, 200)
            if (getTrackingColor(firstPixel, 0) == 0) {
                Log.i("PIXELDATA_Y", Integer.toString(Y(firstPixel)))
                Log.i("PIXELDATA_Cb", Integer.toString(Cb(firstPixel)))
                Log.i("PIXELDATA_Cr", Integer.toString(Cr(firstPixel)))
                // then we have the right green, so we can lock the exposure
                // lock the exposure
                captureRequestBuilder!!.set(CaptureRequest.CONTROL_AE_LOCK, true)
                try {
                    cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                    return 3
                }
                0 // exposure locked successfully
            } else {
                Log.i(TAG, "invalid color, could not lock exposure")
                Log.i(TAG, Integer.toString(Int.MAX_VALUE))
                1 // color not right
            }
        } else {
            2
        } //exposure already locked
    }
}