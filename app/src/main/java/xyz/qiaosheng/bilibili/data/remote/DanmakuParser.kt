package xyz.qiaosheng.bilibili.data.remote

import com.google.gson.JsonParser
import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ResponseBody
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.error.UserFacingException
import xyz.qiaosheng.bilibili.data.remote.proto.DmSegMobileReply
import xyz.qiaosheng.bilibili.model.danmaku.DANMAKU_SEGMENT_DURATION_MS
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

class DanmakuDisabledException : UserFacingException("该视频已关闭弹幕")

class DanmakuParser @Inject constructor() {
    /** Caller supplies an IO dispatcher; reads are capped even without Content-Length. */
    suspend fun parse(body: ResponseBody, segmentIndex: Int): List<DanmakuItem> = body.use {
        require(segmentIndex > 0)
        val declaredSize = body.contentLength()
        if (declaredSize > MAX_PAYLOAD_BYTES) throw InvalidResponseException("弹幕分段超过大小限制")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        val stream = body.byteStream()
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_PAYLOAD_BYTES) throw InvalidResponseException("弹幕分段超过大小限制")
            output.write(buffer, 0, count)
        }
        currentCoroutineContext().ensureActive()
        if (declaredSize >= 0 && declaredSize != output.size().toLong()) {
            throw InvalidResponseException("弹幕分段传输不完整")
        }
        decode(output.toByteArray(), segmentIndex, body.contentType()?.subtype)
    }

    internal suspend fun decode(bytes: ByteArray, segmentIndex: Int, subtype: String? = null): List<DanmakuItem> {
        require(segmentIndex > 0)
        currentCoroutineContext().ensureActive()
        if (bytes.size > MAX_PAYLOAD_BYTES) throw InvalidResponseException("弹幕分段超过大小限制")
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 64)).toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        // 0x0A is both a newline and the protobuf elems tag. A following length can be
        // '{' or '<', so only a real JSON document or known text MIME type is rejected.
        val looksLikeJson = (prefix.startsWith("{") || prefix.startsWith("[")) && try {
            JsonParser.parseString(bytes.toString(Charsets.UTF_8)).let { it.isJsonObject || it.isJsonArray }
        } catch (_: RuntimeException) { false }
        if (subtype?.contains("json", ignoreCase = true) == true || looksLikeJson) {
            throwJsonError(bytes)
        }
        if (subtype?.contains("html", ignoreCase = true) == true ||
            prefix.startsWith("<!doctype", ignoreCase = true) || prefix.startsWith("<html", ignoreCase = true)) {
            throw InvalidResponseException("弹幕接口未返回 Protobuf 数据")
        }
        val reply = try {
            val input = CodedInputStream.newInstance(bytes).apply {
                setSizeLimit(MAX_PAYLOAD_BYTES)
                setRecursionLimit(16)
            }
            DmSegMobileReply.parseFrom(input).also { input.checkLastTagWas(0) }
        } catch (_: InvalidProtocolBufferException) {
            throw InvalidResponseException("弹幕 Protobuf 数据损坏或截断")
        }
        when (reply.state) {
            0 -> Unit
            1 -> throw DanmakuDisabledException()
            else -> throw InvalidResponseException("弹幕接口返回未知状态")
        }
        if (reply.elemsCount > MAX_ITEMS_PER_SEGMENT) throw InvalidResponseException("弹幕分段条目超过限制")
        val startMs = (segmentIndex - 1L) * DANMAKU_SEGMENT_DURATION_MS
        // The service may include the exact end boundary in both neighbouring segments.
        val endMs = startMs + DANMAKU_SEGMENT_DURATION_MS
        val seen = HashSet<String>()
        return buildList {
            for (element in reply.elemsList) {
                currentCoroutineContext().ensureActive()
                if (element.mode !in 1..6 || element.pool !in 0..1) continue
                val timeMs = element.progress.toLong()
                if (timeMs !in startMs..endMs) continue
                val text = element.content.take(MAX_TEXT_LENGTH)
                    .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
                    .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
                if (text.isBlank()) continue
                val id = element.idStr.takeIf { it.isNotBlank() && it.length <= 64 }
                    ?: element.id.takeIf { it > 0 }?.toString() ?: continue
                if (!seen.add(id)) continue
                add(DanmakuItem(id, timeMs, text, element.mode,
                    element.fontsize.takeIf { it > 0 }?.coerceIn(12, 64) ?: 25,
                    element.color and 0xFFFFFF, element.weight.coerceIn(0, 10)))
            }
        }.sortedWith(compareBy<DanmakuItem> { it.timeMs }.thenBy { it.id })
    }

    private fun throwJsonError(bytes: ByteArray): Nothing {
        val json = try {
            JsonParser.parseString(bytes.toString(Charsets.UTF_8)).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) { null }
        val code = try { json?.get("code")?.asInt } catch (_: RuntimeException) { null }
        // Do not log or surface untrusted error text from an API response.
        if (code != null && code != 0) throw ApiException(code, "弹幕接口请求失败")
        throw InvalidResponseException("弹幕接口返回 JSON 而非 Protobuf 数据")
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
        const val MAX_ITEMS_PER_SEGMENT = 6_000
        const val MAX_TEXT_LENGTH = 300
    }
}
