# RecipeExtract

Android app that extracts structured recipes from YouTube videos and blog URLs using Qwen AI.

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Qwen API key

## Setup

1. Open the project in Android Studio.
2. Copy `local.properties.example` to `local.properties` (if needed) and set:
   - `sdk.dir` — path to your Android SDK
   - `QWEN_API_KEY` — your Qwen API key from [Qwen Console](https://home.qwencloud.com/api-keys)
3. Sync Gradle and run on a device or emulator (API 26+).

## Architecture

- **UI:** Jetpack Compose + Material 3 + Navigation Compose
- **Pattern:** MVVM with Hilt dependency injection
- **Data:** Room (saved recipes + URL history), Retrofit/OkHttp (Qwen API), jsoup (HTML parsing)
- **Async:** Kotlin Coroutines + Flow

## Project Structure

```
app/src/main/java/com/recipeextract/app/
├── ui/screens/          Home, RecipeDetail, SavedRecipes
├── ui/components/       RecipeCard, SkeletonLoader
├── ui/theme/            Material 3 theming (light/dark)
├── data/models/         Recipe, API DTOs
├── data/local/          Room database, DAOs, entities
├── data/remote/         QwenApiService, RecipeApiService
├── data/repository/     ApiRepository, RecipeRepository
├── viewmodel/           HomeViewModel, RecipeDetailViewModel, SavedRecipesViewModel
├── utils/               UrlValidator, HtmlParser, Constants
└── di/                  Hilt modules
```

## Features

- URL validation for YouTube, blogs, and recipe sites
- AI-powered recipe extraction via Qwen
- Recent URL history (max 20, stored locally)
- Save favorites with offline access
- Search saved recipes by name or ingredient
- Copy ingredients/instructions to clipboard
- Share recipes as formatted text
- Dark mode support (system + dynamic color on Android 12+)
- Swipe-to-delete on saved recipes

## Testing

```bash
./gradlew test
```

Unit tests cover `UrlValidator`, `HomeViewModel`, `ApiRepository`, and Room integration (Robolectric).

## Release Build

```bash
./gradlew assembleRelease
```

Release builds use R8 minification with ProGuard rules for Retrofit, Gson, Room, and jsoup.

## Notes

- Network requests timeout after 30 seconds.
- YouTube transcript extraction depends on caption availability in the page source.
- Keep your API key in `local.properties` — never commit it to version control.
