package com.kurai.musikk.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for the currently selected song across Stems and Repertoire screens.
 * When null, Stems shows an empty state prompting the user to add a song.
 */
object SelectedSong {
    private val _current = MutableStateFlow<Song?>(null)
    val current: StateFlow<Song?> = _current.asStateFlow()

    fun select(song: Song) {
        _current.value = song
    }

    fun clear() {
        _current.value = null
    }
}
