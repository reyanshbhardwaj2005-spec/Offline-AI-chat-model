//package com.example.llama;
//
//import android.database.Cursor;
//import android.net.Uri;
//import android.os.Bundle;
//import android.provider.OpenableColumns;
//import android.view.View;
//import android.widget.EditText;
//import android.widget.ImageButton;
//import android.widget.TextView;
//
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.view.WindowCompat;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.InputStream;
//
//public class MainActivityTest extends AppCompatActivity {
//
//    private TextView statusText;
//    private EditText userInput;
//    private ImageButton sendButton;
//    private RecyclerView messagesRecyclerView;
//
//    private MessageAdapter messageAdapter;
//    private LlmManager llmManager;
//
//    private boolean modelReady = false;
//    private boolean isGenerating = false;
//
//    private View emptyState;
//    private View composerContainer;
//
//
//    // =========================================================
//    // MODEL PICKER
//    // =========================================================
//
//    private final ActivityResultLauncher<String[]> modelPicker =
//        registerForActivityResult(
//            new ActivityResultContracts.OpenDocument(),
//            uri -> {
//
//                if (uri != null) {
//                    importAndLoadModel(uri);
//                }
//
//            }
//        );
//
//
//    // =========================================================
//    // ON CREATE
//    // =========================================================
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//
//        super.onCreate(savedInstanceState);
//
//        setContentView(R.layout.activity_main);
//        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
//
//        View rootLayout = findViewById(R.id.rootLayout);
//        View composer = findViewById(R.id.composerContainer);
//
////        ViewCompat.setOnApplyWindowInsetsListener(
////            rootLayout,
////            (view, windowInsets) -> {
////
////                Insets systemBars =
////                    windowInsets.getInsets(
////                        WindowInsetsCompat.Type.systemBars()
////                    );
////
////                Insets ime =
////                    windowInsets.getInsets(
////                        WindowInsetsCompat.Type.ime()
////                    );
////
////                int bottomInset =
////                    Math.max(systemBars.bottom, ime.bottom);
////
////                composer.setPadding(
////                    composer.getPaddingLeft(),
////                    composer.getPaddingTop(),
////                    composer.getPaddingRight(),
////                    bottomInset
////                );
////
////                return windowInsets;
////            }
////        );
//
//        /*
//         * Let Android resize the activity when the keyboard opens.
//         *
//         * IMPORTANT:
//         * We are NOT manually translating the composer.
//         * The previous WindowInsets translation was causing
//         * the composer to move too far above the keyboard.
//         */
////        getWindow().setSoftInputMode(
////            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
////        );
//
//
//        // =====================================================
//        // FIND VIEWS
//        // =====================================================
//
//        statusText = findViewById(R.id.statusText);
//
//        userInput = findViewById(R.id.user_input);
//
//        sendButton = findViewById(R.id.sendButton);
//
//        messagesRecyclerView = findViewById(R.id.messages);
//
//        emptyState = findViewById(R.id.emptyState);
//
//        composerContainer = findViewById(
//            R.id.composerContainer
//        );
//
//
//        // =====================================================
//        // RECYCLER VIEW
//        // =====================================================
//
//        LinearLayoutManager layoutManager =
//            new LinearLayoutManager(this);
//
//        messagesRecyclerView.setLayoutManager(layoutManager);
//
//        messageAdapter = new MessageAdapter();
//
//        messagesRecyclerView.setAdapter(messageAdapter);
//
//
//        // =====================================================
//        // LLM MANAGER
//        // =====================================================
//
//        llmManager = new LlmManager(this);
//
//
//        // =====================================================
//        // SEND BUTTON
//        // =====================================================
//
//        sendButton.setOnClickListener(v -> {
//
//            /*
//             * No model loaded
//             */
//            if (!modelReady) {
//
//                openModelPicker();
//
//            }
//
//            /*
//             * Model is generating
//             */
//            else if (isGenerating) {
//
//                stopGeneration();
//
//            }
//
//            /*
//             * Normal message
//             */
//            else {
//
//                sendMessage();
//
//            }
//
//        });
//
//
//        // =====================================================
//        // INITIAL STATE
//        // =====================================================
//
//        statusText.setText("Model not loaded");
//
//        userInput.setHint("Load a model first...");
//    }
//
//
//    // =========================================================
//    // OPEN MODEL PICKER
//    // =========================================================
//
//    private void openModelPicker() {
//
//        modelPicker.launch(
//            new String[]{"*/*"}
//        );
//    }
//
//
//    // =========================================================
//    // IMPORT AND LOAD MODEL
//    // =========================================================
//
//    private void importAndLoadModel(Uri uri) {
//
//        statusText.setText("Copying model...");
//
//
//        new Thread(() -> {
//
//            try {
//
//                // =============================================
//                // CREATE MODELS DIRECTORY
//                // =============================================
//
//                File modelsDir =
//                    new File(
//                        getFilesDir(),
//                        "models"
//                    );
//
//
//                if (!modelsDir.exists()) {
//
//                    modelsDir.mkdirs();
//
//                }
//
//
//                // =============================================
//                // GET FILE NAME
//                // =============================================
//
//                String fileName =
//                    getFileName(uri);
//
//
//                if (fileName == null ||
//                    fileName.isEmpty()) {
//
//                    fileName = "model.gguf";
//
//                }
//
//
//                File modelFile =
//                    new File(
//                        modelsDir,
//                        fileName
//                    );
//
//
//                // =============================================
//                // COPY MODEL
//                // =============================================
//
//                try (
//                    InputStream inputStream =
//                        getContentResolver()
//                            .openInputStream(uri);
//
//                    FileOutputStream outputStream =
//                        new FileOutputStream(modelFile)
//                ) {
//
//                    byte[] buffer =
//                        new byte[8192];
//
//                    int bytesRead;
//
//
//                    while (
//                        (bytesRead =
//                            inputStream.read(buffer))
//                            != -1
//                    ) {
//
//                        outputStream.write(
//                            buffer,
//                            0,
//                            bytesRead
//                        );
//
//                    }
//
//                }
//
//
//                // =============================================
//                // LOAD MODEL
//                // =============================================
//
//                runOnUiThread(() -> {
//
//                    statusText.setText(
//                        "Loading model..."
//                    );
//
//
//                    llmManager.loadModel(
//                        modelFile.getAbsolutePath(),
//
//                        new LlmManager.LoadCallback() {
//
//                            @Override
//                            public void onSuccess() {
//
//                                modelReady = true;
//
//                                statusText.setText(
//                                    "Model ready"
//                                );
//
//                                userInput.setHint(
//                                    "Message..."
//                                );
//
//                                sendButton.setContentDescription(
//                                    "Send message"
//                                );
//                            }
//
//
//                            @Override
//                            public void onError(
//                                Exception error
//                            ) {
//
//                                modelReady = false;
//
//                                statusText.setText(
//                                    "Error: "
//                                        + error.getMessage()
//                                );
//
//                                userInput.setHint(
//                                    "Model failed to load"
//                                );
//                            }
//                        }
//                    );
//
//                });
//
//
//            } catch (Exception e) {
//
//                runOnUiThread(() -> {
//
//                    statusText.setText(
//                        "Error: " + e.getMessage()
//                    );
//
//                });
//
//            }
//
//        }).start();
//    }
//
//
//    // =========================================================
//    // SEND MESSAGE
//    // =========================================================
//
//    private void sendMessage() {
//
//        String message =
//            userInput
//                .getText()
//                .toString()
//                .trim();
//
//
//        // Don't send empty messages
//
//        if (message.isEmpty()) {
//
//            return;
//
//        }
//
//
//        // =====================================================
//        // SHOW CHAT
//        // =====================================================
//
//        showChat();
//
//
//        // =====================================================
//        // GENERATION STARTED
//        // =====================================================
//
//        isGenerating = true;
//
//
//        sendButton.setEnabled(true);
//
//        sendButton.setImageResource(
//            R.drawable.ic_stop
//        );
//
//        sendButton.setContentDescription(
//            "Stop generation"
//        );
//
//
//        statusText.setText(
//            "Generating..."
//        );
//
//
//        // =====================================================
//        // ADD USER MESSAGE
//        // =====================================================
//
//        messageAdapter.addMessage(
//            new Message(
//                message,
//                Message.USER
//            )
//        );
//
//
//        scrollToBottom();
//
//
//        // Clear input
//
//        userInput.setText("");
//
//
//        // =====================================================
//        // ADD EMPTY AI MESSAGE
//        // =====================================================
//
//        messageAdapter.addMessage(
//            new Message(
//                "",
//                Message.ASSISTANT
//            )
//        );
//
//
//        scrollToBottom();
//
//
//        // =====================================================
//        // GET ACTIVE AI MESSAGE HOLDER
//        // =====================================================
//
//        messagesRecyclerView.post(() -> {
//
//            MessageAdapter.MessageViewHolder holder =
//                messageAdapter.getLastViewHolder(
//                    messagesRecyclerView
//                );
//
//
//            if (holder != null) {
//
//                messageAdapter.setActiveAssistantHolder(
//                    holder
//                );
//
//            }
//
//        });
//
//
//        // =====================================================
//        // RESPONSE BUFFER
//        // =====================================================
//
//        final StringBuilder response =
//            new StringBuilder();
//
//
//        // =====================================================
//        // SEND TO LOCAL LLM
//        // =====================================================
//
//        llmManager.sendMessage(
//            message,
//
//            new LlmManager.ChatCallback() {
//
//                // =========================================
//                // TOKEN
//                // =========================================
//
//                @Override
//                public void onToken(String token) {
//
//                    runOnUiThread(() -> {
//
//                        response.append(token);
//
//
//                        messageAdapter.updateLastMessage(
//                            response.toString()
//                        );
//
//
//                        /*
//                         * Keep latest generated text visible.
//                         */
//
//                        messagesRecyclerView.post(() -> {
//
//                            if (!isFinishing()) {
//
//                                messagesRecyclerView.scrollToPosition(
//                                    messageAdapter.getItemCount() - 1
//                                );
//
//                            }
//
//                        });
//
//                    });
//
//                }
//
//
//                // =========================================
//                // COMPLETE
//                // =========================================
//
//                @Override
//                public void onComplete() {
//
//                    runOnUiThread(() -> {
//
//                        isGenerating = false;
//
//
//                        sendButton.setEnabled(true);
//
//
//                        sendButton.setImageResource(
//                            R.drawable.ic_send
//                        );
//
//
//                        sendButton.setContentDescription(
//                            "Send message"
//                        );
//
//
//                        statusText.setText(
//                            "Model ready"
//                        );
//
//
//                        scrollToBottom();
//
//                    });
//
//                }
//
//
//                // =========================================
//                // STOPPED
//                // =========================================
//
//                @Override
//                public void onStopped() {
//
//                    runOnUiThread(() -> {
//
//                        isGenerating = false;
//
//
//                        sendButton.setEnabled(true);
//
//
//                        sendButton.setImageResource(
//                            R.drawable.ic_send
//                        );
//
//
//                        sendButton.setContentDescription(
//                            "Send message"
//                        );
//
//
//                        statusText.setText(
//                            "Generation stopped"
//                        );
//
//
//                        scrollToBottom();
//
//                    });
//
//                }
//
//
//                // =========================================
//                // ERROR
//                // =========================================
//
//                @Override
//                public void onError(
//                    Exception error
//                ) {
//
//                    runOnUiThread(() -> {
//
//                        isGenerating = false;
//
//
//                        response.append(
//                            "\n\nError: "
//                                + error.getMessage()
//                        );
//
//
//                        messageAdapter.updateLastMessage(
//                            response.toString()
//                        );
//
//
//                        sendButton.setEnabled(true);
//
//
//                        sendButton.setImageResource(
//                            R.drawable.ic_send
//                        );
//
//
//                        sendButton.setContentDescription(
//                            "Send message"
//                        );
//
//
//                        statusText.setText(
//                            "Generation error"
//                        );
//
//
//                        scrollToBottom();
//
//                    });
//
//                }
//
//            }
//        );
//    }
//
//
//    // =========================================================
//    // SCROLL TO BOTTOM
//    // =========================================================
//
//    private void scrollToBottom() {
//
//        if (messageAdapter.getItemCount() == 0) {
//
//            return;
//
//        }
//
//
//        messagesRecyclerView.post(() -> {
//
//            int lastPosition =
//                messageAdapter.getItemCount() - 1;
//
//
//            if (lastPosition >= 0) {
//
//                messagesRecyclerView.scrollToPosition(
//                    lastPosition
//                );
//
//            }
//
//        });
//    }
//
//
//    // =========================================================
//    // GET FILE NAME
//    // =========================================================
//
//    private String getFileName(Uri uri) {
//
//        String result = null;
//
//
//        if ("content".equals(uri.getScheme())) {
//
//            try (
//                Cursor cursor =
//                    getContentResolver().query(
//                        uri,
//                        null,
//                        null,
//                        null,
//                        null
//                    )
//            ) {
//
//                if (
//                    cursor != null &&
//                        cursor.moveToFirst()
//                ) {
//
//                    int index =
//                        cursor.getColumnIndex(
//                            OpenableColumns.DISPLAY_NAME
//                        );
//
//
//                    if (index >= 0) {
//
//                        result =
//                            cursor.getString(index);
//
//                    }
//
//                }
//
//            }
//
//        }
//
//
//        // =============================================
//        // FALLBACK
//        // =============================================
//
//        if (result == null) {
//
//            result = uri.getPath();
//
//
//            if (result != null) {
//
//                int cut =
//                    result.lastIndexOf('/');
//
//
//                if (cut != -1) {
//
//                    result =
//                        result.substring(cut + 1);
//
//                }
//
//            }
//
//        }
//
//
//        return result;
//    }
//
//
//    // =========================================================
//    // STOP GENERATION
//    // =========================================================
//
//    private void stopGeneration() {
//
//        if (!isGenerating) {
//
//            return;
//
//        }
//
//
//        /*
//         * Tell the LLM engine to stop.
//         */
//
//        llmManager.stopGeneration();
//
//
//        /*
//         * Update UI immediately.
//         */
//
//        isGenerating = false;
//
//
//        sendButton.setEnabled(true);
//
//
//        sendButton.setImageResource(
//            R.drawable.ic_send
//        );
//
//
//        sendButton.setContentDescription(
//            "Send message"
//        );
//
//
//        statusText.setText(
//            "Generation stopped"
//        );
//    }
//
//
//    // =========================================================
//    // SHOW CHAT
//    // =========================================================
//
//    private void showChat() {
//
//        emptyState.setVisibility(
//            View.GONE
//        );
//
//
//        messagesRecyclerView.setVisibility(
//            View.VISIBLE
//        );
//    }
//
//
//    // =========================================================
//    // ON DESTROY
//    // =========================================================
//
//    @Override
//    protected void onDestroy() {
//
//        if (llmManager != null) {
//
//            llmManager.destroy();
//
//        }
//
//
//        super.onDestroy();
//    }
//}
