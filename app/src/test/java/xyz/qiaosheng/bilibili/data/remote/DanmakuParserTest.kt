package xyz.qiaosheng.bilibili.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.data.remote.proto.DanmakuElem
import xyz.qiaosheng.bilibili.data.remote.proto.DmSegMobileReply
import xyz.qiaosheng.bilibili.model.danmaku.danmakuSegmentIndex

class DanmakuParserTest {
    private val parser = DanmakuParser()

    @Test fun decodesIndependentWireFixtureAndSkipsUnknownFields() = runTest {
        // elems { id:1 progress:150 mode:1 fontsize:25 color:16777215 content:"A" weight:7 }
        // The trailing field 99 deliberately is absent from this app's schema.
        val fixture = "0a1308011096011801201928ffffff073a01414807980601"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val item = parser.decode(fixture, 1).single()
        assertEquals("1", item.id)
        assertEquals(150L, item.timeMs)
        assertEquals("A", item.text)
        assertEquals(25, item.fontSize)
        assertEquals(0xFFFFFF, item.color)
        assertEquals(7, item.weight)
    }

    @Test fun preservesLongStringIdAndAbsoluteTimeAtSegmentBoundaries() = runTest {
        val rows = listOf(
            element(1, 359_999), element(2, 360_000).toBuilder().setIdStr("9007199254740993123").build(),
            element(3, 719_999), element(4, 720_000), element(5, 720_001),
        )
        val result = parser.decode(reply(rows), 2)
        assertEquals(listOf(360_000L, 719_999L, 720_000L), result.map { it.timeMs })
        assertEquals("9007199254740993123", result.first().id)
        assertEquals(1, danmakuSegmentIndex(-1))
        assertEquals(1, danmakuSegmentIndex(359_999))
        assertEquals(2, danmakuSegmentIndex(360_000))
        assertEquals(Int.MAX_VALUE, danmakuSegmentIndex(Long.MAX_VALUE))
    }

    @Test fun sortsDeduplicatesAndFiltersUnsupportedModesWithoutRemovingDistinctRepeats() = runTest {
        val rows = listOf(element(2, 2_000), element(1, 1_000), element(1, 1_000),
            element(3).toBuilder().setMode(9).setPool(2).build(),
            element(4).toBuilder().setContent(" \n ").build(),
            element(5).toBuilder().setProgress(-1).build(),
            element(6).toBuilder().setMode(6).build())
        assertEquals(listOf("6", "1", "2"), parser.decode(reply(rows), 1).map { it.id })
    }

    @Test fun clampsDrawingValuesAndNeverSplitsASurrogatePair() = runTest {
        val row = element(1).toBuilder().setFontsize(1).setColor(-1).setWeight(99)
            .setContent("a".repeat(299) + "\uD83D\uDE00").build()
        val item = parser.decode(reply(listOf(row)), 1).single()
        assertEquals(12, item.fontSize)
        assertEquals(0xFFFFFF, item.color)
        assertEquals(10, item.weight)
        assertEquals(299, item.text.length)
    }

    @Test fun protobufLengthByteThatLooksLikeJsonIsStillParsedAsBinary() = runTest {
        val row = element(1).toBuilder().setContent("a".repeat(115)).build()
        assertEquals(123, row.serializedSize)
        assertEquals(1, parser.decode(reply(listOf(row)), 1).size)
    }

    @Test fun distinguishesSuccessfulEmptyDisabledAndUnknownState() = runTest {
        assertTrue(parser.decode(byteArrayOf(), 1).isEmpty())
        assertTrue(runCatching { parser.decode(DmSegMobileReply.newBuilder().setState(1).build().toByteArray(), 1) }
            .exceptionOrNull() is DanmakuDisabledException)
        assertTrue(runCatching { parser.decode(DmSegMobileReply.newBuilder().setState(3).build().toByteArray(), 1) }
            .exceptionOrNull() is InvalidResponseException)
    }

    @Test fun jsonErrorsHtmlAndTruncatedProtobufCannotBecomeEmptySuccess() = runTest {
        val jsonError = parserFailure(""" {"code":-352,"message":"private response"} """.toByteArray())
        assertTrue(jsonError is ApiException)
        assertEquals(-352, (jsonError as ApiException).code)
        assertFalse(jsonError.message.orEmpty().contains("private response"))
        assertTrue(parserFailure("{}".toByteArray()) is InvalidResponseException)
        assertTrue(parserFailure("<html>blocked</html>".toByteArray()) is InvalidResponseException)
        assertTrue(parserFailure(byteArrayOf(0x0a, 0x7f, 0x08, 0x01)) is InvalidResponseException)
    }

    @Test fun enforcesPayloadAndEntryLimitsAndChecksDeclaredLength() = runTest {
        assertTrue(parserFailure(ByteArray(DanmakuParser.MAX_PAYLOAD_BYTES + 1)) is InvalidResponseException)
        val tooMany = reply(List(DanmakuParser.MAX_ITEMS_PER_SEGMENT + 1) { element(it.toLong() + 1) })
        assertTrue(parserFailure(tooMany) is InvalidResponseException)
        val truncated = object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = 5L
            override fun source() = Buffer().write(byteArrayOf(0x10, 0))
        }
        assertTrue(runCatching { parser.parse(truncated, 1) }.exceptionOrNull() is InvalidResponseException)
        val oversized = ByteArray(DanmakuParser.MAX_PAYLOAD_BYTES + 1).toResponseBody()
        assertTrue(runCatching { parser.parse(oversized, 1) }.exceptionOrNull() is InvalidResponseException)
    }

    @Test fun refusesCancelledParsing() = runTest {
        val outcome = async {
            currentCoroutineContext().job.cancel()
            parser.decode(reply(listOf(element(1))), 1)
        }
        assertTrue(runCatching { outcome.await() }.exceptionOrNull() is CancellationException)
    }

    private suspend fun parserFailure(bytes: ByteArray): Throwable? =
        runCatching { parser.decode(bytes, 1) }.exceptionOrNull()

    private fun element(id: Long, time: Int = 0) = DanmakuElem.newBuilder()
        .setId(id).setProgress(time).setMode(1).setFontsize(25).setContent("same").build()

    private fun reply(rows: List<DanmakuElem>) = DmSegMobileReply.newBuilder().addAllElems(rows).build().toByteArray()
}
