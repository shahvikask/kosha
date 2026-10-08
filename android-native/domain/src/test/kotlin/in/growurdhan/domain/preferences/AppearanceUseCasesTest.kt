package `in`.growurdhan.domain.preferences

import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppearanceUseCasesTest {
    @Test
    fun unknownStoredThemeSafelyDefaultsToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("invalid"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark"))
    }

    @Test
    fun observedSettingsRemainImmutableValues() = runTest {
        val repository = mockk<AppearanceRepository>()
        val expected = AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)
        every { repository.settings } returns flowOf(expected)

        assertEquals(expected, ObserveAppearanceSettings(repository)().first())
    }

    @Test
    fun updateUseCaseDelegatesPersistenceToRepository() = runTest {
        val repository = mockk<AppearanceRepository>()
        coEvery { repository.setTheme(any()) } just Runs
        coEvery { repository.setDynamicColor(any()) } just Runs

        val useCases = UpdateAppearanceSettings(repository)
        useCases.theme(ThemeMode.DARK)
        useCases.dynamicColor(false)

        coVerify(exactly = 1) { repository.setTheme(ThemeMode.DARK) }
        coVerify(exactly = 1) { repository.setDynamicColor(false) }
    }
}
