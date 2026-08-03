package com.localmusic.app.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object PlaylistsTab : Screen("playlists_tab")
    data object Settings : Screen("settings")
    data object NowPlaying : Screen("now_playing")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}
