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

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
//import com.vuzix.ultralite.ConnectionListener;
import com.vuzix.ultralite.UltraliteSDK;
import com.vuzix.ultralite.Layout; // For Vuzix display layouts
import com.vuzix.ultralite.utils.scroll.LiveText;
//import com.vuzix.ultralite.Constants; // Assuming SCROLL_LAYOUT_ID is here
// If ScrollUtils is a class with static methods for creating scroll layouts:
// import com.vuzix.ultralite.utils.scroll.ScrollUtils; 

import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LanguageAssistantApp";
    private static final int REQUEST_CONTROL_TIMEOUT_MS = 10000; // 10 seconds
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private UltraliteSDK ultraliteSDK;

    // UI Elements
    private Spinner spinnerFromLanguage;
    private Spinner spinnerToLanguage;
    private Spinner spinnerDisplayLanguage;
    private EditText editTextScrollingSpeed;
    private Button buttonRun;
    private Button buttonStop;
    private Button buttonAnswer;

    // LiveData for SDK status
    private MutableLiveData<Boolean> isSdkAvailable = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isSdkControlled = new MutableLiveData<>(false);

    // Speech Recognition
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private enum ListeningMode { TRANSLATE, ANSWER_QUESTION }
    private ListeningMode currentListeningMode = ListeningMode.TRANSLATE;
    
    // Gemini AI
    // WARNING: DO NOT SHIP YOUR APP WITH THE API KEY HARDCODED LIKE THIS.
    // TODO: Secure the API Key using BuildConfig or a server-side solution.
    private static final String GEMINI_API_KEY = "YOUR_API_KEY_HERE";
    private com.google.ai.client.generativeai.GenerativeModel geminiModel;


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

        // Get UltraliteSDK instance
        ultraliteSDK = UltraliteSDK.get(this);

        // Observe SDK availability
        ultraliteSDK.getAvailable().observe(this, available -> {
            isSdkAvailable.setValue(available);
            if (available) {
                Log.i(TAG, "Ultralite SDK is available.");
                requestSdkControl();
            } else {
                Log.i(TAG, "Ultralite SDK is not available.");
                isSdkControlled.setValue(false);
            }
        });

        // Observe SDK control status
        isSdkControlled.observe(this, controlled -> {
            buttonRun.setEnabled(controlled);
            buttonAnswer.setEnabled(controlled); // Enable Answer button when SDK is controlled
            if (!controlled) {
                if (isListening) {
                    stopListening();
                }
                buttonStop.setEnabled(false);
                handleControlLost();
            } else {
                handleControlGained();
            }
        });

        // Register connection listener
        //ultraliteSDK.getConnected().registerConnectionListener(connectionListener);
        if (ultraliteSDK.isAvailable()) {
            isSdkAvailable.setValue(true);
        }

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
        boolean sdkControlled = Boolean.TRUE.equals(isSdkControlled.getValue());
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
        if (!isListening && Boolean.TRUE.equals(isSdkControlled.getValue())) {
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
        } else if (!Boolean.TRUE.equals(isSdkControlled.getValue())) {
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


        /*translateText(recognizedText, fromLanguageCode, toLanguageCode, translatedText -> {
            Log.i(TAG, "Translated text (" + toLanguageCode + "): " + translatedText);
            Toast.makeText(MainActivity.this, "Translated: " + translatedText, Toast.LENGTH_SHORT).show();

            transliterateForDisplay(translatedText, displayLanguageCode, new TranslationCallback() {
                 @Override
                 public void onSuccess(String finalDisplayText) {
                    Log.i(TAG, "Final text for display (" + displayLanguageCode + " script): " + finalDisplayText);
                    Toast.makeText(MainActivity.this, "Display: " + finalDisplayText, Toast.LENGTH_LONG).show();
                    displayTextOnGlasses(finalDisplayText);
                 }
                 @Override
                 public void onFailure(Exception e) {
                    Log.e(TAG, "Transliteration failed for translation: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Could not prepare text for glasses.", Toast.LENGTH_SHORT).show();
                 }
            });
        });*/
    }


    private void processSpeechResultForAnswer(String recognizedText) {
        String displayLanguageCode = displayLanguageCodes[spinnerDisplayLanguage.getSelectedItemPosition()];
        Log.d(TAG, "Processing for Answer: '" + recognizedText + "', display answers in " + displayLanguageCode + " script.");
        Toast.makeText(MainActivity.this, "Question: " + recognizedText, Toast.LENGTH_SHORT).show();

        if (geminiModel == null) {
            Toast.makeText(this, "AI Model not initialized. Check API Key.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Gemini model is null in processSpeechResultForAnswer. Cannot proceed.");
            return;
        }

        // Call Gemini AI
        getAiAnswers(recognizedText, aiAnswers -> {
            if (aiAnswers.isEmpty()) {
                Log.w(TAG, "Gemini returned no answers.");
                Toast.makeText(MainActivity.this, "No AI answers found.", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<String> transliteratedAnswers = new ArrayList<>();
            final int totalAnswers = aiAnswers.size();
            if (totalAnswers == 0) { // Should be caught by isEmpty check, but good for safety
                 Log.i(TAG, "No answers to transliterate.");
                 // Potentially show a message or log
                 return;
            }

            for (String answer : aiAnswers) {
                // AI answers are assumed to be in English. Transliterate them to the selected display script.
                transliterateForDisplay(answer, displayLanguageCode, new TranslationCallback() {
                    @Override
                    public void onSuccess(String transliteratedText) {
                        synchronized (transliteratedAnswers) {
                            transliteratedAnswers.add(transliteratedText);
                            if (transliteratedAnswers.size() == totalAnswers) { // All transliterations are done
                                Log.i(TAG, "Final transliterated AI answers: " + transliteratedAnswers);
                                if (!transliteratedAnswers.isEmpty()) {
                                    // Displaying the first answer for now. Concatenation could be an option.
                                    displayTextOnGlasses(transliteratedAnswers.get(0)); 
                                    Toast.makeText(MainActivity.this, "AI Answer (1st on glasses): " + transliteratedAnswers.get(0), Toast.LENGTH_LONG).show();
                                 } else {
                                    Toast.makeText(MainActivity.this, "No valid AI answers to display.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                    @Override
                    public void onFailure(Exception e) {
                         synchronized (transliteratedAnswers) {
                            Log.e(TAG, "Transliteration failed for an AI answer: " + e.getMessage());
                            transliteratedAnswers.add("[Transliteration Error]"); 
                            if (transliteratedAnswers.size() == totalAnswers) { 
                                Log.i(TAG, "Final (with errors) transliterated AI answers: " + transliteratedAnswers);
                                 if (!transliteratedAnswers.isEmpty() && !"[Transliteration Error]".equals(transliteratedAnswers.get(0))) {
                                     displayTextOnGlasses(transliteratedAnswers.get(0));
                                     Toast.makeText(MainActivity.this, "AI Answer (1st, may have errors, on glasses): " + transliteratedAnswers.get(0), Toast.LENGTH_LONG).show();
                                 } else {
                                     Toast.makeText(MainActivity.this, "First AI answer could not be prepared for glasses.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                });
            }
        }, errorMessage -> {
            Log.e(TAG, "Gemini AI Error: " + errorMessage);
            Toast.makeText(MainActivity.this, "AI Error: " + errorMessage, Toast.LENGTH_LONG).show();
        });
    }


    private void displayTextOnGlasses(String text) {
        if (!Boolean.TRUE.equals(isSdkControlled.getValue())) {
            Log.w(TAG, "Cannot display text on glasses: SDK not controlled.");
            Toast.makeText(this, "Glasses not controlled.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "Cannot display empty text on glasses.");
            Toast.makeText(this, "No text to display.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float speed = 1.0f; // Default speed
            String speedStr = editTextScrollingSpeed.getText().toString();
            if (!speedStr.isEmpty()) {
                try {
                    speed = Float.parseFloat(speedStr);
                    if (speed <= 0) {
                        speed = 1.0f; // Fallback to default if invalid
                        Toast.makeText(this, "Invalid speed, using default.", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid speed format, using default: " + speedStr);
                    Toast.makeText(this, "Invalid speed format, using default.", Toast.LENGTH_SHORT).show();
                    // speed remains default 1.0f
                }
            }
            
            // Assuming SCROLL_LAYOUT_ID and a way to set text and speed.
            // This is a conceptual implementation based on typical Vuzix SDK patterns.
            // The actual API might differ (e.g., using ScrollUtils or specific methods).
            // For Z100, direct text scrolling might be `ultraliteSDK.scrollText(text, (int) (speed * 10));`
            // or using a Layout:
            //Layout scrollLayout = new Layout(Constants.LAYOUT_ID_SCROLL); // Replace with actual constant if different
            //scrollLayout.setText(0, text); // Assuming element ID 0 is for the main text
            
            // The Vuzix Z100 SDK might have a different way to set scroll speed.
            // This is a placeholder for how it might be done.
            // It could be a parameter in scrollLayout.setProperty(propertyId, speed) or similar.
            // For now, let's assume a direct scrollText method or that speed is implicitly handled by the layout.
            // If `scrollText` exists and is preferred:
            // ultraliteSDK.scrollText(text, (int) (speed * SOME_SCALING_FACTOR_IF_NEEDED));

            final static int sliceHeight = 48; // Height of each slice of text (including inter-line padding)
            final static int fontSize = 35;    // Font size within one slice of text (smaller than the sliceHeight
            final static int lowestLineShowing = 0;
            final static int maxLinesShowing = 3;

            // If using setLayout:
            ultraliteSDK.setLayout(Layout.SCROLL, 0, true, true, 0);
            UltraliteSDK.ScrollingTextView scrollingTextView = ultralite.getScrollingTextView();
            scrollingTextView.scrollLayoutConfig(sliceHeightInPixels, lowestLineShowing, maxLinesShowing, (int) (speed * SOME_SCALING_FACTOR_IF_NEEDED), true);
            // If setLayout starts scrolling, this is fine. If a separate startScroll is needed:
            // ultraliteSDK.startScrolling(Constants.LAYOUT_ID_SCROLL);
            chunkStringsToEngine(text,sliceHeight,fontSize)

            Log.i(TAG, "Displaying on glasses: '" + text + "' with speed factor: " + speed);
            Toast.makeText(this, "Sending to glasses...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error displaying text on glasses: " + e.getMessage(), e);
            Toast.makeText(this, "Error sending to glasses.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void clearGlassesDisplay() {
        if (Boolean.TRUE.equals(isSdkControlled.getValue())) {
            try {
                // Assuming a method to clear the current layout or text
                //ultraliteSDK.clearLayout(); // Or specific clear for text/scroll area
                Log.i(TAG, "Cleared glasses display.");
                Toast.makeText(this, "Display cleared.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error clearing glasses display: " + e.getMessage(), e);
            }
        }
    }

    private static void chunkStringsToEngine(String content, int sliceHeight, int fontSize)  {
        AckWaiter ackWaiter = new AckWaiter(ultralite);
        TextToImageSlicer slicer = new TextToImageSlicer(content, sliceHeight, fontSize);
        int i = 0;
        // First let's fill the entire screen without waiting, and without scrolling
        while (slicer.hasMoreSlices() && (i<maxLinesShowing) ) {
            // We send the line to the explicit index of the screen without scrolling the screen
            final boolean scrollFirst = false;
            final int sliceIndexNumber = maxLinesShowing - 1 - i;
            scrollingTextView.sendScrollImage(slicer.getNextSlice(), sliceIndexNumber, scrollFirst);
            // we'll wait until the glasses confirm each line has arrived, although this is not
            // necessary as the underlying queue does this. But it demonstrates this mechanism which
            // could allow us to synchronize our UI with the glasses UI
            ackWaiter.waitForAck("Send line of text as image");
            // When this wait finishes, the glasses have replied that they received the text we just sent
            i++;
        }
        // Continue slicing the rest of that same content with some pauses in between
        while (slicer.hasMoreSlices()) {
            demoActivityViewModel.pause(2000);
            // Now we will just send the bottom slice, and request that the previous bottom be
            // scrolled up one position before accepting this as the new bottom slice
            final boolean scrollFirst = true;
            final int bottomSliceIndex = 0;
            scrollingTextView.sendScrollImage(slicer.getNextSlice(), bottomSliceIndex, scrollFirst);
        }
    }


    private void translateText(String text, String fromLanguage, String toLanguage, final TranslationCallback callback) {
        if (fromLanguage.equals(toLanguage)) {
            Log.d(TAG, "Source and target languages are the same. No translation needed.");
            callback.onSuccess(text);
            return;
        }

        if (!Arrays.asList(TranslateLanguage.getAllLanguages()).contains(fromLanguage) ||
            !Arrays.asList(TranslateLanguage.getAllLanguages()).contains(toLanguage)) {
            String errorMsg = "Unsupported language for translation: " + fromLanguage + " or " + toLanguage;
            Log.e(TAG, errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
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
                                Toast.makeText(MainActivity.this, "Translation Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                callback.onFailure(e);
                            })
                            .addOnCompleteListener(task -> translator.close()); 
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ML Kit Model download failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Model Download Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    callback.onFailure(e);
                    translator.close(); 
                });
    }

    // Placeholder for Transliteration
    private void transliterateForDisplay(String text, String targetScriptLanguageCode, final TranslationCallback callback) {
        // TODO: Implement proper transliteration here.
        Log.w(TAG, "Transliteration for display language '" + targetScriptLanguageCode + "' is not yet implemented. Returning original text.");
        callback.onSuccess(text); // Placeholder: returns original text
    }


    interface TranslationCallback {
        void onSuccess(String translatedText);
        void onFailure(Exception e);
    }

    private void initializeGeminiModel() {
        if (GEMINI_API_KEY.equals("YOUR_API_KEY_HERE")) {
            Log.e(TAG, "Gemini API Key not set. AI Answer feature will not work.");
            Toast.makeText(this, "Gemini API Key is not configured.", Toast.LENGTH_LONG).show();
            geminiModel = null; // Ensure it's null if not configured
            return;
        }
        if (geminiModel == null) { // Initialize only if not already done
            try {
                // Using safetySettings and generationConfig as per the new API structure
                com.google.ai.client.generativeai.type.GenerationConfig generationConfig = new com.google.ai.client.generativeai.type.GenerationConfig.Builder()
                    // Add any specific generation parameters here if needed
                    .build(); 
                
                // Using an empty list for safetySettings for now, can be configured
                java.util.List<com.google.ai.client.generativeai.type.SafetySetting> safetySettings = new ArrayList<>();
                
                geminiModel = new com.google.ai.client.generativeai.GenerativeModel(
                    "gemini-pro", // Model name
                    GEMINI_API_KEY,
                    generationConfig,
                    safetySettings
                );
                Log.d(TAG, "Gemini Model Initialized");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Gemini Model: " + e.getMessage(), e);
                Toast.makeText(this, "Failed to initialize AI: " + e.getMessage(), Toast.LENGTH_LONG).show();
                geminiModel = null; // Ensure it's null on failure
            }
        }
    }

    private void getAiAnswers(String conversationText, final AiAnswerCallback callback, final AiErrorCallback errorCallback) {
        if (geminiModel == null) {
            String errorMsg = "Gemini model is not initialized. Check API Key or logs for initialization errors.";
            Log.e(TAG, errorMsg);
            errorCallback.accept(errorMsg);
            return;
        }

        String prompt = "Generate a few likely concise replies to the question: '" + conversationText + "'";
        Log.d(TAG, "Gemini Prompt: " + prompt);

        com.google.common.util.concurrent.ListenableFuture<com.google.ai.client.generativeai.type.GenerateContentResponse> responseFuture = geminiModel.generateContent(prompt);
        
        responseFuture.addListener(() -> {
            try {
                com.google.ai.client.generativeai.type.GenerateContentResponse response = responseFuture.get();
                ArrayList<String> answers = new ArrayList<>();
                // New API structure: response.getText() might be null, check candidates.
                if (response.getCandidates()!=null && !response.getCandidates().isEmpty()) {
                    for (com.google.ai.client.generativeai.type.Candidate candidate : response.getCandidates()) {
                        if (candidate.getContent() != null && candidate.getContent().getParts() != null && !candidate.getContent().getParts().isEmpty()) {
                            for (com.google.ai.client.generativeai.type.Part part : candidate.getContent().getParts()) {
                                if (part.toString() != null && !part.toString().trim().isEmpty()) {
                                     String rawText = part.toString().trim();
                                     // Simple parsing: split by newline, remove list markers
                                     String[] potentialAnswers = rawText.split("\n");
                                     for(String ans : potentialAnswers) {
                                         String cleanedAns = ans.trim().replaceAll("^[*-]\\s*", "");
                                         if (!cleanedAns.isEmpty()) {
                                             answers.add(cleanedAns);
                                         }
                                     }
                                }
                            }
                        }
                    }
                } else if (response.getText() != null && !response.getText().isEmpty()) { 
                    // Fallback for older behavior or simple text responses, though less common with new API
                     String[] potentialAnswers = response.getText().split("\n");
                     for (String ans : potentialAnswers) {
                         String cleanedAns = ans.trim().replaceAll("^[*-]\\s*", "");
                         if (!cleanedAns.isEmpty()) {
                             answers.add(cleanedAns);
                         }
                     }
                }


                if (answers.isEmpty()) {
                    Log.w(TAG, "Gemini returned no parseable answers from candidates or text. Raw text (if any): " + response.getText());
                } else {
                    Log.i(TAG, "Gemini Parsed Answers: " + answers);
                }
                // Run callback on the main thread
                runOnUiThread(() -> callback.accept(answers));

            } catch (Exception e) { // Catches InterruptedException and ExecutionException from future.get()
                Log.e(TAG, "Gemini content generation failed: " + e.getMessage(), e);
                 // Run callback on the main thread
                runOnUiThread(() -> errorCallback.accept("AI content generation error: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this)); // Ensure listener runs on main thread for UI updates from callback
    }

    interface AiAnswerCallback {
        void accept(ArrayList<String> answers);
    }
    interface AiErrorCallback {
        void accept(String errorMessage);
    }


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

    private void requestSdkControl() {
        if (Boolean.TRUE.equals(isSdkAvailable.getValue()) && !ultraliteSDK.getControlledByMe().getValue()) {
            Log.d(TAG, "Requesting SDK control...");
            try {
                boolean requested = ultraliteSDK.requestControl();
                if (requested) {
                    Log.d(TAG, "SDK control request successful. Waiting for onControlGained callback.");
                } else {
                    Log.w(TAG, "SDK control request failed immediately.");
                    isSdkControlled.setValue(false);
                    Toast.makeText(this, "Failed to request control. Ensure Vuzix Connect is active.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) { 
                Log.e(TAG, "Exception while requesting control: " + e.getMessage(), e);
                Toast.makeText(this, "Error requesting control: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                isSdkControlled.setValue(false);
            }
        } else if (ultraliteSDK.getControlledByMe().getValue()) {
             Log.d(TAG, "Already have SDK control.");
             isSdkControlled.postValue(true); 
        }
    }


    private void handleControlGained() {
        Log.d(TAG, "handleControlGained: SDK control acquired.");
        Toast.makeText(this, "Vuzix Glasses Connected", Toast.LENGTH_SHORT).show();
        initializeGeminiModel(); // Initialize Gemini when control is gained
    }

    private void handleControlLost() {
        Log.d(TAG, "handleControlLost: SDK control lost.");
        Toast.makeText(this, "Vuzix Glasses Disconnected", Toast.LENGTH_SHORT).show();
        if (isListening) { 
            stopListening(); // This will also call resetListeningState
        }
        clearGlassesDisplay(); // Also clear glasses display when Stop is pressed
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ultraliteSDK.isAvailable()) {
            isSdkAvailable.setValue(true);
            if (!ultraliteSDK.getControlledByMe().getValue() && Boolean.TRUE.equals(isSdkAvailable.getValue())) {
                requestSdkControl(); // This will also call initializeGeminiModel onControlGained
            } else if (ultraliteSDK.getControlledByMe().getValue()) {
                isSdkControlled.setValue(true);
                initializeGeminiModel(); // Initialize if control already held and model not ready
            }
        } else {
            isSdkAvailable.setValue(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ultraliteSDK != null) {
            //ultraliteSDK.unregisterConnectionListener(connectionListener);
            if (ultraliteSDK.getControlledByMe().getValue()) {
                Log.d(TAG, "onDestroy: Releasing SDK control.");
                ultraliteSDK.releaseControl();
            }
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            Log.d(TAG, "SpeechRecognizer destroyed.");
        }
    }
}
