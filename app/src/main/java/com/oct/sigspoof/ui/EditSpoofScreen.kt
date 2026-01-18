package com.oct.sigspoof.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EditSpoofScreen(
    packageName: String,
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var signature by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf(SpoofOption.CUSTOM) }

    BackHandler {
        onNavigateBack()
    }
    
    // Initialize with existing config if any
    LaunchedEffect(packageName) {
        val app = viewModel.apps.value.find { it.packageName == packageName }
        if (app != null && app.isSpoofed && app.spoofedSignature != null) {
            signature = app.spoofedSignature
            selectedOption = SpoofOption.CUSTOM
        }
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = java.io.File(context.cacheDir, "temp_signature.apk")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val sig = viewModel.getApkSignature(tempFile.absolutePath)
                if (sig != null) {
                   signature = sig
                   selectedOption = SpoofOption.APK
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Configure Spoofing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Package: $packageName", style = MaterialTheme.typography.titleMedium)

            Text("Spoof Source", style = MaterialTheme.typography.titleSmall)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedOption == SpoofOption.INSTALLED,
                    onClick = {
                        selectedOption = SpoofOption.INSTALLED
                        val sig = viewModel.getInstalledSignature(packageName)
                        if (sig != null) signature = sig
                    }
                )
                Text("Installed App Signature")
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedOption == SpoofOption.APK,
                    onClick = {
                        apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                    }
                )
                Text("From APK File")
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedOption == SpoofOption.CUSTOM,
                    onClick = { selectedOption = SpoofOption.CUSTOM }
                )
                Text("Custom String")
            }

            OutlinedTextField(
                value = signature,
                onValueChange = { 
                    signature = it 
                    selectedOption = SpoofOption.CUSTOM
                },
                label = { Text("Base64 Certificate (DER)") },
                placeholder = { Text("Paste the Base64-encoded signing certificate (e.g. MIIC...)") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                maxLines = 10
            )

            Button(
                onClick = {
                    viewModel.updateSpoof(packageName, signature)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signature.isNotBlank()
            ) {
                Text("Save Configuration")
            }
        }
    }
}

enum class SpoofOption {
    INSTALLED, APK, CUSTOM
}
