package com.localmusic.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localmusic.app.LocalMusicApp
import com.localmusic.app.data.importer.ImportResult
import com.localmusic.app.data.importer.MusicMetadataEditor
import com.localmusic.app.data.migration.ImportProgress as MigrationImportProgress
import com.localmusic.app.data.migration.ImportSummary
import com.localmusic.app.data.model.FAVORITES_PLAYLIST_ID
import com.localmusic.app.data.model.PlaylistWithCount
import com.localmusic.app.data.model.Song
import com.localmusic.app.data.model.toSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode { DATE_ADDED, TITLE, ARTIST, ALBUM }

data class LibraryUiState(
    val searchQuery: String = "",
    val currentPlaylistId: Long = FAVORITES_PLAYLIST_ID,
    val currentPlaylistName: String = "主收藏"
)

data class ImportProgress(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val lastResult: ImportResult? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LocalMusicApp
    private val repository = app.repository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_ADDED)

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    val playlists: StateFlow<List<PlaylistWithCount>> = combine(
        repository.observePlaylists(),
        repository.observeSongs()
    ) { userPlaylists, songs ->
        val favorites = PlaylistWithCount(
            id = FAVORITES_PLAYLIST_ID,
            name = "主收藏",
            createdAt = 0L,
            songCount = songs.size,
            isBuiltIn = true
        )
        listOf(favorites) + userPlaylists
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPlaylists: StateFlow<List<PlaylistWithCount>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPlaylistSongs: StateFlow<List<Song>> = _uiState
        .map { it.currentPlaylistId }
        .flatMapLatest { playlistId ->
            if (playlistId == FAVORITES_PLAYLIST_ID) {
                repository.observeSongs()
                    .map { list -> list.map { it.toSong() } }
            } else {
                repository.observePlaylistSongIds(playlistId).combine(
                    repository.observeSongs()
                ) { songIds, allEntities ->
                    val songMap = allEntities.associateBy { it.id }
                    songIds.mapNotNull { songMap[it]?.toSong() }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displaySongs: StateFlow<List<Song>> = combine(
        currentPlaylistSongs,
        _uiState.map { it.searchQuery },
        _sortMode
    ) { songs, query, sort ->
        val filtered = if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
        when (sort) {
            SortMode.DATE_ADDED -> filtered
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortMode.ALBUM -> filtered.sortedBy { it.album.lowercase() }
        }
    }.distinctUntilChanged()
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSongs: List<Song> get() = currentPlaylistSongs.value

    init {
        viewModelScope.launch {
            combine(
                playlists,
                _uiState.map { it.currentPlaylistId }
            ) { lists, currentId ->
                lists.firstOrNull { it.id == currentId }?.name ?: "主收藏"
            }.collect { name ->
                if (name != _uiState.value.currentPlaylistName) {
                    _uiState.value = _uiState.value.copy(currentPlaylistName = name)
                }
            }
        }
    }

    fun setCurrentPlaylist(playlistId: Long) {
        _uiState.value = _uiState.value.copy(
            currentPlaylistId = playlistId,
            searchQuery = ""
        )
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun importFromFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgress(running = true, total = uris.size)
            val result = repository.importFromUris(uris) { processed, total ->
                _importProgress.value = _importProgress.value.copy(processed = processed, total = total)
            }
            _importProgress.value = ImportProgress(running = false, lastResult = result)
        }
    }

    fun importFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            _importProgress.value = ImportProgress(running = true, total = 1)
            val result = repository.importFromTree(treeUri) { processed, total ->
                _importProgress.value = _importProgress.value.copy(processed = processed, total = total)
            }
            _importProgress.value = ImportProgress(running = false, lastResult = result)
        }
    }

    fun clearImportResult() {
        _importProgress.value = _importProgress.value.copy(lastResult = null)
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            onCreated(id)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_uiState.value.currentPlaylistId == playlistId) {
                _uiState.value = _uiState.value.copy(currentPlaylistId = FAVORITES_PLAYLIST_ID)
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId, songIds)
        }
    }

    fun removeSongFromCurrentPlaylist(songId: Long) {
        val playlistId = _uiState.value.currentPlaylistId
        viewModelScope.launch {
            if (playlistId == FAVORITES_PLAYLIST_ID) {
                repository.deleteSong(songId)
            } else {
                repository.removeSongFromPlaylist(playlistId, songId)
            }
        }
    }

    fun removeSongFromLibrary(songId: Long) {
        viewModelScope.launch {
            repository.deleteSong(songId)
        }
    }

    fun incrementPlayCount(songId: Long, onRefreshed: ((Song) -> Unit)? = null) {
        viewModelScope.launch {
            repository.incrementPlayCount(songId)
            repository.getSongById(songId)?.let { refreshed ->
                onRefreshed?.invoke(refreshed)
            }
        }
    }

    fun toggleFavorite(songId: Long, favorite: Boolean, onRefreshed: ((Song) -> Unit)? = null) {
        viewModelScope.launch {
            repository.toggleFavorite(songId, favorite)
            repository.getSongById(songId)?.let { refreshed ->
                onRefreshed?.invoke(refreshed)
            }
        }
    }

    fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        onResult: (MusicMetadataEditor.Result, Song?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateSongMetadata(songId, title, artist, album)
            val updatedSong = if (result is MusicMetadataEditor.Result.Success) {
                repository.getSongById(songId)
            } else null
            onResult(result, updatedSong)
        }
    }

    private val _migrationProgress = MutableStateFlow(MigrationImportProgress("", 0, 0, 0, 0))
    val migrationProgress: StateFlow<MigrationImportProgress> = _migrationProgress.asStateFlow()

    private val _migrationResult = MutableStateFlow<ImportSummary?>(null)
    val migrationResult: StateFlow<ImportSummary?> = _migrationResult.asStateFlow()

    fun clearMigrationResult() {
        _migrationResult.value = null
    }

    suspend fun exportPlaylistsToJson(onProgress: (Int, Int) -> Unit = { _, _ -> }): String {
        return repository.exportPlaylistsToJson(onProgress)
    }

    fun importPlaylistsFromJson(json: String, onFinished: (ImportSummary?) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.importPlaylistsFromJson(json) { progress ->
                _migrationProgress.value = progress
            }
            _migrationResult.value = result
            onFinished(result)
        }
    }

    fun exportPlaylistsAsync(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportPlaylistsToJson()
            onExported(json)
        }
    }
}
