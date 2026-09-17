package xyz.qiaosheng.bilibili.core.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose 可能提供包装过的 Context；窗口副作用需要找到实际 Activity。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
