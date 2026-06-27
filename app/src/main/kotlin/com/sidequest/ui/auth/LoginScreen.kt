package com.sidequest.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sidequest.R
import com.sidequest.ui.components.PillButton
import com.sidequest.ui.components.SecondaryPillButton

/**
 * Login / sign-up screen (Req 13), styled to the SideQuest welcome design:
 * soft decorative color "blobs" in the background, a branded logo lockup, an
 * encouraging headline, and pill-shaped action buttons. Offers email + password
 * that toggles between sign-in and create-account, plus a "continue offline"
 * path since the app is offline-first.
 *
 * Authentication is wired in the data layer (`AuthApi`, token storage, sync);
 * this screen is presentational and proceeds via [onAuthenticated].
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onAuthenticated: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signUp by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Decorative background blobs (soft, blurred tonal circles).
            DecorativeBlobs()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo lockup.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        text = "SideQuest",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.login_email_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_password_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )

                PillButton(
                    text = stringResource(
                        if (signUp) R.string.login_sign_up else R.string.login_sign_in,
                    ),
                    onClick = onAuthenticated,
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    icon = Icons.Filled.Mail,
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = { signUp = !signUp },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (signUp) R.string.login_toggle_to_sign_in else R.string.login_toggle_to_sign_up,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                SecondaryPillButton(
                    text = stringResource(R.string.login_skip),
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Soft, blurred decorative circles in the brand's container tones, echoing the
 * design's background "blobs" that give the welcome screen its playful depth.
 */
@Composable
private fun DecorativeBlobs() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .offsetTopEnd()
                .blur(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .blur(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f)),
        )
    }
}

/** Nudges a blob toward the top-end corner without a full constraint layout. */
private fun Modifier.offsetTopEnd(): Modifier = this.then(
    Modifier.padding(start = 180.dp),
)
