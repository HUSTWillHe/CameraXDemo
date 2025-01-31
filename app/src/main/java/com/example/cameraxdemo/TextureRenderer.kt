package com.example.glsurfaceviewdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TextureRenderer {
//    private lateinit var mContext: Context

    private val vertexShaderCode =
        "uniform mat4 uTMatrix;" +
                "attribute vec4 aPosition;" +
                "attribute vec2 aTexCoord;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  gl_Position = uTMatrix * aPosition;" +
                "  vTexCoord = aTexCoord;" +
                "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
                "#extension GL_OES_EGL_image_external : require \n" +
                "uniform samplerExternalOES uSampler;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  gl_FragColor = texture2D(uSampler, vTexCoord);" +
//                "  gl_FragColor = vec4(0.1, 1.0, 0.4, 1.0);" +
                "}"

    private val coordData = floatArrayOf(
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,  // 0 bottom left
        1.0f, -1.0f, 0.0f, 1.0f, 0.0f,   // 1 bottom right
        -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,  // 2 top left
        1.0f,  1.0f, 0.0f, 1.0f, 1.0f    // 3 top right
    )
    private var translateMatrix = FloatArray(16)
    private val color = floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f)

    private var mProgram = 0
    private var mPositionHandle = 0
    private var mTexCoordHandle = 0
    private var mColorHandle = 0
    private var mTMatrixHandle = 0
    private var mSamplerHandle = 0

    private var mTextureId = 0

    private var vboId = IntArray(1)
    private var vaoId = IntArray(1)

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        val vertexShaderCompiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, vertexShaderCompiled, 0)
        if (vertexShaderCompiled[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e("OpenGL", "type $type  Shader Compilation Failed: $log")
        }
        return shader
    }

//    private fun loadTexture(resourceId: Int): Int {
//        val textureHandler = IntArray(1)
//        GLES30.glGenTextures(1, textureHandler, 0)
//        val texture = textureHandler[0]
//
//        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
//
//        GLES30.glTexParameteri(
//            GLES30.GL_TEXTURE_2D,
//            GLES30.GL_TEXTURE_WRAP_S,
//            GLES30.GL_CLAMP_TO_EDGE
//        )
//        GLES30.glTexParameteri(
//            GLES30.GL_TEXTURE_2D,
//            GLES30.GL_TEXTURE_WRAP_T,
//            GLES30.GL_CLAMP_TO_EDGE
//        )
//
//        val bitmap = BitmapFactory.decodeResource(mContext.resources, resourceId)
//        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bitmap, 0)
//        bitmap.recycle()
//
//        return texture
//    }

    private fun createTexture(): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )

        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        return textureIds[0]
    }

    private lateinit var coordBuffer: FloatBuffer

    fun getTextureId(): Int {
        return mTextureId
    }

    constructor() {
//        mContext = context
        mTextureId = createTexture()

        val bb = ByteBuffer.allocateDirect(coordData.size * 4)
        bb.order(ByteOrder.nativeOrder())
        coordBuffer = bb.asFloatBuffer()
        coordBuffer.put(coordData)
        coordBuffer.position(0)

        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(mProgram, vertexShader)
        GLES30.glAttachShader(mProgram, fragmentShader)

        GLES30.glLinkProgram(mProgram)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(mProgram, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetProgramInfoLog(mProgram)
            Log.e("OpenGL", "Program linking failed: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        GLES30.glGenVertexArrays(1, vaoId, 0)
        GLES30.glBindVertexArray(vaoId[0])

        Matrix.setIdentityM(translateMatrix, 0)

        mPositionHandle = GLES30.glGetAttribLocation(mProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(mPositionHandle)

        mTexCoordHandle = GLES30.glGetAttribLocation(mProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(mTexCoordHandle)

        mTMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uTMatrix")
        mSamplerHandle = GLES30.glGetUniformLocation(mProgram, "uSampler")

        GLES30.glGenBuffers(1, vboId, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bb.capacity(), bb, GLES30.GL_STATIC_DRAW)

        GLES30.glVertexAttribPointer(
            mPositionHandle,
            3,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            0
        )
        GLES30.glVertexAttribPointer(
            mTexCoordHandle,
            2,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            3 * Float.SIZE_BYTES
        )

//        mTextureId = loadTexture(R.drawable.icon)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glUseProgram(mProgram)
        GLES30.glBindVertexArray(vaoId[0])

        GLES30.glUniformMatrix4fv(mTMatrixHandle, 1, false, translateMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
        GLES30.glUniform1i(mSamplerHandle, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindVertexArray(0)
    }

    fun onDestroy() {
        GLES30.glDeleteBuffers(1, vboId, 0)
        GLES30.glDeleteVertexArrays(1, vaoId, 0)

        GLES30.glDeleteProgram(mProgram)
    }
}