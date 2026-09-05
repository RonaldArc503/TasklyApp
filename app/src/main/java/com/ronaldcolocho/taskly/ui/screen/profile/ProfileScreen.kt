package com.ronaldcolocho.taskly.ui.screen.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.ui.state.ProfileUiState
import kotlinx.coroutines.launch

private val AvatarColors = listOf(
    Color(0xFF2563EB),
    Color(0xFF059669),
    Color(0xFF7C3AED),
    Color(0xFFEA580C),
    Color(0xFFDC2626),
    Color(0xFF0F9E9A)
)

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToConverter: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::changeProfilePhoto)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        when (val state = uiState) {
            ProfileUiState.Loading -> LoadingProfile(innerPadding)
            is ProfileUiState.Error -> ProfileError(innerPadding, state.message)
            is ProfileUiState.Success -> {
                LaunchedEffect(state.photoError) {
                    state.photoError?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearPhotoError()
                    }
                }
                ProfileContent(
                    user = state.user,
                    isPhotoUploading = state.isPhotoUploading,
                    contentPadding = innerPadding,
                    onChangePhoto = { photoPicker.launch("image/*") },
                    onSettings = onNavigateToSettings,
                    onNavigateToSaved = onNavigateToSaved,
                    onNavigateToConverter = onNavigateToConverter,
                    onDemo = { scope.launch { snackbarHostState.showSnackbar("Proximamente") } },
                    onLogout = { viewModel.logout(onSuccess = onLogout) }
                )
            }
        }
    }
}

@Composable
private fun LoadingProfile(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun ProfileError(contentPadding: PaddingValues, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center
    ) { Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
}

@Composable
private fun ProfileContent(
    user: UserProfile,
    isPhotoUploading: Boolean,
    contentPadding: PaddingValues,
    onChangePhoto: () -> Unit,
    onSettings: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToConverter: () -> Unit,
    onDemo: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = user.displayName.trim().ifBlank {
        user.email.substringBefore('@').ifBlank { "Usuario" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Perfil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Configuracion")
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(user, displayName, isPhotoUploading, onChangePhoto)
                Spacer(Modifier.height(16.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.phone.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        user.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(
                "Funcionalidades demo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            FeatureRow(Icons.Default.BookmarkBorder, "Mis guardados", "Contenido guardado", onNavigateToSaved)
        }
        item {
            FeatureRow(Icons.Default.PlayCircleOutline, "Conversor YouTube", "Descargar en MP3 o MP4", onNavigateToConverter)
        }
        item {
            FeatureRow(Icons.Default.ContentPaste, "Copia y Pega", "Proximamente", onDemo)
        }
        item {
            FeatureRow(Icons.Default.LibraryMusic, "Mis Musicas", "Proximamente", onDemo)
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable(onClick = onLogout)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesion", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    user: UserProfile,
    displayName: String,
    isUploading: Boolean,
    onChangePhoto: () -> Unit
) {
    val key = user.uid.ifBlank { user.email.ifBlank { displayName } }
    val avatarColor = remember(key) { AvatarColors[(key.hashCode() and Int.MAX_VALUE) % AvatarColors.size] }
    val initial = remember(displayName) { displayName.firstOrNull()?.uppercase() ?: "?" }

    Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(avatarColor.copy(alpha = 0.16f))
                .border(2.dp, avatarColor.copy(alpha = 0.62f), androidx.compose.foundation.shape.CircleShape)
                .clickable(enabled = !isUploading, onClick = onChangePhoto),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoURL.isNotBlank()) {
                AsyncImage(
                    model = user.photoURL,
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(initial, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = avatarColor)
            }
            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
        IconButton(
            onClick = onChangePhoto,
            enabled = !isUploading,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(avatarColor)
                .border(2.dp, MaterialTheme.colorScheme.background, androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Cambiar foto de perfil",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
