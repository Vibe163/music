package com.localmusic.app.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object PlaylistsTab : Screen("playlists_tab")
    data object Settings : Screen("settings")
    data object ImportLogs : Screen("import_logs")
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
}
