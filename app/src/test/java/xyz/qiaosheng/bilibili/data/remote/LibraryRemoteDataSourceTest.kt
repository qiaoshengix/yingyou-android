package xyz.qiaosheng.bilibili.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.data.local.entity.LibraryOutboxEntity
import xyz.qiaosheng.bilibili.data.remote.api.LibraryApiService
import xyz.qiaosheng.bilibili.data.remote.dto.*
import xyz.qiaosheng.bilibili.data.sync.LibraryCredentials
import xyz.qiaosheng.bilibili.support.fakeApi

class LibraryRemoteDataSourceTest {
    private val gson = Gson()
    private val credentials = LibraryCredentials(7, "SESSDATA=fake-seven", "csrf-seven")

    @Test fun realHistoryShapeUsesListNestedHistoryAndCursorBusiness() = runTest {
        val payload = """{"code":0,"data":{"cursor":{"max":90,"view_at":1234,"business":"archive"},"list":[
            {"title":"标题","cover":"//example.test/c.jpg","author_name":"作者","duration":120,"progress":-1,"view_at":5678,
             "history":{"oid":90,"bvid":"BV-history","cid":91,"business":"archive"}}]}}"""
        val api = fakeApi<LibraryApiService> { method, args ->
            assertEquals("history", method)
            assertEquals(50L, args[1]); assertEquals(1000L, args[2]); assertEquals("archive", args[3]); assertEquals("archive", args[4])
            gson.fromJson(payload, HistoryResponse::class.java)
        }
        val page = RetrofitLibraryRemoteDataSource(api, gson).history(credentials, "50|1000|archive")
        assertEquals("90|1234|archive", page.nextCursor); assertTrue(page.hasMore)
        val item = page.videos.single()
        assertEquals(90L, item.aid); assertEquals(91L, item.cid); assertEquals(-1L, item.progressSeconds)
        assertEquals("作者", item.ownerName); assertEquals("https://example.test/c.jpg", item.coverUrl)
    }

    @Test fun repeatedHistoryCursorTerminatesAndMalformedOldArchivesIsRejected() = runTest {
        var response = HistoryResponse(0, HistoryData(HistoryCursor(5, 10, "archive"), listOf(HistoryArchive(history = HistoryTarget(5, "BV-x", 8, "archive")))))
        val remote = RetrofitLibraryRemoteDataSource(fakeApi<LibraryApiService> { _, _ -> response }, gson)
        assertFalse(remote.history(credentials, "5|10|archive").hasMore)
        response = gson.fromJson("""{"code":0,"data":{"cursor":{"max":0},"archives":[]}}""", HistoryResponse::class.java)
        try { remote.history(credentials, null); fail("old incorrect payload accepted") } catch (_: InvalidResponseException) { }
    }

    @Test fun favoritesParseMediasAndNeverTreatMalformedInfoAsAnEmptyFolder() = runTest {
        var value = gson.fromJson("""{"info":{"id":123},"medias":[{"id":90,"type":2,"bv_id":"BV-fav","title":"收藏","cover":"//example.test/f.jpg","duration":8,"upper":{"name":"UP"},"fav_time":99}],"has_more":true}""", FavoriteContentsData::class.java)
        val remote = RetrofitLibraryRemoteDataSource(fakeApi<LibraryApiService> { _, _ -> LibraryResponse(0, data = value) }, gson)
        val page = remote.favorites(credentials, 123, null)
        assertEquals(123L, page.videos.single().folderId); assertEquals("2", page.nextCursor)
        value = FavoriteContentsData()
        try { remote.favorites(credentials, 123, null); fail("malformed response must not erase cache") } catch (_: InvalidResponseException) { }
    }

    @Test fun recentLikesSupportsRecordedObjectAndOlderArrayShapesWithoutFakePagination() = runTest {
        var payload = JsonParser.parseString("""{"list":[{"bvid":"BV-like","aid":1,"cid":2,"title":"赞","duration":9}]}""")
        val remote = RetrofitLibraryRemoteDataSource(fakeApi<LibraryApiService> { _, _ -> LibraryResponse(0, data = payload) }, gson)
        assertEquals("BV-like", remote.recentLikes(credentials).videos.single().bvid)
        payload = payload.asJsonObject.get("list")
        val page = remote.recentLikes(credentials)
        assertEquals(1, page.videos.size); assertFalse(page.hasMore); assertNull(page.nextCursor)
    }

    @Test fun unlikeUsesValueTwoAndSnapshotCsrfWhileAlreadyLikedRetryDoesNotWriteAgain() = runTest {
        var writes = 0
        val api = fakeApi<LibraryApiService> { method, args ->
            when (method) {
                "hasLike" -> LibraryResponse(0, data = 1)
                "like" -> { writes++; assertEquals(2, args[2]); assertEquals("csrf-seven", args[3]); LibraryResponse<JsonElement>(0) }
                else -> error("Unexpected $method")
            }
        }
        val remote = RetrofitLibraryRemoteDataSource(api, gson)
        val operation = LibraryOutboxEntity(7, "LIKES", 0, "BV-like", "op", true, 1, 2, 0, false, 0)
        remote.submit(credentials, operation); assertEquals(0, writes)
        remote.submit(credentials, operation.copy(desired = false)); assertEquals(1, writes)
    }

    @Test fun favoriteWritesOnlyTheChosenOwnedFolderAndChecksMembershipWithRid() = runTest {
        val api = fakeApi<LibraryApiService> { method, args -> when (method) {
            "folders" -> { assertEquals(7L, args[1]); assertEquals(90L, args[2]); LibraryResponse(0, data = FavoriteFoldersData(2,
                listOf(FavoriteFolderDto(123, "甲", favState = 0), FavoriteFolderDto(124, "乙", favState = 1)))) }
            "favorite" -> { assertEquals(90L, args[1]); assertEquals("123", args[2]); assertEquals("", args[3]); assertEquals("csrf-seven", args[4]); LibraryResponse<JsonElement>(0) }
            else -> error("Unexpected $method")
        } }
        RetrofitLibraryRemoteDataSource(api, gson).submit(credentials, LibraryOutboxEntity(7, "FAVORITES", 123,
            "BV-fav", "op", true, 90, 91, 0, false, 0))
    }

    @Test fun historyReportUsesRealSecondsAndDeletionUsesArchiveAidKey() = runTest {
        var reportCalls = 0; var deleteCalls = 0
        val api = fakeApi<LibraryApiService> { method, args -> when (method) {
            "report" -> { reportCalls++; assertEquals(90L, args[1]); assertEquals(91L, args[2]); assertEquals(120L, args[3]);
                assertEquals("csrf-seven", args[4]); LibraryResponse<JsonElement>(0) }
            "deleteHistory" -> { deleteCalls++; assertEquals("archive_90", args[1]); LibraryResponse<JsonElement>(0) }
            else -> error("Unexpected $method")
        } }
        val remote = RetrofitLibraryRemoteDataSource(api, gson)
        val operation = LibraryOutboxEntity(7, "HISTORY", 0, "BV-history", "op", true, 90, 91, 120, true, 0)
        remote.submit(credentials, operation)
        remote.submit(credentials, operation.copy(desired = false))
        assertEquals(1, reportCalls); assertEquals(1, deleteCalls)
    }

    @Test fun missingFavoriteMembershipCannotBeMistakenForNotFavorited() = runTest {
        val api = fakeApi<LibraryApiService> { method, _ ->
            assertEquals("folders", method)
            LibraryResponse(0, data = FavoriteFoldersData(1, listOf(FavoriteFolderDto(123, "收藏夹"))))
        }
        try {
            RetrofitLibraryRemoteDataSource(api, gson).submit(credentials, LibraryOutboxEntity(7, "FAVORITES", 123,
                "BV-fav", "op", true, 90, 91, 0, false, 0))
            fail("unknown membership should not trigger a write")
        } catch (_: InvalidResponseException) { }
    }
}
