package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.remote.dto.DynamicApiItem
import xyz.qiaosheng.bilibili.data.remote.dto.DynamicContent
import xyz.qiaosheng.bilibili.data.remote.dto.DynamicMajor
import xyz.qiaosheng.bilibili.model.dynamic.DynamicForwardedPost
import xyz.qiaosheng.bilibili.model.dynamic.DynamicMedia
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPost

/** 转换动态内容及其媒体附件；不完整的接口条目不会进入动态列表。 */
internal fun DynamicApiItem.toUiModel(): DynamicPost? {
    if (idStr.isBlank()) return null
    val author = modules.moduleAuthor ?: return null
    val stats = modules.moduleStat
    val content = modules.moduleDynamic
    val media = content?.major.toUiModel()
    val forwarded = orig?.let { original ->
        val originalAuthor = original.modules.moduleAuthor
        val originalContent = original.modules.moduleDynamic
        DynamicForwardedPost(
            authorName = originalAuthor?.name.orEmpty(),
            text = originalContent.contentText(),
            media = originalContent?.major.toUiModel()
        )
    }

    return DynamicPost(
        id = idStr,
        authorName = author.name,
        authorAvatar = author.face.toHttps(),
        publishTimestamp = author.pubTs,
        publishAction = author.pubAction,
        text = content.contentText(),
        media = media,
        forwardedPost = forwarded,
        commentCount = stats?.comment?.count ?: 0,
        forwardCount = stats?.forward?.count ?: 0,
        likeCount = stats?.like?.count ?: 0,
        isLiked = stats?.like?.status == true
    )
}

private fun DynamicContent?.contentText(): String {
    if (this == null) return ""
    return desc?.text?.takeIf(String::isNotBlank)
        ?: major?.opus?.summary?.text.orEmpty()
}

private fun DynamicMajor?.toUiModel(): DynamicMedia? {
    if (this == null) return null
    archive?.takeIf { it.bvid.isNotBlank() }?.let {
        return DynamicMedia.Video(
            bvid = it.bvid,
            title = it.title,
            coverUrl = it.cover.toHttps(),
            description = it.desc,
            durationText = it.durationText,
            playCountText = it.stat?.play.orEmpty(),
            danmakuCountText = it.stat?.danmaku.orEmpty()
        )
    }
    val imageUrls = draw?.items
        ?.takeIf { it.isNotEmpty() }
        ?.map { it.src.toHttps() }
        ?: opus?.pics
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.url.toHttps() }
        ?: emptyList()
    if (imageUrls.isNotEmpty()) return DynamicMedia.Images(imageUrls)

    article?.let {
        return DynamicMedia.Article(
            title = it.title,
            description = it.desc,
            coverUrls = it.covers.map(String::toHttps)
        )
    }
    opus?.takeIf { it.title.isNotBlank() }?.let {
        return DynamicMedia.Article(
            title = it.title,
            description = it.summary?.text.orEmpty(),
            coverUrls = emptyList()
        )
    }
    return null
}
