/**
 * Copyright (C) 2015 Open Whisper Systems
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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.contacts.avatars.ContactColors;
import io.forsta.securesms.contacts.avatars.ContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import io.forsta.securesms.recipients.Recipient.RecipientModifiedListener;
import io.forsta.securesms.util.FutureTaskListener;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.ListenableFutureTask;
import io.forsta.securesms.util.NumberUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

import io.forsta.securesms.database.RecipientPreferenceDatabase;

public class Recipients implements Iterable<Recipient>, RecipientModifiedListener {

  private static final String TAG = Recipients.class.getSimpleName();

  private final Set<RecipientsModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientsModifiedListener, Boolean>());
  private final List<Recipient> recipients;

  private Uri          ringtone       = null;
  private long         mutedUntil     = 0;
  private boolean      blocked        = false;
  private RecipientPreferenceDatabase.VibrateState vibrate        = RecipientPreferenceDatabase.VibrateState.DEFAULT;
  private int          expireMessages = 0;
  private boolean      stale          = false;

  Recipients() {
    this(new LinkedList<Recipient>(), null);
  }

  Recipients(List<Recipient> recipients, @Nullable RecipientPreferenceDatabase.RecipientsPreferences preferences) {
    this.recipients = recipients;

    if (preferences != null) {
      ringtone       = preferences.getRingtone();
      mutedUntil     = preferences.getMuteUntil();
      vibrate        = preferences.getVibrateState();
      blocked        = preferences.isBlocked();
      expireMessages = preferences.getExpireMessages();
    }
  }

  Recipients(@NonNull  List<Recipient> recipients,
             @Nullable Recipients stale,
             @NonNull ListenableFutureTask<RecipientsPreferences> preferences)
  {
    this.recipients = recipients;

    if (stale != null) {
      ringtone       = stale.ringtone;
      mutedUntil     = stale.mutedUntil;
      vibrate        = stale.vibrate;
      blocked        = stale.blocked;
      expireMessages = stale.expireMessages;
    }

    preferences.addListener(new FutureTaskListener<RecipientsPreferences>() {
      @Override
      public void onSuccess(RecipientPreferenceDatabase.RecipientsPreferences result) {
        if (result != null) {

          Set<RecipientsModifiedListener> localListeners;

          synchronized (Recipients.this) {
            ringtone       = result.getRingtone();
            mutedUntil     = result.getMuteUntil();
            vibrate        = result.getVibrateState();
            blocked        = result.isBlocked();
            expireMessages = result.getExpireMessages();

            localListeners = new HashSet<>(listeners);
          }

          for (RecipientsModifiedListener listener : localListeners) {
            listener.onModified(Recipients.this);
          }
        }
      }

      @Override
      public void onFailure(ExecutionException error) {
        Log.w(TAG, error);
      }
    });
  }

  public synchronized @Nullable Uri getRingtone() {
    return ringtone;
  }

  public void setRingtone(Uri ringtone) {
    synchronized (this) {
      this.ringtone = ringtone;
    }

    notifyListeners();
  }

  public synchronized boolean isMuted() {
    return System.currentTimeMillis() <= mutedUntil;
  }

  public void setMuted(long mutedUntil) {
    synchronized (this) {
      this.mutedUntil = mutedUntil;
    }

    notifyListeners();
  }

  public synchronized boolean isBlocked() {
    return blocked;
  }

  public void setBlocked(boolean blocked) {
    synchronized (this) {
      this.blocked = blocked;
    }

    notifyListeners();
  }

  public synchronized RecipientPreferenceDatabase.VibrateState getVibrate() {
    return vibrate;
  }

  public void setVibrate(RecipientPreferenceDatabase.VibrateState vibrate) {
    synchronized (this) {
      this.vibrate = vibrate;
    }

    notifyListeners();
  }

  public @NonNull
  ContactPhoto getContactPhoto() {
    if (recipients.size() == 1) return recipients.get(0).getContactPhoto();
    else                        return ContactPhotoFactory.getDefaultGroupPhoto();
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (!isSingleRecipient() || isGroupRecipient()) return MaterialColor.GROUP;
    else if (isEmpty())                                  return ContactColors.UNKNOWN_COLOR;
    else                                                 return recipients.get(0).getColor();
  }

  public synchronized void setColor(@NonNull MaterialColor color) {
    if      (!isSingleRecipient() || isGroupRecipient()) throw new AssertionError("Groups don't have colors!");
    else if (!isEmpty())                                 recipients.get(0).setColor(color);
  }

  public synchronized int getExpireMessages() {
    return expireMessages;
  }

  public void setExpireMessages(int expireMessages) {
    synchronized (this) {
      this.expireMessages = expireMessages;
    }

    notifyListeners();
  }

  public synchronized void addListener(RecipientsModifiedListener listener) {
    if (listeners.isEmpty()) {
      for (Recipient recipient : recipients) {
        recipient.addListener(this);
      }
    }

    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientsModifiedListener listener) {
    listeners.remove(listener);

    if (listeners.isEmpty()) {
      for (Recipient recipient : recipients) {
        recipient.removeListener(this);
      }
    }
  }

  public boolean isEmailRecipient() {
    for (Recipient recipient : recipients) {
      if (NumberUtil.isValidEmail(recipient.getAddress()))
        return true;
    }

    return false;
  }

  public boolean isGroupRecipient() {
    return isSingleRecipient() && GroupUtil.isEncodedGroup(recipients.get(0).getAddress());
  }

  public boolean includesSelf(Context context) {
    for (Recipient recipient: recipients) {
      if (recipient.getAddress().equals(TextSecurePreferences.getLocalNumber(context))) {
        return true;
      }
    }
    return false;
  }

  public String getRecipientExpression() {
    StringBuilder sb = new StringBuilder();
    for (Recipient recipient : recipients) {
      sb.append(recipient.getFullTag()).append(" ");
    }
    return sb.toString();
  }

  public String getLocalizedRecipientExpression(String orgTag) {
    StringBuilder sb = new StringBuilder();
    for (Recipient recipient : recipients) {
      if (recipient.getOrgSlug().equals(orgTag)) {
        sb.append(recipient.getLocalTag()).append(" ");
      } else {
        sb.append(recipient.getFullTag()).append(" ");
      }
    }
    return sb.toString();
  }

  public boolean isEmpty() {
    return this.recipients.isEmpty();
  }

  public boolean isSingleRecipient() {
    return this.recipients.size() == 1;
  }

  public @Nullable Recipient getPrimaryRecipient() {
    if (!isEmpty())
      return this.recipients.get(0);
    else
      return null;
  }

  public Recipient getRecipient(String address) {
    for (Recipient recipient : recipients) {
      if (recipient.getAddress().equals(address)) {
        return recipient;
      }
    }
    return null;
  }

  public List<Recipient> getRecipientsList() {
    return this.recipients;
  }

  public long[] getIds() {
    long[] ids = new long[recipients.size()];
    for (int i=0; i<recipients.size(); i++) {
      ids[i] = recipients.get(i).getRecipientId();
    }
    return ids;
  }

  public List<String> getAddresses() {
    List<String> addresses = new ArrayList<>();
    for (Recipient recipient : recipients) {
      addresses.add(recipient.getAddress());
    }
    return addresses;
  }

  public String getSortedIdsString() {
    Set<Long> recipientSet  = new HashSet<>();

    for (Recipient recipient : this.recipients) {
      recipientSet.add(recipient.getRecipientId());
    }

    long[] recipientArray = new long[recipientSet.size()];
    int i                 = 0;

    for (Long recipientId : recipientSet) {
      recipientArray[i++] = recipientId;
    }

    Arrays.sort(recipientArray);

    return Util.join(recipientArray, " ");
  }

  public @NonNull String[] toNumberStringArray(boolean scrub) {
    String[] recipientsArray     = new String[recipients.size()];
    Iterator<Recipient> iterator = recipients.iterator();
    int i                        = 0;

    while (iterator.hasNext()) {
      String number = iterator.next().getAddress();

      if (scrub && number != null &&
          !Patterns.EMAIL_ADDRESS.matcher(number).matches() &&
          !GroupUtil.isEncodedGroup(number) && !Util.isForstaUid(number))
      {
        number = number.replaceAll("[^0-9+]", "");
      }

      recipientsArray[i++] = number;
    }

    return recipientsArray;
  }

  public @NonNull List<String> toNumberStringList(boolean scrub) {
    List<String> results = new LinkedList<>();
    Collections.addAll(results, toNumberStringArray(scrub));

    return results;
  }

  public String toCondensedString(Context context) {
    StringBuilder sb = new StringBuilder();
    List<String> addresses = new ArrayList<>();

    if (recipients.size() == 1) {
      return getPrimaryRecipient().getName() != null ? getPrimaryRecipient().getName() : "Unknown recipient";
    }

    for (int i=0; i<recipients.size(); i++) {
      String address = recipients.get(i).getAddress();
      if (!address.equals(TextSecurePreferences.getLocalNumber(context))) {
        String name = recipients.get(i).getName();
        if (TextUtils.isEmpty(name)) {
          name = "Unknown Recipient";
        }
        addresses.add(name);
      }
    }

    if(addresses.size() > 2) {
      return addresses.get(0) + " and " + addresses.size() + " others";
    } else if (addresses.size() > 0){
      return TextUtils.join(", ", addresses);
    }

    return "No recipients";
  }

  public String toShortString() {
    String fromString = "";

    for (int i=0;i<recipients.size();i++) {

      fromString += recipients.get(i).toShortString();

      if (i != recipients.size() -1 )
        fromString += ", ";
    }

    return fromString;
  }

  @Override
  public Iterator<Recipient> iterator() {
    return recipients.iterator();
  }

  @Override
  public void onModified(Recipient recipient) {
    notifyListeners();
  }

  private void notifyListeners() {
    Set<RecipientsModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientsModifiedListener listener : localListeners) {
      listener.onModified(this);
    }
  }

  boolean isStale() {
    return stale;
  }

  public void setStale() {
    this.stale = true;
  }

  public interface RecipientsModifiedListener {
    void onModified(Recipients recipient);
  }

}
