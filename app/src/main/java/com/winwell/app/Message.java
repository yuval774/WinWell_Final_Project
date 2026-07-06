package com.winwell.app;

import java.io.Serializable;

// Message represents a single item in the chat conversation.
// There are three kinds of items (told apart by ChatAdapter):
//   • a USER message       (IsUser = true)
//   • a BOT message        (IsUser = false, IsSuggestion = false)
//   • a SUGGESTED ACTIVITY (IsSuggestion = true) — the Accept/Decline card.
public class Message implements Serializable {

    // The text content of the message (used for user + bot messages)
    public String Content;

    // true = sent by the user, false = sent by the bot
    public boolean IsUser;

    // The time the message was created, in milliseconds
    public long Timestamp;

    //  Suggested Activity card fields
    // When IsSuggestion is true, this item is shown as a "Suggested Activity" card
    // with an Accept / Decline action instead of a plain bubble.
    public boolean IsSuggestion = false;
    public String ActivityTitle;
    public String ActivityDescription;
    // 0 = pending (buttons shown), 1 = accepted, 2 = declined
    public int SuggestionState = 0;

    // Normal user/bot message.
    public Message(String content, boolean isUser) {
        Content = content;
        IsUser = isUser;
        Timestamp = System.currentTimeMillis();
    }

    // Builds a "Suggested Activity" card.
    // @param autoAccepted true in Auto-pilot mode (the activity is accepted automatically,
    //                     so no buttons are shown); false in Co-pilot mode (Accept/Decline shown).
    public static Message suggestion(String title, String description, boolean autoAccepted) {
        Message m = new Message("", false);
        m.IsSuggestion = true;
        m.ActivityTitle = title;
        m.ActivityDescription = description;
        m.SuggestionState = autoAccepted ? 1 : 0;
        return m;
    }
}
