package com.sidequest.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.auth.AuthApi
import com.sidequest.data.auth.AuthTokens
import com.sidequest.data.auth.CreateAccountRequest
import com.sidequest.data.auth.LoginRequest
import com.sidequest.data.auth.TokenStore
import com.sidequest.data.local.UserPreferences
import com.sidequest.data.sync.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Drives the sign-in / sign-up screen (Req 13.1, 13.2, 13.3).
 *
 * Bridges the presentational [LoginScreen] to the auth data layer: it calls
 * [AuthApi.login] / [AuthApi.createAccount], persists the returned JWT pair via
 * [TokenStore], records the signed-in email in [UserPreferences] (so the app
 * knows the user has a credentialed account, not just the silent device backup
 * identity), and kicks off a backup pass so the account's snapshot is
 * uploaded/restored. All network failures are mapped to friendly, non-leaky
 * messages; the offline-first local experience is never blocked.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Prefills the display name for sign-up from the locally chosen name. */
    val suggestedDisplayName: String
        get() = userPreferences.displayName.value?.takeIf { it.isNotBlank() } ?: "Adventurer"

    /**
     * Authenticates ([signUp] = false) or creates an account ([signUp] = true)
     * with the given [email] / [password]. On success it stores the tokens and
     * the signed-in email, schedules a backup/restore pass, and flips
     * [LoginUiState.success] so the screen can navigate on.
     */
    fun submit(email: String, password: String, signUp: Boolean) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) return
        if (_uiState.value.loading) return

        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val tokens = if (signUp) {
                        authApi.createAccount(
                            CreateAccountRequest(
                                email = trimmedEmail,
                                password = password,
                                displayName = suggestedDisplayName,
                            ),
                        ).tokens
                    } else {
                        authApi.login(LoginRequest(email = trimmedEmail, password = password)).tokens
                    }
                    tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
                }
            }

            result.onSuccess {
                userPreferences.setSignedInEmail(trimmedEmail)
                // Back up (and, on an empty/fresh install, restore) off the UX path.
                BackupWorker.schedule(appContext)
                _uiState.update { it.copy(loading = false, success = true) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(loading = false, errorMessage = messageFor(throwable, signUp))
                }
            }
        }
    }

    /** Clears a shown error once the user edits the form. */
    fun onInputChanged() {
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * Maps an auth failure to a friendly, non-enumerating message. HTTP status
     * codes mirror the backend's generic responses (401 invalid credentials,
     * 409 email already in use); an [IOException] means the device is offline or
     * the backend is unreachable.
     */
    private fun messageFor(throwable: Throwable, signUp: Boolean): String = when {
        throwable is IOException -> AuthError.OFFLINE
        throwable is HttpException && throwable.code() == 401 -> AuthError.INVALID_CREDENTIALS
        throwable is HttpException && throwable.code() == 409 -> AuthError.EMAIL_IN_USE
        throwable is HttpException && throwable.code() == 400 ->
            if (signUp) AuthError.INVALID_DETAILS else AuthError.INVALID_CREDENTIALS
        else -> AuthError.GENERIC
    }
}

/**
 * Immutable UI state for the login screen: a loading flag while a request is in
 * flight, a friendly [errorMessage] on failure, and a one-shot [success] flag
 * the screen observes to navigate onward.
 */
data class LoginUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

/** Friendly, non-leaky auth error strings surfaced to the user. */
private object AuthError {
    const val OFFLINE = "You're offline. Connect to the internet and try again."
    const val INVALID_CREDENTIALS = "That email or password doesn't look right."
    const val EMAIL_IN_USE = "An account with that email already exists. Try signing in."
    const val INVALID_DETAILS = "Please enter a valid email and a longer password."
    const val GENERIC = "Something went wrong. Please try again."
}
