package xyz.qiaosheng.bilibili.data.remote.dto

data class PlayUrlResponse(
    val code: Int,
    val message: String,
    val data: PlayData?
)

data class PlayData(
    val dash: Dash?,
    val acceptQuality: List<Int> = emptyList(),
    val acceptDescription: List<String> = emptyList()
)

data class Dash(
    val video: List<DashStream>,   // 视频流列表（不同清晰度）
    val audio: List<DashStream>    // 音频流列表（不同品质）
)

data class DashStream(
    val id: Int,            // 清晰度 ID（80=1080P, 64=720P, 32=480P, 16=360P）
    val baseUrl: String,    // 播放地址
    val bandwidth: Int,     // 带宽（用于排序选最高品质）
    val codecs: String,     // 编码格式
    val backupUrl: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = ""
) {
    val qualityName: String
        get() = when (id) {
            127 -> "8K 超高清"
            126 -> "杜比视界"
            120 -> "4K 超清"
            116 -> "1080P60"
            112 -> "1080P+"
            80 -> "1080P"
            64 -> "720P"
            32 -> "480P"
            16 -> "360P"
            else -> "清晰度 $id"
        }
}
