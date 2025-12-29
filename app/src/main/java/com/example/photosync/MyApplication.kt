package com.example.photosync

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var mediaRepository: com.example.photosync.data.repository.MediaRepository

    private val TAG = "MyApplication"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastTriggered = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()

        // Register a ContentObserver to watch MediaStore for new images/videos.
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                // Debounce to avoid frequent scans during bulk updates (e.g., many files added at once)
                val now = System.currentTimeMillis()
                val last = lastTriggered.get()
                if (now - last < 5_000) {
                    Log.d(TAG, "MediaStore change ignored (debounce).")
                    return
                }
                lastTriggered.set(now)

                Log.d(TAG, "MediaStore change detected: $uri. Triggering incremental scan.")
                appScope.launch {
                    try {
                        mediaRepository.scanLocalMedia()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while scanning media after change", e)
                    }
                }
            }
        }

        // Observe images and videos
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.d(TAG, "Registered MediaStore ContentObserver for images and videos.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register MediaStore observer", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
