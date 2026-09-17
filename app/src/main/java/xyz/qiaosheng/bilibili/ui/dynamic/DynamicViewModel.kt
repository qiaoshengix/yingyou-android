package xyz.qiaosheng.bilibili.ui.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.repository.DynamicRepository
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.model.dynamic.DynamicFeedType

/** 维护动态分类；登录会话或分类变化时切换对应的分页数据源。 */
@HiltViewModel
class DynamicViewModel @Inject constructor(
    private val repository: DynamicRepository,
    authSessionManager: AuthSessionManager
) : ViewModel() {
    private val _selectedFeedType = MutableStateFlow(DynamicFeedType.All)
    val selectedFeedType = _selectedFeedType.asStateFlow()

    val isLoggedIn = authSessionManager.authState
        .map { it is AuthState.LoggedIn }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            authSessionManager.authState.value is AuthState.LoggedIn
        )

    // flatMapLatest 会取消旧会话/分类的数据流，退出登录后立即切换为空列表。
    @OptIn(ExperimentalCoroutinesApi::class)
    val posts = authSessionManager.authState
        .flatMapLatest { authState ->
            if (authState is AuthState.LoggedIn) {
                selectedFeedType.flatMapLatest(repository::getDynamicPager)
            } else {
                flowOf(PagingData.empty())
            }
        }
        .cachedIn(viewModelScope)

    fun selectFeedType(feedType: DynamicFeedType) {
        _selectedFeedType.value = feedType
    }
}
