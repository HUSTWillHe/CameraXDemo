package com.example.cameraxdemo

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cameraxdemo.databinding.ActivityMainBinding
import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import com.example.glsurfaceviewdemo.MyGLRenderer
import com.example.glsurfaceviewdemo.MyGLSurfaceView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var mCamera: CameraDevice
    private lateinit var mCamtureSession: CameraCaptureSession
    private var mHandler: Handler? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mRenderer: MyGLRenderer? = null
    private var mGLView: MyGLSurfaceView? = null
    private var mPreviewSize: Size = Size(0, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mGLView == null) {
            mGLView = MyGLSurfaceView(this)

            setContentView(mGLView)

            mRenderer = mGLView?.getRender()

            mGLView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        if (mHandler == null) {
            mHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    super.handleMessage(msg)
                    when (msg.what) {
                        1 -> {
                            val texture = msg.obj as SurfaceTexture
                            mSurfaceTexture = texture

                            captureVideo()
                        }

                        else -> {
                            Log.w(TAG, "othres message: ${msg.what}")
                        }
                    }
                }
            }
        }

        if (allPermissionGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (allPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun captureVideo() {
        if (mCamera == null) return
        if (mSurfaceTexture == null) return

        Log.d(TAG, "set default buffer size: ${mPreviewSize.width}   ${mPreviewSize.height}")
        mSurfaceTexture?.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
        val surface = Surface(mSurfaceTexture)

        val requestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(surface)

        val output = listOf(surface)
        mCamera.createCaptureSession(output, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                mCamtureSession = session
                session.setRepeatingRequest(requestBuilder!!.build(), null, null)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                //TODO("Not yet implemented")
            }
        }, null)
    }

    fun chooseBestSize(sizes: Array<Size>, width: Int, height: Int): Size {
        var bestSize: Size = sizes[0]

        var minDiffWidth = Int.MAX_VALUE
        var minDiffHeight = Int.MAX_VALUE

        for (size in sizes) {
            val diffWidth = abs(size.width - width)
            val diffHeight = abs(size.height - height)

            val isAspectRatioValid =
                abs(size.width.toFloat() / size.height - 4.0f / 3.0f) < 0.01f ||
                        abs(size.width.toFloat() / size.height - 16.0f / 9.0f) < 0.01f
            if (isAspectRatioValid && (diffWidth < minDiffWidth || (diffWidth == minDiffWidth && diffHeight < minDiffHeight))) {
                bestSize = size
                minDiffHeight = diffHeight
                minDiffWidth = diffWidth
            }
        }
        return bestSize
    }

    private fun startCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList[0]
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
        mPreviewSize = chooseBestSize(map.getOutputSizes(SurfaceTexture::class.java), 1280, 720)
        Log.d(TAG, "best size: ${mPreviewSize.width},  ${mPreviewSize.height}")

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    mCamera = camera
                    mRenderer?.setHandler(mHandler)
                }

                override fun onDisconnected(camera: CameraDevice) {
//                    TODO("Not yet implemented")
                }

                override fun onError(camera: CameraDevice, error: Int) {
//                    TODO("Not yet implemented")
                }
            }, null)
        } catch (se: SecurityException) {
            Log.e(TAG, se.toString())
        }
    }

    private fun allPermissionGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                baseContext,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }


    companion object {
        private const val TAG = "CameraXDemo"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSION = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}