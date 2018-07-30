package io.forsta.securesms.database.model;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.securesms.recipients.Recipient;

public class Reply {
    private final long      id;
    private final Recipient author;
    private final String    text;
    private int             vote;

    public Reply(long id , @NonNull Recipient author, @Nullable String text, int vote) {
        this.id         = id;
        this.author     = author;
        this.text       = text;
        this.vote = vote;
    }

    public Reply(long id , @NonNull Recipient author, @Nullable String text) {
        this.id         = id;
        this.author     = author;
        this.text       = text;
    }

    public long getId() {
        return id;
    }

     @NonNull public Recipient getAuthor() {
        return author;
    }

    public @Nullable String getText() {
        return text;
    }

    public @NonNull int getVote() {
        return vote;
    }
}