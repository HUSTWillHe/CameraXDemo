package com.example.glsurfaceviewdemo

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TextureRender {
    private val KERNEL_SIZE = 9

    //创建shader，并为其指定源码
    private val vertexShaderCode =
        "attribute vec4 aPosition;" +
                "uniform mat4 uMVPMatrix;" +
                "uniform mat4 uSTMatrix;" +
                "attribute vec4 aTexCoord;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * aPosition;" +
                "  vTexCoord = (aTexCoord).xy;" +
                "}"

    private val fragmentShaderCodeWB =
        "precision mediump float;" +
                "#extension GL_OES_EGL_image_external : require \n" +
                "uniform samplerExternalOES uTexture;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  vec4 tc = texture2D(uTexture, vTexCoord);" +
                "  float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;" +
                "  gl_FragColor = vec4(color, color, color, 1.0);" +
                "}"

    private val fragmentShaderCodeFilter =
        "#define KERNEL_SIZE ${KERNEL_SIZE} \n" +
                "precision mediump float;" +
                "#extension GL_OES_EGL_image_external : require \n" +
                "uniform float uKernel[KERNEL_SIZE];" +
                "uniform vec2 uTexOffset[KERNEL_SIZE];" +
                "uniform float uColorOffset;" +
                "uniform samplerExternalOES uTexture;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  int i = 0;" +
                "  vec4 sum = vec4(0.0);" +
                "  for (i = 0; i < KERNEL_SIZE; i++) {" +
                "    vec4 tc = texture2D(uTexture, vTexCoord + uTexOffset[i]);" +
                "    sum += tc * uKernel[i];" +
                "  }" +
                "  sum += uColorOffset;" +
                "  gl_FragColor = sum;" +
                "}"

    private val fragmentShaderCodeSketch =
        "#define KERNEL_SIZE ${KERNEL_SIZE} \n" +
                "precision mediump float;" +
                "#extension GL_OES_EGL_image_external : require \n" +
                "uniform float uKernel[KERNEL_SIZE];" +
                "uniform vec2 uTexOffset[KERNEL_SIZE];" +
                "uniform float uColorOffset;" +
                "uniform samplerExternalOES uTexture;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  int i = 0;" +
                "  vec4 sum = vec4(0.0);" +
                "  for (i = 0; i < KERNEL_SIZE; i++) {" +
                "    vec4 tc = texture2D(uTexture, vTexCoord + uTexOffset[i]);" +
                "    sum += tc * uKernel[i];" +
                "  }" +
                "  sum += uColorOffset;" +
                "  float gray = dot(sum.rgb, vec3(0.3, 0.59, 0.11));" +
                "  float alpha = 0.0;" +
                "  if (gl_FragCoord.x > 500.0) {" +
                "    alpha = 1.0 - smoothstep(0.2, 0.8, gray);" +
                "  } else {" +
                "    alpha = 1.0 - gray;" +
                "  }" +
                "  gl_FragColor = mix(vec4(1.0), vec4(0.0, 0.0, 0.0, 1.0), alpha);" +
                "}"

    private val fragmentShaderCodeLut = """
        precision mediump float;
        #extension GL_OES_EGL_image_external : require 
        uniform samplerExternalOES uTexture;
        uniform sampler2D uLutSampler;
        varying vec2 vTexCoord;
        void main() {
            vec4 texColor = texture2D(uTexture, vTexCoord);
            float blueColor = texColor.b * 63.0;
            vec2 quad1, quad2;
            quad1.y = floor(floor(blueColor) / 8.0);
            quad1.x = floor(blueColor) - (quad1.y * 8.0);
            quad2.y = floor(ceil(blueColor) / 8.0);
            quad2.x = ceil(blueColor) - (quad2.y * 8.0);
            
            vec2 texPos1, texPos2;
            texPos1.x = (quad1.x * 0.125) + 0.5 / 512.0 + (0.125 - 1.0 / 512.0) * texColor.r;
            texPos1.y = (quad1.y * 0.125) + 0.5 / 512.0 + (0.125 - 1.0 / 512.0) * texColor.g;
            texPos2.x = (quad2.x * 0.125) + 0.5 / 512.0 + (0.125 - 1.0 / 512.0) * texColor.r;
            texPos2.y = (quad2.y * 0.125) + 0.5 / 512.0 + (0.125 - 1.0 / 512.0) * texColor.g;
            
            vec4 newColor1 = texture2D(uLutSampler, texPos1);
            vec4 newColor2 = texture2D(uLutSampler, texPos2);
            
            gl_FragColor = mix(newColor1, newColor2, fract(blueColor));
        }
    """

    /*private val vertexData = floatArrayOf(
        -1.0f, 1.0f, 0.0f,  0.0f, 0.0f, //左上角
        -1.0f, -1.0f, 0.0f, 0.0f, 1.0f, //左下角
        1.0f, 1.0f, 0.0f,   1.0f, 0.0f, //右上角
        1.0f, -1.0f, 0.0f,  1.0f, 1.0f  //右下角
    )*/

    private val vertexData = floatArrayOf(
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,  // 0 bottom left
        1.0f, -1.0f, 0.0f, 1.0f, 0.0f,   // 1 bottom right
        -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,  // 2 top left
        1.0f, 1.0f, 0.0f, 1.0f, 1.0f    // 3 top right
    )

    private var vboId = IntArray(1)
    private var vboTextureId = IntArray(1)

    private lateinit var mContext: Context

    private var mProgram = -1
    private var mPositionHandle = -1
    private var mTextureHandle = -1
    private var mSamplerHandle = -1
    private var mLutSamplerHandle = -1
    private var mMVPMatrixHandle = -1
    private var mSTMatrixHandle = -1
    private var mKernelHandle = -1
    private var mTexOffsetHandle = -1
    private var mColorOffsetHandle = -1

    private var mTextureId = -1
    private var mLutTextureId = -1

    private val mKernel = FloatArray(KERNEL_SIZE)
    private var mColorOffset = 0.0f
    private lateinit var mTexOffset: FloatArray

    private var vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    /*
    private var textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureData.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    */


    constructor(context: Context, lutBitmap: Bitmap) {
        mContext = context
        mLutTextureId = uploadTexture(lutBitmap)

        vertexBuffer.put(vertexData).position(0)
        //textureBuffer.put(textureData).position(0)

        mTextureId = createTexture()

        //Matrix.setIdentityM(translateMatrix, 0)
        //Matrix.scaleM(translateMatrix, 0, 0.5f, 0.5f, 1.0f)

        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCodeLut)

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

        GLES30.glGenBuffers(1, vboId, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexBuffer.capacity() * 4,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        /*
        GLES30.glGenBuffers(1, vboTextureId, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboTextureId[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, textureBuffer.capacity()*4, textureBuffer, GLES30.GL_STATIC_DRAW)
        */

        mPositionHandle = GLES30.glGetAttribLocation(mProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(mPositionHandle)

        mTextureHandle = GLES30.glGetAttribLocation(mProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(mTextureHandle)

        mSamplerHandle = GLES30.glGetUniformLocation(mProgram, "uTexture")
        mLutSamplerHandle = GLES30.glGetUniformLocation(mProgram, "uLutSampler")
        mMVPMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix")
        mSTMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uSTMatrix")
        mKernelHandle = GLES30.glGetUniformLocation(mProgram, "uKernel")
        if (mKernelHandle < 0) {
            mTexOffsetHandle = -1;
            mColorOffsetHandle = -1;
            mKernelHandle = -1;
        } else {
            mTexOffsetHandle = GLES30.glGetUniformLocation(mProgram, "uTexOffset")
            mColorOffsetHandle = GLES30.glGetUniformLocation(mProgram, "uColorOffset")
//            setKernel(
//                floatArrayOf(
//                    -1f,
//                    -1f,
//                    -1f,
//                    -1f,
//                    9f,
//                    -1f,
//                    -1f,
//                    -1f,
//                    -1f,
//                ), 0f
//            )
//            setKernel(
//                floatArrayOf(
//                    -1f,
//                    0f,
//                    1f,
//                    -2f,
//                    0f,
//                    2f,
//                    -1f,
//                    0f,
//                    1f,
//                ), 0f
//            )
            setKernel(
                floatArrayOf(
                    -1f,
                    -1f,
                    -1f,
                    -1f,
                    9f,
                    -1f,
                    -1f,
                    -1f,
                    -1f,
                ), 0f
            )
//            setKernel(
//                floatArrayOf(
//                    2f,
//                    0f,
//                    0f,
//                    0f,
//                    -1f,
//                    0f,
//                    0f,
//                    0f,
//                    -1f
//                ), 0.5f
//            )
//            setKernel(
//                floatArrayOf(
//                    -2f,
//                    -1f,
//                    0f,
//                    -1f,
//                    0f,
//                    1f,
//                    0f,
//                    1f,
//                    2f
//                ), 0.5f
//            )
            setTexSize(256, 256)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun setKernel(kernel: FloatArray, colorOffset: Float) {
        System.arraycopy(kernel, 0, mKernel, 0, KERNEL_SIZE)
        mColorOffset = colorOffset
    }

    fun setTexSize(width: Int, height: Int) {
        val tw = 1.0f / width
        val th = 1.0f / height

        mTexOffset = floatArrayOf(
            -tw, -th, 0f, -th, tw, -th,
            -tw, 0f, 0f, 0f, tw, 0f,
            -tw, th, 0f, th, tw, th
        )
    }

    fun getTextureId(): Int {
        return mTextureId;
    }

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

    private fun createTexture(): Int {

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])


        GLES30.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR.toFloat()
        );
        GLES30.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR.toFloat()
        );
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        );
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        );
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        return textureIds[0]
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bitmap, 0)
        bitmap.recycle()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return textureIds[0]
    }

    fun draw(mvpMatrix: FloatArray, stMaxtrix: FloatArray) {
        //使用program
        GLES30.glUseProgram(mProgram)

        //将数据传递给shader
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId[0])
        GLES30.glVertexAttribPointer(
            mPositionHandle,
            3,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            0
        )

        //GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboTextureId[0])
        GLES30.glVertexAttribPointer(
            mTextureHandle,
            2,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            3 * Float.SIZE_BYTES
        )

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId)
        GLES30.glUniform1i(mSamplerHandle, 0)

        if (mLutSamplerHandle >= 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLutTextureId)
            GLES30.glUniform1i(mLutSamplerHandle, 1)
        }

        GLES30.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(mSTMatrixHandle, 1, false, stMaxtrix, 0)

        if (mKernelHandle >= 0) {
            GLES30.glUniform1fv(mKernelHandle, KERNEL_SIZE, mKernel, 0)
            GLES30.glUniform2fv(mTexOffsetHandle, KERNEL_SIZE, mTexOffset, 0)
            GLES30.glUniform1f(mColorOffsetHandle, mColorOffset)
        }

        //drawArray, 绘制三角形
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }
}