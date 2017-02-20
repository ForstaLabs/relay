package io.forsta.securesms;

import android.support.annotation.NonNull;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
