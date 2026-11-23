package com.example.appdev.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appdev.R
import com.example.appdev.domain.profile.ProfileType
import com.example.appdev.ui.theme.HeatColors

@StringRes
fun ProfileType?.labelRes(): Int = when (this) {
    null -> R.string.profile_custom
    ProfileType.WORK -> R.string.profile_work
    ProfileType.NAVIGATION -> R.string.profile_navigation
    ProfileType.GAMING -> R.string.profile_gaming
    ProfileType.CHARGING -> R.string.profile_charging
}

/** Row of chips: Custom plus the four profiles. `null` means Custom. */
@Composable
fun ProfileSelector(
    active: ProfileType?,
    onSelect: (ProfileType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<ProfileType?> = listOf(null) + ProfileType.entries
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == active,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = HeatColors.Accent,
                    selectedLabelColor = HeatColors.OnAccent,
                    containerColor = HeatColors.SurfaceRaised,
                    labelColor = HeatColors.TextSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = option == active,
                    borderColor = HeatColors.Outline,
                    selectedBorderColor = HeatColors.Accent,
                ),
            )
        }
    }
}
