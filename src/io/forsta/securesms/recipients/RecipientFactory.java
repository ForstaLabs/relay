/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms.recipients;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import io.forsta.securesms.database.CanonicalAddressDatabase;
import io.forsta.securesms.util.Util;

import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class RecipientFactory {

  public static final String RECIPIENT_CLEAR_ACTION = "io.forsta.securesms.database.RecipientFactory.CLEAR";
  private static final RecipientProvider provider = new RecipientProvider();

  public static Recipients getRecipientsForIds(Context context, String recipientIds, boolean asynchronous) {
    if (TextUtils.isEmpty(recipientIds))
      return new Recipients();

    return getRecipientsForIds(context, Util.split(recipientIds, " "), asynchronous);
  }

  public static @NonNull Recipients getRecipientsFor(Context context, Collection<Recipient> recipients, boolean asynchronous) {
    long[] ids = new long[recipients.size()];
    int    i   = 0;

    for (Recipient recipient : recipients) {
      ids[i++] = recipient.getRecipientId();
    }

    return provider.getRecipients(context, ids, asynchronous);
  }

  public static Recipients getRecipientsFor(Context context, Recipient recipient, boolean asynchronous) {
    long[] ids = new long[1];
    ids[0] = recipient.getRecipientId();

    return provider.getRecipients(context, ids, asynchronous);
  }

  public @NonNull static Recipient getRecipientForId(Context context, long recipientId, boolean asynchronous) {
    return provider.getRecipient(context, recipientId, asynchronous);
  }

  public @NonNull static Recipients getRecipientsForIds(Context context, long[] recipientIds, boolean asynchronous) {
    return provider.getRecipients(context, recipientIds, asynchronous);
  }

  public static @NonNull Recipients getRecipientsFromString(Context context, @NonNull String rawText, boolean asynchronous) {
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");
    List<String>    ids       = new LinkedList<>();

    while (tokenizer.hasMoreTokens()) {
      Optional<Long> id = getRecipientIdFromNumber(context, tokenizer.nextToken());

      if (id.isPresent()) {
        ids.add(String.valueOf(id.get()));
      }
    }

    return getRecipientsForIds(context, ids, asynchronous);
  }

  public static @NonNull Recipients getRecipientsFromStrings(@NonNull Context context, @NonNull List<String> numbers, boolean asynchronous) {
    List<String> ids = new LinkedList<>();

    for (String number : numbers) {
      Optional<Long> id = getRecipientIdFromNumber(context, number);

      if (id.isPresent()) {
        ids.add(String.valueOf(id.get()));
      }
    }

    return getRecipientsForIds(context, ids, asynchronous);
  }

  private static @NonNull Recipients getRecipientsForIds(Context context, List<String> idStrings, boolean asynchronous) {
    long[]       ids      = new long[idStrings.size()];
    int          i        = 0;

    for (String id : idStrings) {
      ids[i++] = Long.parseLong(id);
    }

    return provider.getRecipients(context, ids, asynchronous);
  }

  private static Optional<Long> getRecipientIdFromNumber(Context context, String number) {
    number = number.trim();

    if (number.isEmpty()) return Optional.absent();

    return Optional.of(CanonicalAddressDatabase.getInstance(context).getCanonicalAddressId(number));
  }
  
  public static Recipient getRecipient(Context context, String uid, boolean async) {
    long id = getRecipientIdFromNumber(context, uid).get();
    return provider.getRecipient(context, id, async);
  }

  public static void clearCache(Context context) {
    provider.clearCache();
    context.sendBroadcast(new Intent(RECIPIENT_CLEAR_ACTION));
  }

}
