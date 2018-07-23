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

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.contacts.avatars.ContactColors;
import io.forsta.securesms.contacts.avatars.ContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.recipients.RecipientProvider.RecipientDetails;
import io.forsta.securesms.util.FutureTaskListener;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.ListenableFutureTask;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public class Recipient {

  private final static String TAG = Recipient.class.getSimpleName();

  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final long recipientId;

  private @NonNull  String  number;
  private @Nullable String  name;
  private @Nullable String slug;
  private @Nullable String orgSlug;
  private @Nullable String email;
  private @Nullable String phone;
  private boolean isActive;
  private String userType;
  private boolean stale;

  private ContactPhoto contactPhoto;
  private Uri          contactUri;
  private @Nullable String gravatarHash;

  @Nullable private MaterialColor color;

  Recipient(long recipientId,
            @NonNull  String number,
            @Nullable Recipient stale,
            @NonNull ListenableFutureTask<RecipientDetails> future)
  {
    this.recipientId  = recipientId;
    this.number       = number;
    this.contactPhoto = ContactPhotoFactory.getLoadingPhoto();
    this.color        = null;

    if (stale != null) {
      this.name         = stale.name;
      this.contactUri   = stale.contactUri;
      this.contactPhoto = stale.contactPhoto;
      this.gravatarHash = stale.gravatarHash;
      this.color        = stale.color;
      this.slug = stale.slug;
      this.orgSlug = stale.orgSlug;
      this.email = stale.email;
      this.phone = stale.phone;
      this.isActive = stale.isActive;
      this.userType = stale.userType;
    }

    future.addListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.name         = result.name;
            Recipient.this.number       = result.number;
            Recipient.this.contactUri   = result.contactUri;
            Recipient.this.contactPhoto = result.avatar;
            Recipient.this.gravatarHash = result.gravatarHash;
            Recipient.this.color        = result.color;
            Recipient.this.slug = result.slug;
            Recipient.this.orgSlug = result.orgSlug;
            Recipient.this.email = result.email;
            Recipient.this.phone = result.phone;
            Recipient.this.isActive = result.isActive;
            Recipient.this.userType = result.userType;
          }

          notifyListeners();
        }
      }

      @Override
      public void onFailure(Throwable error) {
        Log.w(TAG, error);
      }
    });
  }

  Recipient(long recipientId, RecipientDetails details) {
    this.recipientId  = recipientId;
    this.number       = details.number;
    this.contactUri   = details.contactUri;
    this.name         = details.name;
    this.contactPhoto = details.avatar;
    this.gravatarHash = details.gravatarHash;
    this.color        = details.color;
    this.slug = details.slug;
    this.orgSlug = details.orgSlug;
    this.email = details.email;
    this.phone = details.phone;
    this.isActive = details.isActive;
    this.userType = details.userType;
  }

  public synchronized @NonNull String getAddress() {
    return number;
  }

  public synchronized @Nullable Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized @Nullable String getName() {
    return this.name;
  }

  public synchronized @Nullable String getSlug() {
    return this.slug;
  }

  public synchronized @Nullable String getOrgSlug() {
    return this.orgSlug;
  }

  public synchronized boolean getIsActive() {
    return this.isActive;
  }

  public synchronized String getUserType() {
    return this.userType;
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (color != null) return color;
    else if (name != null)  return ContactColors.generateFor(name);
    else                    return ContactColors.UNKNOWN_COLOR;
  }

  public void setColor(@NonNull MaterialColor color) {
    synchronized (this) {
      this.color = color;
    }

    notifyListeners();
  }

  public synchronized String getPhone() {
    return phone;
  }

  public synchronized String getEmail() {
    return email;
  }

  public String getFullTag() {
    return "@" + slug + ":" + orgSlug;
  }

  public String getLocalTag() {
    return "@" + slug;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public boolean isGroupRecipient() {
    return GroupUtil.isEncodedGroup(number);
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public synchronized String toShortString() {
    return (name == null ? "Unknown Recipient" : name);
  }

  public synchronized @NonNull ContactPhoto getContactPhoto() {
    return contactPhoto;
  }

  public static Recipient getUnknownRecipient() {
    return new Recipient(-1, new RecipientDetails("Unknown", "Unknown", null,
                                                  null, null, null, null, null, null, false, "PERSON"));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return 31 + (int)this.recipientId;
  }

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(this);
  }

  public interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }

  boolean isStale() {
    return stale;
  }

  void setStale() {
    this.stale = true;
  }

  public String getGravitarUrl() {
    if (!TextUtils.isEmpty(gravatarHash)) {
      return "https://www.gravatar.com/avatar/" + gravatarHash + "?default=404";
    }
    return null;
  }
}
