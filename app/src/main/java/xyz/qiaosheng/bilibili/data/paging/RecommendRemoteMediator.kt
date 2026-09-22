package xyz.qiaosheng.bilibili.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.local.entity.RecommendRemoteKeyEntity
import xyz.qiaosheng.bilibili.data.local.entity.RecommendVideoEntity
import xyz.qiaosheng.bilibili.data.mapper.toEntity
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

@OptIn(ExperimentalPagingApi::class)
class RecommendRemoteMediator(
    private val accountId: Long,
    private val database: BiliDatabase,
    private val loadPage: suspend (Int, Int, Int) -> List<RecommendVideo>

) : RemoteMediator<Int, RecommendVideoEntity>() {

    private val dao = database.recommendDao()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RecommendVideoEntity>
    ): MediatorResult {
        return try {
            val cursor = when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> {
                    return MediatorResult.Success(
                        endOfPaginationReached = true
                    )
                }

                LoadType.APPEND -> {
                    val key = dao.remoteKey(accountId) ?: return MediatorResult.Error(
                        IllegalStateException("推荐分页状态缺失")
                    )
                    key.nextCursor ?: return MediatorResult.Success(
                        endOfPaginationReached = true
                    )
                }
            }

            val videos = loadPage(
                state.config.pageSize,
                cursor,
                cursor
            )

            val endReached = videos.isEmpty()

            database.withTransaction {
                val startPosition = if (loadType == LoadType.REFRESH) {
                    dao.clearVideos(accountId)
                    0L
                } else {
                    checkNotNull(dao.remoteKey(accountId)).nextPosition
                }

                dao.insertVideos(videos.mapIndexed { index, video ->
                    video.toEntity(
                        accountId = accountId,
                        position = startPosition + index
                    )
                })

                dao.saveRemoteKey(
                    RecommendRemoteKeyEntity(
                        accountId = accountId,
                        nextCursor = if (endReached) null else cursor + 1,
                        nextPosition = startPosition + videos.size
                    )
                )
            }

            MediatorResult.Success(
                endOfPaginationReached = endReached
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

}