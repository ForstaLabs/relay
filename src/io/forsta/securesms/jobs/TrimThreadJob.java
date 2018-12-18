/**
 * Copyright (C) 2014 Open Whisper Systems
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
package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.jobmanager.Job;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.util.TextSecurePreferences;

public class TrimThreadJob extends ContextJob {

  private static final String TAG = TrimThreadJob.class.getSimpleName();
  private static final String KEY_THREAD_ID = "thread_id";

  private long    threadId;

  public TrimThreadJob() {
    super(null, null);
  }

  public TrimThreadJob(Context context, long threadId) {
    super(context, JobParameters.newBuilder().withGroupId(TrimThreadJob.class.getSimpleName()).create());
    this.context = context;
    this.threadId = threadId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    threadId = data.getLong(KEY_THREAD_ID);
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_THREAD_ID, threadId).build();
  }

  @Override
  public void onRun() {
    boolean trimmingEnabled   = TextSecurePreferences.isThreadLengthTrimmingEnabled(context);
    int     threadLengthLimit = TextSecurePreferences.getThreadTrimLength(context);

    if (!trimmingEnabled)
      return;

    if (threadId == -1) {
      threadLengthLimit = 100;
    }

    DatabaseFactory.getThreadDatabase(context).trimThread(threadId, threadLengthLimit);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Canceling trim attempt: " + threadId);
  }
}
