package com.example.study_mentor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.study_mentor.data.GamificationRepository
import com.example.study_mentor.data.UserStats
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class AuthState(
    val isLoading: Boolean = false,
    val isUploadingProfilePic: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: FirebaseUser? = null,
    val profileImageBase64: String? = null
)

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val gamificationRepository: GamificationRepository = GamificationRepository()
) : ViewModel() {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isUploadingProfilePic = MutableStateFlow(false)
    val isUploadingProfilePic: StateFlow<Boolean> = _isUploadingProfilePic.asStateFlow()

    private val _profileImageBase64 = MutableStateFlow<String?>(null)
    val profileImageBase64: StateFlow<String?> = _profileImageBase64.asStateFlow()

    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats.asStateFlow()

    private var statsJob: Job? = null
    private var profileJob: Job? = null

    init {
        // Observe current user changes
        val initialUser = auth.currentUser
        _authState.update { it.copy(currentUser = initialUser) }
        observeUserStats(initialUser?.uid)
        observeUserProfile(initialUser?.uid)

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _authState.update { it.copy(currentUser = user) }
            observeUserStats(user?.uid)
            observeUserProfile(user?.uid)
        }
    }

    fun loadUserProfile(uid: String? = auth.currentUser?.uid) {
        observeUserProfile(uid)
    }

    private fun observeUserProfile(uid: String?) {
        profileJob?.cancel()
        if (uid.isNullOrBlank()) {
            _profileImageBase64.value = null
            _authState.value = _authState.value.copy(profileImageBase64 = null)
            return
        }
        profileJob = viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("AuthViewModel", "Error observing user profile", error)
                            return@addSnapshotListener
                        }
                        val base64 = snapshot?.getString("profileImageBase64")
                        _profileImageBase64.value = base64
                        _authState.value = _authState.value.copy(profileImageBase64 = base64)
                    }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to observe profile from Firestore", e)
            }
        }
    }

    private fun observeUserStats(uid: String?) {
        statsJob?.cancel()
        if (uid.isNullOrBlank()) {
            _userStats.value = UserStats(totalXp = 0)
            return
        }
        statsJob = viewModelScope.launch {
            gamificationRepository.getUserStats(uid).collect { stats ->
                _userStats.value = stats
            }
        }
    }

    fun login(email: String, password: String) {
        if (!validateEmail(email)) {
            _authState.update { it.copy(errorMessage = "Invalid email format") }
            return
        }
        if (password.length < 6) {
            _authState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.update { it.copy(isLoading = false, errorMessage = null) }
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        if (name.isBlank()) {
            _authState.value = _authState.value.copy(errorMessage = "Name cannot be empty")
            return
        }
        if (!validateEmail(email)) {
            _authState.value = _authState.value.copy(errorMessage = "Invalid email format")
            return
        }
        if (password.length < 6) {
            _authState.value = _authState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                // Optionally update profile with name
                val user = result.user
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                user?.updateProfile(profileUpdates)?.await()
                
                // Force a refresh of the user to get the display name in the current session
                user?.reload()?.await()
                
                _authState.value = _authState.value.copy(
                    isLoading = false, 
                    errorMessage = null, 
                    currentUser = auth.currentUser
                )
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }

    fun updateDisplayName(newName: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _authState.value = _authState.value.copy(errorMessage = "Name cannot be empty")
            onResult(false, "Name cannot be empty")
            return
        }

        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
            try {
                val user = auth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmed)
                    .build()
                user?.updateProfile(profileUpdates)?.await()
                user?.reload()?.await()
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    currentUser = auth.currentUser
                )
                onResult(true, null)
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage
                )
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun uploadProfilePicture(
        context: Context,
        imageUri: Uri,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val user = auth.currentUser
        if (user == null) {
            val error = "User is not authenticated"
            _authState.value = _authState.value.copy(errorMessage = error)
            onResult(false, error)
            return
        }

        viewModelScope.launch {
            _isUploadingProfilePic.value = true
            _authState.value = _authState.value.copy(isUploadingProfilePic = true, errorMessage = null)
            try {
                // 1. Process & compress image in background thread (max 300x300, JPEG 70%)
                val base64String = withContext(Dispatchers.IO) {
                    processAndCompressImage(context, imageUri)
                } ?: throw IllegalArgumentException("Could not read or compress the selected image")

                // 2. Save directly into Firestore users/{uid} document under profileImageBase64
                val payload = mapOf(
                    "profileImageBase64" to base64String,
                    "lastUpdated" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(user.uid)
                    .set(payload, SetOptions.merge())
                    .await()

                // 3. Update local state
                _profileImageBase64.value = base64String
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    isUploadingProfilePic = false,
                    profileImageBase64 = base64String,
                    errorMessage = null
                )
                _isUploadingProfilePic.value = false
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to save profile picture to Firestore", e)
                val errorMsg = e.localizedMessage ?: "Failed to upload profile picture"
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    isUploadingProfilePic = false,
                    errorMessage = errorMsg
                )
                _isUploadingProfilePic.value = false
                onResult(false, errorMsg)
            }
        }
    }

    /**
     * Resizes the image to max 300x300 pixels, compresses to JPEG format at 70% quality,
     * and encodes the result into a compact Base64 string safe for Firestore.
     */
    private fun processAndCompressImage(context: Context, imageUri: Uri): String? {
        return try {
            // Step 1: Decode image dimensions with inJustDecodeBounds
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // Step 2: Compute inSampleSize to downsample close to max 300x300
            val maxDimension = 300
            var inSampleSize = 1
            while (originalWidth / (inSampleSize * 2) >= maxDimension &&
                originalHeight / (inSampleSize * 2) >= maxDimension
            ) {
                inSampleSize *= 2
            }

            // Step 3: Decode bitmap with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // Step 4: Scale to max 300x300 preserving aspect ratio
            val scale = minOf(
                maxDimension.toFloat() / sampledBitmap.width,
                maxDimension.toFloat() / sampledBitmap.height
            )
            val finalBitmap = if (scale < 1.0f) {
                val targetWidth = (sampledBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (sampledBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // Step 5: Compress to JPEG format at 70% quality
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            finalBitmap.recycle()

            // Step 6: Encode byte array to Base64 String
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error compressing image to Base64", e)
            null
        }
    }

    fun sendPasswordReset(email: String? = null, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val targetEmail = email ?: auth.currentUser?.email
        if (targetEmail.isNullOrBlank()) {
            onResult(false, "No email address found")
            return
        }

        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(targetEmail).await()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun logout() {
        auth.signOut()
        profileJob?.cancel()
        _profileImageBase64.value = null
        _authState.update { it.copy(currentUser = null, profileImageBase64 = null) }
        observeUserStats(null)
    }

    fun clearError() {
        _authState.update { it.copy(errorMessage = null) }
    }

    private fun validateEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
