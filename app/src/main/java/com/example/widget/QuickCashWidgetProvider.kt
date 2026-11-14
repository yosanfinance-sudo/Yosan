package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R

class QuickCashWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickCashWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_TEXT_ENTRY = "com.example.action.QUICK_CASH_TEXT"
        const val ACTION_VOICE_ENTRY = "com.example.action.QUICK_CASH_VOICE"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_cash)

            // Text / typing entry intent
            val textIntent = Intent(context, QuickCashEntryActivity::class.java).apply {
                action = ACTION_TEXT_ENTRY
                putExtra(QuickCashEntryActivity.EXTRA_MODE, QuickCashEntryActivity.MODE_TEXT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val textPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                textIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Mic / voice entry intent
            val voiceIntent = Intent(context, QuickCashEntryActivity::class.java).apply {
                action = ACTION_VOICE_ENTRY
                putExtra(QuickCashEntryActivity.EXTRA_MODE, QuickCashEntryActivity.MODE_VOICE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val voicePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 2,
                voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Bind click handlers
            views.setOnClickPendingIntent(R.id.widget_container, textPendingIntent)
            views.setOnClickPendingIntent(R.id.btn_text_entry, textPendingIntent)
            views.setOnClickPendingIntent(R.id.btn_edit, textPendingIntent)
            views.setOnClickPendingIntent(R.id.btn_mic, voicePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
