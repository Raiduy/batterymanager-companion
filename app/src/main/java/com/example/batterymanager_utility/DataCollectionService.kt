package com.example.batterymanager_utility

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class DataCollectionService : Service() {

    private var collectorWorker: Job? = null
    private lateinit var collector: DataCollector

    private lateinit var dataFields: ArrayList<String>
    private var toCSV: Boolean = false
    private var data: ArrayList<String> = ArrayList()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: begin")
        val sampleRate: Int = intent!!.getIntExtra("sampleRate", 1000)
        Log.i(TAG, "onStartCommand: sampleRate => $sampleRate")
        val rawFields: String? = intent.getStringExtra("dataFields")
        Log.i(TAG, "onStartCommand: rawFields => Timestamp,$rawFields")
        dataFields = rawFields?.split(",") as ArrayList<String>
        dataFields.add(0,"Timestamp")

        toCSV = intent.getBooleanExtra("toCSV", true)

        val notification: Notification = Notification.Builder(this, App.CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        Log.i(TAG, "onStartCommand: notification done")

        startForeground(1, notification)
        Log.i(TAG, "started foreground")

        collector = DataCollector(this, dataFields)

        this.collectorWorker = CoroutineScope(Dispatchers.IO).launch {
            collectData(sampleRate)
        }
        return START_NOT_STICKY
    }

    private suspend fun collectData(sampleRate: Int) {
        Log.i(TAG, "collectData: begin")
        while (true) {
            val stats = collector.getData()
            Log.i(TAG, "stats => $stats")
            data.add(stats)
            if (sampleRate > 0)
                delay(sampleRate.toLong())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        collectorWorker?.cancel()
        // create the file
        if (toCSV) {
            val file = createFile()
            // write to the file
            writeToFile(file)
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun createFile() : File {
        // Create a new file and write data to it.
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BatteryManager.csv")
        if (file.exists()) {
            Log.i("BatteryMgr:createFile", "file already exists")
            return file
        }
        Log.i("BatteryMgr:createFile", "creating file called $file")

        val cols = collector.getDataPoints().joinToString(",")
        FileOutputStream(file).use {
            it.write("$cols\n".toByteArray())
        }
        Log.i("BatteryMgr:createFile", "created file called $file")

        return file
    }

    private fun writeToFile(file: File) {
        val stats = data.joinToString("\n")
        FileOutputStream(file, true).use {
            it.write("$stats\n".toByteArray())
        }
    }

    companion object {
        private const val TAG = "BatteryMgr:DataCollectionService"
        private const val NOTIFICATION_TITLE = "Battery Manager"
        private const val NOTIFICATION_TEXT = "Collecting data..."
    }
}
