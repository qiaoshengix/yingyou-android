package xyz.qiaosheng.bilibili.core.error

import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ExceptionHandlingTest {
    @Test fun businessFailureIsCheckedBeforeMissingData() {
        val failure = assertThrows(ApiException::class.java) {
            requireApiData<String>(-1, "original diagnostic", null)
        }
        assertEquals(-1, failure.code)
        assertEquals("original diagnostic", failure.message)
    }

    @Test fun successfulMissingDataIsNotAnEmptyResult() {
        assertThrows(InvalidResponseException::class.java) { requireApiData<String>(0, null, null) }
    }

    @Test fun emptyCollectionIsValidData() {
        assertEquals(emptyList<String>(), requireApiData(0, null, emptyList<String>()))
    }

    @Test fun successfulWriteDoesNotRequireResponseData() { requireApiSuccess(0, null) }

    @Test fun cancellationEscapesResultBoundary() = runTest {
        val cancellation = CancellationException("cancel")
        try {
            suspendRunCatching<Unit> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    @Test fun ordinaryFailurePreservesOriginalCause() = runTest {
        val error = IOException("network")
        assertSame(error, suspendRunCatching<Unit> { throw error }.exceptionOrNull())
    }

    @Test fun fatalErrorsAreNotConvertedToRequestFailures() = runTest {
        val error = AssertionError("program invariant")
        try {
            suspendRunCatching<Unit> { throw error }
            fail("Fatal errors must escape")
        } catch (actual: AssertionError) { assertSame(error, actual) }
    }

    @Test fun cancellationWinsOverLibraryIOException() = runTest {
        var failureReported = false
        val job = launch {
            suspendRunCatching {
                currentCoroutineContext().cancel()
                throw IOException("library reports cancellation as IO")
            }.onFailure { failureReported = true }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(failureReported)
    }

    @Test fun specificIoMessagesAreNotHiddenByGenericIoBranch() {
        assertTrue(SocketTimeoutException().toUserMessage().contains("超时"))
        assertTrue(UnknownHostException().toUserMessage().contains("网络"))
        assertTrue(SSLException("certificate").toUserMessage().contains("安全连接"))
        assertTrue(JsonSyntaxException("bad json").toUserMessage().contains("解析"))
    }

    @Test fun httpStatusIsDistinctFromBusinessCode() {
        val http = HttpException(Response.error<Unit>(503, "server detail".toResponseBody()))
        assertTrue(http.toUserMessage().contains("服务器暂时不可用"))
        assertTrue(ApiException(503, "business").toUserMessage().contains("错误码：503"))
    }

    @Test fun unknownErrorDoesNotExposeInternalDiagnostic() {
        val secret = "private URL and response"
        assertFalse(IllegalStateException(secret).toUserMessage().contains(secret))
        assertFalse(ApiException(-10, secret).toUserMessage().contains(secret))
    }

    @Test fun reporterLogsOriginalExceptionButNeverLogsCancellation() {
        val logged = mutableListOf<Throwable>()
        val reporter = ErrorReporter { _, error -> logged += error }
        val failure = IOException("diagnostic")
        assertTrue(reporter.message("搜索失败", failure).startsWith("搜索失败："))
        assertSame(failure, logged.single())
        assertThrows(CancellationException::class.java) {
            reporter.message("取消", CancellationException())
        }
        assertEquals(1, logged.size)
    }
}
