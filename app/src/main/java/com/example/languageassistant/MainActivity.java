package com.example.languageassistant;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

// Vuzix SDK related imports will be handled by UltraliteSDKUtils, remove direct ones if no longer used
// import com.vuzix.ultralite.sdk.EventListener; 
// import com.vuzix.ultralite.Constants; 
// import static com.vuzix.ultralite.sdk.LinkStatusListener.LINK_STATUS_DISCONNECTED; 
// import com.vuzix.ultralite.UltraliteSDK;
// import com.vuzix.ultralite.Layout; 
// import com.vuzix.ultralite.utils.scroll.LiveText;
import com.example.languageassistant.utils.GeminiUtils; // Import the new utility class
import com.example.languageassistant.utils.UltraliteSDKUtils; // Import the new SDK utility class
import com.example.languageassistant.utils.TranslationUtils; // Import the new Translation utility class

// Remove direct ML Kit Translation imports if they are no longer used directly in MainActivity
// import com.google.mlkit.common.model.DownloadConditions;
// import com.google.mlkit.nl.translate.TranslateLanguage;
// import com.google.mlkit.nl.translate.Translation;
// import com.google.mlkit.nl.translate.Translator;
// import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LanguageAssistantApp";
    // REQUEST_CONTROL_TIMEOUT_MS moved to UltraliteSDKUtils
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // private UltraliteSDK ultraliteSDK; // Moved to UltraliteSDKUtils

    // UI Elements
    private Spinner spinnerFromLanguage;
    private Spinner spinnerToLanguage;
    private Spinner spinnerDisplayLanguage;
    private EditText editTextScrollingSpeed;
    private Button buttonRun;
    private Button buttonStop;
    private Button buttonAnswer;

    // LiveData for SDK status are now in UltraliteSDKUtils
    // private MutableLiveData<Boolean> isSdkAvailable = new MutableLiveData<>(false);
    // private MutableLiveData<Boolean> isSdkControlled = new MutableLiveData<>(false);

    // Speech Recognition
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private enum ListeningMode { TRANSLATE, ANSWER_QUESTION }
    private ListeningMode currentListeningMode = ListeningMode.TRANSLATE;

    // Gemini AI related variables moved to GeminiUtils.java

    // Language codes - will be populated from string arrays
    private String[] languageDisplayNames;
    private String[] languageCodes;
    private String[] displayLanguageDisplayNames;
    private String[] displayLanguageCodes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        spinnerFromLanguage = findViewById(R.id.spinner_from_language);
        spinnerToLanguage = findViewById(R.id.spinner_to_language);
        spinnerDisplayLanguage = findViewById(R.id.spinner_display_language);
        editTextScrollingSpeed = findViewById(R.id.edittext_scrolling_speed);
        buttonRun = findViewById(R.id.button_run);
        buttonStop = findViewById(R.id.button_stop);
        buttonAnswer = findViewById(R.id.button_answer);

        // Load language arrays
        languageDisplayNames = getResources().getStringArray(R.array.languages_array_display_names);
        languageCodes = getResources().getStringArray(R.array.languages_array_codes);
        displayLanguageDisplayNames = getResources().getStringArray(R.array.display_languages_array_display_names);
        displayLanguageCodes = getResources().getStringArray(R.array.display_languages_array_codes);

        // Populate Spinners
        ArrayAdapter<String> fromAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languageDisplayNames);
        spinnerFromLanguage.setAdapter(fromAdapter);

        ArrayAdapter<String> toAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languageDisplayNames);
        spinnerToLanguage.setAdapter(toAdapter);

        ArrayAdapter<String> displayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, displayLanguageDisplayNames);
        spinnerDisplayLanguage.setAdapter(displayAdapter);


        // Initially disable buttons that require SDK control or are state-dependent
        buttonRun.setEnabled(false); 
        buttonStop.setEnabled(false); 
        buttonAnswer.setEnabled(false); 

        // Initialize UltraliteSDKUtils
        UltraliteSDKUtils.init(getApplicationContext());

        // Observe SDK availability from UltraliteSDKUtils
        UltraliteSDKUtils.isSdkAvailable.observe(this, available -> {
            // This direct call to requestSdkControl might be handled within UltraliteSDKUtils.init
            // Or if MainActivity needs to react directly:
            if (available) {
                Log.i(TAG, "Ultralite SDK is available via Utils.");
                UltraliteSDKUtils.requestSdkControl(); 
            } else {
                Log.i(TAG, "Ultralite SDK is not available via Utils.");
            }
        });

        // Observe SDK control status from UltraliteSDKUtils
        UltraliteSDKUtils.isSdkControlled.observe(this, controlled -> {
            buttonRun.setEnabled(controlled);
            buttonAnswer.setEnabled(controlled);
            if (!controlled) {
                if (isListening) {
                    stopListening(); // This local method might need adjustment if it interacts with SDK state
                }
                buttonStop.setEnabled(false);
                // handleControlLost() is now in UltraliteSDKUtils and called by its EventListener
                // If MainActivity specific UI updates are needed, they can be triggered here
                // or by observing a different LiveData from UltraliteSDKUtils.
            } else {
                // handleControlGained() is now in UltraliteSDKUtils and called by its EventListener
            }
        });
        
        // Register event listener from UltraliteSDKUtils
        UltraliteSDKUtils.addEventListener();


        // Initialize Speech Recognizer
        initializeSpeechRecognizer();

        // Set up button listeners
        buttonRun.setOnClickListener(v -> {
            currentListeningMode = ListeningMode.TRANSLATE;
            startListeningFlow();
        });
        buttonStop.setOnClickListener(v -> stopListening());
        buttonAnswer.setOnClickListener(v -> {
            currentListeningMode = ListeningMode.ANSWER_QUESTION;
            startListeningFlow();
        });
    }

    private void initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available on this device.");
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show();
            buttonRun.setEnabled(false); 
            buttonAnswer.setEnabled(false);
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false); 

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "SpeechRecognizer: Ready for speech (" + currentListeningMode + ")");
                Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer: Beginning of speech");
            }

            @Override
            public void onRmsChanged(float rmsdB) { /* Not needed for now */ }

            @Override
            public void onBufferReceived(byte[] buffer) { /* Not needed for now */ }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer: End of speech");
                // isListening state and button states are handled in onResults/onError
            }

            @Override
            public void onError(int error) {
                String errorMessage = getSpeechErrorMessage(error);
                Log.e(TAG, "SpeechRecognizer Error: " + errorMessage);
                Toast.makeText(MainActivity.this, "Speech Error: " + errorMessage, Toast.LENGTH_LONG).show();
                resetListeningState();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.i(TAG, "SpeechRecognizer Result: " + recognizedText);
                   
                    if (currentListeningMode == ListeningMode.TRANSLATE) {
                        processSpeechResultForTranslation(recognizedText);
                    } else if (currentListeningMode == ListeningMode.ANSWER_QUESTION) {
                        processSpeechResultForAnswer(recognizedText);
                    }
                } else {
                    Log.w(TAG, "SpeechRecognizer: No speech recognized.");
                    Toast.makeText(MainActivity.this, "No speech recognized.", Toast.LENGTH_SHORT).show();
                }
                resetListeningState();
            }

            @Override
            public void onPartialResults(Bundle partialResults) { /* Not needed for now */ }

            @Override
            public void onEvent(int eventType, Bundle params) { /* Not needed for now */ }
        });
    }
    
    private void resetListeningState() {
        isListening = false;
        // Use isSdkControlled from UltraliteSDKUtils
        boolean sdkControlled = Boolean.TRUE.equals(UltraliteSDKUtils.isSdkControlled.getValue());
        buttonRun.setEnabled(sdkControlled);
        buttonAnswer.setEnabled(sdkControlled);
        buttonStop.setEnabled(false);
    }


    private void startListeningFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startListening();
        }
    }

    private void startListening() {
        // Use isSdkControlled from UltraliteSDKUtils
        if (!isListening && Boolean.TRUE.equals(UltraliteSDKUtils.isSdkControlled.getValue())) {
            String fromLanguageTag = languageCodes[spinnerFromLanguage.getSelectedItemPosition()];
            // For "Answer" mode, we assume the question is asked in the "From Language".
            // If AI answers are always in English, this setup is fine.
            // If AI answers in the "From Language", then that's the language to listen in.
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, fromLanguageTag);
            Log.d(TAG, "Starting listening in language: " + fromLanguageTag + " for mode: " + currentListeningMode);

            speechRecognizer.startListening(speechRecognizerIntent);
            isListening = true;
            buttonRun.setEnabled(false);
            buttonAnswer.setEnabled(false);
            buttonStop.setEnabled(true);
        } else if (!Boolean.TRUE.equals(UltraliteSDKUtils.isSdkControlled.getValue())) {
            Toast.makeText(this, "Vuzix glasses not controlled. Cannot start listening.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening() {
        if (isListening) {
            speechRecognizer.stopListening(); // This will trigger onEndOfSpeech and then onResults/onError
            Log.d(TAG, "Stopped listening manually.");
        }
       // resetListeningState() will be called by onResults or onError
    }

    private void processSpeechResultForTranslation(String recognizedText) {
        String fromLanguageCode = languageCodes[spinnerFromLanguage.getSelectedItemPosition()];
        String toLanguageCode = languageCodes[spinnerToLanguage.getSelectedItemPosition()];
        String displayLanguageCode = displayLanguageCodes[spinnerDisplayLanguage.getSelectedItemPosition()];

        Log.d(TAG, "Processing for Translation: '" + recognizedText + "' from " + fromLanguageCode + " to " + toLanguageCode + ", display as " + displayLanguageCode);
        Toast.makeText(MainActivity.this, "Recognized: " + recognizedText, Toast.LENGTH_SHORT).show();


        /* // Example of using TranslationUtils:
        TranslationUtils.translateText(this, recognizedText, fromLanguageCode, toLanguageCode, new TranslationUtils.TranslationCallback() {
            @Override
            public void onSuccess(String translatedText) {
                Log.i(TAG, "Translated text (" + toLanguageCode + "): " + translatedText);
                Toast.makeText(MainActivity.this, "Translated: " + translatedText, Toast.LENGTH_SHORT).show();

                TranslationUtils.transliterateForDisplay(MainActivity.this, translatedText, displayLanguageCode, new TranslationUtils.TranslationCallback() {
                    @Override
                    public void onSuccess(String finalDisplayText) {
                        Log.i(TAG, "Final text for display (" + displayLanguageCode + " script): " + finalDisplayText);
                        Toast.makeText(MainActivity.this, "Display: " + finalDisplayText, Toast.LENGTH_LONG).show();
                        UltraliteSDKUtils.displayTextOnGlasses(finalDisplayText, editTextScrollingSpeed);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Transliteration failed for translation: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Could not prepare text for glasses.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Translation failed: " + e.getMessage());
                 Toast.makeText(MainActivity.this, "Translation failed.", Toast.LENGTH_SHORT).show();
            }
        });
        */
    }


    private void processSpeechResultForAnswer(String recognizedText) {
        String displayLanguageCode = displayLanguageCodes[spinnerDisplayLanguage.getSelectedItemPosition()];
        Log.d(TAG, "Processing for Answer: '" + recognizedText + "', display answers in " + displayLanguageCode + " script.");
        Toast.makeText(MainActivity.this, "Question: " + recognizedText, Toast.LENGTH_SHORT).show();

        // Call Gemini AI using GeminiUtils
        GeminiUtils.getAiAnswers(recognizedText, new GeminiUtils.AiAnswerCallback() {
            @Override
            public void onAnswerReceived(String answer) {
                if (answer.isEmpty()) {
                    Log.w(TAG, "Gemini returned no answer.");
                    Toast.makeText(MainActivity.this, "No AI answer found.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // AI answers are assumed to be in English. Transliterate them to the selected display script.
                TranslationUtils.transliterateForDisplay(MainActivity.this, answer, displayLanguageCode, new TranslationUtils.TranslationCallback() {
                    @Override
                    public void onSuccess(String transliteratedText) {
                        Log.i(TAG, "Final transliterated AI answer: " + transliteratedText);
                        // Call displayTextOnGlasses from UltraliteSDKUtils, passing the EditText
                        UltraliteSDKUtils.displayTextOnGlasses(transliteratedText, editTextScrollingSpeed);
                        Toast.makeText(MainActivity.this, "AI Answer (on glasses): " + transliteratedText, Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Transliteration failed for an AI answer: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Could not prepare AI answer for glasses.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, new GeminiUtils.AiErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Gemini AI Error: " + errorMessage);
                Toast.makeText(MainActivity.this, "AI Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // displayTextOnGlasses, clearGlassesDisplay, chunkStringsToEngine moved to UltraliteSDKUtils

    // translateText, transliterateForDisplay, and TranslationCallback moved to TranslationUtils.java

    // initializeGeminiModel, getAiAnswers, AiAnswerCallback, AiErrorCallback moved to GeminiUtils.java

    // requestSdkControl, eventListener, handleControlGained, handleControlLost are in UltraliteSDKUtils

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "RECORD_AUDIO permission granted.");
                startListening(); 
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied.");
                Toast.makeText(this, "Audio recording permission is required to use speech input.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getSpeechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Error from server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown speech recognition error";
        }
    }

    // requestSdkControl, eventListener, handleControlGained, handleControlLost are now in UltraliteSDKUtils

    @Override
    protected void onResume() {
        super.onResume();
        // Logic related to SDK availability and control is now managed by UltraliteSDKUtils observers
        // and its init method. MainActivity's onResume might not need to do much for SDK state.
        // If there's a need to explicitly check/request control onResume, that can be added:
        // UltraliteSDKUtils.requestSdkControl(); 

        // Gemini initialization might still be relevant here if it depends on Activity lifecycle
        // and not just SDK control.
        if (Boolean.TRUE.equals(UltraliteSDKUtils.isSdkControlled.getValue())) {
             GeminiUtils.initializeGeminiModel(GeminiUtils.GEMINI_API_KEY);
             if (GeminiUtils.geminiModel == null) {
                Toast.makeText(this, "Gemini AI Model not initialized during onResume. Check API Key in GeminiUtils.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove event listener and release control using UltraliteSDKUtils
        UltraliteSDKUtils.removeEventListener();
        UltraliteSDKUtils.releaseControl();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            Log.d(TAG, "SpeechRecognizer destroyed.");
        }
    }
}
