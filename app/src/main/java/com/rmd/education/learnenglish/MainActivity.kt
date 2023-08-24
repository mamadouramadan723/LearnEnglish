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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.rmd.education.learnenglish.databinding.ActivityMainBinding
import okhttp3.Callback
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.File
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
            //playRecord()
            prepareSendingToMS()
            sendAudioFile()
        }
    }

    private fun sendAudioFile() {
        val client = OkHttpClient()

        // Define the file path for recorded_audio.wav
        val audioFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "recorded_audio.wav"
        )

        // Define the request URL
        val url =
            "https://eastus.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed"

        // Define the request headers
        val headers = headersOf(
            "Pronunciation-Assessment",
            "ewogICJSZWZlcmVuY2VUZXh0IjogIkhvdyBkbyBJIHJ1biB0aGlzIHByb2dyYW0iLAogICJHcmFkaW5nU3lzdGVtIjogIkh1bmRyZWRNYXJrIiwKICAiRGltZW5zaW9uIjogIkNvbXByZWhlbnNpdmUiCn0=",
            "Granularity",
            "Word",
            "Ocp-Apim-Subscription-Key", "0e415183127541a48763789785c3dbe4" // Include your authorization key here
        )


        // Create a multipart request builder
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                RequestBody.create("audio/wav".toMediaTypeOrNull(), audioFile)
            )
            .build()

        // Create an OkHttp request
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .post(requestBody)
            .build()

        // Execute the request
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle the failure
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Request failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i(TAG, "Request failed: ${e.message}")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Handle the successful response here
                    runOnUiThread {
                        Toast.makeText(applicationContext, responseBody, Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "$responseBody")
                    }
                } else {
                    // Handle the error response here
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Error: ${response.code} - ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.i(TAG, "Error: ${response.code} - ${response.message}")

                    }
                }
            }
        })
    }

    private fun prepareSendingToMS() {
        val pronAssessmentParamsJson =
            "{\"ReferenceText\":\"Good morning.\",\"GradingSystem\":\"HundredMark\",\"Granularity\":\"FullText\",\"Dimension\":\"Comprehensive\"}"
        val pronAssessmentParamsBytes = pronAssessmentParamsJson.toByteArray(Charsets.UTF_8)
        val pronAssessmentHeader = android.util.Base64.encodeToString(
            pronAssessmentParamsBytes,
            android.util.Base64.DEFAULT
        )
        Log.d(TAG, "Base64 : $pronAssessmentHeader")
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

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
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
        checkRecordPermission()
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

    private fun playRecord() {
        // Initialize the MediaPlayer
        mediaPlayer = MediaPlayer()
        val audioFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "recorded_audio.wav"
        )
        try {
            // Set the audio file you want to play

            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
        } catch (e: IOException) {
            Log.e(TAG, "Error setting data source: ${e.message}")
        }
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            Toast.makeText(this, "Playing audio", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "file recorded : ${audioFile.absolutePath}")
        }
        mediaPlayer.setOnCompletionListener {
            // This code runs when audio playback is completed
            Toast.makeText(this, "Audio playback completed", Toast.LENGTH_SHORT).show()
        }

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
