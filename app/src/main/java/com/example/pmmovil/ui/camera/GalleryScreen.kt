package com.example.pmmovil.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmYellow

/**
 * Galería de evidencia de una factura, entrada directa desde la pantalla de
 * Revisión -- sin pasar por la cámara. Reutiliza [CameraViewModel] (misma
 * lógica de carga/borrado) pero nunca pide permiso de cámara ni enlaza
 * CameraX: solo muestra lo ya tomado.
 */
@Composable
fun GalleryScreen(
    crefAgrupada: String,
    cnofact: String,
    cclienteid: String,
    onClose: () -> Unit,
    onTakePhotos: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localPhotos = uiState.localPhotos.filter { localPhoto ->
        localPhoto.remotePhotoId == null ||
            uiState.remotePhotos.none { it.idFotos == localPhoto.remotePhotoId }
    }
    val allPhotos: List<EvidenceSelection> =
        localPhotos.map(EvidenceSelection::Local) +
            uiState.remotePhotos.map(EvidenceSelection::Remote)
    val canAddPhotos = viewModel.canAddPhoto()
    val canDeletePhotos = viewModel.canDeletePhoto()

    val snackbarHostState = remember { SnackbarHostState() }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var evidenceToDelete by remember { mutableStateOf<EvidenceSelection?>(null) }
    var deleteRequested by remember { mutableStateOf(false) }

    LaunchedEffect(crefAgrupada, cnofact) {
        viewModel.start(crefAgrupada, cnofact)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(evidenceToDelete) { deleteRequested = false }
    LaunchedEffect(uiState.deletingKeys) {
        val selection = evidenceToDelete ?: return@LaunchedEffect
        if (deleteRequested && selection.key() !in uiState.deletingKeys) {
            evidenceToDelete = null
            val stillExists = allPhotos.any { it.key() == selection.key() }
            if (!stillExists) viewerIndex = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PhotoGridScreen(
            photos = allPhotos,
            isLoading = uiState.isLoading,
            deletingKeys = uiState.deletingKeys,
            errorKeys = uiState.errorKeys,
            onBack = onClose,
            onOpenPhoto = { selection ->
                viewerIndex = allPhotos.indexOfFirst { it.key() == selection.key() }
                    .takeIf { it >= 0 }
            },
            onDelete = { evidenceToDelete = it },
            canDelete = canDeletePhotos,
            modifier = Modifier.fillMaxSize()
        )

        if (canAddPhotos) {
            ExtendedFloatingActionButton(
                onClick = onTakePhotos,
                containerColor = PmmYellow,
                contentColor = PmmBlueDeep,
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                text = { Text("Tomar fotos", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
        )
    }

    viewerIndex?.let { index ->
        if (allPhotos.isNotEmpty()) {
            EvidenceViewerPager(
                photos = allPhotos,
                initialIndex = index.coerceIn(0, allPhotos.lastIndex),
                deletingKeys = uiState.deletingKeys,
                onClose = { viewerIndex = null },
                onDelete = { evidenceToDelete = it },
                canDelete = canDeletePhotos
            )
        }
    }

    evidenceToDelete?.let { selection ->
        val isDeleting = deleteRequested && selection.key() in uiState.deletingKeys
        val hasFailed = selection.key() in uiState.errorKeys
        EvidenceDeleteDialog(
            isDeleting = isDeleting,
            hasFailed = hasFailed,
            onConfirmDelete = {
                deleteRequested = true
                when (selection) {
                    is EvidenceSelection.Local -> {
                        viewModel.deleteLocalPhoto(
                            photo = selection.photo,
                            crefAgrupada = crefAgrupada,
                            cnofact = cnofact
                        )
                    }

                    is EvidenceSelection.Remote -> {
                        viewModel.deleteRemotePhoto(
                            photoId = selection.photo.idFotos,
                            crefAgrupada = crefAgrupada,
                            cnofact = cnofact
                        )
                    }
                }
            },
            onDismiss = { evidenceToDelete = null }
        )
    }
}
