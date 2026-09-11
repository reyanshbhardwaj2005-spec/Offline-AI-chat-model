package com.example.llama;

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
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    // =============================================================
    // CHAT UI
    // =============================================================

    private TextView modelNameText;
    private TextView modelStatusText;

    private EditText userInput;

    private ImageButton sendButton;

    private Button loadModelButton;

    private RecyclerView messagesRecyclerView;

    private MessageAdapter messageAdapter;

    private View emptyState;
    private View composerContainer;

    private LinearLayoutManager layoutManager;


    // =============================================================
    // NAVIGATION DRAWER
    // =============================================================

    private DrawerLayout drawerLayout;

    private View drawerNewChat;
    private View drawerModels;
    private View drawerImages;
    private View drawerSettings;
    private View drawerProfile;


    // =============================================================
    // LLM
    // =============================================================

    private LlmManager llmManager;

    private boolean modelReady = false;

    private boolean isGenerating = false;


    // =============================================================
    // SCROLL CONTROL
    // =============================================================

    /*
     * True when RecyclerView should automatically follow
     * the generated response.
     */
    private boolean autoScroll = true;

    /*
     * True while the user is manually dragging the RecyclerView.
     */
    private boolean userIsDragging = false;


    // =============================================================
    // MODEL PICKER
    // =============================================================

    private final ActivityResultLauncher<String[]> modelPicker =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {

                if (uri != null) {
                    importAndLoadModel(uri);
                }

            }
        );


    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        // =========================================================
        // KEYBOARD
        // =========================================================

        getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );


        // =========================================================
        // CHAT VIEWS
        // =========================================================

        modelNameText =
            findViewById(R.id.modelNameText);

        modelStatusText =
            findViewById(R.id.modelStatusText);

        userInput =
            findViewById(R.id.user_input);

        sendButton =
            findViewById(R.id.sendButton);

        loadModelButton =
            findViewById(R.id.loadModelButton);

        messagesRecyclerView =
            findViewById(R.id.messages);

        emptyState =
            findViewById(R.id.emptyState);

        composerContainer =
            findViewById(R.id.composerContainer);


        // =========================================================
        // NAVIGATION DRAWER
        // =========================================================

        drawerLayout =
            findViewById(R.id.drawerLayout);

        drawerNewChat =
            findViewById(R.id.drawerNewChat);

        drawerModels =
            findViewById(R.id.drawerModels);

        drawerImages =
            findViewById(R.id.drawerImages);

        drawerSettings =
            findViewById(R.id.drawerSettings);

        drawerProfile =
            findViewById(R.id.drawerProfile);


        // =========================================================
        // MENU BUTTON
        // =========================================================

        /*
         * IMPORTANT:
         *
         * This assumes your menu ImageButton in activity_main.xml
         * has:
         *
         * android:id="@+id/menuButton"
         */

        ImageButton menuButton =
            findViewById(R.id.menuButton);

        menuButton.setOnClickListener(v -> {

            drawerLayout.openDrawer(
                Gravity.START
            );

        });


        // =========================================================
        // INITIAL MODEL STATE
        // =========================================================

        modelReady = false;

        isGenerating = false;

        autoScroll = true;

        userIsDragging = false;


        modelNameText.setText(
            "No model"
        );

        modelStatusText.setText(
            "No model loaded"
        );

        loadModelButton.setText(
            "Load Model"
        );

        loadModelButton.setEnabled(
            true
        );

        sendButton.setEnabled(
            false
        );

        userInput.setEnabled(
            false
        );

        userInput.setHint(
            "Load a model to start chatting..."
        );


        // =========================================================
        // RECYCLER VIEW
        // =========================================================

        layoutManager =
            new LinearLayoutManager(this);

        layoutManager.setStackFromEnd(false);

        messagesRecyclerView.setLayoutManager(
            layoutManager
        );

        /*
         * Message height changes while the AI is generating.
         */
        messagesRecyclerView.setHasFixedSize(
            false
        );

        /*
         * Disable item animations.
         */
        messagesRecyclerView.setItemAnimator(
            null
        );


        messageAdapter =
            new MessageAdapter();

        messagesRecyclerView.setAdapter(
            messageAdapter
        );


        // =========================================================
        // RECYCLER VIEW SCROLL LISTENER
        // =========================================================

        messagesRecyclerView.addOnScrollListener(
            new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(
                    @NonNull RecyclerView recyclerView,
                    int newState
                ) {

                    super.onScrollStateChanged(
                        recyclerView,
                        newState
                    );


                    // -----------------------------------------
                    // USER STARTED DRAGGING
                    // -----------------------------------------

                    if (newState ==
                        RecyclerView.SCROLL_STATE_DRAGGING) {

                        userIsDragging = true;

                        /*
                         * Give complete control to the user.
                         */
                        autoScroll = false;
                    }


                    // -----------------------------------------
                    // USER STOPPED DRAGGING
                    // -----------------------------------------

                    else if (newState ==
                        RecyclerView.SCROLL_STATE_IDLE) {

                        userIsDragging = false;

                        /*
                         * Only resume automatic scrolling if
                         * the user reached the bottom.
                         */
                        autoScroll = isAtBottom();
                    }
                }


                @Override
                public void onScrolled(
                    @NonNull RecyclerView recyclerView,
                    int dx,
                    int dy
                ) {

                    super.onScrolled(
                        recyclerView,
                        dx,
                        dy
                    );


                    /*
                     * Do not change auto-scroll state while
                     * user is actively dragging.
                     */
                    if (userIsDragging) {
                        return;
                    }


                    /*
                     * If user is at bottom, allow automatic
                     * following again.
                     */
                    if (!recyclerView.canScrollVertically(1)) {

                        autoScroll = true;
                    }
                }
            }
        );


        // =========================================================
        // LLM MANAGER
        // =========================================================

        llmManager =
            new LlmManager(this);


        // =========================================================
        // LOAD MODEL BUTTON
        // =========================================================

        loadModelButton.setOnClickListener(v -> {

            if (!modelReady && !isGenerating) {

                openModelPicker();
            }

        });


        // =========================================================
        // SEND / STOP BUTTON
        // =========================================================

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


        // =========================================================
        // DRAWER ACTIONS
        // =========================================================

        setupDrawerActions();
    }


    // =============================================================
    // DRAWER ACTIONS
    // =============================================================

    private void setupDrawerActions() {


        // =========================================================
        // NEW CHAT
        // =========================================================

        drawerNewChat.setOnClickListener(v -> {

            drawerLayout.closeDrawer(
                Gravity.START
            );

            startNewChat();
        });


        // =========================================================
        // MODELS
        // =========================================================

        drawerModels.setOnClickListener(v -> {

            drawerLayout.closeDrawer(
                Gravity.START
            );

            /*
             * For now this is just a placeholder.
             *
             * Later we'll create a dedicated Models screen.
             */
            modelStatusText.setText(
                "Model management coming soon"
            );
        });


        // =========================================================
        // IMAGES
        // =========================================================

        drawerImages.setOnClickListener(v -> {

            drawerLayout.closeDrawer(
                Gravity.START
            );

            /*
             * Placeholder for the future image section.
             */
            modelStatusText.setText(
                "Images coming soon"
            );
        });


        // =========================================================
        // SETTINGS
        // =========================================================

        drawerSettings.setOnClickListener(v -> {

            drawerLayout.closeDrawer(
                Gravity.START
            );

            /*
             * Placeholder for Settings screen.
             */
            modelStatusText.setText(
                "Settings coming soon"
            );
        });


        // =========================================================
        // PROFILE
        // =========================================================

        drawerProfile.setOnClickListener(v -> {

            drawerLayout.closeDrawer(
                Gravity.START
            );

            /*
             * Placeholder for Profile screen.
             */
            modelStatusText.setText(
                "Profile coming soon"
            );
        });
    }


    // =============================================================
    // NEW CHAT
    // =============================================================

    private void startNewChat() {

        /*
         * Do not allow a new chat while the model is generating.
         */
        if (isGenerating) {
            return;
        }


        // Clear current messages

        messageAdapter.clearMessages();


        // Show empty state

        emptyState.setVisibility(
            View.VISIBLE
        );


        // Hide RecyclerView

        messagesRecyclerView.setVisibility(
            View.GONE
        );


        // Reset input

        userInput.setText("");


        // Reset scrolling

        autoScroll = true;

        userIsDragging = false;


        // Reset status

        if (modelReady) {

            modelStatusText.setText(
                "Model ready"
            );

        } else {

            modelStatusText.setText(
                "No model loaded"
            );
        }
    }


    // =============================================================
    // OPEN MODEL PICKER
    // =============================================================

    private void openModelPicker() {

        modelPicker.launch(
            new String[]{"*/*"}
        );
    }


    // =============================================================
    // IMPORT AND LOAD MODEL
    // =============================================================

    private void importAndLoadModel(Uri uri) {

        runOnUiThread(() -> {

            loadModelButton.setEnabled(
                false
            );

            loadModelButton.setText(
                "Copying..."
            );

            modelStatusText.setText(
                "Copying model..."
            );

            modelNameText.setText(
                "Loading..."
            );
        });


        new Thread(() -> {

            try {

                File modelsDir =
                    new File(
                        getFilesDir(),
                        "models"
                    );


                if (!modelsDir.exists()) {

                    modelsDir.mkdirs();
                }


                String fileName =
                    getFileName(uri);


                if (fileName == null ||
                    fileName.trim().isEmpty()) {

                    fileName =
                        "model.gguf";
                }


                final String finalFileName =
                    fileName;


                File modelFile =
                    new File(
                        modelsDir,
                        finalFileName
                    );


                // -------------------------------------------------
                // COPY MODEL
                // -------------------------------------------------

                try (
                    InputStream inputStream =
                        getContentResolver()
                            .openInputStream(uri);

                    FileOutputStream outputStream =
                        new FileOutputStream(
                            modelFile
                        )
                ) {

                    byte[] buffer =
                        new byte[8192];

                    int bytesRead;

                    while (
                        (bytesRead =
                            inputStream.read(buffer))
                            != -1
                    ) {

                        outputStream.write(
                            buffer,
                            0,
                            bytesRead
                        );
                    }
                }


                // -------------------------------------------------
                // LOAD MODEL
                // -------------------------------------------------

                runOnUiThread(() -> {

                    loadModelButton.setText(
                        "Loading..."
                    );

                    modelStatusText.setText(
                        "Loading model..."
                    );

                    modelNameText.setText(
                        "Loading..."
                    );


                    llmManager.loadModel(
                        modelFile.getAbsolutePath(),

                        new LlmManager.LoadCallback() {

                            @Override
                            public void onSuccess() {

                                modelReady = true;

                                isGenerating = false;


                                modelNameText.setText(
                                    finalFileName
                                );


                                modelStatusText.setText(
                                    "Model loaded successfully"
                                );


                                loadModelButton.setVisibility(
                                    View.GONE
                                );


                                userInput.setEnabled(
                                    true
                                );


                                userInput.setHint(
                                    "Type a message..."
                                );


                                sendButton.setEnabled(
                                    true
                                );


                                sendButton.setImageResource(
                                    R.drawable.ic_send
                                );


                                sendButton.setContentDescription(
                                    "Send message"
                                );
                            }


                            @Override
                            public void onError(
                                Exception error
                            ) {

                                modelReady = false;


                                loadModelButton.setVisibility(
                                    View.VISIBLE
                                );


                                loadModelButton.setEnabled(
                                    true
                                );


                                loadModelButton.setText(
                                    "Load Model"
                                );


                                modelNameText.setText(
                                    "No model"
                                );


                                modelStatusText.setText(
                                    "Error: " +
                                        error.getMessage()
                                );


                                userInput.setEnabled(
                                    false
                                );


                                userInput.setHint(
                                    "Load a model to start chatting..."
                                );


                                sendButton.setEnabled(
                                    false
                                );
                            }
                        }
                    );
                });


            } catch (Exception e) {

                runOnUiThread(() -> {

                    modelReady = false;


                    loadModelButton.setVisibility(
                        View.VISIBLE
                    );


                    loadModelButton.setEnabled(
                        true
                    );


                    loadModelButton.setText(
                        "Load Model"
                    );


                    modelNameText.setText(
                        "No model"
                    );


                    modelStatusText.setText(
                        "Error: " +
                            e.getMessage()
                    );


                    userInput.setEnabled(
                        false
                    );


                    userInput.setHint(
                        "Load a model to start chatting..."
                    );


                    sendButton.setEnabled(
                        false
                    );
                });
            }

        }).start();
    }


    // =============================================================
    // SEND MESSAGE
    // =============================================================

    private void sendMessage() {

        if (!modelReady ||
            isGenerating) {

            return;
        }


        String message =
            userInput
                .getText()
                .toString()
                .trim();


        if (message.isEmpty()) {
            return;
        }


        showChat();


        isGenerating = true;


        /*
         * New message means we want to automatically follow
         * the response.
         */
        autoScroll = true;

        userIsDragging = false;


        // =========================================================
        // STOP BUTTON
        // =========================================================

        sendButton.setEnabled(
            true
        );

        sendButton.setImageResource(
            R.drawable.ic_stop
        );

        sendButton.setContentDescription(
            "Stop generation"
        );


        modelStatusText.setText(
            "Thinking..."
        );


        // =========================================================
        // USER MESSAGE
        // =========================================================

        messageAdapter.addMessage(
            new Message(
                message,
                Message.USER
            )
        );


        userInput.setText("");


        // =========================================================
        // EMPTY ASSISTANT MESSAGE
        // =========================================================

        messageAdapter.addMessage(
            new Message(
                "",
                Message.ASSISTANT
            )
        );


        messageAdapter.setThinking(
            true,
            messagesRecyclerView
        );


        /*
         * Initial scroll only.
         *
         * We do NOT continuously call scrollToPosition()
         * during generation.
         */
        scrollToBottom();


        final StringBuilder response =
            new StringBuilder();


        // =========================================================
        // LLM GENERATION
        // =========================================================

        llmManager.sendMessage(
            message,

            new LlmManager.ChatCallback() {

                // -------------------------------------------------
                // TOKEN
                // -------------------------------------------------

                @Override
                public void onToken(String token) {

                    // ---------------------------------------------
                    // THINKING
                    // ---------------------------------------------

                    if ("__THINKING__".equals(token)) {

                        messageAdapter.setThinking(
                            true,
                            messagesRecyclerView
                        );

                        modelStatusText.setText(
                            "Thinking..."
                        );

                        return;
                    }


                    // ---------------------------------------------
                    // FIRST REAL TOKEN
                    // ---------------------------------------------

                    if (messageAdapter.isThinking()) {

                        messageAdapter.setThinking(
                            false,
                            messagesRecyclerView
                        );
                    }


                    response.append(
                        token
                    );


                    /*
                     * Capture the scroll state BEFORE changing
                     * the height of the TextView.
                     */
                    boolean followResponse =
                        autoScroll &&
                            !userIsDragging;


                    /*
                     * Update only the visible TextView.
                     *
                     * No notifyItemChanged().
                     */
                    messageAdapter.updateLastMessage(
                        response.toString(),
                        messagesRecyclerView
                    );


                    modelStatusText.setText(
                        "Generating..."
                    );


                    /*
                     * Only keep the response at bottom if
                     * the user hasn't taken control.
                     */
                    if (followResponse) {

                        keepLastMessageAtBottom();
                    }
                }


                // -------------------------------------------------
                // COMPLETE
                // -------------------------------------------------

                @Override
                public void onComplete() {

                    isGenerating = false;


                    messageAdapter.setThinking(
                        false,
                        messagesRecyclerView
                    );


                    sendButton.setEnabled(
                        true
                    );


                    sendButton.setImageResource(
                        R.drawable.ic_send
                    );


                    sendButton.setContentDescription(
                        "Send message"
                    );


                    modelStatusText.setText(
                        "Model ready"
                    );


                    if (autoScroll &&
                        !userIsDragging) {

                        keepLastMessageAtBottom();
                    }
                }


                // -------------------------------------------------
                // STOPPED
                // -------------------------------------------------

                @Override
                public void onStopped() {

                    isGenerating = false;


                    messageAdapter.setThinking(
                        false,
                        messagesRecyclerView
                    );


                    sendButton.setEnabled(
                        true
                    );


                    sendButton.setImageResource(
                        R.drawable.ic_send
                    );


                    sendButton.setContentDescription(
                        "Send message"
                    );


                    modelStatusText.setText(
                        "Generation stopped"
                    );
                }


                // -------------------------------------------------
                // ERROR
                // -------------------------------------------------

                @Override
                public void onError(
                    Exception error
                ) {

                    isGenerating = false;


                    messageAdapter.setThinking(
                        false,
                        messagesRecyclerView
                    );


                    response.append(
                        "\n\nError: "
                    ).append(
                        error.getMessage()
                    );


                    messageAdapter.updateLastMessage(
                        response.toString(),
                        messagesRecyclerView
                    );


                    sendButton.setEnabled(
                        true
                    );


                    sendButton.setImageResource(
                        R.drawable.ic_send
                    );


                    sendButton.setContentDescription(
                        "Send message"
                    );


                    modelStatusText.setText(
                        "Generation error"
                    );


                    if (autoScroll &&
                        !userIsDragging) {

                        keepLastMessageAtBottom();
                    }
                }
            }
        );
    }


    // =============================================================
    // STOP GENERATION
    // =============================================================

    private void stopGeneration() {

        if (!isGenerating) {
            return;
        }


        llmManager.stopGeneration();


        isGenerating = false;


        messageAdapter.setThinking(
            false,
            messagesRecyclerView
        );


        sendButton.setEnabled(
            true
        );


        sendButton.setImageResource(
            R.drawable.ic_send
        );


        sendButton.setContentDescription(
            "Send message"
        );


        modelStatusText.setText(
            "Generation stopped"
        );
    }


    // =============================================================
    // SHOW CHAT
    // =============================================================

    private void showChat() {

        emptyState.setVisibility(
            View.GONE
        );

        messagesRecyclerView.setVisibility(
            View.VISIBLE
        );
    }


    // =============================================================
    // CHECK IF AT BOTTOM
    // =============================================================

    private boolean isAtBottom() {

        if (messagesRecyclerView == null) {
            return true;
        }


        return !messagesRecyclerView
            .canScrollVertically(1);
    }


    // =============================================================
    // INITIAL SCROLL
    // =============================================================

    private void scrollToBottom() {

        if (messageAdapter == null ||
            messagesRecyclerView == null ||
            messageAdapter.getItemCount() == 0) {

            return;
        }


        messagesRecyclerView.post(() -> {

            int lastPosition =
                messageAdapter.getItemCount() - 1;


            layoutManager.scrollToPosition(
                lastPosition
            );
        });
    }


    // =============================================================
    // KEEP LAST MESSAGE AT BOTTOM
    // =============================================================

    private void keepLastMessageAtBottom() {

        /*
         * Never automatically scroll while the user is manually
         * scrolling.
         */
        if (!autoScroll ||
            userIsDragging) {

            return;
        }


        if (messageAdapter == null ||
            messagesRecyclerView == null ||
            messageAdapter.getItemCount() == 0) {

            return;
        }


        messagesRecyclerView.post(() -> {

            /*
             * User may have started scrolling after this Runnable
             * was posted.
             */
            if (!autoScroll ||
                userIsDragging) {

                return;
            }


            int lastPosition =
                messageAdapter.getItemCount() - 1;


            RecyclerView.ViewHolder holder =
                messagesRecyclerView
                    .findViewHolderForAdapterPosition(
                        lastPosition
                    );


            if (holder == null) {
                return;
            }


            View lastView =
                holder.itemView;


            int recyclerBottom =
                messagesRecyclerView.getHeight()
                    - messagesRecyclerView
                    .getPaddingBottom();


            /*
             * Calculate how far the last message extends
             * below the visible RecyclerView.
             */
            int difference =
                lastView.getBottom()
                    - recyclerBottom;


            if (difference > 0) {

                /*
                 * Scroll only by the required amount.
                 *
                 * This prevents the jumping/scratching behavior.
                 */
                messagesRecyclerView.scrollBy(
                    0,
                    difference
                );
            }
        });
    }


    // =============================================================
    // GET MODEL FILE NAME
    // =============================================================

    private String getFileName(Uri uri) {

        String result = null;


        if ("content".equals(uri.getScheme())) {

            try (
                Cursor cursor =
                    getContentResolver().query(
                        uri,
                        null,
                        null,
                        null,
                        null
                    )
            ) {

                if (cursor != null &&
                    cursor.moveToFirst()) {

                    int index =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        );


                    if (index >= 0) {

                        result =
                            cursor.getString(index);
                    }
                }
            }
        }


        if (result == null) {

            result =
                uri.getPath();


            if (result != null) {

                int cut =
                    result.lastIndexOf('/');


                if (cut != -1) {

                    result =
                        result.substring(
                            cut + 1
                        );
                }
            }
        }


        return result;
    }


    // =============================================================
    // ON DESTROY
    // =============================================================

    @Override
    protected void onDestroy() {

        if (llmManager != null) {

            llmManager.destroy();
        }


        super.onDestroy();
    }


}
