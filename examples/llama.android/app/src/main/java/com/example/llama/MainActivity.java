package com.example.llama;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.llama.memory.ConversationEntity;
import com.example.llama.memory.MessageEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int RECORD_AUDIO_PERMISSION = 1001;

    // UI Components
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

    // Voice UI
    private View voiceListeningContainer;
    private TextView voiceListeningText;

    // Drawer Components
    private LinearLayout historyContainer;
    private View drawerNewChat;
    private View drawerModels;
    private View drawerImages;

    // STT & TTS
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // Core Logic
    private MessageAdapter messageAdapter;
    private LlmManager llmManager;

    // State
    private boolean modelReady = false;
    private boolean multimodalReady = false;
    private boolean isGenerating = false;

    // Activity Result Launchers
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importAndLoadModel(uri);
            });

    private final ActivityResultLauncher<String[]> mmprojPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importAndLoadMmproj(uri);
            });

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleSelectedImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupManagers();
        setupListeners();
        setupKeyboardHandling();

        // Initial UI State
        updateModelStatus("No model loaded", "No model");
        microphoneButton.setEnabled(true);
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        statusText = findViewById(R.id.modelStatusText);
        modelNameText = findViewById(R.id.modelNameText);
        userInput = findViewById(R.id.user_input);
        addButton = findViewById(R.id.addButton);
        microphoneButton = findViewById(R.id.microphoneButton);
        sendButton = findViewById(R.id.sendButton);
        messagesRecyclerView = findViewById(R.id.messages);
        emptyState = findViewById(R.id.emptyState);
        loadModelButton = findViewById(R.id.loadModelButton);
        menuButton = findViewById(R.id.menuButton);
        moreButton = findViewById(R.id.moreButton);

        voiceListeningContainer = findViewById(R.id.voiceListeningContainer);
        voiceListeningText = findViewById(R.id.voiceListeningText);

        historyContainer = findViewById(R.id.historyContainer);
        drawerNewChat = findViewById(R.id.drawerNewChat);
        drawerModels = findViewById(R.id.drawerModels);
        drawerImages = findViewById(R.id.drawerImages);
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setItemAnimator(null);
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupManagers() {
        llmManager = new LlmManager(this);
        setupSpeechToText();
        setupTextToSpeech();
        setupDrawer();
    }

    private void setupListeners() {
        loadModelButton.setOnClickListener(v -> openModelPicker());
        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.LEFT));
        moreButton.setOnClickListener(v -> { if (!isGenerating) openModelPicker(); });

        sendButton.setOnClickListener(v -> {
            if (!modelReady) openModelPicker();
            else if (isGenerating) stopGeneration();
            else sendMessage();
        });

        addButton.setOnClickListener(v -> {
            if (!modelReady) {
                statusText.setText("Load a model first");
                openModelPicker();
            } else {
                openImagePicker();
            }
        });
    }

    private void setupKeyboardHandling() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void updateModelStatus(String status, String name) {
        statusText.setText(status);
        modelNameText.setText(name);
        if (!modelReady) {
            userInput.setHint("Load a model to start chatting...");
            sendButton.setEnabled(false);
        } else {
            userInput.setHint("Type a message...");
            sendButton.setEnabled(true);
        }
    }

    // ============================================================
    // DRAWER & HISTORY
    // ============================================================

    private void setupDrawer() {
        if (drawerNewChat != null) drawerNewChat.setOnClickListener(v -> { if (!isGenerating) createNewChat(); });
        if (drawerModels != null) drawerModels.setOnClickListener(v -> { if (!isGenerating) openModelPicker(); });
        if (drawerImages != null) drawerImages.setOnClickListener(v -> {
            if (!isGenerating && modelReady) openImagePicker();
            else if (!modelReady) statusText.setText("Load a model first");
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
                    for (ConversationEntity conversation : conversations) {
                        addHistoryItem(conversation);
                    }
                });
            }
            @Override public void onError(Exception error) { Log.e(TAG, "History failed", error); }
        });
    }

    private void addHistoryItem(ConversationEntity conversation) {
        TextView historyItem = new TextView(this);
        String title = conversation.getTitle();
        historyItem.setText(TextUtils.isEmpty(title) ? "New Chat" : title);
        historyItem.setTextSize(14);
        historyItem.setTextColor(Color.DKGRAY);
        historyItem.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        historyItem.setBackgroundResource(android.R.drawable.list_selector_background);
        historyItem.setOnClickListener(v -> {
            selectConversation(conversation.getId());
            drawerLayout.closeDrawers();
        });
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
                                showChatUI();
                                scrollToBottom();
                            } else {
                                hideChatUI();
                            }
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
                    hideChatUI();
                    drawerLayout.closeDrawers();
                    refreshHistory();
                });
            }
            @Override public void onError(Exception e) { statusText.setText("Creation failed"); }
        });
    }

    // ============================================================
    // MODEL LOADING
    // ============================================================

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
                        loadModelButton.setVisibility(View.GONE);
                        updateModelStatus("Model ready", modelFile.getName());
                        showChatUI();
                        initializeVisionAutomatically();
                    }
                    @Override
                    public void onError(Exception e) {
                        modelReady = false;
                        updateModelStatus("Error: " + e.getMessage(), "No model");
                    }
                }));
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Load failed: " + e.getMessage()));
            }
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
                    @Override public void onError(Exception e) { Log.e(TAG, "Vision load failed", e); }
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
            } catch (Exception e) { Log.e(TAG, "Mmproj copy failed", e); }
        }).start();
    }

    // ============================================================
    // CHAT ACTIONS
    // ============================================================

    private void sendMessage() {
        String message = userInput.getText().toString().trim();
        if (message.isEmpty() || !modelReady) return;

        stopSpeaking();
        showChatUI();
        isGenerating = true;
        sendButton.setImageResource(R.drawable.ic_stop);
        messageAdapter.addMessage(new Message(message, Message.USER));
        messageAdapter.addMessage(new Message("", Message.ASSISTANT));
        scrollToBottom();
        userInput.setText("");

        llmManager.sendMessage(message, new LlmManager.ChatCallback() {
            @Override public void onToken(String text) {
                if (LlmManager.THINKING_SIGNAL.equals(text)) messageAdapter.setThinking(true, messagesRecyclerView);
                else {
                    messageAdapter.setThinking(false, messagesRecyclerView);
                    messageAdapter.updateLastMessage(text, messagesRecyclerView);
                }
            }
            @Override
            public void onComplete(String full) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    messageAdapter.updateLastMessage(full, messagesRecyclerView);
                    sendButton.setImageResource(R.drawable.ic_send);
                    scrollToBottom();
                    refreshHistory();
                });
            }
            @Override public void onStopped() { finalizeGeneration("Stopped"); }
            @Override public void onError(Exception e) { finalizeGeneration("Error: " + e.getMessage()); }
        });
    }

    private void finalizeGeneration(String status) {
        runOnUiThread(() -> {
            isGenerating = false;
            sendButton.setImageResource(R.drawable.ic_send);
            statusText.setText(status);
            messageAdapter.setThinking(false, messagesRecyclerView);
        });
    }

    private void stopGeneration() {
        llmManager.stopGeneration();
        stopSpeaking();
        finalizeGeneration("Generation stopped");
    }

    // ============================================================
    // MEDIA PICKER & IMAGE ANALYSIS
    // ============================================================

    private void openImagePicker() {
        imagePicker.launch("image/*");
    }

    private void handleSelectedImage(Uri uri) {
        if (!multimodalReady) {
            statusText.setText("Vision model required");
            new AlertDialog.Builder(this)
                    .setTitle("Vision Projector Required")
                    .setMessage("To analyze images, you need a vision projector model. Load one now?")
                    .setPositiveButton("Select mmproj", (d, w) -> openMmprojPicker())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Prompt Dialog
        runOnUiThread(() -> {
            final EditText promptInput = new EditText(this);
            promptInput.setHint("Ask about this image...");
            promptInput.setGravity(Gravity.TOP | Gravity.START);
            promptInput.setMinLines(3);
            promptInput.setText(userInput.getText().toString());

            FrameLayout container = new FrameLayout(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dpToPx(16);
            lp.setMargins(m, dpToPx(8), m, m);
            promptInput.setLayoutParams(lp);
            container.addView(promptInput);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Image Selected")
                    .setView(container)
                    .setPositiveButton("Send", null)
                    .setNegativeButton("Cancel", null)
                    .create();

            dialog.show();

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String prompt = promptInput.getText().toString().trim();
                if (prompt.isEmpty()) prompt = "Describe this image.";
                final String finalPrompt = prompt;
                dialog.dismiss();

                new Thread(() -> {
                    try {
                        File visionDir = new File(getCacheDir(), "vision");
                        if (!visionDir.exists()) visionDir.mkdirs();
                        File imageFile = new File(visionDir, "vision_" + System.currentTimeMillis() + getImageExtension(uri));
                        copyUriToFile(uri, imageFile);
                        runOnUiThread(() -> sendVisionMessage(imageFile.getAbsolutePath(), finalPrompt));
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Process failed", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            });
        });
    }

    private void sendVisionMessage(String imagePath, String message) {
        if (isGenerating) return;
        showChatUI();
        isGenerating = true;
        sendButton.setImageResource(R.drawable.ic_stop);
        messageAdapter.addMessage(new Message(message, Message.USER));
        messageAdapter.addMessage(new Message("", Message.ASSISTANT));
        scrollToBottom();

        llmManager.sendImageMessage(imagePath, message, new LlmManager.ChatCallback() {
            @Override public void onToken(String text) {
                if (LlmManager.THINKING_SIGNAL.equals(text)) messageAdapter.setThinking(true, messagesRecyclerView);
                else {
                    messageAdapter.setThinking(false, messagesRecyclerView);
                    messageAdapter.updateLastMessage(text, messagesRecyclerView);
                }
            }
            @Override public void onComplete(String full) {
                runOnUiThread(() -> {
                    isGenerating = false;
                    messageAdapter.updateLastMessage(full, messagesRecyclerView);
                    sendButton.setImageResource(R.drawable.ic_send);
                    scrollToBottom();
                    refreshHistory();
                });
            }
            @Override public void onStopped() { finalizeGeneration("Stopped"); }
            @Override public void onError(Exception e) { finalizeGeneration("Vision failed"); }
        });
    }

    // ============================================================
    // STT & TTS IMPLEMENTATION
    // ============================================================

    private void setupSpeechToText() {
        microphoneButton.setOnClickListener(v -> {
            if (!modelReady) { statusText.setText("Load model first"); openModelPicker(); return; }
            if (isListening) stopSpeechToText();
            else startSpeechToText();
        });

        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { setMicUI(true, "Listening..."); }
            @Override public void onBeginningOfSpeech() { voiceListeningText.setText("Speak now"); }
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { voiceListeningText.setText("Processing..."); }
            @Override public void onError(int e) { setMicUI(false, "Mic error"); }
            @Override public void onResults(Bundle r) {
                ArrayList<String> matches = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    userInput.setText(matches.get(0));
                    sendMessage();
                }
                setMicUI(false, "Model ready");
            }
            @Override public void onPartialResults(Bundle p) {
                ArrayList<String> matches = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) userInput.setText(matches.get(0));
            }
            @Override public void onEvent(int t, Bundle p) {}
        });
    }

    private void setMicUI(boolean listening, String status) {
        runOnUiThread(() -> {
            isListening = listening;
            voiceListeningContainer.setVisibility(listening ? View.VISIBLE : View.GONE);
            statusText.setText(status);
        });
    }

    private void startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION);
            return;
        }
        try {
            speechRecognizer.startListening(speechRecognizerIntent);
        } catch (Exception e) { setMicUI(false, "Mic failed"); }
    }

    private void stopSpeechToText() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        setMicUI(false, "Model ready");
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
                ttsReady = true;
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { runOnUiThread(() -> messageAdapter.clearSpeakingPosition()); }
            @Override public void onError(String id) { runOnUiThread(() -> messageAdapter.clearSpeakingPosition()); }
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
    // HELPERS
    // ============================================================

    private void copyUriToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) return;
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

    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }

    private void scrollToBottom() {
        messagesRecyclerView.post(() -> {
            if (messageAdapter.getItemCount() > 0)
                messagesRecyclerView.getLayoutManager().scrollToPosition(messageAdapter.getItemCount() - 1);
        });
    }

    private void showChatUI() {
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (messagesRecyclerView != null) messagesRecyclerView.setVisibility(View.VISIBLE);
    }

    private void hideChatUI() {
        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
        if (messagesRecyclerView != null) messagesRecyclerView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startSpeechToText();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (llmManager != null) llmManager.destroy();
        super.onDestroy();
    }
}
