package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.ThemeMode
import `in`.growurdhan.domain.preferences.AppearanceRepository
import `in`.growurdhan.domain.preferences.ObserveAppearanceSettings
import `in`.growurdhan.domain.preferences.UpdateAppearanceSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import `in`.growurdhan.core.model.AppearanceSettings
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun userThemeSelectionPersistsThroughDomainUseCase() = runTest {
        val repository = mockk<AppearanceRepository>()
        every { repository.settings } returns flowOf(AppearanceSettings())
        coEvery { repository.setTheme(any()) } just Runs
        val viewModel = AppViewModel(
            ObserveAppearanceSettings(repository),
            UpdateAppearanceSettings(repository),
        )

        viewModel.onIntent(AppIntent.SelectTheme(ThemeMode.DARK))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setTheme(ThemeMode.DARK) }
    }
}
