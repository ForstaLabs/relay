package io.forsta.securesms.jobs;

import android.content.Context;

import io.forsta.securesms.jobmanager.Job;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.dependencies.ContextDependent;

public abstract class ContextJob extends Job implements ContextDependent {

  protected transient Context context;

  protected ContextJob(Context context, JobParameters parameters) {
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
