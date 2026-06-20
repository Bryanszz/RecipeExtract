package com.recipeextract.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.ui.screens.HomeScreen
import com.recipeextract.app.ui.screens.RecipeDetailScreen
import com.recipeextract.app.ui.screens.SavedRecipesScreen
import com.recipeextract.app.viewmodel.RecipeDetailViewModel

object Routes {
    const val HOME = "home"
    const val RECIPE_DETAIL = "recipe_detail"
    const val SAVED_RECIPES = "saved_recipes"
    const val SAVED_RECIPE_DETAIL = "saved_recipe_detail/{recipeId}"

    fun savedRecipeDetail(recipeId: Long) = "saved_recipe_detail/$recipeId"
}

@Composable
fun RecipeExtractNavHost() {
    val navController = rememberNavController()
    var extractedRecipe by remember { mutableStateOf<Recipe?>(null) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSaved = { navController.navigate(Routes.SAVED_RECIPES) },
                onRecipeExtracted = { recipe ->
                    extractedRecipe = recipe
                    navController.navigate(Routes.RECIPE_DETAIL)
                }
            )
        }

        composable(Routes.RECIPE_DETAIL) {
            extractedRecipe?.let { recipe ->
                RecipeDetailScreen(
                    recipe = recipe,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.SAVED_RECIPES) {
            SavedRecipesScreen(
                onNavigateBack = { navController.popBackStack() },
                onRecipeClick = { recipeId ->
                    navController.navigate(Routes.savedRecipeDetail(recipeId))
                }
            )
        }

        composable(
            route = Routes.SAVED_RECIPE_DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong("recipeId") ?: 0L
            SavedRecipeDetailRoute(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SavedRecipeDetailRoute(
    recipeId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()

    recipe?.let {
        RecipeDetailScreen(
            recipe = it,
            onNavigateBack = onNavigateBack,
            viewModel = viewModel
        )
    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
