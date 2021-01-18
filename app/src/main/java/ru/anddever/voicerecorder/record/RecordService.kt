package ru.anddever.voicerecorder.record

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import ru.anddever.voicerecorder.MainActivity
import ru.anddever.voicerecorder.R
import ru.anddever.voicerecorder.database.RecordDatabase
import ru.anddever.voicerecorder.database.RecordDatabaseDao
import ru.anddever.voicerecorder.database.RecordingItem
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class RecordService : Service() {

    private var mFileName: String? = null
    private var mFilePath: String? = null
    //private var mCountRecords: Int? = null

    private var mRecorder: MediaRecorder? = null

    private var mStartingTimeMillis: Long = 0
    private var mElapsedMillis: Long = 0

    private var mDatabase: RecordDatabaseDao? = null

    private val mJob = Job()
    private val mUiScope = CoroutineScope(Dispatchers.Main + mJob)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mDatabase = RecordDatabase.getInstance(application).recordDatabaseDao
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //mCountRecords = intent?.extras!!["COUNT"] as Int?

        startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        setFileNameAndPath()

        mRecorder = MediaRecorder()
        mRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mRecorder?.setOutputFile(mFilePath)
        mRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mRecorder?.setAudioChannels(1)
        mRecorder?.setAudioEncodingBitRate(192000)

        try {
            mRecorder?.prepare()
            mRecorder?.start()
            mStartingTimeMillis = System.currentTimeMillis()
            startForeground(1, createNotification())
        } catch (e: IOException) {
            Log.e("RecordService", "prepare failed")
        }
    }

    private fun createNotification(): Notification? {
        val mBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(application, getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_baseline_mic_white_36)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_recording))
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivities(
                        applicationContext, 0, arrayOf(
                            Intent(
                                applicationContext,
                                MainActivity::class.java
                            )
                        ), 0
                    )
                )
        return mBuilder.build()
    }

    private fun setFileNameAndPath() {
        var count = 0
        var f: File
        val dateTime = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())
            .format(System.currentTimeMillis())

        do {
            mFileName = (getString(R.string.default_file_name)
                    + "_" + dateTime + count + ".mp4")
            mFilePath = application.getExternalFilesDir(null)?.absolutePath
            mFilePath += "/$mFileName"

            ++count

            f = File(mFilePath)
        } while (f.exists() && !f.isDirectory)
    }

    private fun stopRecording() {
        val recordingItem = RecordingItem()

        mRecorder?.stop()
        mElapsedMillis = System.currentTimeMillis() - mStartingTimeMillis
        mRecorder?.release()
        Toast.makeText(
            this,
            getString(R.string.toast_recording_finish),
            Toast.LENGTH_LONG
        ).show()

        recordingItem.name = mFileName.toString()
        recordingItem.filePath = mFilePath.toString()
        recordingItem.length = mElapsedMillis
        recordingItem.time = System.currentTimeMillis()

        mRecorder = null

        try {
            mUiScope.launch {
                withContext(Dispatchers.IO) {
                    mDatabase?.insert(recordingItem)
                }
            }
        } catch (e: Exception) {
            Log.e("RecordService", "exception", e)
        }
    }

    override fun onDestroy() {
        if (mRecorder != null) {
            stopRecording()
        }
        super.onDestroy()
    }
}