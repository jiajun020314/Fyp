package com.example.fyp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.fyp.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale

class ObjectRecognition : AppCompatActivity(),TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech
    lateinit var labels: List<String>
    var paint = Paint()
    var colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var model: AutoModel1
    private val baseSpeechRate = 0.4f // Adjust the base speech rate as needed
    private val labelDelayMillis = 1000L // 1 second delay between labels
    private var lastSpokenTime = 0L
    private val spokenLabels = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_recognition)


        getPermission()
        textToSpeech = TextToSpeech(this, this)
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = AutoModel1.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView1)

        textureView = findViewById(R.id.textureView1)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                outputs.numberOfDetectionsAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x: Int

                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        val label = labels[classes[index].toInt()]

                        // Check if the label has not been spoken before
                        if (!spokenLabels.contains(label)) {
                            // Add this line to speak the label
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastSpokenTime > labelDelayMillis) {
                                // Add this line to speak the label
                                textToSpeech.speak(label, TextToSpeech.QUEUE_ADD, null, null)

                                // Update the last spoken time
                                lastSpokenTime = currentTime
                            }

                            paint.color = colors[index]
                            paint.style = Paint.Style.STROKE
                            canvas.drawRect(
                                RectF(
                                    locations[x + 1] * w,
                                    locations[x] * h,
                                    locations[x + 3] * w,
                                    locations[x + 2] * h
                                ),
                                paint
                            )
                            paint.style = Paint.Style.FILL
                            canvas.drawText(label, locations[x + 1] * w, locations[x] * h, paint)

                            // Add the label to the spokenLabels set
                            spokenLabels.add(label)
                        }
                    }
                }

                // Clear the spoken labels set if no labels are detected in the current frame
                if (spokenLabels.isEmpty()) {
                    spokenLabels.clear()
                }
                imageView.setImageBitmap(mutable)
            }

        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(p0: CameraCaptureSession) {
                            p0.setRepeatingRequest(captureRequest.build(), null, null)
                        }

                        override fun onConfigureFailed(p0: CameraCaptureSession) {

                        }

                    },
                    handler
                )

            }

            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }

        }, handler)
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported or missing data")
            } else {
                // TTS is initialized successfully
                textToSpeech.setSpeechRate(baseSpeechRate)  // Set the speech rate
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
