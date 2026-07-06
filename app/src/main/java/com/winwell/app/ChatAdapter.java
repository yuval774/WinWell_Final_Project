package com.winwell.app;

import java.util.ArrayList;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// ChatAdapter connects our list of messages to the RecyclerView.
// It knows how to create and fill two different types of chat bubbles -
// one for the user's messages and one for the bot's messages.
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Constants to tell apart user messages from bot messages
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private static final int VIEW_TYPE_SUGGESTION = 3;

    // Lets ChatActivity react when the user accepts/declines a suggested activity.
    public interface SuggestionListener {
        void onAccept(Message message);
        void onDecline(Message message);
    }
    private SuggestionListener suggestionListener;
    public void setSuggestionListener(SuggestionListener l) { this.suggestionListener = l; }

    // The list that holds all the chat messages
    private List<Message> messageList;

    // Keeps track of the last animated item so we don't re-animate old messages
    private int lastPosition = -1;

    // The user's profile photo URI (loaded from Firestore in ChatActivity).
    // Shown as the little avatar next to each of the user's messages.
    private String userPhotoUri = "";

    // Constructor - initializes an empty list to hold messages
    public ChatAdapter() {
        super();
        this.messageList = new ArrayList<>();
    }

    // Sets the user's profile photo and refreshes the bubbles so the avatar appears.
    public void setUserPhotoUri(String uri) {
        this.userPhotoUri = uri;
        notifyDataSetChanged();
    }

    // Tells the RecyclerView which layout to use for each message.
    // If the message was sent by the user, return USER type.
    // If it was sent by the bot, return BOT type.
    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        if (message.IsSuggestion) {
            return VIEW_TYPE_SUGGESTION;
        } else if (message.IsUser) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_BOT;
        }
    }

    // Creates a new ViewHolder when the RecyclerView needs one.
    // Depending on the view type, it inflates either the user bubble layout
    // or the bot bubble layout.
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SUGGESTION) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_suggestion, parent, false);
            return new SuggestionViewHolder(view);
        } else if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_bot, parent, false);
            return new BotMessageViewHolder(view);
        }
    }

    // Fills in the data for each message when it appears on screen.
    // It checks the type and calls the right ViewHolder's bind method,
    // then applies the slide-in animation.
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        // Cast to the correct ViewHolder type and bind the message data
        if (holder.getItemViewType() == VIEW_TYPE_SUGGESTION) {
            SuggestionViewHolder sh = (SuggestionViewHolder) holder;
            sh.bind(message);

            // Accept → mark accepted, refresh the card, notify ChatActivity
            sh.BtnAccept.setOnClickListener(v -> {
                message.SuggestionState = 1;
                notifyItemChanged(holder.getAdapterPosition());
                if (suggestionListener != null) suggestionListener.onAccept(message);
            });
            // Decline → mark declined, refresh the card, notify ChatActivity
            sh.BtnDecline.setOnClickListener(v -> {
                message.SuggestionState = 2;
                notifyItemChanged(holder.getAdapterPosition());
                if (suggestionListener != null) suggestionListener.onDecline(message);
            });
        } else if (holder.getItemViewType() == VIEW_TYPE_USER) {
            ((UserMessageViewHolder) holder).bind(message, userPhotoUri);
        } else {
            ((BotMessageViewHolder) holder).bind(message);
        }

        // Apply the fall-down animation to new messages
        setAnimation(holder.itemView, position);
    }

    // Applies a slide-in animation to new messages as they appear.
    // Only animates messages that haven't been shown yet to avoid
    // re-animating when scrolling back up.
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            android.view.animation.Animation animation = android.view.animation.AnimationUtils
                    .loadAnimation(viewToAnimate.getContext(), R.anim.item_animation_fall_down);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    // Returns the total number of messages in the chat
    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // Adds a new message to the chat list and notifies the RecyclerView
    // that a new item was inserted, so it can update the display smoothly.
    public void AddMessage(Message message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }
}
