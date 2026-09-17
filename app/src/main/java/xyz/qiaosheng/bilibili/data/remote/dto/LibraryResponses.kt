package xyz.qiaosheng.bilibili.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LibraryResponse<T>(val code: Int = -1, val message: String? = null, val data: T? = null)
data class FavoriteFoldersData(val count: Int? = null, val list: List<FavoriteFolderDto>? = null)
data class FavoriteFolderDto(
    val id: Long = 0, val title: String? = null,
    @SerializedName("media_count") val mediaCount: Int = 0,
    @SerializedName("fav_state") val favState: Int? = null
)
data class FavoriteContentsData(
    val info: FavoriteFolderDto? = null,
    val medias: List<FavoriteMediaDto>? = null,
    @SerializedName("has_more") val hasMore: Boolean? = null
)
data class FavoriteMediaDto(
    val id: Long = 0, val type: Int = 0, val bvid: String? = null,
    @SerializedName("bv_id") val bvId: String? = null,
    val title: String? = null, val cover: String? = null, val duration: Long = 0,
    val upper: LibraryOwnerDto? = null,
    @SerializedName("fav_time") val favTime: Long = 0
)
data class LibraryOwnerDto(val name: String? = null)
data class LibraryVideoDto(
    val bvid: String? = null, val aid: Long = 0, val cid: Long = 0, val title: String? = null,
    val pic: String? = null, val duration: Long = 0, val owner: LibraryOwnerDto? = null
)
