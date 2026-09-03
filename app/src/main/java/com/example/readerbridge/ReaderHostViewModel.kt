package com.example.readerbridge

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aatricks.easyreader.data.local.PreferencesManager
import io.aatricks.easyreader.data.repository.LibraryRepository
import javax.inject.Inject

/**
 * Host-scoped ViewModel giving the reader host access to the engine's own singletons
 * (theme preferences + library database) so the reader's settings and Chapters sheet work
 * exactly as they do in a standalone build of the engine.
 */
@HiltViewModel
class ReaderHostViewModel @Inject constructor(
    val preferencesManager: PreferencesManager,
    val libraryRepository: LibraryRepository
) : ViewModel()
