package com.example.pmmovil.ui.settings.nfc

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.nfc.NfcBadgeManager
import com.example.pmmovil.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NfcAssocState {
    data object Intro : NfcAssocState
    data object FetchingPayload : NfcAssocState
    data object Waiting : NfcAssocState
    data object Reading : NfcAssocState
    data class Confirmed(val uid: String, val tech: String, val alreadyWas: Boolean) : NfcAssocState
    data class Conflict(val uid: String) : NfcAssocState
    data class ErrorState(val message: String) : NfcAssocState
}

@HiltViewModel
class NfcAssociationViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<NfcAssocState>(NfcAssocState.Intro)
    val state: StateFlow<NfcAssocState> = _state.asStateFlow()

    // El payload de este usuario se pide una sola vez por sesión de
    // asociación y se guarda aquí -- no en el StateFlow -- porque nunca debe
    // pintarse en pantalla, solo escribirse a la etiqueta.
    private var cachedPayload: String? = null
    private var allowOverwrite = false

    fun hasExistingAssociation(): Boolean = authRepository.getNfcAssociationInfo() != null

    fun startAssociation() {
        _state.value = NfcAssocState.FetchingPayload
        viewModelScope.launch {
            authRepository.getMiGafete()
                .onSuccess { payload ->
                    cachedPayload = payload
                    allowOverwrite = false
                    _state.value = NfcAssocState.Waiting
                }
                .onFailure { error ->
                    _state.value = NfcAssocState.ErrorState(
                        error.message ?: "No se pudo preparar tu gafete."
                    )
                }
        }
    }

    fun cancel() {
        cachedPayload = null
        allowOverwrite = false
        _state.value = NfcAssocState.Intro
    }

    /** El usuario confirmó que quiere sobrescribir una etiqueta con datos ajenos. */
    fun confirmOverwrite() {
        allowOverwrite = true
        _state.value = NfcAssocState.Waiting
    }

    fun retryAfterError() {
        _state.value = if (cachedPayload != null) NfcAssocState.Waiting else NfcAssocState.Intro
    }

    fun finish() {
        cachedPayload = null
        allowOverwrite = false
        _state.value = NfcAssocState.Intro
    }

    /**
     * Callback de NfcAdapter.ReaderCallback -- llega en un hilo de binder, no
     * el principal. Por eso todo el trabajo real vive en un launch propio en
     * vez de ejecutarse inline.
     */
    fun onTagDiscovered(tag: Tag) {
        if (_state.value != NfcAssocState.Waiting) return
        val payload = cachedPayload ?: return
        _state.value = NfcAssocState.Reading

        viewModelScope.launch(Dispatchers.IO) {
            val uid = NfcBadgeManager.tagUid(tag)
            val tech = NfcBadgeManager.tagTech(tag)
            val existing = NfcBadgeManager.readText(tag)

            if (existing == payload) {
                authRepository.markNfcAssociated(uid)
                _state.value = NfcAssocState.Confirmed(uid, tech, alreadyWas = true)
                return@launch
            }

            // Es la MISMA etiqueta física que ya asociamos antes en este
            // teléfono (mismo UID), solo que su contenido ya no coincide --
            // lo normal cuando cambia la contraseña o se reemite el gafete
            // QR. No tiene sentido advertirle "puede ser de otra persona"
            // sobre su propio gafete: se actualiza directo.
            val isOwnPreviousTag = existing != null && uid == authRepository.getNfcAssociationInfo()?.uid

            if (!existing.isNullOrBlank() && !allowOverwrite && !isOwnPreviousTag) {
                _state.value = NfcAssocState.Conflict(uid)
                return@launch
            }

            when (val result = NfcBadgeManager.writeText(tag, payload)) {
                NfcBadgeManager.WriteResult.Success -> {
                    allowOverwrite = false
                    authRepository.markNfcAssociated(uid)
                    _state.value = NfcAssocState.Confirmed(uid, tech, alreadyWas = false)
                }
                NfcBadgeManager.WriteResult.ReadOnly ->
                    _state.value = NfcAssocState.ErrorState("Esta etiqueta es de solo lectura, no se puede asociar.")
                NfcBadgeManager.WriteResult.TooSmall ->
                    _state.value = NfcAssocState.ErrorState("La etiqueta no tiene espacio suficiente para el gafete.")
                NfcBadgeManager.WriteResult.Unsupported ->
                    _state.value = NfcAssocState.ErrorState("Esta etiqueta no es compatible (no soporta NDEF).")
                is NfcBadgeManager.WriteResult.Failed ->
                    _state.value = NfcAssocState.ErrorState("El gafete se movió antes de terminar. Vuelve a intentarlo.")
            }
        }
    }
}
