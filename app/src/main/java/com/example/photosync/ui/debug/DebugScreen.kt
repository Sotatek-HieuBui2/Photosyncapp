package com.example.photosync.ui.debug

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DebugInfo(
    val lastScanTime: Long = 0,
    val dbItemCount: Int = 0,
    val dbImageCount: Int = 0,
    val dbVideoCount: Int = 0,
    val mediaStoreImageCount: Int = 0,
    val mediaStoreVideoCount: Int = 0,
    val sampleMediaStoreItems: List<String> = emptyList(),
    val sampleDbItems: List<String> = emptyList(),
    val logs: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val debugInfo by viewModel.debugInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadDebugInfo(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDebugInfo(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Storage & Scan Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    DebugCard(title = "Last Scan Time") {
                        val time = if (debugInfo.lastScanTime == 0L) {
                            "Never scanned"
                        } else {
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(debugInfo.lastScanTime * 1000))
                        }
                        Text(time, fontFamily = FontFamily.Monospace)
                    }
                }

                item {
                    DebugCard(title = "Database Items") {
                        Column {
                            Text("Total: ${debugInfo.dbItemCount}", fontFamily = FontFamily.Monospace)
                            Text("Images: ${debugInfo.dbImageCount}", fontFamily = FontFamily.Monospace)
                            Text("Videos: ${debugInfo.dbVideoCount}", fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                item {
                    DebugCard(title = "MediaStore Items (Device)") {
                        Column {
                            Text("Images: ${debugInfo.mediaStoreImageCount}", fontFamily = FontFamily.Monospace)
                            Text("Videos: ${debugInfo.mediaStoreVideoCount}", fontFamily = FontFamily.Monospace)
                            Text(
                                "Total: ${debugInfo.mediaStoreImageCount + debugInfo.mediaStoreVideoCount}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    val missing = (debugInfo.mediaStoreImageCount + debugInfo.mediaStoreVideoCount) - debugInfo.dbItemCount
                    DebugCard(
                        title = "Comparison",
                        containerColor = if (missing > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column {
                            Text(
                                "Missing in DB: $missing items",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            if (missing > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "⚠️ Some media files are not in the database!",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sample MediaStore Items (first 10)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(debugInfo.sampleMediaStoreItems) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sample DB Items (first 10)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(debugInfo.sampleDbItems) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.forceFullRescan(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Force Full Rescan")
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.clearDatabase() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear DB")
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(debugInfo.logs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DebugCard(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
