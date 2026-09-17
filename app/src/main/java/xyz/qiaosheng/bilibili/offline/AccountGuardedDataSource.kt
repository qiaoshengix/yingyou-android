package xyz.qiaosheng.bilibili.offline

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** 已创建的播放器/下载连接也在账号改变后的下一次读取停止，不能只在打开页面时检查账号。 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AccountGuardedDataSource(
    private val delegate: DataSource,
    private val currentAccount: () -> Long?
) : DataSource {
    private var owner: Long? = null

    override fun open(dataSpec: DataSpec): Long {
        owner = OfflineKeys.accountId(dataSpec.key) ?: throw IOException("离线媒体缺少有效的账号标识")
        checkAccount()
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkAccount()
        return delegate.read(buffer, offset, length)
    }

    private fun checkAccount() {
        if (owner == null || owner != currentAccount()) throw IOException("账号已切换，请返回当前账号的离线缓存")
    }

    override fun getUri(): Uri? = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun close() { try { delegate.close() } finally { owner = null } }
}
