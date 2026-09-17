package xyz.qiaosheng.bilibili.data.remote.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import xyz.qiaosheng.bilibili.data.remote.DanmakuParser

/** Also bounds Retrofit's automatic buffering of non-2xx error bodies. */
internal class DanmakuBodyLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        if (body.contentLength() > DanmakuParser.MAX_PAYLOAD_BYTES) {
            body.close()
            throw IOException("弹幕分段超过大小限制")
        }
        val source = object : ForwardingSource(body.source()) {
            private var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, minOf(byteCount, DanmakuParser.MAX_PAYLOAD_BYTES - total + 1))
                if (count > 0) total += count
                if (total > DanmakuParser.MAX_PAYLOAD_BYTES) {
                    close()
                    throw IOException("弹幕分段超过大小限制")
                }
                return count
            }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }).build()
    }
}
