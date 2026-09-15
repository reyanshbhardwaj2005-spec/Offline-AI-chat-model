package com.example.llama;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter
    extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    public interface OnSpeakClickListener {
        void onSpeakClicked(String text, int position);
    }

    private final List<Message> messages = new ArrayList<>();

    private boolean thinking = false;

    private OnSpeakClickListener speakClickListener;

    /**
     * Position of the assistant message currently being spoken.
     *
     * -1 means nothing is speaking.
     */
    private int speakingPosition = -1;


    // ---------------------------------------------------------
    // Listener
    // ---------------------------------------------------------

    public void setOnSpeakClickListener(OnSpeakClickListener listener) {
        this.speakClickListener = listener;
    }


    // ---------------------------------------------------------
    // RecyclerView
    // ---------------------------------------------------------

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent,
        int viewType) {

        View view = LayoutInflater
            .from(parent.getContext())
            .inflate(
                R.layout.item_message,
                parent,
                false
            );

        return new MessageViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
        @NonNull MessageViewHolder holder,
        int position) {

        Message message = messages.get(position);

        String content = message.getContent();

        if (content == null) {
            content = "";
        }

        holder.messageText.setText(content);


        // -----------------------------------------------------
        // USER MESSAGE
        // -----------------------------------------------------

        if (message.getType() == Message.USER) {

            LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)
                    holder.messageText.getLayoutParams();

            params.gravity = Gravity.END;

            holder.messageText.setLayoutParams(params);

            holder.messageText.setBackground(
                createBubble(
                    Color.rgb(80, 120, 230)
                )
            );

            holder.messageText.setTextColor(Color.WHITE);


            // User messages should never have TTS.

            holder.speakButton.setVisibility(View.GONE);

            holder.speakButton.setOnClickListener(null);

            return;
        }


        // -----------------------------------------------------
        // ASSISTANT MESSAGE
        // -----------------------------------------------------

        LinearLayout.LayoutParams params =
            (LinearLayout.LayoutParams)
                holder.messageText.getLayoutParams();

        params.gravity = Gravity.START;

        holder.messageText.setLayoutParams(params);

        holder.messageText.setBackground(
            createBubble(
                Color.rgb(235, 235, 235)
            )
        );

        holder.messageText.setTextColor(Color.BLACK);


        // -----------------------------------------------------
        // THINKING STATE
        // -----------------------------------------------------

        if (thinking
            && position == messages.size() - 1) {

            holder.speakButton.setVisibility(View.GONE);

            holder.speakButton.setOnClickListener(null);

            return;
        }


        // -----------------------------------------------------
        // ASSISTANT TTS BUTTON
        // -----------------------------------------------------

        if (!content.trim().isEmpty()
            && !content.equals("Thinking...")) {

            holder.speakButton.setVisibility(View.VISIBLE);

            updateSpeakButton(
                holder,
                position
            );


            /*
             * IMPORTANT:
             *
             * Do NOT capture the 'position' supplied to
             * onBindViewHolder().
             *
             * RecyclerView positions can change.
             *
             * Instead, retrieve the current position when
             * the button is actually clicked.
             */
            holder.speakButton.setOnClickListener(v -> {

                int adapterPosition =
                    holder.getBindingAdapterPosition();

                if (adapterPosition
                    == RecyclerView.NO_POSITION) {

                    return;
                }

                if (adapterPosition < 0
                    || adapterPosition >= messages.size()) {

                    return;
                }

                Message currentMessage =
                    messages.get(adapterPosition);

                String currentContent =
                    currentMessage.getContent();

                if (currentContent == null
                    || currentContent.trim().isEmpty()) {

                    return;
                }

                if (speakClickListener != null) {

                    speakClickListener.onSpeakClicked(
                        currentContent,
                        adapterPosition
                    );
                }
            });

        } else {

            holder.speakButton.setVisibility(View.GONE);

            holder.speakButton.setOnClickListener(null);
        }
    }


    @Override
    public int getItemCount() {
        return messages.size();
    }


    // ---------------------------------------------------------
    // TTS Button UI
    // ---------------------------------------------------------

    private void updateSpeakButton(
        MessageViewHolder holder,
        int position) {

        if (position == speakingPosition) {

            holder.speakButton.setText("⏹ Stop");

            holder.speakButton.setContentDescription(
                "Stop speaking"
            );

        } else {

            holder.speakButton.setText("🔊 Speak");

            holder.speakButton.setContentDescription(
                "Speak response"
            );
        }
    }


    // ---------------------------------------------------------
    // Speaking Position
    // ---------------------------------------------------------

    public void setSpeakingPosition(int position) {

        int oldPosition = speakingPosition;

        speakingPosition = position;


        /*
         * Refresh the old speaking message so its button
         * changes from Stop → Speak.
         */
        if (oldPosition >= 0
            && oldPosition < messages.size()) {

            notifyItemChanged(oldPosition);
        }


        /*
         * Refresh the new speaking message so its button
         * changes from Speak → Stop.
         */
        if (position >= 0
            && position < messages.size()
            && position != oldPosition) {

            notifyItemChanged(position);
        }
    }


    public void clearSpeakingPosition() {

        if (speakingPosition < 0) {
            return;
        }

        int oldPosition = speakingPosition;

        speakingPosition = -1;

        if (oldPosition >= 0
            && oldPosition < messages.size()) {

            notifyItemChanged(oldPosition);
        }
    }


    // ---------------------------------------------------------
    // Add Message
    // ---------------------------------------------------------

    public void addMessage(Message message) {

        if (message == null) {
            return;
        }

        messages.add(message);

        notifyItemInserted(
            messages.size() - 1
        );
    }


    // ---------------------------------------------------------
    // Update Last Message
    // ---------------------------------------------------------

    public void updateLastMessage(
        String content,
        RecyclerView recyclerView) {

        if (messages.isEmpty()) {
            return;
        }

        int lastPosition =
            messages.size() - 1;

        Message lastMessage =
            messages.get(lastPosition);

        lastMessage.setContent(
            content == null ? "" : content
        );


        /*
         * During streaming, update the visible TextView
         * directly.
         *
         * This avoids forcing RecyclerView to completely
         * rebind the item for every token.
         */
        RecyclerView.ViewHolder viewHolder =
            recyclerView.findViewHolderForAdapterPosition(
                lastPosition
            );

        if (viewHolder instanceof MessageViewHolder) {

            MessageViewHolder messageHolder =
                (MessageViewHolder) viewHolder;

            messageHolder.messageText.setText(
                lastMessage.getContent()
            );


            /*
             * If the response has become a real assistant
             * response, make sure the TTS button is visible
             * immediately.
             */
            if (lastMessage.getType() == Message.ASSISTANT
                && !thinking
                && lastMessage.getContent() != null
                && !lastMessage.getContent()
                .trim()
                .isEmpty()
                && !lastMessage.getContent()
                .equals("Thinking...")) {

                messageHolder.speakButton.setVisibility(
                    View.VISIBLE
                );

                updateSpeakButton(
                    messageHolder,
                    lastPosition
                );

                /*
                 * Use the ViewHolder's current adapter
                 * position at click time.
                 */
                messageHolder.speakButton.setOnClickListener(
                    v -> {

                        int adapterPosition =
                            messageHolder
                                .getBindingAdapterPosition();

                        if (adapterPosition
                            == RecyclerView.NO_POSITION) {

                            return;
                        }

                        if (adapterPosition < 0
                            || adapterPosition >= messages.size()) {

                            return;
                        }

                        Message currentMessage =
                            messages.get(
                                adapterPosition
                            );

                        String currentContent =
                            currentMessage.getContent();

                        if (currentContent == null
                            || currentContent
                            .trim()
                            .isEmpty()) {

                            return;
                        }

                        if (speakClickListener != null) {

                            speakClickListener.onSpeakClicked(
                                currentContent,
                                adapterPosition
                            );
                        }
                    }
                );

            } else {

                messageHolder.speakButton.setVisibility(
                    View.GONE
                );

                messageHolder.speakButton.setOnClickListener(
                    null
                );
            }

        } else {

            /*
             * IMPORTANT FIX:
             *
             * The ViewHolder may not exist yet.
             *
             * Instead of waiting for scrolling to cause a
             * rebind, explicitly ask RecyclerView to rebind
             * the item on the next UI pass.
             */
            recyclerView.post(() -> {

                if (lastPosition >= 0
                    && lastPosition < messages.size()) {

                    notifyItemChanged(
                        lastPosition
                    );
                }
            });
        }
    }


    // ---------------------------------------------------------
    // Finish Last Message
    // ---------------------------------------------------------

    /**
     * Call this when the assistant response has completely
     * finished streaming.
     *
     * This guarantees that RecyclerView performs a proper
     * final bind and therefore the Speak button is correctly
     * displayed.
     */
    public void finishLastMessage(
        RecyclerView recyclerView) {

        if (messages.isEmpty()) {
            return;
        }

        int lastPosition =
            messages.size() - 1;

        recyclerView.post(() -> {

            if (lastPosition >= 0
                && lastPosition < messages.size()) {

                notifyItemChanged(
                    lastPosition
                );
            }
        });
    }


    // ---------------------------------------------------------
    // Thinking
    // ---------------------------------------------------------

    public void setThinking(
        boolean thinking,
        RecyclerView recyclerView) {

        this.thinking = thinking;

        if (messages.isEmpty()) {
            return;
        }

        int lastPosition = messages.size() - 1;

        /*
         * When entering thinking state, change the message text.
         */
        if (thinking) {

            messages.get(lastPosition)
                .setContent("Thinking...");
        }

        /*
         * IMPORTANT:
         *
         * Do not manually hide the Speak button here when
         * thinking becomes false.
         *
         * When thinking finishes, RecyclerView must perform a
         * complete bind so onBindViewHolder() can decide whether
         * the Speak button should be visible.
         */
        recyclerView.post(() -> {

            if (lastPosition >= 0
                && lastPosition < messages.size()) {

                notifyItemChanged(lastPosition);
            }
        });
    }


    public boolean isThinking() {
        return thinking;
    }


    // ---------------------------------------------------------
    // Get Last Message
    // ---------------------------------------------------------

    public String getLastMessage() {

        if (messages.isEmpty()) {
            return "";
        }

        String content =
            messages.get(
                messages.size() - 1
            ).getContent();

        return content == null ? "" : content;
    }


    // ---------------------------------------------------------
    // Bubble
    // ---------------------------------------------------------

    private GradientDrawable createBubble(
        int color) {

        GradientDrawable drawable =
            new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(
            24f
        );

        return drawable;
    }


    // ---------------------------------------------------------
    // ViewHolder
    // ---------------------------------------------------------

    static class MessageViewHolder
        extends RecyclerView.ViewHolder {

        TextView messageText;

        Button speakButton;


        MessageViewHolder(
            @NonNull View itemView) {

            super(itemView);

            messageText =
                itemView.findViewById(
                    R.id.messageText
                );

            speakButton =
                itemView.findViewById(
                    R.id.speakButton
                );
        }
    }


    // ---------------------------------------------------------
    // Clear Messages
    // ---------------------------------------------------------

    public void clearMessages() {

        messages.clear();

        thinking = false;

        speakingPosition = -1;

        notifyDataSetChanged();
    }
}
