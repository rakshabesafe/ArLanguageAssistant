# Vuzix Z100 Language Assistant

## Overview

The Vuzix Z100 Language Assistant is an Android application designed to run on Vuzix Z100 Smart Glasses. It provides real-time language translation and AI-powered answers directly in the user's field of view. The primary purpose of this application is to assist users in multilingual conversations by translating spoken language and to offer quick, AI-generated answers to user questions, all hands-free via the Vuzix Z100 interface.

## Features

*   **Real-time Voice Translation:** Captures user's voice input and translates it from a selected source language to a target language.
*   **Flexible Language Selection:** Allows users to choose:
    *   "From Language": The language the user will speak in.
    *   "To Language": The language the input should be translated to.
    *   "Display Language": The script/language in which the final text will be displayed (e.g., display Kannada text in English/Latin script).
*   **Transliteration Support:** (Placeholder) Includes a feature to transliterate the translated text into the script of the chosen "Display Language." *Currently, this is a placeholder and returns the translated text without actual script conversion.*
*   **Vuzix Z100 Display:** Shows translated text and AI answers as scrolling text on the Vuzix Z100 glasses display.
*   **Adjustable Scrolling Speed:** Users can input a numerical value to control the scrolling speed of the text on the glasses.
*   **AI-Powered Answers:** Users can ask questions, and the application will use Google's Gemini AI to generate a few probable concise answers, which are then displayed as scrolling text.
*   **Stop Functionality:** A "Stop" button allows users to halt the current operation (voice listening, text display).

## Requirements

*   **Android Device:** An Android phone or compatible device running Android 12 (API Level 31) or later.
*   **Vuzix Z100 Smart Glasses:** The application is specifically designed for these AR glasses.
*   **Vuzix Connect App:** Must be installed on the Android device to facilitate communication with the Z100 glasses.
*   **Internet Connection:** Required for:
    *   Downloading ML Kit translation models.
    *   Performing translations.
    *   Accessing the Google AI (Gemini) for answers.
*   **Gemini AI API Key:**
    *   A valid API key for the Google AI Generative Language API (Gemini) is required for the "Answer" feature.
    *   **Security Note:** The current implementation uses a hardcoded API key in `MainActivity.java` (`GEMINI_API_KEY`). **This is NOT secure for production.** For a real application, this key should be stored securely, for example, in `local.properties`, fetched from a secure server, or by using Android Keystore. The placeholder value "YOUR_API_KEY_HERE" must be replaced.

## Setup and Installation

1.  **Clone the Repository:**
    ```bash
    git clone <repository_url>
    ```
2.  **Open in Android Studio:** Open the cloned project in Android Studio (latest stable version recommended).
3.  **Configure Gemini API Key (Crucial for AI Answers):**
    *   Open `app/src/main/java/com/example/languageassistant/MainActivity.java`.
    *   Locate the `GEMINI_API_KEY` constant.
    *   Replace `"YOUR_API_KEY_HERE"` with your actual Gemini API key.
    *   **Remember the security warning above for production apps.**
4.  **Build the APK:**
    *   Wait for Android Studio to sync Gradle dependencies. The Vuzix Ultralite SDK is included via JitPack and should be resolved automatically.
    *   From the menu, select "Build" > "Build Bundle(s) / APK(s)" > "Build APK(s)".
5.  **Install the APK:**
    *   Once the build is complete, locate the APK (usually in `app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`).
    *   Install the APK on your Android device connected to the Vuzix Z100 glasses. Ensure the Vuzix Connect app is set up and connected to the glasses.

## How to Use

1.  **Launch the Application:** Open the "Language Assistant" app on your Android device. The UI will appear on the device screen, and output will be sent to the Vuzix Z100 glasses.
2.  **Ensure Glasses are Connected:** Verify that the Vuzix Z100 glasses are connected to your phone via the Vuzix Connect app. The app will show "Vuzix Glasses Connected" or "Disconnected" Toasts. Buttons for "Run" and "Answer" will be enabled only when glasses are connected and controlled.
3.  **Language Selection:**
    *   **From Language Spinner:** Select the language you will be speaking.
    *   **To Language Spinner:** Select the language you want your speech translated into.
    *   **Display Language Spinner:** Select the language/script for the final text displayed on the glasses. (Note: Transliteration is currently a placeholder).
4.  **Scrolling Speed:**
    *   **Scrolling Speed EditText:** Enter a numerical value (e.g., 1.0, 1.5, 2.0) to control how fast the text scrolls on the glasses. Higher numbers mean faster scrolling. Defaults to 1.0 if invalid.
5.  **Performing Translation:**
    *   Click the **"Run"** button.
    *   The app will start listening for voice input ("Listening..." Toast).
    *   Speak clearly into your Android device's microphone.
    *   The recognized text will be shown via a Toast.
    *   The translated text (after placeholder transliteration) will be sent to the Vuzix Z100 glasses as scrolling text.
6.  **Getting AI Answers:**
    *   Click the **"Answer"** button.
    *   The app will start listening ("Listening..." Toast).
    *   Ask a question clearly into your Android device's microphone.
    *   The recognized question will be shown via a Toast.
    *   The AI-generated answers (after placeholder transliteration, typically the first answer) will be sent to the Vuzix Z100 glasses as scrolling text.
7.  **Stopping an Operation:**
    *   Click the **"Stop"** button to:
        *   Stop active voice listening.
        *   Clear any text currently displayed on the Vuzix Z100 glasses.

## Dependencies

*   **Vuzix Ultralite SDK:** For communication and display control on Vuzix Z100 glasses. Integrated via JitPack.
*   **Google ML Kit Translate API:** For on-device and cloud-based text translation.
*   **Google AI Generative Language API (Gemini):** For generating AI-powered answers.

## Known Issues/Limitations

*   **Transliteration is a Placeholder:** The feature to convert translated text into different scripts (e.g., Kannada text to Latin script) is currently a placeholder. The `transliterateForDisplay()` function returns the translated text as-is without actual script conversion. This means the "Display Language" spinner primarily acts as an indicator for future implementation.
*   **AI Answer Display:** Currently, only the first AI-generated answer is typically sent to the glasses. The Gemini API might return multiple answers, and the parsing logic is basic.
*   **Scrolling Speed Application:** The exact effect of the "Scrolling Speed" value depends on the Vuzix Ultralite SDK's interpretation for `LAYOUT_ID_SCROLL`. Fine-tuning or different API calls might be needed for precise speed control.
*   **Error Handling:** While basic error handling is in place (Toasts, logs), it can be further improved for a more robust user experience.
*   **API Key Security:** The Gemini AI API key is hardcoded, which is insecure for production. This needs to be addressed for any real-world deployment.
*   **Network Dependency:** Translation model downloads, translations, and AI answers require an active internet connection. The app may not function fully offline for these features.
*   **Speech Recognition Language:** The speech recognizer's language is set based on the "From Language". Accuracy may vary depending on the selected language and Android's support for it.
