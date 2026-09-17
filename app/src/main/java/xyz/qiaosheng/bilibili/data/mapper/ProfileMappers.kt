package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.remote.dto.UserInfoData
import xyz.qiaosheng.bilibili.model.profile.UserProfile

/** 将用户接口字段转换为个人页面模型。 */
fun UserInfoData.toUiModel(): UserProfile = UserProfile(
    mid = mid,
    name = name,
    avatarUrl = face.toHttps(),
    sign = sign,
    level = level,
    followingCount = following,
    followerCount = follower,
    coins = coins
)
