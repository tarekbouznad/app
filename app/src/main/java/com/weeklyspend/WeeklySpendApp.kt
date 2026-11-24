package com.weeklyspend

import android.app.Application
import com.weeklyspend.data.Repository
import com.weeklyspend.notify.Notifications
import com.weeklyspend.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeeklySpendApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            Repository.get(this@WeeklySpendApp).ensureSeeded()
            Scheduler.scheduleAll(this@WeeklySpendApp)
        }
    }
}
