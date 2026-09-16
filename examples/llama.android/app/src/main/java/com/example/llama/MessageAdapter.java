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
        void onSpeakClicked(String text, int position);
    }

    // =============================================================
    // DATA
    // =============================================================
    private final List<Message> messages = new ArrayList<>();
    private boolean thinking = false;
    private OnSpeakClickListener speakClickListener;
    /*
     * Position of the assistant message currently being spoken.
     *
     * -1 = nothing is being spoken.
     */
    private int speakingPosition = -1;
    // =============================================================
    // LISTENER
    // =============================================================

    public void setOnSpeakClickListener(OnSpeakClickListener listener) {
        this.speakClickListener = listener;
    }
    // =============================================================
    // CREATE VIEW HOLDER
    // =============================================================

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }
    // =============================================================
    // BIND
    // =============================================================

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        String content = message.getContent();
        if (content == null) {
            content = "";
        }
        holder.messageText.setText(content);
        // ---------------------------------------------------------
        // MESSAGE TEXT LAYOUT
        // ---------------------------------------------------------
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.messageText.getLayoutParams();
        // ---------------------------------------------------------
        // USER MESSAGE
        // ---------------------------------------------------------
        if (message.getType() == Message.USER) {
            params.gravity = Gravity.END;
            holder.messageText.setBackground(createBubble(Color.rgb(80, 120, 230)));
            holder.messageText.setTextColor(Color.WHITE);

            /*
             * User messages never have TTS.
             */
            holder.speakButton.setVisibility(View.GONE);
            holder.speakButton.setOnClickListener(null);
        }
        // ---------------------------------------------------------
        // ASSISTANT MESSAGE
        // ---------------------------------------------------------
        else {
            params.gravity = Gravity.START;
            holder.messageText.setBackground(createBubble(Color.rgb(235, 235, 235)));
            holder.messageText.setTextColor(Color.BLACK);

            /*
             * Only show Speak when actual content exists.
             */
            if (!content.trim().isEmpty() && !"Thinking...".equals(content)) {
                holder.speakButton.setVisibility(View.VISIBLE);
                updateSpeakButton(holder, position);

                /*
                 * IMPORTANT:
                 *
                 * Do NOT capture the original "position".
                 *
                 * RecyclerView positions can change.
                 */
                holder.speakButton.setOnClickListener(v -> {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) {
                        return;
                    }
                    if (adapterPosition >= messages.size()) {
                        return;
                    }
                    if (speakClickListener != null) {
                        String text = messages.get(adapterPosition).getContent();
                        if (text == null) {
                            text = "";
                        }
                        if (!text.trim().isEmpty()) {
                            speakClickListener.onSpeakClicked(text, adapterPosition);
                        }
                    }
                });
            } else {
                holder.speakButton.setVisibility(View.GONE);
                holder.speakButton.setOnClickListener(null);
            }
        }
        holder.messageText.setLayoutParams(params);
    }
    // =============================================================
    // ITEM COUNT
    // =============================================================

    @Override
    public int getItemCount() {
        return messages.size();
    }
    // =============================================================
    // UPDATE SPEAK BUTTON
    // =============================================================

    private void updateSpeakButton(MessageViewHolder holder, int position) {
        if (position == speakingPosition) {
            holder.speakButton.setText("⏹ Stop");
            holder.speakButton.setContentDescription("Stop speaking");
        } else {
            holder.speakButton.setText("🔊 Speak");
            holder.speakButton.setContentDescription("Speak response");
        }
    }
    // =============================================================
    // SET SPEAKING POSITION
    // =============================================================

    public void setSpeakingPosition(int position) {
        int oldPosition = speakingPosition;
        speakingPosition = position;

        /*
         * Refresh old speaking button.
         */
        if (oldPosition >= 0 && oldPosition < messages.size()) {
            notifyItemChanged(oldPosition);
        }

        /*
         * Refresh new speaking button.
         */
        if (position >= 0 && position < messages.size() && position != oldPosition) {
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
        int oldPosition = speakingPosition;
        speakingPosition = -1;
        if (oldPosition >= 0 && oldPosition < messages.size()) {
            notifyItemChanged(oldPosition);
        }
    }
    // =============================================================
    // GET SPEAKING POSITION
    // =============================================================

    public int getSpeakingPosition() {
        return speakingPosition;
    }
    // =============================================================
    // ADD MESSAGE
    // =============================================================

    public void addMessage(Message message) {
        int position = messages.size();
        messages.add(message);
        notifyItemInserted(position);
    }
    // =============================================================
    // STREAMING UPDATE
    // =============================================================

    public void updateLastMessage(String content, RecyclerView recyclerView) {
        if (messages.isEmpty()) {
            return;
        }
        int lastPosition = messages.size() - 1;
        Message message = messages.get(lastPosition);
        if (content == null) {
            content = "";
        }

        /*
         * Update data model.
         */
        message.setContent(content);

        /*
         * IMPORTANT:
         *
         * Never call notifyItemChanged() during streaming.
         *
         * The LLM manager sends the accumulated response.
         */
        if (recyclerView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(lastPosition);
        if (!(holder instanceof MessageViewHolder)) {
            return;
        }
        MessageViewHolder messageHolder = (MessageViewHolder) holder;

        /*
         * Update only the TextView.
         */
        if (!content.equals(messageHolder.messageText.getText().toString())) {
            messageHolder.messageText.setText(content);
        }
        // ---------------------------------------------------------
        // TTS
        // ---------------------------------------------------------
        if (message.getType() == Message.ASSISTANT && !content.trim().isEmpty() && !"Thinking...".equals(content)) {
            messageHolder.speakButton.setVisibility(View.VISIBLE);
            updateSpeakButton(messageHolder, lastPosition);

            /*
             * Use current adapter position.
             */
            messageHolder.speakButton.setOnClickListener(v -> {
                int adapterPosition = messageHolder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                if (adapterPosition >= messages.size()) {
                    return;
                }
                if (speakClickListener != null) {
                    String text = messages.get(adapterPosition).getContent();
                    if (text == null) {
                        text = "";
                    }
                    if (!text.trim().isEmpty()) {
                        speakClickListener.onSpeakClicked(text, adapterPosition);
                    }
                }
            });
        } else {
            messageHolder.speakButton.setVisibility(View.GONE);
            messageHolder.speakButton.setOnClickListener(null);
        }
    }
    // =============================================================
    // THINKING
    // =============================================================

    public void setThinking(boolean thinking, RecyclerView recyclerView) {
        this.thinking = thinking;
        if (messages.isEmpty()) {
            return;
        }
        int lastPosition = messages.size() - 1;
        Message lastMessage = messages.get(lastPosition);
        if (thinking) {
            lastMessage.setContent("Thinking...");
        } else if ("Thinking...".equals(lastMessage.getContent())) {
            lastMessage.setContent("");
        }
        if (recyclerView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(lastPosition);
        if (!(holder instanceof MessageViewHolder)) {
            return;
        }
        MessageViewHolder messageHolder = (MessageViewHolder) holder;
        String content = lastMessage.getContent();
        if (content == null) {
            content = "";
        }
        messageHolder.messageText.setText(content);

        /*
         * Never allow TTS while thinking.
         */
        messageHolder.speakButton.setVisibility(View.GONE);
        messageHolder.speakButton.setOnClickListener(null);
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
        String content = messages.get(messages.size() - 1).getContent();
        return content == null ? "" : content;
    }
    // =============================================================
    // GET MESSAGE
    // =============================================================

    public Message getMessage(int position) {
        if (position < 0 || position >= messages.size()) {
            return null;
        }
        return messages.get(position);
    }
    // =============================================================
    // CREATE BUBBLE
    // =============================================================

    private GradientDrawable createBubble(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(32);
        return drawable;
    }
    // =============================================================
    // VIEW HOLDER
    // =============================================================

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        Button speakButton;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            speakButton = itemView.findViewById(R.id.speakButton);
        }
    }
    // =============================================================
    // REPLACE MESSAGES
    // =============================================================

    public void replaceMessages(List<Message> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        thinking = false;
        speakingPosition = -1;
        notifyDataSetChanged();
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
