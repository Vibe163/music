package com.localmusic.app.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object PlaylistsTab : Screen("playlists_tab")
    data object Settings : Screen("settings")
    data object NowPlaying : Screen("now_playing")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
    // 创作者训练空间（独立模块，不影响现有路由）
    data object CreatorFeed : Screen("creator_feed")
    data object CreatorPublish : Screen("creator_publish")
    data object CreatorProfile : Screen("creator_profile/{authorId}") {
        fun createRoute(authorId: String) = "creator_profile/$authorId"
    }
    // 用户主页（独立路由，与播放页完全隔离，避免叠加渲染）
    data object UserHome : Screen("user_home/{userId}") {
        fun createRoute(userId: String) = "user_home/$userId"
    }
}
