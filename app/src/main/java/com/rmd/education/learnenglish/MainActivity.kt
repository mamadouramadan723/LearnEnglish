package com.rmd.education.learnenglish

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rmd.education.learnenglish.databinding.ActivityMainBinding
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioRecord: AudioRecord
    private var recordingThread: Thread? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkRecordPermission()

        binding.recordBtn.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        binding.stopBtn.setOnClickListener {
            stopRecording()
        }
        binding.playBtn.setOnClickListener {
            //playRecord()
        }
    }

    
    fun checkRecordPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(RECORD_AUDIO, READ_MEDIA_AUDIO),
                REQUEST_PERMISSION_CODE
            )
        } else {
            initAudioRecorder()
        }
    }

    private fun initAudioRecorder() {
        audioRecord = AudioRecord(
            AUDIO_SOURCE,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
    }

    private fun startRecording() {
        //First check whether the above object actually initialized
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "error initializing AudioRecord");
            return
        }
        //Now start the audio recording
        audioRecord.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeAudioDataToFile()
        }
        recordingThread!!.start()
    }

    private fun writeAudioDataToFile() {
        // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size
        val data = ByteArray(bufferSize / 2)

        var outputStream: FileOutputStream? = null
        try {

            var outputFile =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/recorded_audio.wav"
            outputStream = FileOutputStream(outputFile)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "exception while closing output stream $e")
            return
        }
        while (isRecording) {
            val bytesRead = audioRecord.read(data, 0, data.size)

            try {
                outputStream.write(data, 0, bytesRead)
                // clean up file writing operations
            } catch (e: IOException) {
                Log.e(TAG, "exception while closing output stream $e")
                e.printStackTrace()
            }
        }
        try {
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "exception while closing output stream $e")
            e.printStackTrace()
        }
    }


    private fun stopRecording() {
        isRecording = false
        audioRecord.stop()
        recordingThread?.join()
        audioRecord.release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // this method is called when user will
        // grant the permission for audio recording.
        when (requestCode) {
            REQUEST_PERMISSION_CODE -> if (grantResults.isNotEmpty()) {
                val permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val permissionToStore = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (permissionToRecord && permissionToStore) {
                    Toast.makeText(applicationContext, "Permission Granted : ${ Environment.getExternalStorageDirectory().absolutePath }", Toast.LENGTH_LONG)
                        .show()
                    initAudioRecorder()
                } else {
                    Toast.makeText(
                        applicationContext, "Denied : ToRecord = $permissionToRecord\n" +
                                "ToStore = $permissionToStore", Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
    }


    companion object {
        const val REQUEST_PERMISSION_CODE = 101
        const val TAG = "Debug TAG : "

        const val SAMPLE_RATE = 44100
        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        //for raw audio can use
        const val RAW_AUDIO_SOURCE = MediaRecorder.AudioSource.UNPROCESSED
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    }
}
