package `in`.growurdhan.app.presentation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.growurdhan.domain.vault.VaultAuthorization

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultAuthorizationModule {
    @Binds abstract fun bindAuthorization(gate: VaultSessionGate): VaultAuthorization
}
