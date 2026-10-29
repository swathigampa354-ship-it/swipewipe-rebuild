package com.swipewipe.rebuild.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swipewipe.rebuild.ui.MainActivity
import com.swipewipe.rebuild.R
import com.swipewipe.rebuild.db.AppDatabase
import kotlinx.coroutines.flow.first

class RemindersWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stats = db.appDao().observeAllStats()
        val prefs = com.swipewipe.rebuild.SettingsStore(applicationContext).flow.first()
        val deletedToday = prefs.deletedToday

        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val streakChannel = applicationContext.getString(R.string.channel_streak)
        val cooldownChannel = applicationContext.getString(R.string.channel_cooldown)
        val similarChannel = applicationContext.getString(R.string.channel_similar_images)

        val streakBody = if (deletedToday > 0) "$deletedToday items cleared today — keep the streak"
        else "Your cleanup streak is waiting. 2 minutes to keep it going."

        val similarGroups = runCatching { db.appDao().duplicateGroupIds().size }.getOrDefault(0)
        if (similarGroups > 0) {
            val similarNotification = NotificationCompat.Builder(applicationContext, similarChannel)
                .setContentTitle("Groups waiting for you")
                .setContentText("$similarGroups similar-image groups have not been reviewed yet")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(9002, similarNotification) }
        }

        val notif = NotificationCompat.Builder(applicationContext, streakChannel)
            .setContentTitle("Photo cleanup streak")
            .setContentText(streakBody)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(9003, notif) }
        runCatching {
            nm.notify(
                9004,
                NotificationCompat.Builder(applicationContext, cooldownChannel)
                    .setContentTitle("Swipewipe cooldown done")
                    .setContentText("The deck is ready whenever you want to continue swiping.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            )
        }
        return Result.success()
    }
}
