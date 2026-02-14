package com.example.photosync

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build as AndroidBuild
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.surfaceColorAtElevation
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photosync.ui.main.MainViewModel
import com.example.photosync.ui.components.MediaItemCard
import com.example.photosync.ui.main.MediaUiItem
import com.example.photosync.ui.theme.PhotoSyncTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import com.example.photosync.ui.tools.ToolsScreen
import com.example.photosync.ui.tools.CollageScreen
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

enum class Screen {
    Photos, Search, Library, Collages
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoSyncTheme {
                // Main Navigation Controller
                var currentScreen by remember { mutableStateOf(Screen.Photos) }
                val context = LocalContext.current
                val mainViewModel: MainViewModel = hiltViewModel()
                
                // Shared delete logic
                var pendingDeleteItems by remember { mutableStateOf<List<com.example.photosync.data.local.MediaItemEntity>?>(null) }
                
                val deleteLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    val deletedItems = pendingDeleteItems.orEmpty()
                    if (result.resultCode == Activity.RESULT_OK) {
                        if (deletedItems.isNotEmpty()) {
                            mainViewModel.handleLocalDeletionSuccess(deletedItems.map { it.id })
                        }
                        Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Deletion cancelled or failed", Toast.LENGTH_SHORT).show()
                    }
                    pendingDeleteItems = null
                }

                fun launchDelete(items: List<com.example.photosync.data.local.MediaItemEntity>) {
                    if (items.isEmpty()) return
                    pendingDeleteItems = items
                    if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.R) {
                        val uris = items.map { Uri.parse(it.id) }
                        val intentSender = MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
                        deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } else {
                        Toast.makeText(context, "Batch delete requires Android 11+", Toast.LENGTH_SHORT).show()
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (currentScreen != Screen.Collages) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp
                            ) {
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            if (currentScreen == Screen.Photos) Icons.Filled.Image else Icons.Outlined.Image, 
                                            contentDescription = "Photos"
                                        ) 
                                    },
                                    label = { Text("Photos") },
                                    selected = currentScreen == Screen.Photos,
                                    onClick = { currentScreen = Screen.Photos }
                                )
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            if (currentScreen == Screen.Search) Icons.Filled.Search else Icons.Outlined.Search, 
                                            contentDescription = "Search"
                                        ) 
                                    },
                                    label = { Text("Search") },
                                    selected = currentScreen == Screen.Search,
                                    onClick = { currentScreen = Screen.Search }
                                )
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            if (currentScreen == Screen.Library) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary, 
                                            contentDescription = "Library"
                                        ) 
                                    },
                                    label = { Text("Library") },
                                    selected = currentScreen == Screen.Library,
                                    onClick = { currentScreen = Screen.Library }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.Photos -> MainScreen(
                                viewModel = mainViewModel,
                                onNavigateToTools = { currentScreen = Screen.Library }, // Redirect to Library
                                onDeleteRequested = { items -> launchDelete(items) }
                            )
                            Screen.Search -> ToolsScreen( // Reusing ToolsScreen as Search for now, will refactor
                                onNavigateBack = { currentScreen = Screen.Photos },
                                onNavigateToCollages = { currentScreen = Screen.Collages },
                                onDeleteItems = { items -> launchDelete(items) },
                                isSearchMode = true // New parameter to force search mode
                            )
                            Screen.Library -> ToolsScreen( // Reusing ToolsScreen as Library
                                onNavigateBack = { currentScreen = Screen.Photos },
                                onNavigateToCollages = { currentScreen = Screen.Collages },
                                onDeleteItems = { items -> launchDelete(items) },
                                isSearchMode = false
                            )
                            Screen.Collages -> CollageScreen(
                                onNavigateBack = { currentScreen = Screen.Library }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToTools: () -> Unit,
    onDeleteRequested: (List<com.example.photosync.data.local.MediaItemEntity>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val context = LocalContext.current
    var showProfileDialog by remember { mutableStateOf(false) }
    var viewingMediaItem by remember { mutableStateOf<com.example.photosync.data.local.MediaItemEntity?>(null) }
    
    // Lottie Animation State
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.success_animation))
    var showSuccessAnimation by remember { mutableStateOf(false) }

    // Trigger success animation
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage == "Sync Completed!") {
            showSuccessAnimation = true
            delay(2500) 
            showSuccessAnimation = false
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startSyncProcess()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.handleSignInResult(account)
            } catch (e: ApiException) {
                e.printStackTrace()
                viewModel.setError("Sign in failed: Code ${e.statusCode}")
            }
        } else {
            viewModel.setError("Sign in cancelled or failed (ResultCode: ${result.resultCode})")
        }
    }

    // Auto-launch sign-in when ViewModel requests re-auth (consume once)
    LaunchedEffect(key1 = uiState.triggerSignIn) {
        if (uiState.triggerSignIn) {
            try {
                val intent = viewModel.getSignInIntent()
                launcher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                viewModel.clearTriggerSignIn()
            }
        }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        val permissionsToRequest = if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            // Permissions already granted, start sync
            viewModel.startSyncProcess()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("PhotoSync", style = MaterialTheme.typography.headlineMedium)
                        if (uiState.isSignedIn && uiState.userEmail != null) {
                            Text(
                                text = uiState.userEmail!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (uiState.isSignedIn) {
                        // Auto Sync Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Auto Sync"
                                    stateDescription = if (uiState.isAutoSyncEnabled) "On" else "Off"
                                    this.role = Role.Switch
                                }
                                .clickable { viewModel.toggleAutoSync(!uiState.isAutoSyncEnabled) }
                                .padding(8.dp) // Increase touch target
                        ) {
                            Text("Auto", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.isAutoSyncEnabled,
                                onCheckedChange = null, // Handled by Row clickable
                                modifier = Modifier.scale(0.8f) // Slightly larger than 0.7
                            )
                        }

                        // Profile Button
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                        }
                    } else {
                        Button(onClick = {
                            viewModel.clearError()
                            val signInIntent = viewModel.getSignInIntent()
                            launcher.launch(signInIntent)
                        }) {
                            Text("Sign In")
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (uiState.isSignedIn) {
                Box(contentAlignment = Alignment.Center) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            progress = uiState.progress,
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 4.dp
                        )
                    }
                    FloatingActionButton(
                        onClick = { viewModel.startSyncProcess() },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ) {
                        if (uiState.isLoading) {
                            Icon(Icons.Default.Sync, contentDescription = "Syncing")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Progress Banner
            AnimatedVisibility(visible = uiState.isLoading || uiState.statusMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = uiState.statusMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Error Message
            if (uiState.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Media Grid
            if (uiState.mediaItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No photos found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.mediaItems,
                        span = { item ->
                            when (item) {
                                is MediaUiItem.Header -> GridItemSpan(maxLineSpan)
                                is MediaUiItem.Media -> GridItemSpan(1)
                            }
                        },
                        key = { item ->
                            when (item) {
                                is MediaUiItem.Header -> item.title
                                is MediaUiItem.Media -> item.item.id
                            }
                        }
                    ) { item ->
                        when (item) {
                            is MediaUiItem.Header -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            is MediaUiItem.Media -> {
                                MediaItemCard(
                                    item = item.item,
                                    onClick = {
                                        if (item.item.isLocal) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(item.item.id), item.item.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            viewingMediaItem = item.item
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Signed in as: ${uiState.userEmail}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Wifi Only Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleWifiOnly(!uiState.isWifiOnly) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Sync only on Wi-Fi",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.isWifiOnly,
                            onCheckedChange = null // Handled by Row clickable
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Free up space
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.prepareFreeUpSpace { items ->
                                    if (items.isNotEmpty()) {
                                        onDeleteRequested(items)
                                    } else {
                                        Toast.makeText(context, "No items to free up", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showProfileDialog = false
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Free up space",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Remove original photos & videos from device that are already backed up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Tools & Utilities
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showProfileDialog = false
                                onNavigateToTools()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Tools & Utilities",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Duplicate finder, Smart optimize, and more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showProfileDialog = false
                }) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSuccessAnimation) {
        Dialog(onDismissRequest = { showSuccessAnimation = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LottieAnimation(
                        composition = composition,
                        iterations = 1,
                        modifier = Modifier.size(150.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sync Completed!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    if (viewingMediaItem != null) {
        PhotoViewer(item = viewingMediaItem!!, onDismiss = { viewingMediaItem = null })
    }
}

@Composable
fun PhotoViewer(item: com.example.photosync.data.local.MediaItemEntity, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Smart Restore Logic: Load thumbnail first, then full
            // Use a decent preview size (w640) for the placeholder to avoid too much blur
            val highResUrl = if (item.remoteUrl != null) "${item.remoteUrl}=d" else item.id
            val lowResUrl = if (item.remoteUrl != null) "${item.remoteUrl}=w640-h640" else null
            
            // 1. Low Res (Thumbnail) - Only if remote
            if (lowResUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(lowResUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                    // Removed alpha to show full brightness placeholder
                )
            }

            // 2. High Res (Full) - Fades in on top
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(highResUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

