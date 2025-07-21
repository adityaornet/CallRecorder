package com.brainpulse.callrecorder

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_share_receiver)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (audioUri != null) {
                // Do something with the audio file (e.g., play it or upload it)

                saveAudioToCallRecordings(audioUri)
                Toast.makeText(this, "Received audio: $audioUri", Toast.LENGTH_SHORT).show()
                Log.e("abdfvf", "onCreate: "+audioUri )

                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(mainIntent)
            }
        }
        finish()
    }

    private fun saveAudioToCallRecordings(sourceUri: Uri) {
        val resolver = contentResolver
        val displayName = getUriDetails(sourceUri)
        val timeStamp = extractTimeStamp(displayName)
//        Log.d("record timestamp","record timestamp"+timeStamp.toString())
//        val number = getPhoneNumber(timeStamp)
//        Log.d("call timestamp","call timestamp"+number?.second.toString())

        val date = Date(timeStamp ?: System.currentTimeMillis())
        val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = format.format(date)

        val sharedPref = getSharedPreferences("CallPrefs", Context.MODE_PRIVATE)
        val savedNumber = sharedPref.getString("lastDialedNumber", null)


        val fileName = "${savedNumber}_${formattedDate.replace(":", "_").replace(" ", "_")}.mp3"
        Log.d("SaveCheck", "Checking existence for file: $fileName")


        // Check if file already exists
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        val cursor = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )


        cursor?.use {
            if (it.moveToFirst()) {
                Toast.makeText(this, "File already exists!", Toast.LENGTH_SHORT).show()
                return  // Exit early
            }
        }

        // If not found, proceed to save
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/CallRecordings/")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }

        val audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newUri = resolver.insert(audioCollection, values)

        if (newUri != null) {
            resolver.openOutputStream(newUri).use { outputStream ->
                resolver.openInputStream(sourceUri).use { inputStream ->
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                        Toast.makeText(this, "Saved to Music/CallRecordings/", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to open streams", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Failed to insert into MediaStore", Toast.LENGTH_SHORT).show()
        }
    }

    fun extractTimeStamp1(displayName: String?): Long? {
        if (displayName == null) return null

        return when {
            displayName.startsWith("record-") -> {
                // Case: record-1752667212488.wav
                displayName.substringAfter("record-").substringBefore(".").toLongOrNull()
            }
            displayName.contains('_') -> {
                // Case: Amar Yadav ORNET(7977287040)_20250709140625.mp3
                val dateString = displayName.split('_').lastOrNull()?.substringBefore(".")
                dateString?.let {
                    try {
                        val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                        val date = format.parse(it)
                        date?.time
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            else -> null
        }
    }


    fun extractTimeStamp(displayName: String?): Long? {
        val brand = Build.BRAND.lowercase()
        val osVersion = Build.VERSION.RELEASE.toIntOrNull()

        if (osVersion != null) {
            return when {
                (brand.contains("oppo") || brand.contains("motorola")) && (osVersion > 10)-> {
                    // Example: record-1752667212488.wav
                    displayName
                        ?.substringAfter("record-")
                        ?.substringBefore(".")
                        ?.toLongOrNull()
                }

                brand.contains("redmi") && (osVersion > 10) -> {
                    // Example: Amar Yadav ORNET(7977287040)_20250709140625.mp3
                    val dateString = displayName
                        ?.split('_')
                        ?.lastOrNull()
                        ?.substringBefore(".")

                    dateString?.let {
                        try {
                            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                            val date = format.parse(it)
                            date?.time
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }

                brand.contains("samsung") && (osVersion > 10) -> {
                    //                samsung galaxy s24 fan edition
                    //                Display Name: Call recording Aditya Nikam_250715_145458.m4a
                    //                Display Name: Call recording 7021169037_250717_175621.m4a
                    val parts = displayName?.split('_')
                    val datePart = parts?.getOrNull(1) // "250717"
                    val timePart = parts?.getOrNull(2)?.substringBefore(".") // "175621"
                    val fullDateTime = datePart + timePart // "250717175621"

                    fullDateTime?.let {
                        try {
                            val format = SimpleDateFormat("yyMMddHHmmss", Locale.US)
                            val date = format.parse(it)
                            date?.time
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }
                else -> null
            }
        }
        return null
    }



    private fun getUriDetails(uri: Uri): String? {
        val cursor = contentResolver.query(
            uri,
            null, // all columns
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                val displayName = if (displayNameIndex != -1) it.getString(displayNameIndex) else "Unknown"
                val size = if (sizeIndex != -1) it.getLong(sizeIndex) else -1L
                val mimeType = contentResolver.getType(uri)

                Log.d("UriDetails", "Display Name: $displayName")
                Log.d("UriDetails", "Size: $size bytes")
                Log.d("UriDetails", "MIME Type: $mimeType")
                return displayName
            } else {
                Log.d("UriDetails", "No data found for URI: $uri")

            }
        }
        return ""
    }

//    private fun getPhoneNumber(timeStamp: Long?): Pair<String, Long>? {
//        if (timeStamp == null) return null
//
//        val recordingTimestamp = timeStamp
//        val timeWindowMs = 30000L  // 3 seconds margin
//
//        val cursor = contentResolver.query(
//            CallLog.Calls.CONTENT_URI,
//            null,
//            null,
//            null,
//            "${CallLog.Calls.DATE} Desc"
//        )
//
//        cursor?.use {
//            while (it.moveToNext()) {
//                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).filter { it.isDigit() }.takeLast(10)
//                val callDate = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
//                val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
//                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
//                Log.d("number",number)
//
//                val startTime = callDate
//                val endTime = callDate + duration * 1000
//
//                if (recordingTimestamp in (startTime - timeWindowMs)..(startTime + timeWindowMs)) {
//                    val date = Date(startTime)
//                    val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
//                    val formattedDate = format.format(date)
//
//                    Log.e("CallMatch", "$number : $formattedDate")
//
//                    return Pair(number, callDate)
//                }
//            }
//        }
//
//        return null
//    }



}