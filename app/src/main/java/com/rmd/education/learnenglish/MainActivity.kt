package com.rmd.education.learnenglish

import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.rmd.education.learnenglish.databinding.ActivityMainBinding
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : AppCompatActivity() {
    private var isRecording = false
    private var recordingThread: Thread? = null
    private lateinit var audioRecord: AudioRecord
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var binding: ActivityMainBinding
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )
    private val outputFile =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "recorded_audio.wav"
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
            playRecord()
        }
    }

    private fun playRecord() {
        // Initialize the MediaPlayer
        mediaPlayer = MediaPlayer()

        try {
            // Set the audio file you want to play
            val audioFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "recorded_audio.wav"
            )
            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
        } catch (e: IOException) {
            Log.e(TAG, "Error setting data source: ${e.message}")
        }
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            Toast.makeText(this, "Playing audio", Toast.LENGTH_SHORT).show()
        }
        mediaPlayer.setOnCompletionListener {
            // This code runs when audio playback is completed
            Toast.makeText(this, "Audio playback completed", Toast.LENGTH_SHORT).show()
        }

    }


    private fun checkRecordPermission() {
        val result = ActivityCompat.checkSelfPermission(this, READ_MEDIA_AUDIO)
        val result1 = ActivityCompat.checkSelfPermission(this, RECORD_AUDIO)
        if (result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED) {
            initAudioRecorder()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(RECORD_AUDIO, READ_MEDIA_AUDIO),
                REQUEST_PERMISSION_CODE
            )
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
        //Now start the audio recording
        initAudioRecorder()
        audioRecord.startRecording()
        isRecording = true
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        val outputStream: FileOutputStream?
        outputStream = FileOutputStream(outputFile)

        recordingThread = Thread {
            writeWavHeader(outputStream)
            writeAudioDataToFile(outputStream)
            outputStream.close()
        }
        recordingThread?.start()
    }

    private fun writeWavHeader(outputStream: FileOutputStream) {
        val totalAudioLen = outputStream.channel.size()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * SAMPLE_RATE * channels / 8

        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(channels.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray())
        header.putInt(totalAudioLen.toInt())

        outputStream.write(header.array())
    }

    private fun writeAudioDataToFile(outputStream: FileOutputStream) {
        val buffer = ByteArray(bufferSize)
        try {
            while (isRecording) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, e.message.toString())
            e.printStackTrace()
        }
    }


    private fun stopRecording() {
        isRecording = false
        audioRecord.stop()
        recordingThread?.join()
        audioRecord.release()
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        applicationContext,
                        "Permission Granted : ${Environment.getExternalStorageDirectory().absolutePath}",
                        Toast.LENGTH_LONG
                    )
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
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    companion object {
        const val REQUEST_PERMISSION_CODE = 101
        const val TAG = "+++My Debug TAG : "

        const val SAMPLE_RATE = 44100
        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        //for raw audio can use
        const val RAW_AUDIO_SOURCE = MediaRecorder.AudioSource.UNPROCESSED
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
