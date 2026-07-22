package takagi.ru.monica.autofill_ng

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.AutofillPickerActivityV2

/**
 * 主动填充通知（阶段1）：AccessibilityService 检测到某 App 有匹配密码条目时，
 * 主动发一条通知；点击通知唤起 Monica 手动选择器（与快捷磁贴入口等价）。
 */
object ActiveFillNotificationHelper {

    private const val CHANNEL_ID = "active_fill_channel"
    private const val CHANNEL_NAME = "主动填充提示"
    private const val NOTIFICATION_ID = 9528

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到可填充的登录框时提示"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun canPost(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    fun showActiveFillNotification(context: Context, packageName: String, appName: String): Boolean {
        createChannel(context)
        if (!canPost(context)) {
            android.util.Log.w("ActiveFill", "Notifications unavailable, skip active fill prompt")
            return false
        }

        val intent = Intent(context, AutofillPickerActivityV2::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AutofillPickerActivityV2.EXTRA_MANUAL_MODE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle("Monica 可填充")
            .setContentText("点此为 $appName 填充账号密码")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(15_000)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            android.util.Log.w("ActiveFill", "Notification permission not granted", e)
            false
        }
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
