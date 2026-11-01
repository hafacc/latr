package cc.hafa.latr.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import cc.hafa.latr.util.InfoPages

/** An info page bundled with the app, with a button by its title that opens its web page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(asset: String, path: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val page = remember(asset) {
        InfoPages.parse(context.assets.open(asset).bufferedReader().use { it.readText() })
    }
    val uriHandler = LocalUriHandler.current
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = page.title, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { uriHandler.openUri(InfoPages.SITE_URL + path) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open on the web",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            page.sections.forEach { section ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = section.heading, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        InfoPages.textParts(section.body).forEach { part ->
                            if (part.href != null) {
                                withLink(LinkAnnotation.Url(InfoPages.SITE_URL + part.href, linkStyles)) {
                                    append(part.text)
                                }
                            } else {
                                append(part.text)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.closing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
