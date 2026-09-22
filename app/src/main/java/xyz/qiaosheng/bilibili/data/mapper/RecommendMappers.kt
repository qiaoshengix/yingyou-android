package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.data.local.entity.RecommendVideoEntity
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

fun RecommendVideo.toEntity(
    accountId: Long,
    position: Long
) = RecommendVideoEntity(
    accountId = accountId,
    bvid = bvid,
    title = title,
    coverUrl = coverUrl,
    duration = duration,
    ownerName = ownerName,
    ownerAvatar = ownerAvatar,
    playCount = playCount,
    danmakuCount = danmakuCount,
    position = position
)

fun RecommendVideoEntity.toUiModel() = RecommendVideo(
    bvid = bvid,
    title = title,
    coverUrl = coverUrl,
    duration = duration,
    ownerName = ownerName,
    ownerAvatar = ownerAvatar,
    playCount = playCount,
    danmakuCount = danmakuCount
)
