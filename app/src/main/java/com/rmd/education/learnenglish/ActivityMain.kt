package com.rmd.education.learnenglish

import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ActivityMain : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkRecordPermission()
        setupUI()
    }

    private fun setupUI() {
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
            prepareHeader()
            sendAudioFile()
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

    @OptIn(DelicateCoroutinesApi::class)
    private fun startRecording() {
        //if (isRecording) return

        checkRecordPermission()
        audioRecord.startRecording()
        isRecording = true
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        GlobalScope.launch(Dispatchers.IO) {
            val outputStream = FileOutputStream(outputFile)
            writeWavHeader(outputStream)
            writeAudioDataToFile(outputStream)
            outputStream.close()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        audioRecord.stop()
        audioRecord.release()
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
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

    private fun sendAudioFile() {
        // Create a logging interceptor
        val loggingInterceptor =
            HttpLoggingInterceptor { message -> // Log the message to your preferred logging framework
                Log.d("OkHttp", message)
            }

        // Set the logging level (BODY includes request and response bodies)
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient()
            .newBuilder().addInterceptor(loggingInterceptor)
            .build()

        val headers = Headers.Builder()
            .add("Pronunciation-Assessment", pronAssessmentHeader)
            .add("Granularity", "Word")
            .add("Ocp-Apim-Subscription-Key", getString(R.string.subscriptionKey))
            .build()

        val requestBody =
            outputFile.asRequestBody("audio/wav; codecs=audio/pcm; samplerate=16000".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(URL)
            .headers(headers)
            .post(requestBody)
            .build()

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
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    // Handle the successful response here
                    runOnUiThread {
                        Toast.makeText(applicationContext, responseBody, Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "body : $responseBody")
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

    private fun prepareHeader() {
        val pronAssessmentParamsJson =
            "{\"ReferenceText\":\"Good morning.\",\"GradingSystem\":\"HundredMark\",\"Granularity\":\"FullText\",\"Dimension\":\"Comprehensive\"}"
        val pronAssessmentParamsBytes = pronAssessmentParamsJson.toByteArray(Charsets.UTF_8)
        pronAssessmentHeader = android.util.Base64.encodeToString(
            pronAssessmentParamsBytes,
            android.util.Base64.NO_WRAP
        )
        Log.d(TAG, "Base64 : $pronAssessmentHeader")
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
        if (isRecording) {
            stopRecording()
        }
    }

    // Declaration
    companion object {
        private const val REQUEST_PERMISSION_CODE = 101
        private const val TAG = "+++My Debug TAG : "

        private const val SAMPLE_RATE = 44100
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RECORDED_AUDIO_FILE_NAME = "recorded_audio.wav"
        private const val URL =
            "https://eastus.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed"
    }

    private var isRecording = false
    private var pronAssessmentHeader: String = ""
    private lateinit var audioRecord: AudioRecord
    private lateinit var binding: ActivityMainBinding
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )
    private val outputFile: File by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            RECORDED_AUDIO_FILE_NAME
        )
    }
}