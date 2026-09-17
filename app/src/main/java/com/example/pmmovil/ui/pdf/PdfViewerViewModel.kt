package com.example.pmmovil.ui.pdf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.api.PMMovilApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val api: PMMovilApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPdf(creferencia: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = withContext(Dispatchers.IO) { api.downloadPdf(creferencia) }
                if (response.isSuccessful && response.body() != null) {
                    val file = withContext(Dispatchers.IO) {
                        val tempFile = File(context.cacheDir, "temp_doc_${creferencia}.pdf")
                        val inputStream = response.body()!!.byteStream()
                        val outputStream = FileOutputStream(tempFile)
                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    }
                    _pdfFile.value = file
                } else {
                    _error.value = "Documento no encontrado o error en servidor"
                }
            } catch (e: Exception) {
                _error.value = "Error al descargar el PDF: ${e.message}"
            }
            _isLoading.value = false
        }
    }
}
