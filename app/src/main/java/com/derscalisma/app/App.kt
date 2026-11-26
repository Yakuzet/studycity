package com.derscalisma.app

import android.app.Application
import com.derscalisma.app.data.Repo
import com.derscalisma.app.notify.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Repo.init(this)
    }
}
