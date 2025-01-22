package me.ag2s.app

import android.app.Application
import android.content.Context

lateinit var app: App

class App: Application() {



    override fun onCreate() {
        super.onCreate()
        app=this
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        app=this

    }





}