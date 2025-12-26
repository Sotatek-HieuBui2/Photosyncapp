package com.example.photosync.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.photosync.data.local.MediaItemEntity
import com.example.photosync.ui.components.MediaItemCard
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCollages: () -> Unit = {},
    onDeleteItems: (List<MediaItemEntity>) -> Unit = {},
    isSearchMode: Boolean = false,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(if (isSearchMode) "Search" else "Library") },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isSearchMode) {
                // Search Section
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            viewModel.searchByTag(it)
                        },
                        placeholder = { Text("Search photos (e.g. 'Food', 'Cat')") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.searchByTag("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
                
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }

                item {
                    ToolCard(
                        title = "Object Scan",
                        icon = Icons.Default.ImageSearch,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { viewModel.scanForObjects() }
                    )
                }
            } else {
                // Library / Tools Section
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Utilities", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    ToolCard(
                        title = "Duplicate Finder",
                        icon = Icons.Default.ContentCopy,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = {
                            viewModel.scanForDuplicates()
                            showDuplicateDialog = true
                        }
                    )
                }

                item {
                    ToolCard(
                        title = "Smart Optimize",
                        icon = Icons.Default.AutoFixHigh,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = { /* TODO */ }
                    )
                }

                item {
                    ToolCard(
                        title = "Collages",
                        icon = Icons.Default.Collections,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onNavigateToCollages
                    )
                }
                
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ToolCard(
                        title = "Backup Verification",
                        icon = Icons.Default.VerifiedUser,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { /* TODO */ },
                        horizontal = true
                    )
                }
            }

            // Status Message
            if (uiState.message != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = uiState.message!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Search Results (Only show in Search Mode)
            if (isSearchMode && uiState.searchResults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Found ${uiState.searchResults.size} items",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(uiState.searchResults) { item ->
                    val context = LocalContext.current
                    MediaItemCard(
                        item = item,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(item.id), item.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDuplicateDialog) {
        DuplicateResultDialog(
            uiState = uiState,
            onDismiss = { showDuplicateDialog = false },
            onDelete = { items ->
                onDeleteItems(items)
                showDuplicateDialog = false
                viewModel.clearMessage()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    horizontal: Boolean = false
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (horizontal) 80.dp else 160.dp)
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun DuplicateResultDialog(
    uiState: ToolsUiState,
    onDismiss: () -> Unit,
    onDelete: (List<MediaItemEntity>) -> Unit
) {
    // State to track selected items for deletion
    // Default: Select all except the first one in each group (keep one)
    val selectedItems = remember(uiState.duplicateGroups) {
        mutableStateListOf<MediaItemEntity>().apply {
            uiState.duplicateGroups.forEach { group ->
                // Skip the first one (keep it), select the rest
                if (group.size > 1) {
                    addAll(group.drop(1))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Finder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scanning...", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (uiState.duplicateGroups.isEmpty()) {
                    Text("No duplicates found.")
                } else {
                    Text("Found ${uiState.duplicateGroups.size} sets of duplicates.")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 300.dp)
                    ) {
                        items(uiState.duplicateGroups) { group ->
                            DuplicateGroupItem(
                                group = group,
                                selectedItems = selectedItems,
                                onToggleSelect = { item ->
                                    if (selectedItems.contains(item)) {
                                        selectedItems.remove(item)
                                    } else {
                                        selectedItems.add(item)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${selectedItems.size} items selected for deletion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (!uiState.isLoading && uiState.duplicateGroups.isNotEmpty()) {
                TextButton(
                    onClick = { onDelete(selectedItems.toList()) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Selected")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DuplicateGroupItem(
    group: List<MediaItemEntity>,
    selectedItems: List<MediaItemEntity>,
    onToggleSelect: (MediaItemEntity) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "Group: ${group.firstOrNull()?.fileName ?: "Unknown"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        group.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelect(item) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = selectedItems.contains(item),
                    onCheckedChange = { onToggleSelect(item) }
                )
                Text(
                    text = "${item.fileName} (${formatSize(item.fileSize)})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1) String.format("%.2f MB", mb) else String.format("%.2f KB", kb)
}



