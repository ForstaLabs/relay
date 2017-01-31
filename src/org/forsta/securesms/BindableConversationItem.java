package org.forsta.securesms;

import android.support.annotation.NonNull;

import org.forsta.securesms.crypto.MasterSecret;
import org.forsta.securesms.database.model.MessageRecord;
import org.forsta.securesms.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);

  MessageRecord getMessageRecord();
}
