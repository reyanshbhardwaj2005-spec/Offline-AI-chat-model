package com.example.llama;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.llama.ai.voice.AndroidSpeechToTextEngine;
import com.example.llama.ai.voice.SpeechToTextEngine;
import com.example.llama.memory.ConversationEntity;
import com.example.llama.memory.MessageEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {

    // =============================================================
    // CHAT UI
    // =============================================================

    private TextView modelNameText;
    private TextView modelStatusText;

    private EditText userInput;
    private ImageButton sendButton;

    private ImageButton microphoneButton;
    private SpeechToTextEngine speechToTextEngine;

    private View voiceListeningContainer;
    private ImageView voiceListeningIcon;
    private TextView voiceListeningText;
    private TextView voiceListeningSubtext;

    private AnimatorSet voicePulseAnimator;

    private boolean isListening = false;

    private static final int RECORD_AUDIO_REQUEST_CODE = 1001;

    private Button loadModelButton;

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;

    private View emptyState;

    // =============================================================
    // DRAWER
    // =============================================================

    private DrawerLayout drawerLayout;

    private View drawerNewChat;
    private View drawerModels;
    private View drawerImages;
    private View drawerSettings;
    private View drawerProfile;

    private LinearLayout historyContainer;

    // =============================================================
    // LLM
    // =============================================================

    private LlmManager llmManager;

    private boolean modelReady = false;
    private boolean isGenerating = false;

    private long currentConversationId = -1L;
    private long selectedConversationId = -1L;

    // =============================================================
    // SCROLL
    // =============================================================

    private boolean autoScroll = true;
    private boolean userIsDragging = false;

    // =============================================================
    // MODEL PICKER
    // =============================================================

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {

        if (uri != null) {
            importAndLoadModel(uri);
        }
    });

    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        bindViews();

        setupRecyclerView();

        setupDrawer();

        setupButtons();

        setupInitialState();

        llmManager = new LlmManager(this);

        // =========================================================
        // SPEECH TO TEXT
        // =========================================================

        speechToTextEngine = new AndroidSpeechToTextEngine(this, new AndroidSpeechToTextEngine.Listener() {

            @Override
            public void onReady() {

                runOnUiThread(() -> {

                    isListening = true;

                    microphoneButton.setContentDescription(
                        "Stop listening"
                    );

                    modelStatusText.setText("Listening...");

                    showListeningUI();
                });
            }
            @Override
            public void onBeginningOfSpeech() {

                runOnUiThread(() -> {

                    isListening = true;

                    microphoneButton.setContentDescription(
                        "Stop listening"
                    );

                    voiceListeningText.setText("Listening...");
                    voiceListeningSubtext.setText("I'm listening");

                    modelStatusText.setText("Listening...");
                });
            }

            @Override
            public void onPartialResult(String text) {

                runOnUiThread(() -> {

                    if (text != null && !text.trim().isEmpty()) {

                        userInput.setText(text);

                        userInput.setSelection(
                            userInput.length()
                        );

                        voiceListeningSubtext.setText(
                            "Keep speaking..."
                        );
                    }
                });
            }

            @Override
            public void onFinalResult(String text) {

                runOnUiThread(() -> {

                    isListening = false;

                    hideListeningUI();

                    microphoneButton.setContentDescription(
                        "Voice input"
                    );

                    if (text != null && !text.trim().isEmpty()) {

                        userInput.setText(text);

                        userInput.setSelection(
                            userInput.length()
                        );

                        modelStatusText.setText(
                            "Ready to send"
                        );

                    } else {

                        modelStatusText.setText(
                            modelReady
                                ? "Model ready"
                                : "No model loaded"
                        );
                    }
                });
            }

            @Override
            public void onEndOfSpeech() {

                runOnUiThread(() -> {

                    voiceListeningText.setText(
                        "Processing..."
                    );

                    voiceListeningSubtext.setText(
                        "Converting speech to text"
                    );

                    modelStatusText.setText(
                        "Converting speech..."
                    );
                });
            }

            @Override
            public void onError(String error) {

                runOnUiThread(() -> {

                    isListening = false;

                    hideListeningUI();

                    microphoneButton.setContentDescription(
                        "Voice input"
                    );

                    modelStatusText.setText(error);

                    Toast.makeText(
                        MainActivity.this,
                        error,
                        Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });

        // =========================================================
        // RESTORE CURRENT CONVERSATION
        // =========================================================

        llmManager.initializeConversation(new LlmManager.ConversationCallback() {

            @Override
            public void onSuccess(ConversationEntity conversation) {

                currentConversationId = conversation.getId();

                selectedConversationId = conversation.getId();

                loadConversationMessages(currentConversationId);

                refreshHistory();
            }

            @Override
            public void onError(Exception error) {

                modelStatusText.setText("Database error: " + error.getMessage());
            }
        });
    }

    // =============================================================
    // BIND VIEWS
    // =============================================================

    private void bindViews() {

        modelNameText = findViewById(R.id.modelNameText);

        modelStatusText = findViewById(R.id.modelStatusText);

        userInput = findViewById(R.id.user_input);

        sendButton = findViewById(R.id.sendButton);

        microphoneButton = findViewById(R.id.microphoneButton);

        loadModelButton = findViewById(R.id.loadModelButton);

        messagesRecyclerView = findViewById(R.id.messages);

        emptyState = findViewById(R.id.emptyState);

        drawerLayout = findViewById(R.id.drawerLayout);

        drawerNewChat = findViewById(R.id.drawerNewChat);

        drawerModels = findViewById(R.id.drawerModels);

        drawerImages = findViewById(R.id.drawerImages);

        drawerSettings = findViewById(R.id.drawerSettings);

        drawerProfile = findViewById(R.id.drawerProfile);

        historyContainer = findViewById(R.id.historyContainer);

        voiceListeningContainer =
            findViewById(R.id.voiceListeningContainer);

        voiceListeningIcon =
            findViewById(R.id.voiceListeningIcon);

        voiceListeningText =
            findViewById(R.id.voiceListeningText);

        voiceListeningSubtext =
            findViewById(R.id.voiceListeningSubtext);
    }

    // =============================================================
    // INITIAL STATE
    // =============================================================

    private void setupInitialState() {

        modelReady = false;

        isGenerating = false;

        isListening = false;

        modelNameText.setText("No model");

        modelStatusText.setText("No model loaded");

        loadModelButton.setText("Load Model");

        loadModelButton.setEnabled(true);

        sendButton.setEnabled(false);

        userInput.setEnabled(false);

        userInput.setHint("Load a model to start chatting...");

        emptyState.setVisibility(View.VISIBLE);

        messagesRecyclerView.setVisibility(View.GONE);

        microphoneButton.setEnabled(false);

        microphoneButton.setContentDescription("Voice input");
    }

    // =============================================================
    // RECYCLER VIEW
    // =============================================================

    private void setupRecyclerView() {

        layoutManager = new LinearLayoutManager(this);

        layoutManager.setStackFromEnd(false);

        messagesRecyclerView.setLayoutManager(layoutManager);

        messagesRecyclerView.setHasFixedSize(false);

        messagesRecyclerView.setItemAnimator(null);

        messageAdapter = new MessageAdapter();

        messagesRecyclerView.setAdapter(messageAdapter);

        messagesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {

                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {

                    userIsDragging = true;

                    autoScroll = false;

                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    userIsDragging = false;

                    autoScroll = isAtBottom();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {

                if (userIsDragging) {
                    return;
                }

                if (!recyclerView.canScrollVertically(1)) {

                    autoScroll = true;
                }
            }
        });
    }

    // =============================================================
    // BUTTONS
    // =============================================================

    private void setupButtons() {

        // ---------------------------------------------------------
        // MICROPHONE
        // ---------------------------------------------------------

        microphoneButton.setOnClickListener(v -> {

            if (!modelReady) {
                return;
            }

            if (isGenerating) {
                return;
            }

            if (isListening) {

                stopVoiceInput();

            } else {

                startVoiceInput();
            }
        });

        // ---------------------------------------------------------
        // MENU
        // ---------------------------------------------------------

        ImageButton menuButton = findViewById(R.id.menuButton);

        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.START));

        // ---------------------------------------------------------
        // LOAD MODEL
        // ---------------------------------------------------------

        loadModelButton.setOnClickListener(v -> {

            if (!modelReady && !isGenerating) {

                openModelPicker();
            }
        });

        // ---------------------------------------------------------
        // SEND
        // ---------------------------------------------------------

        sendButton.setOnClickListener(v -> {

            if (!modelReady) {
                return;
            }

            if (isGenerating) {

                stopGeneration();

            } else {

                sendMessage();
            }
        });
    }

    // =============================================================
    // DRAWER
    // =============================================================

    private void setupDrawer() {

        drawerNewChat.setOnClickListener(v -> {

            drawerLayout.closeDrawer(Gravity.START);

            startNewChat();
        });

        drawerModels.setOnClickListener(v -> {

            drawerLayout.closeDrawer(Gravity.START);

            modelStatusText.setText(modelReady ? "Model: " + modelNameText.getText() : "No model loaded");
        });

        drawerImages.setOnClickListener(v -> {

            drawerLayout.closeDrawer(Gravity.START);

            modelStatusText.setText("Images are not implemented yet");
        });

        drawerSettings.setOnClickListener(v -> {

            drawerLayout.closeDrawer(Gravity.START);

            modelStatusText.setText("Settings are not implemented yet");
        });

        drawerProfile.setOnClickListener(v -> {

            drawerLayout.closeDrawer(Gravity.START);

            modelStatusText.setText("Local profile");
        });
    }

    // =============================================================
    // HISTORY
    // =============================================================

    private void refreshHistory() {

        if (llmManager == null || historyContainer == null) {

            return;
        }

        llmManager.getConversations(new LlmManager.ConversationsCallback() {

            @Override
            public void onSuccess(List<ConversationEntity> conversations) {

                renderHistory(conversations);
            }

            @Override
            public void onError(Exception error) {

                error.printStackTrace();
            }
        });
    }

    private void renderHistory(List<ConversationEntity> conversations) {

        historyContainer.removeAllViews();

        if (conversations == null || conversations.isEmpty()) {

            TextView empty = createHistoryText("No conversations yet", false);

            historyContainer.addView(empty);

            return;
        }

        boolean todayAdded = false;
        boolean yesterdayAdded = false;
        boolean earlierAdded = false;

        for (ConversationEntity conversation : conversations) {

            long timestamp = conversation.getUpdatedAt();

            if (isToday(timestamp)) {

                if (!todayAdded) {

                    addHistorySectionTitle("Today");

                    todayAdded = true;
                }

            } else if (isYesterday(timestamp)) {

                if (!yesterdayAdded) {

                    addHistorySectionTitle("Yesterday");

                    yesterdayAdded = true;
                }

            } else {

                if (!earlierAdded) {

                    addHistorySectionTitle("Earlier");

                    earlierAdded = true;
                }
            }

            addHistoryItem(conversation);
        }
    }

    private void addHistorySectionTitle(String title) {

        TextView text = createHistoryText(title, true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        params.topMargin = title.equals("Today") ? dp(4) : dp(14);

        text.setLayoutParams(params);

        historyContainer.addView(text);
    }

    private void addHistoryItem(ConversationEntity conversation) {

        TextView item = createHistoryText(conversation.getTitle(), false);

        item.setTag(conversation.getId());

        updateHistoryItemAppearance(item, conversation.getId());

        item.setOnClickListener(v -> {

            long conversationId = (Long) v.getTag();

            if (isGenerating) {

                modelStatusText.setText("Stop generation before switching chats");

                return;
            }

            selectedConversationId = conversationId;

            updateHistorySelection();

            modelStatusText.setText("Opening chat...");

            selectConversation(conversationId);
        });

        item.setContentDescription("Open conversation " + conversation.getTitle());

        historyContainer.addView(item);
    }

    private void updateHistorySelection() {

        for (int i = 0; i < historyContainer.getChildCount(); i++) {

            View child = historyContainer.getChildAt(i);

            if (child instanceof TextView && child.getTag() instanceof Long) {

                long id = (Long) child.getTag();

                updateHistoryItemAppearance((TextView) child, id);
            }
        }
    }

    private void updateHistoryItemAppearance(TextView item, long conversationId) {

        if (conversationId == selectedConversationId) {

            item.setTextColor(android.graphics.Color.rgb(20, 20, 20));

            item.setTypeface(null, android.graphics.Typeface.BOLD);

            item.setBackgroundColor(android.graphics.Color.rgb(238, 238, 238));

        } else {

            item.setTextColor(android.graphics.Color.rgb(51, 51, 51));

            item.setTypeface(null, android.graphics.Typeface.NORMAL);

            item.setBackgroundResource(R.drawable.bg_drawer_item);
        }
    }

    private TextView createHistoryText(String text, boolean sectionTitle) {

        TextView view = new TextView(this);

        if (sectionTitle) {

            view.setText(text);

            view.setTextSize(12);

            view.setTextColor(android.graphics.Color.rgb(145, 145, 145));

            view.setPadding(dp(12), dp(8), dp(12), dp(6));

        } else {

            view.setText(text);

            view.setTextSize(14);

            view.setTextColor(android.graphics.Color.rgb(51, 51, 51));

            view.setGravity(Gravity.CENTER_VERTICAL);

            view.setSingleLine(true);

            view.setEllipsize(android.text.TextUtils.TruncateAt.END);

            view.setPadding(dp(12), 0, dp(12), 0);

            view.setMinHeight(dp(44));

            view.setBackgroundResource(R.drawable.bg_drawer_item);

            view.setClickable(true);

            view.setFocusable(true);
        }

        return view;
    }

    // =============================================================
    // SELECT CONVERSATION
    // =============================================================

    private void selectConversation(long conversationId) {

        if (isGenerating) {
            return;
        }

        modelStatusText.setText(modelReady ? "Loading conversation..." : "Conversation loaded");

        llmManager.selectConversation(conversationId, new LlmManager.ConversationCallback() {

            @Override
            public void onSuccess(ConversationEntity conversation) {

                currentConversationId = conversation.getId();

                selectedConversationId = conversation.getId();

                loadConversationMessages(currentConversationId);

                updateHistorySelection();

                drawerLayout.closeDrawer(Gravity.START);

                modelStatusText.setText(modelReady ? "Model ready" : "Conversation selected");
            }

            @Override
            public void onError(Exception error) {

                modelStatusText.setText("Could not open chat: " + error.getMessage());

                selectedConversationId = currentConversationId;

                updateHistorySelection();
            }
        });
    }

    // =============================================================
    // LOAD CONVERSATION MESSAGES
    // =============================================================

    private void loadConversationMessages(long conversationId) {

        llmManager.getConversationMessages(conversationId, new LlmManager.MessagesCallback() {

            @Override
            public void onSuccess(List<MessageEntity> messages) {

                messageAdapter.clearMessages();

                if (messages == null || messages.isEmpty()) {

                    emptyState.setVisibility(View.VISIBLE);

                    messagesRecyclerView.setVisibility(View.GONE);

                } else {

                    emptyState.setVisibility(View.GONE);

                    messagesRecyclerView.setVisibility(View.VISIBLE);

                    for (MessageEntity message : messages) {

                        int type = "user".equals(message.getRole()) ? Message.USER : Message.ASSISTANT;

                        messageAdapter.addMessage(new Message(message.getContent(), type));
                    }

                    autoScroll = true;

                    userIsDragging = false;

                    scrollToBottom();
                }
            }

            @Override
            public void onError(Exception error) {

                modelStatusText.setText("Could not load messages");
            }
        });
    }

    // =============================================================
    // NEW CHAT
    // =============================================================

    private void startNewChat() {

        if (isGenerating) {
            return;
        }

        modelStatusText.setText("Creating new chat...");

        llmManager.createNewConversation(new LlmManager.ConversationCallback() {

            @Override
            public void onSuccess(ConversationEntity conversation) {

                currentConversationId = conversation.getId();

                selectedConversationId = conversation.getId();

                messageAdapter.clearMessages();

                emptyState.setVisibility(View.VISIBLE);

                messagesRecyclerView.setVisibility(View.GONE);

                userInput.setText("");

                autoScroll = true;

                userIsDragging = false;

                modelStatusText.setText(modelReady ? "Model ready" : "No model loaded");

                refreshHistory();
            }

            @Override
            public void onError(Exception error) {

                modelStatusText.setText("Could not create chat: " + error.getMessage());
            }
        });
    }

    // =============================================================
    // MODEL PICKER
    // =============================================================

    private void openModelPicker() {

        modelPicker.launch(new String[]{"*/*"});
    }

    // =============================================================
    // IMPORT / LOAD MODEL
    // =============================================================

    private void importAndLoadModel(Uri uri) {

        loadModelButton.setEnabled(false);

        loadModelButton.setText("Copying...");

        modelStatusText.setText("Copying model...");

        modelNameText.setText("Loading...");

        new Thread(() -> {

            try {

                File modelsDir = new File(getFilesDir(), "models");

                if (!modelsDir.exists() && !modelsDir.mkdirs()) {

                    throw new IllegalStateException("Could not create model directory");
                }

                String fileName = getFileName(uri);

                if (fileName == null || fileName.trim().isEmpty()) {

                    fileName = "model.gguf";
                }

                final File modelFile = new File(modelsDir, fileName);

                try (InputStream inputStream = getContentResolver().openInputStream(uri);

                     FileOutputStream outputStream = new FileOutputStream(modelFile)) {

                    if (inputStream == null) {

                        throw new IllegalStateException("Could not open model file");
                    }

                    byte[] buffer = new byte[8192];

                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {

                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                runOnUiThread(() -> {

                    loadModelButton.setText("Loading...");

                    modelStatusText.setText("Loading model...");

                    modelNameText.setText("Loading...");

                    llmManager.loadModel(modelFile.getAbsolutePath(), new LlmManager.LoadCallback() {

                        @Override
                        public void onSuccess() {

                            modelReady = true;

                            isGenerating = false;

                            modelNameText.setText(modelFile.getName());

                            modelStatusText.setText("Model ready");

                            loadModelButton.setVisibility(View.GONE);

                            userInput.setEnabled(true);

                            userInput.setHint("Message...");

                            sendButton.setEnabled(true);

                            microphoneButton.setEnabled(true);

                            microphoneButton.setContentDescription("Voice input");

                            sendButton.setImageResource(R.drawable.ic_send);

                            loadModelButton.setEnabled(true);

                            loadModelButton.setText("Load Model");

                            refreshHistory();
                        }

                        @Override
                        public void onError(Exception error) {

                            modelReady = false;

                            loadModelButton.setVisibility(View.VISIBLE);

                            loadModelButton.setEnabled(true);

                            loadModelButton.setText("Load Model");

                            modelNameText.setText("No model");

                            modelStatusText.setText("Model error: " + error.getMessage());

                            userInput.setEnabled(false);

                            sendButton.setEnabled(false);

                            microphoneButton.setEnabled(false);
                        }
                    });
                });

            } catch (Exception error) {

                runOnUiThread(() -> {

                    loadModelButton.setVisibility(View.VISIBLE);

                    loadModelButton.setEnabled(true);

                    loadModelButton.setText("Load Model");

                    modelNameText.setText("No model");

                    modelStatusText.setText("Error: " + error.getMessage());

                    microphoneButton.setEnabled(false);
                });
            }

        }).start();
    }

    // =============================================================
    // SEND MESSAGE
    // =============================================================

    private void sendMessage() {

        if (!modelReady || isGenerating) {

            return;
        }

        String message = userInput.getText().toString().trim();

        if (message.isEmpty()) {
            return;
        }

        // Make sure voice recognition is not running
        // when the user sends the message.
        if (isListening) {

            speechToTextEngine.cancelListening();

            isListening = false;

            hideListeningUI();

            microphoneButton.setContentDescription(
                "Voice input"
            );
        }

        showChat();

        isGenerating = true;

        autoScroll = true;

        userIsDragging = false;

        sendButton.setEnabled(true);

        sendButton.setImageResource(R.drawable.ic_stop);

        sendButton.setContentDescription("Stop generation");

        modelStatusText.setText("Thinking...");

        messageAdapter.addMessage(new Message(message, Message.USER));

        userInput.setText("");

        messageAdapter.addMessage(new Message("", Message.ASSISTANT));

        messageAdapter.setThinking(true, messagesRecyclerView);

        scrollToBottom();

        refreshHistory();

        final StringBuilder response = new StringBuilder();

        llmManager.sendMessage(message, new LlmManager.ChatCallback() {

            @Override
            public void onToken(String token) {

                if (LlmManager.THINKING_SIGNAL.equals(token)) {

                    messageAdapter.setThinking(true, messagesRecyclerView);

                    modelStatusText.setText("Thinking...");

                    return;
                }

                if (messageAdapter.isThinking()) {

                    messageAdapter.setThinking(false, messagesRecyclerView);
                }

                response.setLength(0);

                response.append(token);

                boolean followResponse = autoScroll && !userIsDragging;

                messageAdapter.updateLastMessage(response.toString(), messagesRecyclerView);

                modelStatusText.setText("Generating...");

                if (followResponse) {

                    keepLastMessageAtBottom();
                }
            }

            @Override
            public void onComplete(String fullResponse) {

                isGenerating = false;

                messageAdapter.setThinking(false, messagesRecyclerView);

                sendButton.setEnabled(true);

                sendButton.setImageResource(R.drawable.ic_send);

                sendButton.setContentDescription("Send message");

                modelStatusText.setText("Model ready");

                refreshHistory();

                if (autoScroll && !userIsDragging) {

                    keepLastMessageAtBottom();
                }
            }

            @Override
            public void onStopped() {

                isGenerating = false;

                messageAdapter.setThinking(false, messagesRecyclerView);

                sendButton.setEnabled(true);

                sendButton.setImageResource(R.drawable.ic_send);

                sendButton.setContentDescription("Send message");

                modelStatusText.setText("Generation stopped");

                refreshHistory();
            }

            @Override
            public void onError(Exception error) {

                isGenerating = false;

                messageAdapter.setThinking(false, messagesRecyclerView);

                response.append("\n\nError: ");

                response.append(error.getMessage());

                messageAdapter.updateLastMessage(response.toString(), messagesRecyclerView);

                sendButton.setEnabled(true);

                sendButton.setImageResource(R.drawable.ic_send);

                sendButton.setContentDescription("Send message");

                modelStatusText.setText("Generation error");

                refreshHistory();

                if (autoScroll && !userIsDragging) {

                    keepLastMessageAtBottom();
                }
            }
        });
    }

    // =============================================================
    // STOP
    // =============================================================

    private void stopGeneration() {

        if (!isGenerating) {
            return;
        }

        llmManager.stopGeneration();

        isGenerating = false;

        messageAdapter.setThinking(false, messagesRecyclerView);

        sendButton.setEnabled(true);

        sendButton.setImageResource(R.drawable.ic_send);

        sendButton.setContentDescription("Send message");

        modelStatusText.setText("Generation stopped");
    }

    // =============================================================
    // SHOW CHAT
    // =============================================================

    private void showChat() {

        emptyState.setVisibility(View.GONE);

        messagesRecyclerView.setVisibility(View.VISIBLE);
    }

    // =============================================================
    // SCROLL
    // =============================================================

    private boolean isAtBottom() {

        return !messagesRecyclerView.canScrollVertically(1);
    }

    private void scrollToBottom() {

        if (messageAdapter == null || messagesRecyclerView == null || messageAdapter.getItemCount() == 0) {

            return;
        }

        messagesRecyclerView.post(() -> {

            int lastPosition = messageAdapter.getItemCount() - 1;

            layoutManager.scrollToPosition(lastPosition);
        });
    }

    private void keepLastMessageAtBottom() {

        if (!autoScroll || userIsDragging) {

            return;
        }

        if (messageAdapter == null || messagesRecyclerView == null || messageAdapter.getItemCount() == 0) {

            return;
        }

        messagesRecyclerView.post(() -> {

            if (!autoScroll || userIsDragging) {

                return;
            }

            int lastPosition = messageAdapter.getItemCount() - 1;

            RecyclerView.ViewHolder holder = messagesRecyclerView.findViewHolderForAdapterPosition(lastPosition);

            if (holder == null) {
                return;
            }

            View lastView = holder.itemView;

            int recyclerBottom = messagesRecyclerView.getHeight() - messagesRecyclerView.getPaddingBottom();

            int difference = lastView.getBottom() - recyclerBottom;

            if (difference > 0) {

                messagesRecyclerView.scrollBy(0, difference);
            }
        });
    }

    // =============================================================
    // DATE HELPERS
    // =============================================================

    private boolean isToday(long timestamp) {

        Calendar target = Calendar.getInstance();

        target.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();

        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR) && target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isYesterday(long timestamp) {

        Calendar target = Calendar.getInstance();

        target.setTimeInMillis(timestamp);

        Calendar yesterday = Calendar.getInstance();

        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        return target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR);
    }

    private int dp(int value) {

        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // =============================================================
    // FILE NAME
    // =============================================================

    private String getFileName(Uri uri) {

        String result = null;

        if ("content".equals(uri.getScheme())) {

            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {

                if (cursor != null && cursor.moveToFirst()) {

                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                    if (index >= 0) {

                        result = cursor.getString(index);
                    }
                }
            }
        }

        if (result == null) {

            result = uri.getPath();

            if (result != null) {

                int cut = result.lastIndexOf('/');

                if (cut != -1) {

                    result = result.substring(cut + 1);
                }
            }
        }

        return result;
    }

    // =============================================================
    // VOICE INPUT
    // =============================================================

    private void startVoiceInput() {

        if (!modelReady || isGenerating) {
            return;
        }

        if (speechToTextEngine == null) {

            modelStatusText.setText(
                "Speech recognition unavailable"
            );

            return;
        }

        if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                new String[]{
                    Manifest.permission.RECORD_AUDIO
                },
                RECORD_AUDIO_REQUEST_CODE
            );

            return;
        }

        isListening = true;

        microphoneButton.setContentDescription(
            "Stop listening"
        );

        modelStatusText.setText(
            "Starting voice input..."
        );

        voiceListeningText.setText(
            "Starting..."
        );

        voiceListeningSubtext.setText(
            "Preparing microphone"
        );

        showListeningUI();

        speechToTextEngine.startListening();
    }

    private void stopVoiceInput() {

        if (speechToTextEngine != null) {

            speechToTextEngine.stopListening();
        }

        isListening = false;

        hideListeningUI();

        microphoneButton.setContentDescription(
            "Voice input"
        );

        modelStatusText.setText(
            modelReady
                ? "Model ready"
                : "No model loaded"
        );
    }

    // =============================================================
    // PERMISSION RESULT
    // =============================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startVoiceInput();

            } else {

                isListening = false;

                microphoneButton.setContentDescription("Voice input");

                modelStatusText.setText("Microphone permission denied");
            }
        }
    }

    // =============================================================
    // DESTROY
    // =============================================================

    @Override
    protected void onDestroy() {

        /*
         * Cancel voice recognition first.
         * This prevents the SpeechRecognizer from
         * continuing to use the Activity after it is destroyed.
         */

        if (speechToTextEngine != null) {

            speechToTextEngine.cancelListening();

            speechToTextEngine.destroy();
        }

        if (llmManager != null) {

            llmManager.destroy();
        }

        super.onDestroy();
    }

    private void showListeningUI() {

        voiceListeningContainer.setVisibility(View.VISIBLE);

        voiceListeningText.setText("Listening...");
        voiceListeningSubtext.setText("Speak now");

        voiceListeningIcon.setScaleX(1.0f);
        voiceListeningIcon.setScaleY(1.0f);

        if (voicePulseAnimator != null) {
            voicePulseAnimator.cancel();
        }

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
            voiceListeningIcon,
            View.SCALE_X,
            1.0f,
            1.18f,
            1.0f
        );

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
            voiceListeningIcon,
            View.SCALE_Y,
            1.0f,
            1.18f,
            1.0f
        );

        scaleX.setDuration(900);
        scaleY.setDuration(900);

        voicePulseAnimator = new AnimatorSet();

        voicePulseAnimator.playTogether(
            scaleX,
            scaleY
        );

        voicePulseAnimator.setInterpolator(
            new AccelerateDecelerateInterpolator()
        );

        voicePulseAnimator.addListener(
            new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {

                    if (isListening) {
                        voicePulseAnimator.start();
                    }
                }
            }
        );

        voicePulseAnimator.start();
    }
    private void hideListeningUI() {

        if (voicePulseAnimator != null) {

            voicePulseAnimator.cancel();

            voicePulseAnimator = null;
        }

        voiceListeningIcon.setScaleX(1.0f);
        voiceListeningIcon.setScaleY(1.0f);

        voiceListeningContainer.setVisibility(View.GONE);
    }
}
