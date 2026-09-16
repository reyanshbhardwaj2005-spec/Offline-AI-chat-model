package com.example.llama;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.example.llama.memory.ConversationEntity;
import com.example.llama.memory.MessageEntity;

public class MainActivity extends AppCompatActivity {
    // ============================================================
    // UI
    // ============================================================
    private TextView statusText;
    private TextView modelNameText;
    private EditText userInput;
    private ImageButton addButton;
    private ImageButton microphoneButton;
    private ImageButton sendButton;
    private Button loadModelButton;
    private ImageButton menuButton;
    private ImageButton moreButton;
    private DrawerLayout drawerLayout;
    private RecyclerView messagesRecyclerView;
    private View emptyState;
    private View composerContainer;

    // Voice UI
    private View voiceListeningContainer;
    private TextView voiceListeningText;

    // ============================================================
    // Drawer
    // ============================================================
    private LinearLayout historyContainer;
    private View drawerNewChat;
    private View drawerModels;
    private View drawerImages;

    // ============================================================
    // SPEECH TO TEXT
    // ============================================================
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private static final int RECORD_AUDIO_PERMISSION = 1001;

    // ============================================================
    // Core
    // ============================================================
    private MessageAdapter messageAdapter;
    private LlmManager llmManager;

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // ============================================================
    // State
    // ============================================================
    private boolean modelReady = false;
    private boolean multimodalReady = false;
    private boolean isGenerating = false;

    // ============================================================
    // Model Picker
    // ============================================================
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) {
            importAndLoadModel(uri);
        }
    });

    // ============================================================
    // Vision Projector Picker
    // ============================================================
    private final ActivityResultLauncher<String[]> mmprojPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) {
            importAndLoadMmproj(uri);
        }
    });

    // ============================================================
    // Image Picker
    // ============================================================
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) {
            handleSelectedImage(uri);
        }
    });

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --------------------------------------------------------
        // Find Main Views
        // --------------------------------------------------------
        drawerLayout = findViewById(R.id.drawerLayout);
        statusText = findViewById(R.id.modelStatusText);
        modelNameText = findViewById(R.id.modelNameText);
        userInput = findViewById(R.id.user_input);
        addButton = findViewById(R.id.addButton);
        microphoneButton = findViewById(R.id.microphoneButton);
        sendButton = findViewById(R.id.sendButton);
        messagesRecyclerView = findViewById(R.id.messages);
        emptyState = findViewById(R.id.emptyState);
        composerContainer = findViewById(R.id.composerContainer);
        loadModelButton = findViewById(R.id.loadModelButton);
        menuButton = findViewById(R.id.menuButton);
        moreButton = findViewById(R.id.moreButton);

        // --------------------------------------------------------
        // Find Voice Views
        // --------------------------------------------------------
        voiceListeningContainer = findViewById(R.id.voiceListeningContainer);
        voiceListeningText = findViewById(R.id.voiceListeningText);

        // --------------------------------------------------------
        // Find Drawer Views
        // --------------------------------------------------------
        historyContainer = findViewById(R.id.historyContainer);
        drawerNewChat = findViewById(R.id.drawerNewChat);
        drawerModels = findViewById(R.id.drawerModels);
        drawerImages = findViewById(R.id.drawerImages);

        // --------------------------------------------------------
        // Keyboard
        // --------------------------------------------------------
        setupKeyboardHandling();

        // --------------------------------------------------------
        // RecyclerView
        // --------------------------------------------------------
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setItemAnimator(null);
        messagesRecyclerView.setNestedScrollingEnabled(true);

        // --------------------------------------------------------
        // Adapter
        // --------------------------------------------------------
        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setAdapter(messageAdapter);

        // --------------------------------------------------------
        // Managers
        // --------------------------------------------------------
        llmManager = new LlmManager(this);
        setupSpeechToText();
        setupTextToSpeech();

        // --------------------------------------------------------
        // Drawer
        // --------------------------------------------------------
        setupDrawer();

        // --------------------------------------------------------
        // Initial UI State
        // --------------------------------------------------------
        modelReady = false;
        multimodalReady = false;
        isGenerating = false;
        statusText.setText("No model loaded");
        modelNameText.setText("No model");
        userInput.setHint("Load a model to start chatting...");
        sendButton.setEnabled(false);
        microphoneButton.setEnabled(true); // Always enabled for user prompting

        // --------------------------------------------------------
        // Listeners
        // --------------------------------------------------------
        loadModelButton.setOnClickListener(v -> openModelPicker());
        menuButton.setOnClickListener(v -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START);
            }
        });
        moreButton.setOnClickListener(v -> {
            if (!isGenerating) openModelPicker();
        });

        sendButton.setOnClickListener(v -> {
            if (!modelReady) {
                openModelPicker();
            } else if (isGenerating) {
                stopGeneration();
            } else {
                sendMessage();
            }
        });

        addButton.setOnClickListener(v -> {
            if (!modelReady) {
                statusText.setText("Load a model first");
                openModelPicker();
                return;
            }
            if (!multimodalReady) {
                statusText.setText("Vision is unavailable");
                return;
            }
            openImagePicker();
        });
    }

    // ============================================================
    // KEYBOARD HANDLING
    // ============================================================

    private void setupKeyboardHandling() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    // ============================================================
    // DRAWER
    // ============================================================

    private void setupDrawer() {
        if (drawerNewChat != null) drawerNewChat.setOnClickListener(v -> { if (!isGenerating) createNewChat(); });
        if (drawerModels != null) drawerModels.setOnClickListener(v -> { if (!isGenerating) openModelPicker(); });
        if (drawerImages != null) drawerImages.setOnClickListener(v -> {
            if (isGenerating) return;
            if (!modelReady) { statusText.setText("Load a model first"); return; }
            if (!multimodalReady) { statusText.setText("Vision is unavailable"); return; }
            openImagePicker();
        });
        refreshHistory();
    }

    private void refreshHistory() {
        if (historyContainer == null) return;
        llmManager.getConversations(new LlmManager.ConversationsCallback() {
            @Override
            public void onSuccess(List<ConversationEntity> conversations) {
                runOnUiThread(() -> {
                    historyContainer.removeAllViews();
                    if (conversations == null) return;
                    for (ConversationEntity conversation : conversations) addHistoryItem(conversation);
                });
            }
            @Override public void onError(Exception error) { android.util.Log.e("MainActivity", "History load failed", error); }
        });
    }

    private void addHistoryItem(ConversationEntity conversation) {
        TextView historyItem = new TextView(this);
        String title = conversation.getTitle();
        historyItem.setText((title == null || title.isEmpty()) ? "New Chat" : title);
        historyItem.setTextSize(14);
        historyItem.setTextColor(Color.DKGRAY);
        historyItem.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        historyItem.setBackgroundResource(android.R.drawable.list_selector_background);
        historyItem.setOnClickListener(v -> selectConversation(conversation.getId()));
        historyContainer.addView(historyItem);
    }

    private void selectConversation(long conversationId) {
        if (isGenerating) return;
        llmManager.selectConversation(conversationId, new LlmManager.ConversationCallback() {
            @Override
            public void onSuccess(ConversationEntity conversation) {
                llmManager.getConversationMessages(conversationId, new LlmManager.MessagesCallback() {
                    @Override
                    public void onSuccess(List<MessageEntity> messages) {
                        runOnUiThread(() -> {
                            messageAdapter.clearMessages();
                            if (messages != null && !messages.isEmpty()) {
                                for (MessageEntity m : messages) {
                                    int type = "user".equalsIgnoreCase(m.getRole()) ? Message.USER : Message.ASSISTANT;
                                    messageAdapter.addMessage(new Message(m.getContent(), type));
                                }
                                showChat();
                                scrollToBottom();
                            } else {
                                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                                if (messagesRecyclerView != null) messagesRecyclerView.setVisibility(View.GONE);
                            }
                            if (drawerLayout != null) drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                        });
                    }
                    @Override public void onError(Exception e) { statusText.setText("Load failed"); }
                });
            }
            @Override public void onError(Exception e) { statusText.setText("Selection failed"); }
        });
    }

    private void createNewChat() {
        if (isGenerating) return;
        llmManager.createNewConversation(new LlmManager.ConversationCallback() {
            @Override
            public void onSuccess(ConversationEntity c) {
                runOnUiThread(() -> {
                    messageAdapter.clearMessages();
                    if (messagesRecyclerView != null) messagesRecyclerView.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                    if (drawerLayout != null) drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    refreshHistory();
                });
            }
            @Override public void onError(Exception e) { statusText.setText("Chat creation failed"); }
        });
    }

    private void openModelPicker() {
        modelPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void importAndLoadModel(Uri uri) {
        statusText.setText("Loading model...");
        new Thread(() -> {
            try {
                File modelsDir = new File(getFilesDir(), "models");
                if (!modelsDir.exists()) modelsDir.mkdirs();
                File modelFile = new File(modelsDir, getFileName(uri));
                copyUriToFile(uri, modelFile);
                runOnUiThread(() -> llmManager.loadModel(modelFile.getAbsolutePath(), new LlmManager.LoadCallback() {
                    @Override
                    public void onSuccess() {
                        modelReady = true;
                        modelNameText.setText(modelFile.getName());
                        loadModelButton.setVisibility(View.GONE);
                        statusText.setText("Model ready");
                        userInput.setHint("Type a message...");
                        sendButton.setEnabled(true);
                        microphoneButton.setEnabled(true);
                        showChat();
                        initializeVisionAutomatically();
                    }
                    @Override
                    public void onError(Exception e) {
                        modelReady = false;
                        statusText.setText("Error: " + safeErrorMessage(e));
                    }
                }));
            } catch (Exception e) { runOnUiThread(() -> statusText.setText("Import failed")); }
        }).start();
    }

    private void initializeVisionAutomatically() {
        File modelsDir = new File(getFilesDir(), "models");
        File[] files = modelsDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().startsWith("mmproj")) {
                llmManager.loadMultimodalModel(f.getAbsolutePath(), new LlmManager.LoadCallback() {
                    @Override public void onSuccess() { multimodalReady = true; statusText.setText("Ready • Vision Enabled"); }
                    @Override public void onError(Exception e) {}
                });
                break;
            }
        }
    }

    private void openMmprojPicker() {
        mmprojPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void importAndLoadMmproj(Uri uri) {
        new Thread(() -> {
            try {
                File modelsDir = new File(getFilesDir(), "models");
                File mmprojFile = new File(modelsDir, "mmproj-vision.gguf");
                copyUriToFile(uri, mmprojFile);
                runOnUiThread(() -> llmManager.loadMultimodalModel(mmprojFile.getAbsolutePath(), new LlmManager.LoadCallback() {
                    @Override public void onSuccess() { multimodalReady = true; statusText.setText("Vision enabled"); }
                    @Override public void onError(Exception e) { statusText.setText("Vision failed"); }
                }));
            } catch (Exception e) {}
        }).start();
    }

    private void openImagePicker() {
        imagePicker.launch("image/*");
    }

    private void handleSelectedImage(Uri uri) {
        if (!multimodalReady) { openMmprojPicker(); return; }
        new Thread(() -> {
            try {
                File visionDir = new File(getCacheDir(), "vision");
                if (!visionDir.exists()) visionDir.mkdirs();
                File imageFile = new File(visionDir, "selected_image" + getImageExtension(uri));
                copyUriToFile(uri, imageFile);
                runOnUiThread(() -> sendVisionMessage(imageFile.getAbsolutePath()));
            } catch (Exception e) {}
        }).start();
    }

    private void sendVisionMessage(String imagePath) {
        if (isGenerating) return;
        String input = userInput.getText().toString().trim();
        final String message = input.isEmpty() ? "Describe this image." : input;
        userInput.setText("");
        showChat();
        isGenerating = true;
        sendButton.setImageResource(R.drawable.ic_stop);
        messageAdapter.addMessage(new Message(message, Message.USER));
        messageAdapter.addMessage(new Message("", Message.ASSISTANT));
        scrollToBottom();

        llmManager.sendImageMessage(imagePath, message, new LlmManager.ChatCallback() {
            @Override public void onToken(String text) {
                if (LlmManager.THINKING_SIGNAL.equals(text)) messageAdapter.setThinking(true, messagesRecyclerView);
                else { messageAdapter.setThinking(false, messagesRecyclerView); messageAdapter.updateLastMessage(text, messagesRecyclerView); }
            }
            @Override
            public void onComplete(String full) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    messageAdapter.updateLastMessage(full, messagesRecyclerView);
                    sendButton.setImageResource(R.drawable.ic_send);
                    scrollToBottom();
                    // Automatic speech removed per user request.
                });
            }
            @Override public void onStopped() { runOnUiThread(() -> { isGenerating = false; sendButton.setImageResource(R.drawable.ic_send); }); }
            @Override public void onError(Exception e) { runOnUiThread(() -> { isGenerating = false; sendButton.setImageResource(R.drawable.ic_send); }); }
        });
    }

    private void sendMessage() {
        String message = userInput.getText().toString().trim();
        if (message.isEmpty() || !modelReady) return;
        stopSpeaking();
        showChat();
        isGenerating = true;
        sendButton.setImageResource(R.drawable.ic_stop);
        messageAdapter.addMessage(new Message(message, Message.USER));
        messageAdapter.addMessage(new Message("", Message.ASSISTANT));
        scrollToBottom();
        userInput.setText("");

        llmManager.sendMessage(message, new LlmManager.ChatCallback() {
            @Override public void onToken(String text) {
                if (LlmManager.THINKING_SIGNAL.equals(text)) messageAdapter.setThinking(true, messagesRecyclerView);
                else { messageAdapter.setThinking(false, messagesRecyclerView); messageAdapter.updateLastMessage(text, messagesRecyclerView); }
            }
            @Override
            public void onComplete(String full) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    messageAdapter.updateLastMessage(full, messagesRecyclerView);
                    sendButton.setImageResource(R.drawable.ic_send);
                    scrollToBottom();
                    // Automatic speech removed per user request.
                });
            }
            @Override public void onStopped() { runOnUiThread(() -> { isGenerating = false; sendButton.setImageResource(R.drawable.ic_send); }); }
            @Override public void onError(Exception e) { runOnUiThread(() -> { isGenerating = false; sendButton.setImageResource(R.drawable.ic_send); }); }
        });
    }

    private void stopGeneration() {
        llmManager.stopGeneration();
        stopSpeaking();
        isGenerating = false;
        sendButton.setImageResource(R.drawable.ic_send);
        messageAdapter.setThinking(false, messagesRecyclerView);
    }

    private void copyUriToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    private String getFileName(Uri uri) {
        String res = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) res = c.getString(i);
                }
            }
        }
        return res != null ? res : "model.gguf";
    }

    private String getImageExtension(Uri uri) {
        String type = getContentResolver().getType(uri);
        if ("image/png".equals(type)) return ".png";
        if ("image/webp".equals(type)) return ".webp";
        return ".jpg";
    }

    private String safeErrorMessage(Exception e) { return e != null ? e.getMessage() : "Unknown error"; }
    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }

    private void scrollToBottom() {
        messagesRecyclerView.post(() -> {
            if (messageAdapter.getItemCount() > 0)
                messagesRecyclerView.getLayoutManager().scrollToPosition(messageAdapter.getItemCount() - 1);
        });
    }

    private void showChat() {
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (messagesRecyclerView != null) messagesRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.stopListening(); speechRecognizer.destroy(); }
        if (llmManager != null) llmManager.destroy();
        super.onDestroy();
    }

    // ============================================================
    // TTS SETUP
    // ============================================================

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
                ttsReady = true;
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) { runOnUiThread(() -> messageAdapter.clearSpeakingPosition()); }
            @Override public void onError(String utteranceId) { runOnUiThread(() -> messageAdapter.clearSpeakingPosition()); }
        });

        messageAdapter.setOnSpeakClickListener((text, position) -> {
            if (!ttsReady) return;
            if (messageAdapter.getSpeakingPosition() == position) stopSpeaking();
            else speakText(text, position);
        });
    }

    private void speakText(String text, int position) {
        if (textToSpeech == null || !ttsReady) return;
        textToSpeech.stop();
        messageAdapter.setSpeakingPosition(position);
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "msg_" + position);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "msg_" + position);
    }

    private void stopSpeaking() {
        if (textToSpeech != null) textToSpeech.stop();
        messageAdapter.clearSpeakingPosition();
    }

    // ============================================================
    // STT SETUP
    // ============================================================

    private void setupSpeechToText() {
        microphoneButton.setOnClickListener(v -> {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                statusText.setText("Speech not supported");
                Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!modelReady) {
                statusText.setText("Load a model first");
                openModelPicker();
                return;
            }
            if (isListening) stopSpeechToText();
            else startSpeechToText();
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    if (voiceListeningContainer != null) voiceListeningContainer.setVisibility(View.VISIBLE);
                    if (voiceListeningText != null) voiceListeningText.setText("Listening...");
                });
            }

            @Override
            public void onBeginningOfSpeech() {
                runOnUiThread(() -> { if (voiceListeningText != null) voiceListeningText.setText("Speak now"); });
            }

            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                runOnUiThread(() -> { if (voiceListeningText != null) voiceListeningText.setText("Processing..."); });
            }

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    if (voiceListeningContainer != null) voiceListeningContainer.setVisibility(View.GONE);
                    statusText.setText("Voice input error: " + error);
                    android.util.Log.e("MainActivity", "STT Error: " + error);
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                runOnUiThread(() -> {
                    isListening = false;
                    if (voiceListeningContainer != null) voiceListeningContainer.setVisibility(View.GONE);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        userInput.setText(recognizedText);
                        userInput.setSelection(userInput.length());

                        // Automatically send the recognized text
                        if (modelReady && !isGenerating && !recognizedText.trim().isEmpty()) {
                            sendMessage();
                        }
                    }
                });
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    runOnUiThread(() -> {
                        userInput.setText(matches.get(0));
                        userInput.setSelection(userInput.length());
                    });
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION);
            return;
        }

        try {
            // Re-initialize to ensure a fresh state
            initSpeechRecognizer();

            if (speechRecognizer != null) {
                speechRecognizer.startListening(speechRecognizerIntent);
                // Immediate feedback
                isListening = true;
                if (voiceListeningContainer != null) voiceListeningContainer.setVisibility(View.VISIBLE);
                if (voiceListeningText != null) voiceListeningText.setText("Starting mic...");
            }
        } catch (Exception e) {
            statusText.setText("Mic failed");
            isListening = false;
        }
    }

    private void stopSpeechToText() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        isListening = false;
        if (voiceListeningContainer != null) voiceListeningContainer.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startSpeechToText();
    }
}
