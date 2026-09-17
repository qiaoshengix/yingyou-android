package xyz.qiaosheng.bilibili.data.remote.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WbiSignerTest {
    @Test
    fun sign_matchesKnownWbiSignatureVector() {
        val signedParameters = WbiSignature.sign(
            parameters = mapOf(
                "foo" to "114",
                "bar" to "514",
                "baz" to "1919810"
            ),
            imgUrl = "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
            subUrl = "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png",
            timestampSeconds = 1_702_204_169L
        )

        assertEquals("1702204169", signedParameters["wts"])
        assertEquals(
            "6149fdadf571698ca7e6a567265cd0ee",
            signedParameters["w_rid"]
        )
    }
}
