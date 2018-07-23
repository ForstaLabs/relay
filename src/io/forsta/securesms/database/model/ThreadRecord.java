/**
 * Copyright (C) 2012 Moxie Marlinspike
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
package io.forsta.securesms.database.model;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.database.MmsSmsColumns;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.ExpirationUtil;
import io.forsta.securesms.util.GroupUtil;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

  private @NonNull  final Context context;
  private @Nullable final Uri     snippetUri;
  private           final long    count;
  private           final boolean read;
  private           final int     distributionType;
  private           final boolean archived;
  private           final long    expiresIn;
  private final String distribution;
  private final String expression;
  private final String title;
  private final String threadUid;
  private final String color;
  private final boolean pinned;
  private final int threadType;

  public ThreadRecord(@NonNull Context context, @NonNull Body body, @Nullable Uri snippetUri,
                      @NonNull Recipients recipients, long date, long count, boolean read,
                      long threadId, int receiptCount, int status, long snippetType,
                      int distributionType, boolean archived, long expiresIn, String distribution, String title, String threadUid, String color, String expression, boolean pinned, int threadType)
  {
    super(context, body, recipients, date, date, threadId, status, receiptCount, snippetType);
    this.context          = context.getApplicationContext();
    this.snippetUri       = snippetUri;
    this.count            = count;
    this.read             = read;
    this.distributionType = distributionType;
    this.archived         = archived;
    this.expiresIn        = expiresIn;
    this.distribution = distribution;
    this.title = title;
    this.threadUid = threadUid;
    this.color = color;
    this.expression = expression;
    this.pinned = pinned;
    this.threadType = threadType;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  @Override
  public SpannableString getDisplayBody() {
    String body = getBody().getBody();
    try {
      ForstaMessage forstaBody = getForstaMessageBody();
      body = forstaBody.getTextBody();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    if (SmsDatabase.Types.isEndSessionType(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_secure_session_reset));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(type)) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time));
    }
    return new SpannableString(body);
  }

  public String getSenderAddress() {
    try {
      ForstaMessage forstaMessage = getForstaMessageBody();
      return forstaMessage.getSenderId();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return "";
  }

  private SpannableString emphasisAdded(String sequence) {
    return emphasisAdded(sequence, 0, sequence.length());
  }

  private SpannableString emphasisAdded(String sequence, int start, int end) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                      start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public long getCount() {
    return count;
  }

  public boolean isRead() {
    return read;
  }

  public long getDate() {
    return getDateReceived();
  }

  public boolean isArchived() {
    return archived;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public String getDistribution() {
    return distribution;
  }

  public String getTitle() {
    return title;
  }

  public String getThreadUid() {
    return threadUid;
  }

  public MaterialColor getColor() {
    try {
      return MaterialColor.fromSerialized(color);
    } catch (MaterialColor.UnknownColorException e) {
      Log.w("ThreadRecord", "Invalid or null color");
    }
    return MaterialColor.GREY;
  }

  public String getPrettyExpression() {
    return expression;
  }

  public boolean isPinned() {
    return pinned;
  }

  public int getThreadType() {
    return threadType;
  }
}
