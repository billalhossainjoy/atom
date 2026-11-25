package com.billal.datom.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val dateTaken: Long,
    val bucketName: String,
    val isVideo: Boolean,
    val durationMs: Long,
)

object MediaImageStore {
    private const val MAX_ITEMS = 500

    private val _images = MutableStateFlow<List<MediaImage>>(emptyList())
    val images: StateFlow<List<MediaImage>> = _images.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var observing = false

    /** Register a MediaStore observer so new photos/videos appear automatically. */
    fun startObserving(context: Context) {
        if (observing) return
        observing = true
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                scope.launch { load(appContext) }
            }
        }
        resolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer
        )
    }

    fun load(context: Context) {
        val result = mutableListOf<MediaImage>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                while (cursor.moveToNext() && result.size < MAX_ITEMS) {
                    val id = cursor.getLong(idCol)
                    result += MediaImage(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id
                        ),
                        dateTaken = cursor.getLong(dateCol),
                        bucketName = cursor.getString(bucketCol) ?: "",
                        isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                        durationMs = cursor.getLong(durationCol),
                    )
                }
            }
        } catch (e: SecurityException) {
            // Media permission not granted yet
        }
        _images.value = result
    }
}
