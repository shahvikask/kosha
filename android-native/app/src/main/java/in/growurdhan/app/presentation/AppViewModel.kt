package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode
import `in`.growurdhan.domain.preferences.ObserveAppearanceSettings
import `in`.growurdhan.domain.preferences.UpdateAppearanceSettings
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val errorMessage: String? = null,
)

sealed interface AppIntent {
    data class SelectTheme(val mode: ThemeMode) : AppIntent
    data class SetDynamicColor(val enabled: Boolean) : AppIntent
    data object DismissError : AppIntent
}

/** User intents enter here; repository data flows back as immutable UI state. */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeAppearanceSettings: ObserveAppearanceSettings,
    private val updateAppearanceSettings: UpdateAppearanceSettings,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> =
        combine(observeAppearanceSettings(), error) { appearance, message ->
            AppUiState(appearance, message)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppUiState(),
        )

    fun onIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.SelectTheme -> persist { updateAppearanceSettings.theme(intent.mode) }
            is AppIntent.SetDynamicColor -> persist { updateAppearanceSettings.dynamicColor(intent.enabled) }
            AppIntent.DismissError -> error.value = null
        }
    }

    private fun persist(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                error.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // No file paths, tokens, personal identifiers or exception bodies exposed to UI.
                error.value = "The display preference could not be saved. Please try again."
            }
        }
    }
}
