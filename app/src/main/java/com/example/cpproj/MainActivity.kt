package com.example.cpproj
import android.content.res.AssetManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.cpproj.databinding.ActivityMainBinding
import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity(), ImageAnalysis.Analyzer  {
    private val _paint = Paint()
    external fun loadClassifier(path:String)
    external fun detect(src: ByteArray, width: Int, height: Int, rotation: Int): FloatArray
    var out:String = ""
    private lateinit var imageBytes: ByteArray
    override fun analyze(image: ImageProxy) {
        if (image.planes.size < 3) {return}
        val rotation = image.imageInfo.rotationDegrees
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        if (!::imageBytes.isInitialized) {
            imageBytes = ByteArray(ySize + uSize + vSize)
        }

        //U and V are swapped
        yBuffer.get(imageBytes, 0, ySize);
        vBuffer.get(imageBytes, ySize, vSize);
        uBuffer.get(imageBytes, ySize + vSize, uSize);
        val bbox = detect(imageBytes,image.width,image.height,rotation)
        val canvas =  surfaceView2.holder.lockCanvas()

        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)

        out = "image width : ${image.width} viewport width: ${surfaceView2.width}"
        textView2.text = out
        _paint.color = Color.RED
        _paint.style = Paint.Style.STROKE
        _paint.strokeWidth = 3f
        _paint.textSize = 50f
        _paint.textAlign = Paint.Align.LEFT
        val x:Float = 200f
        val y:Float = 200f
        val frameWidth = image.width
        val frameHeight = image.height
        // Get the frame dimensions
        val w = if (rotation == 0 || rotation == 180) frameWidth else frameHeight
        val h = if (rotation == 0 || rotation == 180) frameHeight else frameWidth

        // detection coords are in frame coord system, convert to screen coords
        val scaleX = viewFinder.width.toFloat() / w
        val scaleY = viewFinder.height.toFloat() / h

        // The camera view offset on screen
        val xoff =  viewFinder.left.toFloat()
        val yoff =  viewFinder.top.toFloat()

        val imageLeft = xoff + bbox[0] * scaleX
        val imageRight = xoff + (bbox[0]+bbox[2]) * scaleX
        val imageTop = yoff + bbox[1] * scaleY
        val imageBottom = yoff + (bbox[1]+bbox[2]) * scaleY

        // Draw the rect

        canvas.drawRect(imageLeft, imageTop, imageRight, imageBottom, _paint)
        surfaceView2.holder.unlockCanvasAndPost(canvas)
        image.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
/* SAVE FILES IN ASSETS FOLDER TO DEVICE STORAGE */
        var filepath:String = saveAssetToStorage("haarcascade_frontalface_alt.xml")
        loadClassifier(filepath)
        surfaceView2.setZOrderOnTop(true)
        surfaceView2.holder.setFormat(PixelFormat.TRANSPARENT)

/*  LAUNCH DEVICE CAMERA
Request camera permissions*/
if (allPermissionsGranted()) {
   startCamera()
} else {
   ActivityCompat.requestPermissions(
       this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
}
// Set up the listener for take photo button
cameraExecutor = Executors.newSingleThreadExecutor()
}

    external fun stringFromJNI(): String

        // Used to load the 'native-lib' library on application startup.


// STORAGE VARIABLES CLASSES AND FUNCTIONS

private fun saveAssetToStorage(fname:String):String{
    var myExternalFile:File = File(getExternalFilesDir("/"),fname)
    if(!myExternalFile.isFile) {
   try {
       val fileOutPutStream = FileOutputStream(myExternalFile)
       val ins: InputStream
       ins = resources.assets.open(fname)
       val buffer = ByteArray(4096)
       var bytesRead: Int
       while (ins.read(buffer).also { bytesRead = it } != -1) {
           fileOutPutStream.write(buffer, 0, bytesRead)
       }
       fileOutPutStream.close()
       ins.close()
   } catch (e: Error) {
       e.printStackTrace()
   }
    }
    return myExternalFile.absolutePath
}

    // CAMERA VARIABLES CLASSES AND FUNCTIONS
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val rotation = viewFinder.display.rotation

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, this)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult( requestCode:Int, permissions: Array<String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        init {
            System.loadLibrary("SharedObject1")
        }
    }
}