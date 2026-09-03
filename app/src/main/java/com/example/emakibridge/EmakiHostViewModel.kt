package com.example.emakibridge

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aatricks.easyreader.data.local.PreferencesManager
import io.aatricks.easyreader.data.repository.LibraryRepository
import javax.inject.Inject

/**
 * Host-scoped ViewModel giving the Emaki reader host access to Emaki's own singletons
 * (theme preferences + library database) so the reader's settings and Chapters sheet work
 * exactly as they do in the Emaki app.
 */
@HiltViewModel
class EmakiHostViewModel @Inject constructor(
    val preferencesManager: PreferencesManager,
    val libraryRepository: LibraryRepository
) : ViewModel()
