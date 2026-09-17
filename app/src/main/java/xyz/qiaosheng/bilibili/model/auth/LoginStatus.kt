package xyz.qiaosheng.bilibili.model.auth

/** 二维码轮询后的业务状态，供登录页面展示与会话建立流程使用。 */
enum class LoginStatus {
    Waiting,        // 等待扫码
    Scanned,        // 已扫码，等待确认
    Expired,        // 二维码过期
    Success,        // 登录成功
    Failed          // 登录失败
}
