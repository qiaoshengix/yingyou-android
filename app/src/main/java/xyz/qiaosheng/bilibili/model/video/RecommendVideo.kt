package xyz.qiaosheng.bilibili.model.video

data class RecommendVideo(
    val bvid: String,           // 视频 BV 号（如 BV1xx411c7mD）
    val title: String,          // 标题
    val coverUrl: String,       // 封面 URL
    val duration: Int,          // 时长（秒）
    val ownerName: String,      // UP主名
    val ownerAvatar: String,    // UP主头像 URL
    val playCount: Long,        // 播放量
    val danmakuCount: Long,     // 弹幕量
)
