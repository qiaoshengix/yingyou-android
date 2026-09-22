package xyz.qiaosheng.bilibili.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import xyz.qiaosheng.bilibili.data.repository.VideoRecommendRepository
import xyz.qiaosheng.bilibili.model.video.RecommendVideo
import xyz.qiaosheng.bilibili.data.sync.LibrarySession

/** 首页只暴露 Paging 数据流；加载与错误状态直接使用 Paging 的 LoadState。 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    videoRecommendRepository: VideoRecommendRepository,
    private val session: LibrarySession
) : ViewModel() {
    // ViewModel 存活期间复用分页，返回首页时不会因为重新收集而新建同一份数据。
    @OptIn(ExperimentalCoroutinesApi::class)
    val videos = session.accountId
        .flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(PagingData.empty<RecommendVideo>())
            } else {
                videoRecommendRepository.getRecommendVideoPager(accountId)
            }
        }
        .cachedIn(viewModelScope)

}
