package com.musicapp.player.feature.about

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AboutUiState(
    val metadata: AboutMetadata? = null,
    val loadFailed: Boolean = false,
    val isLicenseVisible: Boolean = false,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    metadataSource: AboutMetadataSource,
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow(
            runCatching(metadataSource::load).fold(
                onSuccess = { AboutUiState(metadata = it) },
                onFailure = { AboutUiState(loadFailed = true) },
            ),
        )
    val uiState: StateFlow<AboutUiState> = mutableUiState.asStateFlow()

    fun showLicenses() {
        mutableUiState.update { state ->
            if (state.metadata == null) state else state.copy(isLicenseVisible = true)
        }
    }

    fun dismissLicenses() {
        mutableUiState.update { it.copy(isLicenseVisible = false) }
    }
}
