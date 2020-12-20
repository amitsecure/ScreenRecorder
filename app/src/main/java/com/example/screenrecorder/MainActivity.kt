package com.example.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : Activity() {
    private var mScreenDensity = 0
    private var mProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaProjectionCallback: MediaProjectionCallback? = null
    private lateinit var mToggleButton: ToggleButton
    private lateinit var btnRecordFolder: Button
    private var mMediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val metrics = DisplayMetrics()
        getWindowManager().getDefaultDisplay().getMetrics(metrics)
        mScreenDensity = metrics.densityDpi

        val PERMISSION_ALL = 1
        val PERMISSIONS = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        )

        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }

        initRecorder()
        prepareRecorder()
        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        mToggleButton = findViewById(R.id.toggle) as ToggleButton
        btnRecordFolder = findViewById(R.id.btnRecordFolder) as Button

        mToggleButton.setOnClickListener { v -> onToggleScreenShare(v) }
        mMediaProjectionCallback = MediaProjectionCallback()

        btnRecordFolder.visibility=Button.INVISIBLE

        btnRecordFolder.setOnClickListener(View.OnClickListener {
//            val intent = Intent(Intent.ACTION_VIEW)
//            val filePath=Environment.getExternalStorageDirectory().path + File.separator + "Recordings/"
//            val uri: Uri = Uri.parse(filePath)
//            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
//            intent.setDataAndType(uri, "*/*")
//
//            startActivityForResult(intent,10)

//            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//                // Provide read access to files and sub-directories in the user-selected
//                // directory.
//                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
//
//                // Optionally, specify a URI for the directory that should be opened in
//                // the system file picker when it loads.
//                putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/storage/emulated/0/Recordings/*")
//            }
//
//            startActivityForResult(intent, 10)
        })
    }

    fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mMediaProjection != null) {
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: $requestCode")
            return
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(
                    this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT
            ).show()
            mToggleButton!!.isChecked = false
            return
        }
        mMediaProjection = mProjectionManager!!.getMediaProjection(resultCode, data!!)
        mMediaProjection!!.registerCallback(mMediaProjectionCallback, null)
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    fun onToggleScreenShare(view: View) {
        if ((view as ToggleButton).isChecked) {
            shareScreen()
        } else {
            mMediaRecorder!!.stop()
            mMediaRecorder!!.reset()
            Log.v(TAG, "Recording Stopped")
            stopScreenSharing()
        }
    }

    private fun shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(
                    mProjectionManager!!.createScreenCaptureIntent(),
                    PERMISSION_CODE
            )
            return
        }
        if(mMediaRecorder == null)
        {
            initRecorder()
            prepareRecorder()
        }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return
        }
        mVirtualDisplay!!.release()
        mMediaRecorder=null
    //    mMediaRecorder!!.release()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay(
                "MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder?.surface, null /*Callbacks*/, null /*Handler*/
        )
    }

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (mToggleButton!!.isChecked) {
                mToggleButton!!.isChecked = false
                mMediaRecorder!!.stop()
                mMediaRecorder!!.reset()
                Log.v(TAG, "Recording Stopped")
            }
            mMediaProjection = null
            stopScreenSharing()
            Log.i(TAG, "MediaProjection Stopped")
        }
    }

    private fun prepareRecorder() {
        try {
            mMediaRecorder!!.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            finish()
        } catch (e: IOException) {
            e.printStackTrace()
            finish()
        }
    }

    val filePath: String?
        get() {
            val directory =
                Environment.getExternalStorageDirectory().toString() + File.separator + "Recordings"
            if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
                Toast.makeText(this, "Failed to get External Storage", Toast.LENGTH_SHORT).show()
                return null
            }
            val folder = File(directory)
            var success = true
            if (!folder.exists()) {
                success = folder.mkdir()
            }
            val filePath: String
            filePath = if (success) {
                val videoName = "capture_" + curSysDate + ".mp4"
                directory + File.separator + videoName
            } else {
                Toast.makeText(this, "Failed to create Recordings directory", Toast.LENGTH_SHORT)
                    .show()
                return null
            }
            return filePath
        }

    val curSysDate: String
        get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())

    private fun initRecorder() {
        if (mMediaRecorder == null) {
            mMediaRecorder = MediaRecorder()
            mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
            mMediaRecorder!!.setVideoEncodingBitRate(512 * 4000)
            mMediaRecorder!!.setVideoFrameRate(30)
            mMediaRecorder!!.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mMediaRecorder!!.setOutputFile(filePath)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_CODE = 1
        private const val DISPLAY_WIDTH = 480
        private const val DISPLAY_HEIGHT = 640
    }
}




/*
 val path = "/storage/emulated/0/Recordings"
                Log.d("Files", "Path: $path")
                val directory = File(path)
                val files = directory.listFiles()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Arrays.sort(files, Comparator.comparingLong { obj: File -> obj.lastModified() })
                }
 */