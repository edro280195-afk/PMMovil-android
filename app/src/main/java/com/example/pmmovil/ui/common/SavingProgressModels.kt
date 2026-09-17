package com.example.pmmovil.ui.common

enum class SavingStepStatus { PENDING, ACTIVE, SUCCESS, ERROR }

data class SavingProgressStep(
    val id: String,
    val label: String,
    val detail: String,
    val status: SavingStepStatus = SavingStepStatus.PENDING
)

data class SavingProgressState(
    val isVisible: Boolean = false,
    val percent: Int = 0,
    val currentTitle: String = "Iniciando guardado...",
    val steps: List<SavingProgressStep> = emptyList(),
    val hasError: Boolean = false,
    val errorMessage: String = "",
    val isFinished: Boolean = false
)
