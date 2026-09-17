package xyz.qiaosheng.bilibili.offline

import xyz.qiaosheng.bilibili.data.remote.dto.DashStream
import xyz.qiaosheng.bilibili.data.remote.dto.PlayData
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity

internal data class OfflineStreams(val video: DashStream, val audio: DashStream)

/** 只选择当前播放接口实际返回的可播放流；不合成高权限清晰度或处理 DRM。 */
internal fun selectOfflineStreams(data: PlayData, qualityId: Int?, previous: OfflineVideoEntity? = null): OfflineStreams {
    val dash = offlineRequireNotNull(data.dash) { "当前视频未提供可缓存的音视频流" }
    val videos = dash.video.filter { it.baseUrl.startsWith("https://") || it.baseUrl.startsWith("http://") }
    val quality = qualityId ?: videos.maxOfOrNull { it.id }
    val eligible = videos.filter { it.id == quality }
    val video = if (previous != null) eligible.firstOrNull {
        it.codecs == previous.videoCodec && it.bandwidth == previous.videoBandwidth
    } else eligible.minWithOrNull(compareBy<DashStream> {
        when {
            it.codecs.startsWith("avc", true) -> 0
            it.codecs.startsWith("hev", true) || it.codecs.startsWith("hvc", true) -> 1
            else -> 2
        }
    }.thenByDescending { it.bandwidth })
    val selectedVideo = offlineRequireNotNull(video) { "所选清晰度当前不可用，请删除此任务后重新选择" }
    val audios = dash.audio.filter { it.baseUrl.startsWith("https://") || it.baseUrl.startsWith("http://") }
    val audio = if (previous != null) audios.firstOrNull {
        it.id == previous.audioId && it.codecs == previous.audioCodec && it.bandwidth == previous.audioBandwidth
    } else audios.minWithOrNull(compareBy<DashStream> {
        if (it.codecs.startsWith("mp4a", true)) 0 else 1
    }.thenByDescending { it.bandwidth })
    val selectedAudio = offlineRequireNotNull(audio) { "当前视频未提供可缓存的音频流，请删除此任务后重新选择" }
    return OfflineStreams(selectedVideo, selectedAudio)
}
