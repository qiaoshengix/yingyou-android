package xyz.qiaosheng.bilibili.core.error

/** 只能传入由应用编写的操作说明；不得放入接口原文、签名 URL 或其他敏感信息。 */
open class UserFacingException(val userMessage: String) : IllegalArgumentException(userMessage)
