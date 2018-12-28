package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.WorkerParameters;
import io.forsta.securesms.jobmanager.Job;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.dependencies.ContextDependent;

public abstract class ContextJob extends Job implements ContextDependent {

  protected transient Context context;

  protected ContextJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected ContextJob(@NonNull Context context, @NonNull JobParameters parameters) {
    super(context, parameters);
    this.context = context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  protected Context getContext() {
    return context;
  }
}
