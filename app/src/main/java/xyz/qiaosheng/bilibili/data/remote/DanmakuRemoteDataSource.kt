package xyz.qiaosheng.bilibili.data.remote

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import xyz.qiaosheng.bilibili.data.remote.api.DanmakuApiService
import xyz.qiaosheng.bilibili.data.remote.network.WbiSigner
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

interface DanmakuRemoteDataSource {
    suspend fun loadSegment(session: DanmakuSessionSnapshot, aid: Long, cid: Long, segmentIndex: Int): List<DanmakuItem>
}

class RetrofitDanmakuRemoteDataSource @Inject constructor(
    private val api: DanmakuApiService,
    private val signer: WbiSigner,
    private val parser: DanmakuParser,
) : DanmakuRemoteDataSource {
    override suspend fun loadSegment(
        session: DanmakuSessionSnapshot, aid: Long, cid: Long, segmentIndex: Int,
    ): List<DanmakuItem> = withContext(Dispatchers.IO) {
        require(aid > 0 && cid > 0 && segmentIndex > 0)
        try {
            val parameters = signer.sign(mapOf(
                "type" to "1", "oid" to cid.toString(), "pid" to aid.toString(),
                "segment_index" to segmentIndex.toString(),
            ))
            currentCoroutineContext().ensureActive()
            val body = api.segment(session.cookieHeader, parameters)
            coroutineScope {
                val parsing = async { parser.parse(body, segmentIndex) }
                try {
                    parsing.await()
                } finally {
                    // Closing from the awaiting coroutine unblocks a socket read on cancellation.
                    body.close()
                }
            }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw error
        }
    }
}
