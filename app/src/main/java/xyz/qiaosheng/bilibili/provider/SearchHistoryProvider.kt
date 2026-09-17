package xyz.qiaosheng.bilibili.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.net.toUri
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Callable
import xyz.qiaosheng.bilibili.data.local.dao.SearchHistoryDao
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase

/** 搜索历史的同步访问入口；应用内调用 ContentResolver 时应切到 IO 线程。 */
class SearchHistoryProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "xyz.qiaosheng.bilibili.provider"
        val CONTENT_URI: Uri = "content://$AUTHORITY/search_history".toUri()
        const val MIME_DIR = "vnd.android.cursor.dir/vnd.$AUTHORITY.search_history"
        const val MIME_ITEM = "vnd.android.cursor.item/vnd.$AUTHORITY.search_history"
        const val COLUMN_KEYWORD = "keyword"
        const val COLUMN_SEARCH_TIME = "searchTime"

        private const val TABLE = "search_history"
        private const val CODE_SEARCH_HISTORY = 1
        private const val CODE_SEARCH_HISTORY_ITEM = 2
        private val columns = setOf(COLUMN_KEYWORD, COLUMN_SEARCH_TIME)
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, TABLE, CODE_SEARCH_HISTORY)
            addURI(AUTHORITY, "$TABLE/*", CODE_SEARCH_HISTORY_ITEM)
        }
    }

    // ContentProvider 不支持直接 @AndroidEntryPoint，通过入口复用 Hilt 的数据库单例。
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun database(): BiliDatabase
    }

    private val database: BiliDatabase by lazy {
        EntryPointAccessors.fromApplication(
            checkNotNull(context).applicationContext,
            DatabaseEntryPoint::class.java
        ).database()
    }

    override fun onCreate(): Boolean {
        // 此时 Application.onCreate 尚未执行，不在主线程打开数据库。
        return context != null
    }

    override fun getType(uri: Uri): String = when (matchUri(uri)) {
        CODE_SEARCH_HISTORY -> MIME_DIR
        else -> MIME_ITEM
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val (where, args) = resolveSelection(uri, selection, selectionArgs)
        require(projection == null || (projection.isNotEmpty() && projection.all { it in columns })) {
            "Projection must contain only keyword and searchTime"
        }
        val requestedOrder = sortOrder?.takeIf { it.isNotBlank() }
        require(requestedOrder == null || requestedOrder.split(',').all {
            it.trim().matches(Regex("(keyword|searchTime)(\\s+(ASC|DESC))?", RegexOption.IGNORE_CASE))
        }) { "Invalid sort order: $sortOrder" }
        val order = requestedOrder ?: SearchHistoryDao.RECENT_ORDER

        val query = SupportSQLiteQueryBuilder.builder(TABLE)
            .columns(projection ?: columns.toTypedArray())
            .selection(where, args)
            .orderBy(order)
            .create()
        return database.query(query).apply {
            // 集合操作也可能影响单条记录，因此所有游标都监听集合 URI。
            setNotificationUri(checkNotNull(context).contentResolver, CONTENT_URI)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        require(matchUri(uri) == CODE_SEARCH_HISTORY) { "Insert requires the collection URI: $uri" }
        val contentValues = validateValues(values, inserting = true)
        database.runInTransaction(Callable {
            // 与 SearchHistoryDao 一致：再次搜索同一个关键字时替换时间。
            database.openHelper.writableDatabase.apply {
                insert(TABLE, SQLiteDatabase.CONFLICT_REPLACE, contentValues)
                execSQL(SearchHistoryDao.PRUNE_QUERY)
            }
        })
        notifyChanged()
        // appendPath 会编码斜杠、空格等字符，关键字不能作为数字 ID 拼接。
        return CONTENT_URI.buildUpon()
            .appendPath(contentValues.getAsString(COLUMN_KEYWORD))
            .build()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val (where, args) = resolveSelection(uri, selection, selectionArgs)
        val contentValues = validateValues(values, inserting = false)
        if (contentValues.size() == 0) return 0
        val count = database.runInTransaction(Callable {
            val sqlite = database.openHelper.writableDatabase
            sqlite.update(TABLE, SQLiteDatabase.CONFLICT_ABORT, contentValues, where, args).also {
                sqlite.execSQL(SearchHistoryDao.PRUNE_QUERY)
            }
        })
        if (count > 0) notifyChanged()
        return count
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val (where, args) = resolveSelection(uri, selection, selectionArgs)
        val count = database.runInTransaction(Callable {
            database.openHelper.writableDatabase.delete(TABLE, where, args)
        })
        if (count > 0) notifyChanged()
        return count
    }

    private fun matchUri(uri: Uri): Int {
        val code = uriMatcher.match(uri)
        require(uri.scheme == "content" && code != UriMatcher.NO_MATCH) { "Unknown URI: $uri" }
        return code
    }

    private fun resolveSelection(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Pair<String?, Array<out Any?>> {
        val code = matchUri(uri)
        val where = selection?.takeIf { it.isNotBlank() }
        require(where != null || selectionArgs.isNullOrEmpty()) {
            "selectionArgs requires a selection"
        }
        val args = selectionArgs?.toList().orEmpty()
        return if (code == CODE_SEARCH_HISTORY_ITEM) {
            val keyword = requireNotNull(uri.lastPathSegment)
            val itemWhere = "$COLUMN_KEYWORD = ?"
            // 括号保证调用者的 OR 条件不会扩大单条 URI 的操作范围。
            (if (where == null) itemWhere else "$itemWhere AND ($where)") to
                (listOf(keyword) + args).toTypedArray()
        } else {
            where to args.toTypedArray()
        }
    }

    private fun validateValues(values: ContentValues?, inserting: Boolean): ContentValues {
        requireNotNull(values) { "ContentValues must not be null" }
        require(values.keySet().all { it in columns }) { "Only keyword and searchTime can be written" }
        return ContentValues(values).apply {
            if (inserting || containsKey(COLUMN_KEYWORD)) {
                val keyword = get(COLUMN_KEYWORD)
                require(keyword is String && keyword.isNotBlank()) { "keyword must be a non-blank string" }
            }
            if (containsKey(COLUMN_SEARCH_TIME)) {
                val time = get(COLUMN_SEARCH_TIME)
                require(time is Long || time is Int || time is Short || time is Byte) {
                    "searchTime must be an integer timestamp"
                }
            } else if (inserting) {
                put(COLUMN_SEARCH_TIME, System.currentTimeMillis())
            }
        }
    }

    private fun notifyChanged() {
        // Room 事务负责通知 DAO Flow；这里负责通知 ContentResolver 的观察者。
        checkNotNull(context).contentResolver.notifyChange(CONTENT_URI, null)
    }
}
