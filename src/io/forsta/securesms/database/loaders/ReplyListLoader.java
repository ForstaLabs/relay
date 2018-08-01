package io.forsta.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.util.AbstractCursorLoader;
import io.forsta.securesms.database.DatabaseFactory;

public class ReplyListLoader extends AbstractCursorLoader {
    private long messageId;

    public ReplyListLoader(Context context, long messageId) {
        super(context);
        this.messageId = messageId;
    }

    @Override
    public Cursor getCursor() {
        return DatabaseFactory.getMmsDatabase(context).getMessage(messageId);
    }
}
