package com.weeklyspend.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.weeklyspend.R
import com.weeklyspend.budget.Money
import com.weeklyspend.budget.WeekStatus
import com.weeklyspend.data.Repository
import com.weeklyspend.ingest.runAsync
import com.weeklyspend.ui.MainActivity

/** Home-screen widget: left this week and left today. Refreshed after every change and just after midnight. */
class BudgetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { updateAll(context, Repository.get(context).weekStatus()) }
    }

    companion object {
        fun updateAll(context: Context, st: WeekStatus) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java))
            if (ids.isEmpty()) return
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val color = when {
                st.weekSpent > st.weekLimit -> Color.parseColor("#E53935")
                st.weekPct >= 80 -> Color.parseColor("#FFB300")
                else -> Color.parseColor("#43A047")
            }
            val views = RemoteViews(context.packageName, R.layout.widget_budget).apply {
                setTextViewText(R.id.widget_week, Money.format(st.weekLeft))
                setTextColor(R.id.widget_week, color)
                setTextViewText(R.id.widget_today, "Today: ${Money.format(st.dayLeft)} left")
                setProgressBar(R.id.widget_progress, 100, st.weekPct.coerceIn(0, 100), false)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
