package xyz.qiaosheng.bilibili.core.network

/** B 站的图片地址可能省略协议，统一转换后再交给图片加载器。 */
fun String.toHttps(): String = when {
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}
