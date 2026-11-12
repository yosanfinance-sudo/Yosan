package com.example.features.walkthrough

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

object SpotlightTourManager {
    var activeStep by mutableStateOf<SpotlightStep?>(null)
    val targetBoundsMap = mutableStateMapOf<SpotlightStep, Rect>()

    fun startTour() {
        activeStep = SpotlightStep.IMPORT_STATEMENT
    }

    fun dismissTour() {
        activeStep = null
    }

    fun nextStep() {
        val current = activeStep ?: return
        val values = SpotlightStep.values()
        val nextIndex = current.ordinal + 1
        if (nextIndex < values.size) {
            activeStep = values[nextIndex]
        } else {
            activeStep = null
        }
    }

    fun previousStep() {
        val current = activeStep ?: return
        val values = SpotlightStep.values()
        val prevIndex = current.ordinal - 1
        if (prevIndex >= 0) {
            activeStep = values[prevIndex]
        }
    }
}
