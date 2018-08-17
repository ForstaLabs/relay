package io.forsta.securesms.mms;


import android.support.annotation.Nullable;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.recipients.Recipient;

import java.util.List;

public class QuoteModel {

    private final long             id;
    private final Recipient        author;
    private final String           text;
    private final List<Attachment> attachments;

    public QuoteModel(long id, Recipient author, String text, @Nullable List<Attachment> attachments) {
        this.id          = id;
        this.author      = author;
        this.text        = text;
        this.attachments = attachments;
    }

    public long getId() {
        return id;
    }

    public Recipient getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }
}