package com.sidequest.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sidequest.R
import com.sidequest.ui.components.PillButton

/**
 * Join Organization screen (Req 12, 13): enter an invite code to join a team so
 * shared leaderboards become available. Styled to the SideQuest design with a
 * friendly icon hero, a rounded code field, and pill actions. Organization
 * membership is resolved by the backend; this presentational screen captures
 * the code and hands it to [onJoined].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinOrganizationScreen(
    modifier: Modifier = Modifier,
    onJoined: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var code by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.join_org_title)) },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reminder_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }

            Text(
                text = stringResource(R.string.join_org_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.join_org_code_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            PillButton(
                text = stringResource(R.string.join_org_join),
                onClick = onJoined,
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.join_org_skip),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
