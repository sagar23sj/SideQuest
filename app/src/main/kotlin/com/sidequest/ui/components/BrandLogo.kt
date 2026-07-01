package com.sidequest.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sidequest.R

/**
 * The SideQuest brand lockup: the rocket mark (identical to the launcher icon,
 * via `R.drawable.ic_brand_mark`) optionally paired with the "SideQuest"
 * wordmark. Centralizing it here keeps the app's identity consistent across the
 * login/sign-up screen, the loading splash, and the profile hero — so what users
 * see in-app matches the icon on their home screen.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    showWordmark: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(markSize),
        )
        if (showWordmark) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
