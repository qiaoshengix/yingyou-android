package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.remote.dto.RecommendItem
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

/** 将推荐接口条目转换为首页视频卡片模型。 */
fun RecommendItem.toUiModel(): RecommendVideo {
    return RecommendVideo(
        bvid = bvid,
        title = title,
        coverUrl = pic.toHttps(),
        duration = duration,
        ownerName = owner.name,
        ownerAvatar = owner.face,
        playCount = stat.view,
        danmakuCount = stat.danmaku
    )
}
