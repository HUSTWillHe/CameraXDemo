package com.example.glsurfaceviewdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Message
import android.util.Log
import com.example.cameraxdemo.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLSurfaceView : GLSurfaceView {
    private lateinit var myRenderer: MyGLRenderer

    constructor(context: Context) : super(context) {
        setEGLContextClientVersion(3)
        myRenderer = MyGLRenderer(this)
        setRenderer(myRenderer)
    }

    fun onDestroy() {
        myRenderer.onDestroy()
    }

    fun getRender(): MyGLRenderer {
        return myRenderer
    }
}

class MyGLRenderer(glView: MyGLSurfaceView) : GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {
    //    private lateinit var mTriangle: Triangle
    private lateinit var mTextureRenderer: TextureRenderer
//    private var mContext = context
    private var mGLView = glView

    //    private lateinit var bitmap: Bitmap
    private var mIWidth = 0
    private var mIHeight = 0
    private var mSurfaceWidth = 0
    private var mSurfaceHeight = 0
    private var mStartX = 0
    private var mStartY = 0
    private var mViewWidth = 0
    private var mViewHeight = 0

    private var mHandler: Handler? = null
    private var mSurfaceTexture: SurfaceTexture? = null

    init {
//        bitmap = loadImage()
//        mIWidth = bitmap.width
//        mIHeight = bitmap.height
    }

    fun setHandler(handler: Handler?) {
        if (handler != null) {
            mHandler = handler
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        mGLView.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
//        mTriangle = Triangle()
        mTextureRenderer = TextureRenderer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        mSurfaceWidth = width
        mSurfaceHeight = height

        mSurfaceTexture = SurfaceTexture(mTextureRenderer.getTextureId())
        mSurfaceTexture?.setOnFrameAvailableListener(this)

        val msg = Message.obtain()
        msg.obj = mSurfaceTexture
        msg.what = 1
        if (mHandler != null) {
            mHandler?.sendMessage(msg)
        }

//        calculateViewPort()
//        Log.d(
//            "MyGLSurfaceView",
//            "surface width: $width, height: $height. image width: $mIWidth, height: $mIHeight"
//        )
//        Log.d(
//            "MyGLSurfaceView",
//            "start x: $mStartX start y: $mStartY, view width: $mViewWidth, view height $mViewHeight"
//        )
//        GLES30.glViewport(mStartX, mStartY, mViewWidth, mViewHeight)
        GLES30.glViewport(0, 0, width, height)
    }

    private fun calculateViewPort() {
        val imageRatio = mIWidth / mIHeight.toFloat()
        val surfaceRatio = mSurfaceWidth / mSurfaceHeight.toFloat()
        if (imageRatio > surfaceRatio) {
            mViewWidth = mSurfaceWidth
            mViewHeight = (mSurfaceWidth / imageRatio).toInt()
        } else if (imageRatio < surfaceRatio) {
            mViewHeight = mSurfaceHeight
            mViewWidth = (mSurfaceHeight * imageRatio).toInt()
        } else {
            mViewWidth = mSurfaceWidth
            mViewHeight = mSurfaceHeight
        }

        mStartX = (mSurfaceWidth - mViewWidth) / 2
        mStartY = (mSurfaceHeight - mViewHeight) / 2
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
//        mTriangle.draw()
//        Matrix.setIdentityM(mMVP, 0)
        mSurfaceTexture?.updateTexImage()
        mTextureRenderer.draw()
    }

    fun onDestroy() {
//        mTriangle.onDestroy()
        mTextureRenderer.onDestroy()
    }

//    private fun loadImage(): Bitmap {
//        val option = BitmapFactory.Options()
//        option.inScaled = false
//        return BitmapFactory.decodeResource(mContext.resources, R.drawable.icon, option)
//    }
}