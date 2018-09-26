package io.forsta.securesms;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;

import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsSmsColumns;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.util.LRUCache;
import io.forsta.securesms.util.TextSecurePreferences;

public class ReplyListAdapter extends CursorAdapter {
    private final static String TAG = ReplyListAdapter.class.getSimpleName();

    private Context      mContext;
    private int          mResource;
    private MasterSecret masterSecret;
    private final @NonNull MmsSmsDatabase db;

    private static final int MAX_CACHE_SIZE = 40;
    private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
            Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));


    public ReplyListAdapter(@NonNull Context context, int resource, Cursor cursor, MasterSecret masterSecret) {
        super(context, cursor);
        mContext = context;
        mResource = resource;
        this.masterSecret = masterSecret;
        db = DatabaseFactory.getMmsSmsDatabase(mContext);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(mResource, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        long messageId              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
        String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));

        MessageRecord messageRecord = getMessageRecord(messageId, cursor, type);
        long localId                = RecipientFactory.getRecipientIdFromNum(context,TextSecurePreferences.getLocalNumber(context));

        Recipient author;
        if (messageRecord.isOutgoing()) {
            author = Recipient.from(context, localId, true);
        } else {
            author = messageRecord.getIndividualRecipient();
        }

        String body = messageRecord.getPlainTextBody();
        int vote = messageRecord.getVoteCount();

        TextView voteCount = view.findViewById(R.id.reply_vote);
        AvatarImageView contactPhoto = view.findViewById(R.id.reply_contact_photo);
        TextView bodyText = view.findViewById(R.id.reply_text);

        if(vote > 0) {
            voteCount.setVisibility(View.VISIBLE);
            voteCount.setText("(" + String.valueOf(vote) + ")");
        } else {
            voteCount.setVisibility(View.GONE);
        }
        contactPhoto.setAvatar(author, true);
        bodyText.setText(body);
    }

    private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
        final SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);
        if (reference != null) {
            final MessageRecord record = reference.get();
            if (record != null) return record;
        }

        final MessageRecord messageRecord = db.readerFor(cursor, masterSecret).getCurrent();

        messageRecordCache.put(type + messageId, new SoftReference<>(messageRecord));

        return messageRecord;
    }
}
