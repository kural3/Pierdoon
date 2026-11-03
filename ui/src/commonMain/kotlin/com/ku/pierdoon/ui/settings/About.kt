package com.ku.pierdoon.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ku.pierdoon.ui.components.SpinningLogo
import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.about_credits
import com.ku.pierdoon.ui.resources.about_github
import com.ku.pierdoon.ui.resources.about_github_desc
import com.ku.pierdoon.ui.resources.about_hint
import com.ku.pierdoon.ui.resources.about_issues
import com.ku.pierdoon.ui.resources.about_issues_desc
import com.ku.pierdoon.ui.resources.about_license
import com.ku.pierdoon.ui.resources.about_license_desc
import com.ku.pierdoon.ui.resources.about_releases
import com.ku.pierdoon.ui.resources.about_releases_desc
import com.ku.pierdoon.ui.resources.about_tagline
import com.ku.pierdoon.ui.resources.app_name
import com.ku.pierdoon.ui.resources.settings_version
import org.jetbrains.compose.resources.stringResource

private const val REPO = "https://github.com/kural3/Pierdoon"

/** "About": the logo to turn in your fingers, what the app is, and where it lives. */
@Composable
fun AboutContent(version: String, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SpinningLogo(Modifier.padding(top = 8.dp).size(260.dp))
        Text(
            stringResource(Res.string.about_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displaySmallEmphasized)
        Text(
            stringResource(Res.string.settings_version, version),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
    Group(null) {
        listOf<@Composable (Shape) -> Unit>(
            { shape -> ActionItem(shape, stringResource(Res.string.about_github), stringResource(Res.string.about_github_desc)) { uri.openUri(REPO) } },
            { shape -> ActionItem(shape, stringResource(Res.string.about_releases), stringResource(Res.string.about_releases_desc)) { uri.openUri("$REPO/releases") } },
            { shape -> ActionItem(shape, stringResource(Res.string.about_issues), stringResource(Res.string.about_issues_desc)) { uri.openUri("$REPO/issues") } },
            { shape -> ActionItem(shape, stringResource(Res.string.about_license), stringResource(Res.string.about_license_desc)) { uri.openUri("$REPO/blob/main/LICENSE") } },
        )
    }
    Text(
        stringResource(Res.string.about_credits),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    )
}
