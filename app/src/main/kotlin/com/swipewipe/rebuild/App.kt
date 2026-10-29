package com.swipewipe.rebuild

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import com.swipewipe.rebuild.db.AppDatabase
import com.swipewipe.rebuild.workers.Schedule

class App : Application(), Configuration.Provider {

    lateinit var db: AppDatabase
        private set
    lateinit var media: MediaStoreRepo
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var graph: GraphManager
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "swipewipe_database.db")
            .fallbackToDestructiveMigration()
            .build()
        media = MediaStoreRepo(this)
        settings = SettingsStore(this)
        graph = GraphManager(this)
        createNotificationChannels()
        Schedule.scheduleRecurring(this)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val names = listOf(
            "Swipewipe" to R.string.channel_swipewipe,
            "streak" to R.string.channel_streak,
            "cooldown" to R.string.channel_cooldown,
            "similar_images" to R.string.channel_similar_images,
            "media_tools_progress" to R.string.channel_media_tools_progress
        )
        names.forEach { (id, res) ->
            val importance = if (id == "media_tools_progress")
                NotificationManager.IMPORTANCE_LOW
            else NotificationManager.IMPORTANCE_HIGH
            nm.createNotificationChannel(
                NotificationChannel(id, getString(res), importance)
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(RebuildWorkerFactory(db, media, settings, graph))
            .build()
}
