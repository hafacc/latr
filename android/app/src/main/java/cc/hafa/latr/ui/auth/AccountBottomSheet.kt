package cc.hafa.latr.ui.auth

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.hafa.latr.ui.theme.LatrTheme

internal const val PRIVACY_URL = "https://latr.hafa.cc/privacy/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBottomSheet(
    authState: AuthState.SignedIn,
    pickLogEnabled: Boolean,
    onPickLogChange: (Boolean) -> Unit,
    isAdmin: Boolean,
    loadPickCounts: suspend () -> List<Long>?,
    onSignOut: () -> Unit,
    onDeleteAccount: (keep: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfilePhoto(url = authState.photoUrl, size = 72.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = authState.displayName ?: "Account",
                style = MaterialTheme.typography.headlineSmall
            )
            if (authState.email != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = authState.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = pickLogEnabled, role = Role.Switch, onValueChange = onPickLogChange)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Improve snooze suggestions",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = pickLogEnabled, onCheckedChange = null)
            }
            if (isAdmin) {
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStats = true }
                        .padding(vertical = 12.dp)
                )
            }
            Text(
                text = "Privacy",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(PRIVACY_URL) }
                    .padding(vertical = 12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    onSignOut()
                    onDismiss()
                }) {
                    Text("Sign out")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete account")
                }
            }
        }
    }

    if (showStats) {
        StatsBottomSheet(loadCounts = loadPickCounts, onDismiss = { showStats = false })
    }

    if (showDeleteConfirm) {
        val confirmDelete = { keep: Boolean ->
            showDeleteConfirm = false
            onDeleteAccount(keep)
            onDismiss()
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete your account?") },
            text = { Text("This deletes your account and everything synced to it.") },
            confirmButton = {
                // Three buttons don't fit the dialog's row.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { confirmDelete(true) }) {
                        Text("Keep todos on this device")
                    }
                    TextButton(
                        onClick = { confirmDelete(false) },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete everything")
                    }
                }
            }
        )
    }
}

@Preview
@Composable
private fun AccountBottomSheetPreview() {
    LatrTheme {
        AccountBottomSheet(
            authState = AuthState.SignedIn(
                displayName = "Jane Doe",
                email = "jane@example.com",
                photoUrl = null,
            ),
            pickLogEnabled = false,
            onPickLogChange = {},
            isAdmin = true,
            loadPickCounts = { null },
            onSignOut = {},
            onDeleteAccount = {},
            onDismiss = {}
        )
    }
}
