package io.forsta.securesms.database.model;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

//import io.forsta.securesms.database.Address;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;

public class Quote {
    //maybe add messageRef here?
    private final long      id;
    private final Recipient author;
    private final String    text;
    private SlideDeck attachment;

    public Quote(long id , @NonNull Recipient author, @Nullable String text, @NonNull SlideDeck attachment) {
        this.id         = id;
        this.author     = author;
        this.text       = text;
        this.attachment = attachment;
    }

    public Quote(long id , @NonNull Recipient author, @Nullable String text) {
        this.id         = id;
        this.author     = author;
        this.text       = text;
    }

    public long getId() {
        return id;
    }

     @NonNull Recipient getAuthor() {
        return author;
    }

    public @Nullable String getText() {
        return text;
    }

    public @NonNull SlideDeck getAttachment() {
        return attachment;
    }
}