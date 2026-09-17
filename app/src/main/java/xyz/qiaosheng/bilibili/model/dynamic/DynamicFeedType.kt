package xyz.qiaosheng.bilibili.model.dynamic

enum class DynamicFeedType(val apiValue: String, val label: String) {
    All(apiValue = "all", label = "全部"),
    Video(apiValue = "video", label = "视频")
}
