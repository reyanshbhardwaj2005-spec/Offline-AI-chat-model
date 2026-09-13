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

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    // =============================================================
    // SPEAK CALLBACK
    // =============================================================

    public interface OnSpeakClickListener {

        void onSpeakClicked(
            String text,
            int position
        );
    }

    private final List<Message> messages =
        new ArrayList<>();

    private boolean thinking = false;

    private OnSpeakClickListener speakClickListener;

    /*
     * Position of the assistant message currently being spoken.
     *
     * -1 means nothing is currently being spoken.
     */
    private int speakingPosition = -1;

    // =============================================================
    // LISTENER
    // =============================================================

    public void setOnSpeakClickListener(
        OnSpeakClickListener listener
    ) {

        this.speakClickListener = listener;
    }

    // =============================================================
    // CREATE VIEW HOLDER
    // =============================================================

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent,
        int viewType
    ) {

        View view =
            LayoutInflater.from(parent.getContext())
                .inflate(
                    R.layout.item_message,
                    parent,
                    false
                );

        return new MessageViewHolder(view);
    }

    // =============================================================
    // BIND
    // =============================================================

    @Override
    public void onBindViewHolder(
        @NonNull MessageViewHolder holder,
        int position
    ) {

        Message message =
            messages.get(position);

        holder.messageText.setText(
            message.getContent()
        );

        LinearLayout.LayoutParams params =
            (LinearLayout.LayoutParams)
                holder.messageText.getLayoutParams();

        // =========================================================
        // USER MESSAGE
        // =========================================================

        if (message.getType() == Message.USER) {

            params.gravity = Gravity.END;

            holder.messageText.setBackground(
                createBubble(
                    Color.rgb(80, 120, 230)
                )
            );

            holder.messageText.setTextColor(
                Color.WHITE
            );

            /*
             * User messages don't need TTS.
             */
            holder.speakButton.setVisibility(
                View.GONE
            );

            holder.speakButton.setOnClickListener(
                null
            );

        }

        // =========================================================
        // ASSISTANT MESSAGE
        // =========================================================

        else {

            params.gravity = Gravity.START;

            holder.messageText.setBackground(
                createBubble(
                    Color.rgb(235, 235, 235)
                )
            );

            holder.messageText.setTextColor(
                Color.BLACK
            );

            /*
             * Show TTS button only when there is actual content.
             *
             * During "Thinking..." or an empty streaming message,
             * the button stays hidden.
             */
            String content =
                message.getContent();

            if (content != null
                && !content.trim().isEmpty()
                && !content.equals("Thinking...")) {

                holder.speakButton.setVisibility(
                    View.VISIBLE
                );

                updateSpeakButton(
                    holder,
                    position
                );

                holder.speakButton.setOnClickListener(
                    v -> {

                        if (speakClickListener != null) {

                            speakClickListener.onSpeakClicked(
                                message.getContent(),
                                position
                            );
                        }
                    }
                );

            } else {

                holder.speakButton.setVisibility(
                    View.GONE
                );

                holder.speakButton.setOnClickListener(
                    null
                );
            }
        }

        holder.messageText.setLayoutParams(
            params
        );
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // =============================================================
    // UPDATE SPEAK BUTTON
    // =============================================================

    private void updateSpeakButton(
        MessageViewHolder holder,
        int position
    ) {

        if (position == speakingPosition) {

            holder.speakButton.setText(
                "⏹ Stop"
            );

            holder.speakButton.setContentDescription(
                "Stop speaking"
            );

        } else {

            holder.speakButton.setText(
                "🔊 Speak"
            );

            holder.speakButton.setContentDescription(
                "Speak response"
            );
        }
    }

    // =============================================================
    // SET SPEAKING POSITION
    // =============================================================

    public void setSpeakingPosition(
        int position
    ) {

        int oldPosition =
            speakingPosition;

        speakingPosition =
            position;

        /*
         * Refresh only the affected buttons.
         *
         * We don't use notifyDataSetChanged() because that would
         * unnecessarily refresh the entire conversation.
         */

        if (oldPosition >= 0
            && oldPosition < messages.size()) {

            notifyItemChanged(oldPosition);
        }

        if (position >= 0
            && position < messages.size()
            && position != oldPosition) {

            notifyItemChanged(position);
        }
    }

    // =============================================================
    // CLEAR SPEAKING
    // =============================================================

    public void clearSpeakingPosition() {

        if (speakingPosition < 0) {
            return;
        }

        int oldPosition =
            speakingPosition;

        speakingPosition = -1;

        if (oldPosition >= 0
            && oldPosition < messages.size()) {

            notifyItemChanged(oldPosition);
        }
    }

    // =============================================================
    // ADD MESSAGE
    // =============================================================

    public void addMessage(
        Message message
    ) {

        messages.add(message);

        notifyItemInserted(
            messages.size() - 1
        );
    }

    // =============================================================
    // UPDATE LAST MESSAGE
    // =============================================================

    public void updateLastMessage(
        String content,
        RecyclerView recyclerView
    ) {

        if (messages.isEmpty()) {
            return;
        }

        int lastPosition =
            messages.size() - 1;

        messages.get(lastPosition)
            .setContent(content);

        /*
         * Get currently visible ViewHolder.
         */
        RecyclerView.ViewHolder holder =
            recyclerView.findViewHolderForAdapterPosition(
                lastPosition
            );

        /*
         * Update only the TextView.
         *
         * We deliberately don't call notifyItemChanged()
         * during streaming because that caused the UI jumping.
         */
        if (holder instanceof MessageViewHolder) {

            MessageViewHolder messageHolder =
                (MessageViewHolder) holder;

            messageHolder.messageText.setText(
                content
            );

            /*
             * The Speak button should appear once actual
             * assistant content starts arriving.
             */
            if (messages.get(lastPosition).getType()
                == Message.ASSISTANT
                && content != null
                && !content.trim().isEmpty()
                && !content.equals("Thinking...")) {

                messageHolder.speakButton.setVisibility(
                    View.VISIBLE
                );

                updateSpeakButton(
                    messageHolder,
                    lastPosition
                );

                messageHolder.speakButton.setOnClickListener(
                    v -> {

                        if (speakClickListener != null) {

                            speakClickListener.onSpeakClicked(
                                messages.get(lastPosition)
                                    .getContent(),
                                lastPosition
                            );
                        }
                    }
                );

            } else {

                messageHolder.speakButton.setVisibility(
                    View.GONE
                );
            }
        }
    }

    // =============================================================
    // THINKING
    // =============================================================

    public void setThinking(
        boolean thinking,
        RecyclerView recyclerView
    ) {

        this.thinking = thinking;

        if (messages.isEmpty()) {
            return;
        }

        int lastPosition =
            messages.size() - 1;

        if (thinking) {

            messages.get(lastPosition)
                .setContent("Thinking...");
        }

        RecyclerView.ViewHolder holder =
            recyclerView.findViewHolderForAdapterPosition(
                lastPosition
            );

        if (holder instanceof MessageViewHolder) {

            MessageViewHolder messageHolder =
                (MessageViewHolder) holder;

            messageHolder.messageText.setText(
                messages.get(lastPosition)
                    .getContent()
            );

            /*
             * Hide TTS while thinking.
             */
            messageHolder.speakButton.setVisibility(
                View.GONE
            );
        }
    }

    // =============================================================
    // IS THINKING
    // =============================================================

    public boolean isThinking() {

        return thinking;
    }

    // =============================================================
    // LAST MESSAGE
    // =============================================================

    public String getLastMessage() {

        if (messages.isEmpty()) {
            return "";
        }

        return messages
            .get(messages.size() - 1)
            .getContent();
    }

    // =============================================================
    // CREATE BUBBLE
    // =============================================================

    private GradientDrawable createBubble(
        int color
    ) {

        GradientDrawable drawable =
            new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(32);

        return drawable;
    }

    // =============================================================
    // VIEW HOLDER
    // =============================================================

    static class MessageViewHolder
        extends RecyclerView.ViewHolder {

        TextView messageText;

        Button speakButton;

        MessageViewHolder(
            @NonNull View itemView
        ) {

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

    // =============================================================
    // CLEAR
    // =============================================================

    public void clearMessages() {

        messages.clear();

        thinking = false;

        speakingPosition = -1;

        notifyDataSetChanged();
    }
}
