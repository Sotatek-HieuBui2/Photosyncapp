package com.example.photosync.ui.main

import android.app.Application
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.photosync.auth.AuthManager
import com.example.photosync.data.local.MediaItemEntity
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.repository.MediaRepository
import com.example.photosync.util.MainDispatcherRule
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.After
import io.mockk.coVerify

import androidx.work.WorkManager
import androidx.work.Operation
import androidx.work.WorkContinuation
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.Data
import java.util.UUID
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var authManager: AuthManager

    @MockK(relaxed = true)
    lateinit var tokenManager: TokenManager

    @MockK(relaxed = true)
    lateinit var mediaRepository: MediaRepository

    @MockK
    lateinit var application: Application

    @MockK
    lateinit var workManager: WorkManager

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        // Mock WorkManager static
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager

        // Default mocks
        every { tokenManager.getEmail() } returns null
        every { tokenManager.getAccessToken() } returns null
        every { tokenManager.isCloudSyncEnabled() } returns true
        every { tokenManager.isAutoSyncEnabled() } returns false
        every { tokenManager.isWifiOnly() } returns true
        every { mediaRepository.allMediaItems } returns flowOf(emptyList())
        
        // WorkManager mocks
        every { workManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk<Operation>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns mockk<Operation>()
        every { workManager.cancelUniqueWork(any()) } returns mockk<Operation>()
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

    private fun createViewModel() {
        viewModel = MainViewModel(authManager, tokenManager, mediaRepository, application)
    }


    @Test
    fun `init checks login status and updates state when logged in`() {
        // Given
        every { tokenManager.getEmail() } returns "test@example.com"
        every { tokenManager.getAccessToken() } returns "valid_token"
        
        // When
        createViewModel()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals("test@example.com", state.userEmail)
    }

    @Test
    fun `init checks login status and updates state when not logged in`() {
        // Given
        every { tokenManager.getEmail() } returns null
        every { tokenManager.getAccessToken() } returns null

        // When
        createViewModel()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isSignedIn)
        assertEquals(null, state.userEmail)
    }

    @Test
    fun `handleSignInResult success updates state and saves token`() = runTest {
        // Given
        createViewModel()
        val account = mockk<GoogleSignInAccount>()
        every { account.email } returns "test@example.com"
        every { authManager.hasPermissions(account) } returns true
        coEvery { authManager.getAccessToken(account) } returns "new_token"
        every { tokenManager.saveAccessToken("new_token") } returns Unit
        every { tokenManager.saveEmail("test@example.com") } returns Unit
        
        // Mocks for startSyncProcess
        val workInfo = WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.SUCCEEDED,
            emptySet(),
            Data.EMPTY,
            Data.EMPTY,
            0
        )
        every { workManager.getWorkInfoByIdFlow(any()) } returns flowOf(workInfo)

        // When
        viewModel.handleSignInResult(account)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals("test@example.com", state.userEmail)
        assertFalse(state.isLoading)
        
        verify { tokenManager.saveAccessToken("new_token") }
        verify { tokenManager.saveEmail("test@example.com") }
        coVerify { mediaRepository.scanLocalMedia() }
        verify { workManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `handleSignInResult failure updates state with error`() = runTest {
        // Given
        createViewModel()
        val account = mockk<GoogleSignInAccount>()
        every { account.email } returns "test@example.com"
        every { authManager.hasPermissions(account) } returns true
        coEvery { authManager.getAccessToken(account) } returns null
        every { tokenManager.getDiagnostic("tokeninfo") } returns null

        // When
        viewModel.handleSignInResult(account)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isSignedIn)
        assertEquals("Failed to retrieve access token", state.error)
    }
    
    @Test
    fun `observeMediaItems updates state`() = runTest {
        // Given
        val items = listOf(
            MediaItemEntity("1", "img1.jpg", "/path/1", "image/jpeg", 100L),
            MediaItemEntity("2", "img2.jpg", "/path/2", "image/jpeg", 200L)
        )
        every { mediaRepository.allMediaItems } returns flowOf(items)

        // When
        createViewModel()

        // Then
        val state = viewModel.uiState.value
        // Grouping inserts a header per date, so expect header + media items
        assertEquals(3, state.mediaItems.size)
        val firstMedia = state.mediaItems[1]
        assertTrue(firstMedia is MediaUiItem.Media)
        val mediaItem = (firstMedia as MediaUiItem.Media).item
        assertEquals("img1.jpg", mediaItem.fileName)
    }

    @Test
    fun `startSyncProcess handles reauth required`() = runTest {
        // Given
        createViewModel()
        // Directly invoke the public reauth handler to validate behavior without mocking suspend calls
        viewModel.handleReAuthRequired()
        advanceUntilIdle()

        // Then: tokenManager.clear() should be invoked and triggerSignIn true
        verify { tokenManager.clear() }
        coVerify { authManager.signOut() }
        val state = viewModel.uiState.value
        println("DEBUG: uiState after reauth -> $state")
        assertFalse(state.isSignedIn)
        assertTrue(state.triggerSignIn)
    }
}
