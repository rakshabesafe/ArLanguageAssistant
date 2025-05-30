package com.example.languageassistant.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Arrays;

public class TranslationUtils {

    private static final String TAG = "TranslationUtils";

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onFailure(Exception e);
    }

    public static void translateText(Context context, String text, String fromLanguage, String toLanguage, final TranslationCallback callback) {
        if (fromLanguage.equals(toLanguage)) {
            Log.d(TAG, "Source and target languages are the same. No translation needed.");
            callback.onSuccess(text);
            return;
        }

        if (!Arrays.asList(TranslateLanguage.getAllLanguages()).contains(fromLanguage) ||
            !Arrays.asList(TranslateLanguage.getAllLanguages()).contains(toLanguage)) {
            String errorMsg = "Unsupported language for translation: " + fromLanguage + " or " + toLanguage;
            Log.e(TAG, errorMsg);
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            callback.onFailure(new Exception(errorMsg));
            return;
        }

        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(fromLanguage)
                        .setTargetLanguage(toLanguage)
                        .build();
        final Translator translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Language model for " + fromLanguage + "->" + toLanguage + " downloaded or already available.");
                    translator.translate(text)
                            .addOnSuccessListener(callback::onSuccess)
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "ML Kit Translation failed: " + e.getMessage());
                                Toast.makeText(context, "Translation Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                callback.onFailure(e);
                            })
                            .addOnCompleteListener(task -> translator.close());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ML Kit Model download failed: " + e.getMessage());
                    Toast.makeText(context, "Model Download Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    callback.onFailure(e);
                    translator.close();
                });
    }

    public static void transliterateForDisplay(Context context, String text, String targetScriptLanguageCode, final TranslationCallback callback) {
        // This is a VERY rudimentary placeholder for transliteration.
        // A proper solution would require a comprehensive transliteration engine, library, or API.
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "Input text for transliteration is empty. Returning original text.");
            callback.onSuccess(text);
            return;
        }

        if ("en".equalsIgnoreCase(targetScriptLanguageCode)) {
            Log.d(TAG, "Attempting basic transliteration for target script: " + targetScriptLanguageCode);
            StringBuilder transliteratedText = new StringBuilder();
            for (char c : text.toCharArray()) {
                switch (c) {
                    // Simple Kannada vowel and consonant mappings (very incomplete)
                    case 'ಅ': transliteratedText.append("a"); break;
                    case 'ಆ': transliteratedText.append("aa"); break;
                    case 'ಇ': transliteratedText.append("i"); break;
                    case 'ಈ': transliteratedText.append("ii"); break;
                    case 'ಉ': transliteratedText.append("u"); break;
                    case 'ಊ': transliteratedText.append("uu"); break;
                    case 'ಕ': transliteratedText.append("ka"); break;
                    case 'ಖ': transliteratedText.append("kha"); break;
                    case 'ಗ': transliteratedText.append("ga"); break;
                    case 'ಘ': transliteratedText.append("gha"); break;
                    case 'ಚ': transliteratedText.append("cha"); break;
                    case 'ಛ': transliteratedText.append("chha"); break;
                    case 'ಜ': transliteratedText.append("ja"); break;
                    case 'ಝ': transliteratedText.append("jha"); break;
                    case 'ಟ': transliteratedText.append("ṭa"); break;
                    case 'ಠ': transliteratedText.append("ṭha"); break;
                    case 'ಡ': transliteratedText.append("ḍa"); break;
                    case 'ಢ': transliteratedText.append("ḍha"); break;
                    case 'ಣ': transliteratedText.append("ṇa"); break;
                    case 'ತ': transliteratedText.append("ta"); break;
                    case 'ಥ': transliteratedText.append("tha"); break;
                    case 'ದ': transliteratedText.append("da"); break;
                    case 'ಧ': transliteratedText.append("dha"); break;
                    case 'ನ': transliteratedText.append("na"); break;
                    case 'ಪ': transliteratedText.append("pa"); break;
                    case 'ಫ': transliteratedText.append("pha"); break;
                    case 'ಬ': transliteratedText.append("ba"); break;
                    case 'ಭ': transliteratedText.append("bha"); break;
                    case 'ಮ': transliteratedText.append("ma"); break;
                    case 'ಯ': transliteratedText.append("ya"); break;
                    case 'ರ': transliteratedText.append("ra"); break;
                    case 'ಲ': transliteratedText.append("la"); break;
                    case 'ವ': transliteratedText.append("va"); break;
                    case 'ಶ': transliteratedText.append("sha"); break;
                    case 'ಷ': transliteratedText.append("ṣa"); break;
                    case 'ಸ': transliteratedText.append("sa"); break;
                    case 'ಹ': transliteratedText.append("ha"); break;
                    case 'ಳ': transliteratedText.append("ḷa"); break;
                    case ' ': transliteratedText.append(" "); break;
                    case '.': transliteratedText.append(". "); break;
                    case ',': transliteratedText.append(", "); break;
                    default:
                        transliteratedText.append("?"); // Placeholder for unmapped chars
                        Log.w(TAG, "Unmapped character in basic transliteration: " + c);
                        break;
                }
            }
            Log.d(TAG, "Basic transliteration performed. Original: '" + text + "', Transliterated: '" + transliteratedText.toString() + "'");
            callback.onSuccess(transliteratedText.toString());
        } else {
            Log.w(TAG, "Transliteration for display language '" + targetScriptLanguageCode + "' is not implemented or not 'en'. Returning original text.");
            callback.onSuccess(text);
        }
    }
}
