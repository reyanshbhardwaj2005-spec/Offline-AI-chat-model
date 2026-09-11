package com.example.llama;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter
    extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<Message> messages =
        new ArrayList<>();

    private boolean thinking = false;

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

        } else {

            params.gravity = Gravity.START;

            holder.messageText.setBackground(
                createBubble(
                    Color.rgb(235, 235, 235)
                )
            );

            holder.messageText.setTextColor(
                Color.BLACK
            );
        }

        holder.messageText.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // =============================================================
    // ADD MESSAGE
    // =============================================================

    public void addMessage(Message message) {

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

        // Update adapter data
        messages.get(lastPosition)
            .setContent(content);

        /*
         * Get the currently visible ViewHolder.
         */
        RecyclerView.ViewHolder holder =
            recyclerView.findViewHolderForAdapterPosition(
                lastPosition
            );

        /*
         * Update only the TextView.
         *
         * We deliberately DO NOT call:
         *
         * notifyItemChanged(lastPosition);
         *
         * because that was causing the scratching/jumping
         * while the response was growing.
         */
        if (holder instanceof MessageViewHolder) {

            MessageViewHolder messageHolder =
                (MessageViewHolder) holder;

            messageHolder.messageText.setText(
                content
            );
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

        MessageViewHolder(
            @NonNull View itemView
        ) {

            super(itemView);

            messageText =
                itemView.findViewById(
                    R.id.messageText
                );
        }
    }

    public void clearMessages() {
        messages.clear();
        thinking = false;
        notifyDataSetChanged();
    }
}
