package com.recipeextract.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipeextract.app.data.local.UrlHistoryEntity
import com.recipeextract.app.data.models.ExtractionResult
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.data.models.UiState
import com.recipeextract.app.data.repository.ApiRepository
import com.recipeextract.app.data.repository.RecipeRepository
import com.recipeextract.app.utils.UrlValidationResult
import com.recipeextract.app.utils.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apiRepository: ApiRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _extractionState = MutableStateFlow<UiState<Recipe>>(UiState.Idle)
    val extractionState: StateFlow<UiState<Recipe>> = _extractionState.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val recentUrls: StateFlow<List<UrlHistoryEntity>> = recipeRepository
        .getRecentUrls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onUrlChanged(url: String) {
        _urlInput.value = url
        _validationError.value = null
    }

    fun selectRecentUrl(url: String) {
        _urlInput.value = url
        _validationError.value = null
    }

    fun extractRecipe() {
        val url = _urlInput.value.trim()
        when (val validation = UrlValidator.validate(url)) {
            is UrlValidationResult.Invalid -> {
                _validationError.value = validation.message
                return
            }
            is UrlValidationResult.Valid -> performExtraction(validation.normalizedUrl)
        }
    }

    fun pasteUrl(url: String) {
        _urlInput.value = url.trim()
        _validationError.value = null
    }

    fun clearError() {
        _validationError.value = null
        _statusMessage.value = null
        if (_extractionState.value is UiState.Error) {
            _extractionState.value = UiState.Idle
        }
    }

    fun resetExtractionState() {
        _extractionState.value = UiState.Idle
    }

    private fun performExtraction(url: String) {
        viewModelScope.launch {
            _extractionState.value = UiState.Loading
            _validationError.value = null
            _statusMessage.value = "Extracting recipe… (please wait)"

            when (val result = apiRepository.extractRecipeFromUrl(url)) {
                is ExtractionResult.Success -> {
                    recipeRepository.addUrlToHistory(url, result.recipe.title)
                    if (result.fromCache) {
                        _statusMessage.value = "Using saved recipe"
                    } else {
                        _statusMessage.value = null
                    }
                    _extractionState.value = UiState.Success(result.recipe)
                }
                is ExtractionResult.Error -> {
                    _statusMessage.value = null
                    _extractionState.value = UiState.Error(result.message)
                }
            }
        }
    }
}
