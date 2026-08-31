package com.ronaldcolocho.taskly.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ronaldcolocho.taskly.ui.state.ProfileUiState

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ProfileUiState.Loading -> {
            CircularProgressIndicator()
        }
        is ProfileUiState.Error -> {
            Text(text = state.message, color = MaterialTheme.colorScheme.error)
        }
        is ProfileUiState.Success -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Perfil",
                    style = MaterialTheme.typography.titleLarge
                )
                
                OutlinedTextField(
                    value = state.user.displayName,
                    onValueChange = {},
                    label = { Text("Nombre") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = state.user.phone,
                    onValueChange = {},
                    label = { Text("Teléfono") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.logout(onSuccess = onLogout) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar Sesión")
                }
            }
        }
    }
}
