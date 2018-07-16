package io.forsta.securesms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.MediaMmsMessageRecord;
import io.forsta.securesms.recipients.Recipients;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);

  MessageRecord getMessageRecord();

  //void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(MediaMmsMessageRecord messageRecord);
  }
}
