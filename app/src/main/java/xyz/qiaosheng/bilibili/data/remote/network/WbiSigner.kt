package xyz.qiaosheng.bilibili.data.remote.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.error.requireApiSuccess
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService

@Singleton
class WbiSigner @Inject constructor(
    private val apiService: BiliApiService
) {
    suspend fun sign(parameters: Map<String, String>): Map<String, String> {
        val navResponse = apiService.getNav()
        // nav 未登录时可能带非零 code，但仍提供公共签名密钥。
        val wbiImage = navResponse.data?.wbiImg ?: run {
            requireApiSuccess(navResponse.code, navResponse.message)
            throw InvalidResponseException("获取 WBI 密钥失败：响应缺少 wbi_img")
        }

        return WbiSignature.sign(
            parameters = parameters,
            imgUrl = wbiImage.imgUrl,
            subUrl = wbiImage.subUrl,
            timestampSeconds = System.currentTimeMillis() / 1000
        )
    }
}

internal object WbiSignature {
    private val invalidCharacters = Regex("[!'()*]")

    private val mixinKeyTable = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32,
        15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19,
        29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63,
        57, 62, 11, 36, 20, 34, 44, 52
    )

    fun sign(
        parameters: Map<String, String>,
        imgUrl: String,
        subUrl: String,
        timestampSeconds: Long
    ): Map<String, String> {
        val sourceKey = extractKey(imgUrl) + extractKey(subUrl)
        require(sourceKey.length > mixinKeyTable.max()) {
            "WBI 密钥格式不正确"
        }

        val mixinKey = buildString {
            mixinKeyTable.forEach { index -> append(sourceKey[index]) }
        }.take(32)

        val unsignedParameters = parameters + ("wts" to timestampSeconds.toString())
        val canonicalQuery = unsignedParameters
            .toSortedMap()
            .entries
            .joinToString("&") { (key, value) ->
                val filteredValue = value.replace(invalidCharacters, "")
                "${encode(key)}=${encode(filteredValue)}"
            }

        return unsignedParameters + ("w_rid" to md5(canonicalQuery + mixinKey))
    }

    private fun extractKey(url: String): String = url
        .substringBefore('?')
        .substringAfterLast('/')
        .substringBeforeLast('.')

    private fun encode(value: String): String = URLEncoder
        .encode(value, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .replace("%7E", "~")

    private fun md5(value: String): String = MessageDigest
        .getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
