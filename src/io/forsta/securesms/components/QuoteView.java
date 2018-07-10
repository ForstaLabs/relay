package io.forsta.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.bumptech.glide.load.engine.DiskCacheStrategy;

import io.forsta.securesms.R;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.mms.AudioSlide;
import io.forsta.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import io.forsta.securesms.mms.DocumentSlide;

import io.forsta.securesms.mms.ImageSlide;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.mms.VideoSlide;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipient.RecipientModifiedListener;
import io.forsta.securesms.util.Util;

import java.util.List;

public class QuoteView extends LinearLayout implements Recipient.RecipientModifiedListener {

    private static final String TAG = QuoteView.class.getSimpleName();

    private static final int MESSAGE_TYPE_PREVIEW  = 0;
    private static final int MESSAGE_TYPE_OUTGOING = 1;
    private static final int MESSAGE_TYPE_INCOMING = 2;

    private View      rootView;
    private TextView  authorView;
    private TextView  bodyView;
    private ImageView quoteBarView;
    private ImageView attachmentView;
    private ImageView attachmentVideoOverlayView;
    private ViewGroup attachmentIconContainerView;
    private ImageView attachmentIconView;
    private ImageView attachmentIconBackgroundView;
    private ImageView dismissView;

    private long      id;
    private Recipient author;
    private String    body;
    private TextView  mediaDescriptionText;
    private SlideDeck attachments;
    private int       messageType;
    private int       roundedCornerRadiusPx;

    private final Path  clipPath = new Path();
    private final RectF drawRect = new RectF();

    public QuoteView(Context context) {
        super(context);
        initialize(null);
    }

    private void initialize(AttributeSet attrs) {
        inflate(getContext(), R.layout.quote_view, this);

        this.rootView                     = findViewById(R.id.quote_root);
        this.authorView                   = (TextView) findViewById(R.id.quote_author);
        this.bodyView                     = (TextView) findViewById(R.id.quote_text);
        this.quoteBarView                 = (ImageView) findViewById(R.id.quote_bar);
        this.attachmentView               = (ImageView) findViewById(R.id.quote_attachment);
        this.attachmentVideoOverlayView   = (ImageView) findViewById(R.id.quote_video_overlay);
        this.attachmentIconContainerView  = (ViewGroup) findViewById(R.id.quote_attachment_icon_container);
        this.attachmentIconView           = (ImageView) findViewById(R.id.quote_attachment_icon);
        this.attachmentIconBackgroundView = (ImageView) findViewById(R.id.quote_attachment_icon_background);
        this.dismissView                  = (ImageView) findViewById(R.id.quote_dismiss);
        this.roundedCornerRadiusPx        = getResources().getDimensionPixelSize(R.dimen.quote_corner_radius);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawRect.left = 0;
        drawRect.right = getWidth();
        drawRect.top = 0;
        drawRect.bottom = getHeight();

        clipPath.reset();
        clipPath.addRoundRect(drawRect, roundedCornerRadiusPx, roundedCornerRadiusPx, Path.Direction.CW);
        canvas.clipPath(clipPath);
    }

    public void dismiss() {
        if (this.author != null) this.author.removeListener(this);

        this.id     = 0;
        this.author = null;
        this.body   = null;

        setVisibility(GONE);
    }

    public void setQuote() {
        if (this.author != null) this.author.removeListener(this);

        this.id          = id;
        this.author      = author;
        this.body        = body;
        this.attachments = attachments;

        author.addListener(this);
        setQuoteAuthor(author);
        setQuoteText(body, attachments);
        setQuoteAttachment(attachments, author);
    }

    private void setQuoteAuthor(Recipient recipient) {

    }

    private void setQuoteText(String body, SlideDeck attachments) {

    }

    private void setQuoteAttachment(SlideDeck attachments, Recipient author) {

    }

    @Override
    public void onModified(Recipient recipient) {

    }
}
