package com.epfl.esl.workoutapp.mobile.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.epfl.esl.workoutapp.R
import com.google.android.gms.wearable.DataClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userKey: String,
    dataClient: DataClient,
    onBack: () -> Unit,
    // We can reuse the same VM instance if scoped correctly,
    // or create a new one with the same factory.
    vm: ProfileViewModel = viewModel(factory = ProfileVmFactory(userKey))
) {
    // Load initial data
    LaunchedEffect(Unit) {
        vm.start()
    }

    val currentUsername by vm.username.collectAsState()
    val currentWeight by vm.weight.collectAsState()
    val currentHeight by vm.height.collectAsState()
    val currentImageUrl by vm.profileImageUrl.collectAsState()

    // Local state for editing
    var editUsername by remember { mutableStateOf("") }
    var editWeight by remember { mutableStateOf("") }
    var editHeight by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Initialize local state when data loads from Firebase
    LaunchedEffect(currentUsername) { if (editUsername.isBlank()) editUsername = currentUsername }
    LaunchedEffect(currentWeight) { if (currentWeight > 0) editWeight = currentWeight.toString() }
    LaunchedEffect(currentHeight) { if (currentHeight > 0) editHeight = currentHeight.toString() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) {
        uri: Uri? ->
        if (uri != null) {
            vm.updateProfileImage(uri, context, dataClient)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Save changes
                        val wText = editWeight.trim()
                        val hText = editHeight.trim()

                        val w: Int? = if (wText.isBlank()) null else wText.toIntOrNull()
                        val h: Int? = if (hText.isBlank()) null else hText.toIntOrNull()


                        if (editUsername.isNotBlank()) {
                            vm.updateProfile(
                                editUsername,
                                w,
                                h,
                                context,
                                dataClient)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        // Launch picker on click
                        imagePickerLauncher.launch("image/*")
                    }
            ) {
                if (currentImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentImageUrl)
                            .crossfade(true)
                            // Fix: Add a signature (like the current time) or disable memory cache
                            // to force the app to check for a new image.
                            .memoryCacheKey(currentImageUrl + System.currentTimeMillis())
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback icon
                    Image(
                        painter = painterResource(id = R.drawable.user_image), // Ensure you have a placeholder or use an Icon
                        contentDescription = "Default Profile",
                        modifier = Modifier.padding(20.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Text("Tap image to change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Personal Information", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = editUsername,
                onValueChange = { editUsername = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = editWeight,
                    onValueChange = { editWeight = it },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = editHeight,
                    onValueChange = { editHeight = it },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}