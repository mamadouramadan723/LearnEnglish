package com.rmd.education.learnenglish

import android.Manifest
import android.content.pm.PackageManager
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
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MyMainActivity : AppCompatActivity() {

    private var mediaRecorder: MediaRecorder? = null

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
        val result = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (result == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            initMediaRecorder()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_PERMISSION_CODE
            )
        }
    }

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private fun initMediaRecorder() {
        mediaRecorder = MediaRecorder(this)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder?.setOutputFile(outputFilePath)
    }

    private fun startRecording() {
        checkRecordPermission()
        mediaRecorder?.prepare()
        mediaRecorder?.start()
        isRecording = true
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        if (!isRecording) return

        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
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
            .newBuilder()
            .addInterceptor(loggingInterceptor)
            .build()

        val headers = Headers.Builder()
            .add("Pronunciation-Assessment", pronAssessmentHeader)
            .add("Granularity", "Word")
            .add("Ocp-Apim-Subscription-Key", getString(R.string.subscriptionKey))
            .build()

        val audioFile = File(outputFilePath)
        val requestBody = audioFile.asRequestBody("audio/x-aac".toMediaTypeOrNull())

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
        stopRecording()
    }

    companion object {
        private const val REQUEST_PERMISSION_CODE = 101
        private const val TAG = "+++My Debug TAG : "

        private const val URL =
            "https://eastus.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed"

        private var isRecording = false
        private var pronAssessmentHeader: String = ""

    }

    private lateinit var binding: ActivityMainBinding
    private val outputFilePath: String by lazy {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        "${dir.absolutePath}/recorded_audio.aac"
    }
}