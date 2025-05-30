package com.example.languageassistant.utils;

import android.util.Log;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.TextPart;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiUtils {

    public static final String GEMINI_API_KEY = "YOUR_API_KEY"; // Replace with your actual API key
    public static GenerativeModel geminiModel;
    private static final Executor executor = Executors.newSingleThreadExecutor();

    public interface AiAnswerCallback {
        void onAnswerReceived(String answer);
    }

    public interface AiErrorCallback {
        void onError(String errorMessage);
    }

    public static void initializeGeminiModel(String apiKey) {
        geminiModel = new GenerativeModel(
                "gemini-pro",
                apiKey,
                com.google.ai.client.generativeai.type.GenerationConfig.builder().build(),
                new ArrayList<>()
        );
    }

    public static void getAiAnswers(String question, AiAnswerCallback callback, AiErrorCallback errorCallback) {
        if (geminiModel == null) {
            errorCallback.onError("Gemini model is not initialized.");
            return;
        }

        Content content = new Content.Builder()
                .addPart(new TextPart(question))
                .build();

        ListenableFuture<com.google.ai.client.generativeai.type.GenerateContentResponse> future = GenerativeModelFutures.from(geminiModel).generateContentStream(content);
        Futures.addCallback(future, new FutureCallback<com.google.ai.client.generativeai.type.GenerateContentResponse>() {
            @Override
            public void onSuccess(com.google.ai.client.generativeai.type.GenerateContentResponse result) {
                StringBuilder answer = new StringBuilder();
                for (com.google.ai.client.generativeai.type.Candidate candidate : result.getCandidatesList()) {
                    for (com.google.ai.client.generativeai.type.Content part : candidate.getContent().getPartsList()) {
                        if (part instanceof TextPart) {
                            answer.append(((TextPart) part).getText());
                        }
                    }
                }
                callback.onAnswerReceived(answer.toString());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("GeminiUtils", "Error generating content: " + t.getMessage(), t);
                errorCallback.onError("Failed to get answer from AI: " + t.getMessage());
            }
        }, executor);
    }
}
