package io.forsta.securesms;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.TextView;

import org.whispersystems.libsignal.InvalidMessageException;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;

import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsColumns;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.model.DisplayRecord;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.LRUCache;

public class ReplyListAdapter extends CursorAdapter {

    private Context mContext;
    private int mResource;
    private MasterSecret masterSecret;
    private final @NonNull MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(mContext);
    private static final int MAX_CACHE_SIZE = 40;
    private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
            Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));


    public ReplyListAdapter(@NonNull Context context, int resource, Cursor cursor, MasterSecret masterSecret) {
        super(context, cursor);
        mContext = context;
        mResource = resource;
        this.masterSecret = masterSecret;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(mResource, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        String address = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsColumns.ADDRESS));
        long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));

        Recipient author = getRecipientsFor(address).getPrimaryRecipient();
        MessageRecord messageRecord = getMessageRecord(messageId, cursor, type);

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

    private Recipients getRecipientsFor(String address) {
        if (TextUtils.isEmpty(address) || address.equals("insert-address-token")) {
            return RecipientFactory.getRecipientsFor(mContext, Recipient.getUnknownRecipient(), true);
        }

        Recipients recipients =  RecipientFactory.getRecipientsFromString(mContext, address, true);

        if (recipients == null || recipients.isEmpty()) {
            return RecipientFactory.getRecipientsFor(mContext, Recipient.getUnknownRecipient(), true);
        }

        return recipients;
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
