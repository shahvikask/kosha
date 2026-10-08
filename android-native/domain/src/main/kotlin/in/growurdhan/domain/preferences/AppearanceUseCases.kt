package `in`.growurdhan.domain.preferences

import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAppearanceSettings @Inject constructor(
    private val repository: AppearanceRepository,
) {
    operator fun invoke(): Flow<AppearanceSettings> = repository.settings
}

class UpdateAppearanceSettings @Inject constructor(
    private val repository: AppearanceRepository,
) {
    suspend fun theme(mode: ThemeMode) = repository.setTheme(mode)
    suspend fun dynamicColor(enabled: Boolean) = repository.setDynamicColor(enabled)
}
