package xyz.qiaosheng.bilibili.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingExceptionTest {
    @Test fun explicitlyAuthoredMessageIsDisplayed() {
        assertEquals("请删除此任务后重新选择", UserFacingException("请删除此任务后重新选择").toUserMessage())
    }

    @Test fun arbitraryExceptionMessageIsNotExposed() {
        assertFalse(IllegalArgumentException("https://cdn.test/video?secret=1").toUserMessage().contains("secret"))
    }
}
