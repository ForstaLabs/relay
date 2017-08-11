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
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.contacts.avatars.BitmapContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactColors;
import io.forsta.securesms.contacts.avatars.ContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.database.CanonicalAddressDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.LRUCache;
import io.forsta.securesms.util.ListenableFutureTask;
import io.forsta.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import io.forsta.securesms.database.RecipientPreferenceDatabase;

public class RecipientProvider {

  private static final String TAG = RecipientProvider.class.getSimpleName();

  private static final RecipientCache  recipientCache         = new RecipientCache();
  private static final RecipientsCache recipientsCache        = new RecipientsCache();
  private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER
  };

  private static final Map<String, RecipientDetails> STATIC_DETAILS = new HashMap<String, RecipientDetails>() {{
    put("262966", new RecipientDetails("Amazon", "262966", null,
                                       ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_amazon),
                                       ContactColors.UNKNOWN_COLOR));
  }};

  @NonNull Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);
    if (cachedRecipient != null && !cachedRecipient.isStale()) return cachedRecipient;

    String number = CanonicalAddressDatabase.getInstance(context).getAddressFromId(recipientId);

    if (asynchronous) {
      cachedRecipient = new Recipient(recipientId, number, cachedRecipient, getRecipientDetailsAsync(context, recipientId, number));
    } else {
      cachedRecipient = new Recipient(recipientId, getRecipientDetailsSync(context, recipientId, number));
    }

    recipientCache.set(recipientId, cachedRecipient);
    return cachedRecipient;
  }

  @NonNull Recipients getRecipients(Context context, long[] recipientIds, boolean asynchronous) {
    Recipients cachedRecipients = recipientsCache.get(new RecipientIds(recipientIds));
    if (cachedRecipients != null && !cachedRecipients.isStale()) {
      return cachedRecipients;
    }

    List<Recipient> recipientList = new LinkedList<>();

    for (long recipientId : recipientIds) {
      recipientList.add(getRecipient(context, recipientId, asynchronous));
    }

    if (asynchronous) cachedRecipients = new Recipients(recipientList, cachedRecipients, getRecipientsPreferencesAsync(context, recipientIds));
    else              cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesSync(context, recipientIds));

    recipientsCache.set(new RecipientIds(recipientIds), cachedRecipients);
    return cachedRecipients;
  }

  void clearCache() {
    recipientCache.reset();
    recipientsCache.reset();
  }

  private @NonNull
  ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context,
                                                                  final long recipientId,
                                                                  final @NonNull String number)
  {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetailsSync(context, recipientId, number);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull RecipientDetails getRecipientDetailsSync(Context context, long recipientId, @NonNull String number) {
    if (GroupUtil.isEncodedGroup(number)) return getGroupRecipientDetails(context, number);
    else                                  return getIndividualRecipientDetails(context, recipientId, number);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(Context context, long recipientId, @NonNull String number) {
    Optional<RecipientPreferenceDatabase.RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(new long[]{recipientId});
    MaterialColor color       = preferences.isPresent() ? preferences.get().getColor() : null;

    ContactDb db = DbFactory.getContactDb(context);
    Cursor cursor  = db.getContactByAddress(number);
    try {
      if (cursor != null && cursor.moveToFirst()) {
        final String uid = cursor.getString(cursor.getColumnIndex(ContactDb.UID));
        if (uid != null) {
          URL avatarUrl = getGravitarUrl(cursor.getString(cursor.getColumnIndex(ContactDb.EMAIL)));
          String       name         = cursor.getString(cursor.getColumnIndex(ContactDb.NAME));
          ContactPhoto contactPhoto = ContactPhotoFactory.getDefaultContactPhoto(name);
          Bitmap gravatar = getContactGravatar(avatarUrl);
          if (gravatar != null) {
            contactPhoto = new BitmapContactPhoto(gravatar);
          }
          return new RecipientDetails(name, uid, Uri.EMPTY, contactPhoto, color);
        } else {
          Log.w(TAG, "resultNumber is null");
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return new RecipientDetails(null, number, null, ContactPhotoFactory.getDefaultContactPhoto(null), color);
  }

  private @NonNull RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    try {
      GroupDatabase.GroupRecord record  = DatabaseFactory.getGroupDatabase(context)
                                                         .getGroup(GroupUtil.getDecodedId(groupId));

      if (record != null) {
        ContactPhoto contactPhoto = ContactPhotoFactory.getGroupContactPhoto(record.getAvatar());
        return new RecipientDetails(record.getTitle(), groupId, null, contactPhoto, null);
      }

      return new RecipientDetails(null, groupId, null, ContactPhotoFactory.getDefaultGroupPhoto(), null);
    } catch (IOException e) {
      Log.w("RecipientProvider", e);
      return new RecipientDetails(null, groupId, null, ContactPhotoFactory.getDefaultGroupPhoto(), null);
    }
  }

  private @Nullable
  RecipientPreferenceDatabase.RecipientsPreferences getRecipientsPreferencesSync(Context context, long[] recipientIds) {
    return DatabaseFactory.getRecipientPreferenceDatabase(context)
                          .getRecipientsPreferences(recipientIds)
                          .orNull();
  }

  private ListenableFutureTask<RecipientPreferenceDatabase.RecipientsPreferences> getRecipientsPreferencesAsync(final Context context, final long[] recipientIds) {
    ListenableFutureTask<RecipientPreferenceDatabase.RecipientsPreferences> task = new ListenableFutureTask<>(new Callable<RecipientPreferenceDatabase.RecipientsPreferences>() {
      @Override
      public RecipientPreferenceDatabase.RecipientsPreferences call() throws Exception {
        return getRecipientsPreferencesSync(context, recipientIds);
      }
    });

    asyncRecipientResolver.execute(task);

    return task;
  }

  public static class RecipientDetails {
    @Nullable public final String        name;
    @NonNull  public final String        number;
    @NonNull  public final ContactPhoto  avatar;
    @Nullable public final Uri           contactUri;
    @Nullable public final MaterialColor color;

    public RecipientDetails(@Nullable String name, @NonNull String number,
                            @Nullable Uri contactUri, @NonNull ContactPhoto avatar,
                            @Nullable MaterialColor color)
    {
      this.name       = name;
      this.number     = number;
      this.avatar     = avatar;
      this.contactUri = contactUri;
      this.color      = color;
    }
  }

  private static class RecipientIds {
    private final long[] ids;

    private RecipientIds(long[] ids) {
      this.ids = ids;
    }

    public boolean equals(Object other) {
      if (other == null || !(other instanceof RecipientIds)) return false;
      return Arrays.equals(this.ids, ((RecipientIds) other).ids);
    }

    public int hashCode() {
      return Arrays.hashCode(ids);
    }
  }

  private static class RecipientCache {

    private final Map<Long,Recipient> cache = new LRUCache<>(1000);

    public synchronized Recipient get(long recipientId) {
      return cache.get(recipientId);
    }

    public synchronized void set(long recipientId, Recipient recipient) {
      cache.put(recipientId, recipient);
    }

    public synchronized void reset() {
      for (Recipient recipient : cache.values()) {
        recipient.setStale();
      }
    }

  }

  private static class RecipientsCache {

    private final Map<RecipientIds,Recipients> cache = new LRUCache<>(1000);

    public synchronized Recipients get(RecipientIds ids) {
      return cache.get(ids);
    }

    public synchronized void set(RecipientIds ids, Recipients recipients) {
      cache.put(ids, recipients);
    }

    public synchronized void reset() {
      for (Recipients recipients : cache.values()) {
        recipients.setStale();
      }
    }

  }

  private URL getGravitarUrl(String email) {
    try {
      if (email != null && email.length() > 0) {
        String hash = ForstaUtils.md5Hex(email);
        return new URL("https://www.gravatar.com/avatar/" + hash);
      }
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return null;
  }

  private Bitmap getContactGravatar(URL url) {
    try {
      InputStream is = (InputStream) url.getContent();
      Bitmap d = BitmapFactory.decodeStream(is);
      is.close();
      return d;
    } catch (Exception e) {
      return null;
    }
  }
}