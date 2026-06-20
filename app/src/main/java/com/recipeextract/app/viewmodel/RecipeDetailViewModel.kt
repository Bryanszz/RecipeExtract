package com.recipeextract.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId: Long = savedStateHandle.get<Long>("recipeId") ?: 0L

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    init {
        if (recipeId > 0) {
            loadSavedRecipe(recipeId)
        }
    }

    fun setRecipe(recipe: Recipe) {
        _recipe.value = recipe
        viewModelScope.launch {
            _isSaved.value = recipeRepository.isRecipeSaved(recipe.sourceUrl)
        }
    }

    private fun loadSavedRecipe(id: Long) {
        viewModelScope.launch {
            val loaded = recipeRepository.getRecipeById(id)
            _recipe.value = loaded
            _isSaved.value = loaded != null
        }
    }

    fun toggleSave() {
        val current = _recipe.value ?: return
        viewModelScope.launch {
            if (_isSaved.value) {
                recipeRepository.deleteRecipe(current.copy(id = recipeId.takeIf { it > 0 } ?: current.id))
                _isSaved.value = false
                _saveMessage.value = "Recipe removed from favorites"
            } else {
                val savedId = recipeRepository.saveRecipe(current)
                _recipe.value = current.copy(id = savedId, savedAt = System.currentTimeMillis())
                _isSaved.value = true
                _saveMessage.value = "Recipe saved to favorites"
            }
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}
