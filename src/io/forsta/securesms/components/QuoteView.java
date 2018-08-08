package io.forsta.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
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

import com.annimon.stream.Stream;


import io.forsta.securesms.R;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
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

    public QuoteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(attrs);
    }

    public QuoteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public QuoteView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(attrs);
    }

    private void initialize(@Nullable AttributeSet attrs) {
        inflate(getContext(), R.layout.quote_view, this);

        this.rootView                     = findViewById(R.id.quote_root);
        this.authorView                   = findViewById(R.id.quote_author);
        this.bodyView                     = findViewById(R.id.quote_text);
        this.quoteBarView                 = findViewById(R.id.quote_bar);
        this.dismissView                  = findViewById(R.id.quote_dismiss);
        this.mediaDescriptionText         = findViewById(R.id.media_name);
        this.roundedCornerRadiusPx        = getResources().getDimensionPixelSize(R.dimen.quote_corner_radius);

        if (attrs != null) {
            TypedArray typedArray  = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
            messageType = typedArray.getInt(R.styleable.QuoteView_message_type, 0);
            typedArray.recycle();

            dismissView.setVisibility(messageType == MESSAGE_TYPE_PREVIEW ? VISIBLE : GONE);
        }

        dismissView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setVisibility(GONE);
                InputPanel.returnInputHint();
            }
        });

        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT < 18) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawRect.left = 0;
        drawRect.top = 0;
        drawRect.right = getWidth();
        drawRect.bottom = getHeight();

        clipPath.reset();
        clipPath.addRoundRect(drawRect, roundedCornerRadiusPx, roundedCornerRadiusPx, Path.Direction.CW);
        canvas.clipPath(clipPath);
    }

    public void setQuote(long id, @NonNull Recipient author, @Nullable String body, @NonNull SlideDeck attachments) {
        if (this.author != null) this.author.removeListener(this);

        this.id          = id;
        this.author      = author;
        this.body        = body;
        this.attachments = attachments;

        author.addListener(this);
        setQuoteAuthor(author);
        setQuoteText(body, attachments);
    }

    public void dismiss() {
        if (this.author != null) this.author.removeListener(this);

        this.id     = 0;
        this.author = null;
        this.body   = null;

        setVisibility(GONE);
    }

    @Override
    public void onModified(final Recipient recipient) {
        Util.runOnMain(() -> {
            if (recipient == author) {
                setQuoteAuthor(recipient);
            }
        });
    }

    private void setQuoteAuthor(@NonNull Recipient author) {
        boolean outgoing    = messageType != MESSAGE_TYPE_INCOMING;
        boolean isOwnNumber = Util.isOwnNumber(getContext(), author.getAddress());

        authorView.setText(isOwnNumber ? getContext().getString(R.string.QuoteView_you)
                : author.toShortString());
        authorView.setTextColor(author.getColor().toQuoteTitleColor(getContext()));
        quoteBarView.setImageResource(author.getColor().toQuoteBarColorResource(getContext(), outgoing));

        GradientDrawable background = (GradientDrawable) rootView.getBackground();
        background.setColor(author.getColor().toQuoteBackgroundColor(getContext(), outgoing));
        background.setStroke(getResources().getDimensionPixelSize(R.dimen.quote_outline_width),
                author.getColor().toQuoteOutlineColor(getContext(), outgoing));
    }

    private void setQuoteText(@Nullable String body, @NonNull SlideDeck attachments) {
        if (!TextUtils.isEmpty(body) || !attachments.containsMediaSlide()) {
            bodyView.setVisibility(VISIBLE);
            bodyView.setText(body == null ? "" : body);
            mediaDescriptionText.setVisibility(GONE);
            return;
        }

        bodyView.setVisibility(GONE);
        mediaDescriptionText.setVisibility(VISIBLE);
        mediaDescriptionText.setTypeface(null, Typeface.ITALIC);

        List<Slide> audioSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasAudio).limit(1).toList();
        List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(Slide::hasDocument).limit(1).toList();
        List<Slide> imageSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasImage).limit(1).toList();
        List<Slide> videoSlides    = Stream.of(attachments.getSlides()).filter(Slide::hasVideo).limit(1).toList();

        // Given that most types have images, we specifically check images last
        if (!audioSlides.isEmpty()) {
            mediaDescriptionText.setText(R.string.QuoteView_audio);
        } else if (!documentSlides.isEmpty()) {
            String filename = documentSlides.get(0).getFileName().orNull();
            if (!TextUtils.isEmpty(filename)) {
                mediaDescriptionText.setTypeface(null, Typeface.NORMAL);
                mediaDescriptionText.setText(filename);
            } else {
                mediaDescriptionText.setText(R.string.QuoteView_document);
            }
        } else if (!videoSlides.isEmpty()) {
            mediaDescriptionText.setText(R.string.QuoteView_video);
        } else if (!imageSlides.isEmpty()) {
            mediaDescriptionText.setText(R.string.QuoteView_photo);
        }
    }

    public long getQuoteId() {
        return id;
    }

    public Recipient getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public List<Attachment> getAttachments() {
        return attachments.asAttachments();
    }
}