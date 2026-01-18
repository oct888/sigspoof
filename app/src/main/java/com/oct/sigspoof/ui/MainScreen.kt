package com.oct.sigspoof.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults


@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onEditApp: (String) -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsState()
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Signature Spoofer") }
            )
        },
        floatingActionButton = {
            if (hasPendingChanges) {
                FloatingActionButton(
                    onClick = { viewModel.commitChanges() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply Changes"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Debug Warning
            if (com.oct.sigspoof.BuildConfig.SKIP_SIGNATURE_VERIFICATION) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Security Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Security Risk! (Debug Build)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "This build is signed with the default debug certificate. You should only use this build for testing purposes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (!isServiceConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Module Not Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Enable the module in LSPosed settings and reboot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Permission Restriction Warning (Chinese ROMs)
            val permissionRestricted by viewModel.permissionRestricted.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            if (permissionRestricted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "App List Restricted",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Your device may require additional permissions to see installed apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { com.oct.sigspoof.utils.PermissionHelper.openAppListPermission(context) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
            }
            
            // Search and Filter Header
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    label = { Text("Search Apps") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { viewModel.onToggleSystemApps(it) }
                    )
                    Text("Show System Apps")
                }
            }
            
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                @OptIn(ExperimentalMaterial3Api::class)
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            AppItem(
                                app = app,
                                onToggle = { 
                                    when {
                                        app.pendingState == PendingState.PENDING_REMOVE -> {
                                            viewModel.cancelRemoval(app.packageName)
                                        }
                                        app.isSpoofed -> {
                                            viewModel.markForRemoval(app.packageName)
                                        }
                                        else -> {
                                            onEditApp(app.packageName)
                                        }
                                    }
                                },
                                onClick = { onEditApp(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val isPendingRemoval = app.pendingState == PendingState.PENDING_REMOVE
    
    if (isPendingRemoval) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            AppItemContent(app = app, onToggle = onToggle, isPendingRemoval = true)
        }
    } else if (app.isSpoofed) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            AppItemContent(app = app, onToggle = onToggle, isPendingRemoval = false)
        }
    } else {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            AppItemContent(app = app, onToggle = onToggle, isPendingRemoval = false)
        }
    }
}

@Composable
fun AppItemContent(
    app: AppInfo,
    onToggle: () -> Unit,
    isPendingRemoval: Boolean
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (isPendingRemoval) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = if (isPendingRemoval) TextDecoration.LineThrough else TextDecoration.None,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPendingRemoval) {
                    Text(
                        text = "Pending Removal",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (app.isSpoofed) {
                    Text(
                        text = "Spoofed",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (app.isSystem) {
                     Text(
                        text = "System",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Checkbox(
            checked = app.isSpoofed && !isPendingRemoval,
            onCheckedChange = { onToggle() }
        )
    }
}
