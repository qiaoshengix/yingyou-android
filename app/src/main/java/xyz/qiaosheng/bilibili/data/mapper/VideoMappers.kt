package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.remote.dto.ReplyItem
import xyz.qiaosheng.bilibili.data.remote.dto.VideoDetailData
import xyz.qiaosheng.bilibili.model.video.Comment
import xyz.qiaosheng.bilibili.model.video.VideoDetail
import xyz.qiaosheng.bilibili.model.video.VideoOwner

/** 视频详情和评论的接口模型转换；粉丝数由仓库额外查询后补充。 */
fun VideoDetailData.toUiModel(): VideoDetail {
    return VideoDetail(
        bvid = bvid,
        title = title,
        desc = desc,
        coverUrl = pic.toHttps(),
        duration = duration,
        playCount = stat.view,
        danmakuCount = stat.danmaku,
        likeCount = stat.like,
        coinCount = stat.coin,
        favoriteCount = stat.favorite,
        shareCount = stat.share,
        replyCount = stat.reply,
        pubdate = pubdate,
        owner = VideoOwner(
            mid = owner.mid,
            name = owner.name,
            avatarUrl = owner.face,
            fansCount = 0,  // 粉丝数需要额外 API
            following = false
        )
    )
}

fun ReplyItem.toUiModel(): Comment = Comment(
    id = rpid,
    userName = member.uname,
    avatarUrl = member.avatar.toHttps(),
    content = content.message,
    likeCount = like,
    replyCount = rcount,
    timestamp = ctime
)
