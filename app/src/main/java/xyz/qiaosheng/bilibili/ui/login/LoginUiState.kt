package xyz.qiaosheng.bilibili.ui.login

import android.graphics.Bitmap
import xyz.qiaosheng.bilibili.model.auth.LoginStatus

data class LoginUiState(
    val qrBitmap: Bitmap,
    val loginStatus: LoginStatus
) {
}
