package io.forsta.securesms.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Attr;

import io.forsta.securesms.R;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.util.Util;

public class ReplyView extends LinearLayout {

    private View            rootView;
    private TextView        bodyView;
    private AvatarImageView contactPhoto;
    private TextView        voteCount;

    private long      id;
    private Recipient author;
    private String    body;
    private int       vote;


    public ReplyView(Context context) {
        super(context);
        initialize(null);
    }

    public ReplyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(attrs);
    }

    public ReplyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void initialize(@Nullable AttributeSet attrs) {
        inflate(getContext(), R.layout.reply_view, this);

        this.bodyView     = findViewById(R.id.reply_text);
        this.contactPhoto = findViewById(R.id.reply_contact_photo);
        this.voteCount  = findViewById(R.id.reply_vote);
    }

    public void setReply(long id, Recipient author, String body, int vote) {
        this.id     = id;
        this.author = author;
        this.body   = body;
        this.vote   = vote;

        setReplyAvatar(author);
        setReplyText(body);
        setReplyVote(vote);
    }

    public void dismiss() {

        this.id = 0;
        this.author = null;
        this.body = null;
        this.vote = 0;

        setVisibility(View.GONE);
    }

    private void setReplyAvatar(Recipient author) {
        contactPhoto.setVisibility(View.VISIBLE);
        contactPhoto.setAvatar(author,true);
    }

    private void setReplyText(String body) {
        bodyView.setVisibility(View.VISIBLE);
        bodyView.setText(body == null ? "" : body);
    }

    private void setReplyVote(int vote) {
        if(vote > 0) {
            voteCount.setVisibility(View.VISIBLE);
            voteCount.setText("(" + vote + ")" );
        } else {
            voteCount.setVisibility(View.GONE);
        }
    }

    public Recipient getReplyAuthor() {
        return author;
    }

    public int getVote() {
        return vote;
    }

    public String getReplyBody() {
        return body;
    }

    public long getReplyId() {
        return id;
    }
}
