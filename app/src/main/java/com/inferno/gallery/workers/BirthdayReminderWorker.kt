package com.inferno.gallery.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inferno.gallery.data.db.DatabaseProvider
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class BirthdayReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_WORK = "PeopleBirthdayReminder"
        private const val CHANNEL_ID = "people_birthdays"

        fun schedule(context: Context) {
            val now = ZonedDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMinutes = ChronoUnit.MINUTES.between(now, next).coerceAtLeast(1)

            val request = PeriodicWorkRequestBuilder<BirthdayReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val db = DatabaseProvider.getDatabase(applicationContext)
        val today = LocalDate.now()
        val matches = db.faceDao().getBirthdayPeople().filter { person ->
            val epoch = person.birthdayEpochDay ?: return@filter false
            val dob = LocalDate.ofEpochDay(epoch)
            dob.monthValue == today.monthValue && dob.dayOfMonth == today.dayOfMonth
        }

        if (matches.isEmpty()) return Result.success()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "People birthdays",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Birthday reminders for people saved in Pitchcraft"
            }
        )

        matches.forEach { person ->
            val name = person.name?.takeIf { it.isNotBlank() } ?: "Someone in your gallery"
            val relation = person.relation?.takeIf { it.isNotBlank() }
            val text = if (relation != null) {
                "Today is $name's birthday • $relation"
            } else {
                "Today is $name's birthday"
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle("Birthday reminder 🎂")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify((10_000 + person.clusterId).toInt(), notification)
        }

        return Result.success()
    }
}
