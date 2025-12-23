package com.example.photosync

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.photosync.ui.main.MainViewModel
import com.example.photosync.ui.theme.PhotoSyncTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.Manifest
import android.os.Build
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoSyncTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startSyncProcess()
        } else {
            // Handle permission denied (optional: show snackbar)
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
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Photo Sync App", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = uiState.statusMessage ?: "Loading...")
        } else {
            if (uiState.isSignedIn) {
                Text(text = "Signed in as: ${uiState.userEmail}")
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = uiState.statusMessage ?: "Ready to sync")
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { 
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }

                    val allPermissionsGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allPermissionsGranted) {
                        viewModel.startSyncProcess()
                    } else {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }) {
                    Text("Sync Now")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(onClick = { viewModel.signOut() }) {
                    Text("Sign Out")
                }
            } else {
                Button(
                    onClick = {
                        val signInIntent = viewModel.getSignInIntent()
                        launcher.launch(signInIntent)
                    }
                ) {
                    Text("Sign in with Google")
                }
            }
        }
        
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }
    }
}
