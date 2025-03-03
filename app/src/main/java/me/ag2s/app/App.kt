package me.ag2s.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath

lateinit var app: App

class App: Application() , SingletonImageLoader.Factory {



    override fun onCreate() {
        super.onCreate()
        app=this


    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        app=this



    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)

            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.externalCacheDir!!.resolve("coil").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
//            .components {
//
//                add(
//                    KtorNetworkFetcherFactory(httpClient = Ktor.client)
//                )
//            }
            .build()
    }


}