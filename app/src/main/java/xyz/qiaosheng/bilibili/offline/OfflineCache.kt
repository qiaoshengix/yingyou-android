package xyz.qiaosheng.bilibili.offline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 下载专用持久目录，不使用可被系统随时回收的 cacheDir，也不让在线播放淘汰离线内容。 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineCache internal constructor(context: Context, directory: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, File(context.filesDir, "offline-media"))
    internal val databaseProvider = StandaloneDatabaseProvider(context)
    internal val cache = SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)

    internal fun length(key: String): Long = ContentMetadata.getContentLength(cache.getContentMetadata(key))

    internal fun cachedBytes(key: String): Long = cache.getCachedSpans(key).sumOf { span ->
        if (span.file?.let { it.isFile && it.length() >= span.length } == true) span.length else 0L
    }

    internal fun isComplete(key: String): Boolean {
        val length = length(key)
        if (length <= 0 || !cache.isCached(key, 0, length)) return false
        return hasCompleteByteCoverage(length, cache.getCachedSpans(key).map { span ->
            CachedByteSpan(span.position, span.length, span.file?.let { it.isFile && it.length() >= span.length } == true)
        })
    }

    internal fun downloadFactory(upstream: DataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)

    /** 不存在远程回退，任何缺块、已删除文件、未知长度都会失败。 */
    internal fun localOnlyFactory(): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(null)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
}

internal data class CachedByteSpan(val position: Long, val length: Long, val fileAvailable: Boolean)

internal fun hasCompleteByteCoverage(length: Long, spans: List<CachedByteSpan>): Boolean {
    if (length <= 0) return false
    var covered = 0L
    for (span in spans.sortedBy { it.position }) {
        if (!span.fileAvailable || span.length <= 0 || span.position < 0) return false
        if (span.position > covered) return false
        val end = if (Long.MAX_VALUE - span.position < span.length) Long.MAX_VALUE else span.position + span.length
        covered = maxOf(covered, end)
        if (covered >= length) return true
    }
    return false
}
