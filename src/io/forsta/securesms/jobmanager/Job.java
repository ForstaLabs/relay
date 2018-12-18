package io.forsta.securesms.jobmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.R;
import io.forsta.securesms.jobmanager.dependencies.ContextDependent;
import io.forsta.securesms.jobmanager.requirements.NetworkRequirement;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import io.forsta.securesms.service.GenericForegroundService;
import io.forsta.securesms.jobmanager.SafeData;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public abstract class Job extends Worker implements Serializable {

  private static final long serialVersionUID = -4658540468214421276L;

  private static final String TAG = Job.class.getSimpleName();

  static final String KEY_RETRY_COUNT            = "Job_retry_count";
  static final String KEY_RETRY_UNTIL            = "Job_retry_until";
  static final String KEY_SUBMIT_TIME            = "Job_submit_time";
  static final String KEY_REQUIRES_NETWORK   = "Job_requires_network";
  static final String KEY_REQUIRES_MASTER_SECRET = "Job_requires_master_secret";

  private JobParameters parameters;

  public Job(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  /**
   * Invoked when a job is first created in our own codebase.
   */
  @SuppressLint("RestrictedApi")
  protected Job(@NonNull Context context, @Nullable JobParameters parameters) {
    //noinspection ConstantConditions
    super(context, new WorkerParameters(null, null, Collections.emptySet(), null, 0, null, null, null));
    this.parameters = parameters;
  }

  @NonNull
  @Override
  public Result doWork() {
    Data data = getInputData();

    log("doWork()" + logSuffix());

    ApplicationContext.getInstance(getApplicationContext()).ensureInitialized();
    ApplicationContext.getInstance(getApplicationContext()).injectDependencies(this);

    if (this instanceof ContextDependent) {
      ((ContextDependent)this).setContext(getApplicationContext());
    }

    initialize(new SafeData(data));

    boolean foregroundRunning = false;

    try {
      if (withinRetryLimits(data)) {
        if (requirementsMet(data)) {
          if (needsForegroundService(data)) {
            Log.i(TAG, "Running a foreground service with description '" + getDescription() + "' to aid in job execution." + logSuffix());
            GenericForegroundService.startForegroundTask(getApplicationContext(), getDescription());
            foregroundRunning = true;
          }

          onRun();
          log("Successfully completed." + logSuffix());
          return Result.SUCCESS;
        } else {
          log("Retrying due to unmet requirements." + logSuffix());
          return retry();
        }
      } else {
        warn("Failing after hitting the retry limit." + logSuffix());
        return cancel();
      }
    } catch (Exception e) {
      if (onShouldRetry(e)) {
        log("Retrying after a retryable exception." + logSuffix());
        return retry();
      }
      warn("Failing due to an exception." + logSuffix(), e);
      return cancel();
    } finally {
      if (foregroundRunning) {
        Log.i(TAG, "Stopping the foreground service." + logSuffix());
        GenericForegroundService.stopForegroundTask(getApplicationContext());
      }
    }
  }

  private boolean needsForegroundService(@NonNull Data data) {
    NetworkRequirement networkRequirement = new NetworkRequirement(getApplicationContext());
    boolean            requiresNetwork    = data.getBoolean(KEY_REQUIRES_NETWORK, false);

    return requiresNetwork && !networkRequirement.isPresent();
  }

  @Override
  public void onStopped() {
    log("onStopped()" + logSuffix());
  }

  final void onSubmit(UUID id) {
    log(id, "onSubmit()");
    onAdded();
  }


  /**
   * @return A string that represents what the task does. Will be shown in a foreground notification
   *         if necessary.
   */
  protected String getDescription() {
    return getApplicationContext().getString(R.string.Job_working_in_the_background);
  }
  /**
   * Called after a run has finished and we've determined a retry is required, but before the next
   * attempt is run.
   */
  protected void onRetry() { }

  /**
   * Called after a job has been added to the JobManager queue. Invoked off the main thread, so its
   * safe to do longer-running work. However, work should finish relatively quickly, as it will
   * block the submission of future tasks.
   */
  protected void onAdded() { }

  /**
   * All instance state needs to be persisted in the provided {@link Data.Builder} so that it can
   * be restored in {@link #initialize(SafeData)}.
   * @param dataBuilder The builder where you put your state.
   * @return The result of {@code dataBuilder.build()}.
   */
  protected abstract @NonNull Data serialize(@NonNull Data.Builder dataBuilder);

  /**
   * Restore all of your instance state from the provided {@link Data}. It should contain all of
   * the data put in during {@link #serialize(Data.Builder)}.
   * @param data Where your data is stored.
   */
  protected abstract void initialize(@NonNull SafeData data);

  /**
   * Called to actually execute the job.
   * @throws Exception
   */
  public abstract void onRun() throws Exception;

  /**
   * Called if a job fails to run (onShouldRetry returned false, or the number of retries exceeded
   * the job's configured retry count.
   */
  protected abstract void onCanceled();

  /**
   * If onRun() throws an exception, this method will be called to determine whether the
   * job should be retried.
   *
   * @param exception The exception onRun() threw.
   * @return true if onRun() should be called again, false otherwise.
   */
  protected abstract boolean onShouldRetry(Exception exception);

  @Nullable JobParameters getJobParameters() {
    return parameters;
  }

  private Result retry() {
    onRetry();
    return Result.RETRY;
  }

  private Result cancel() {
    onCanceled();
    return Result.SUCCESS;
  }

  private boolean requirementsMet(Data data) {
    boolean met = true;

    if (data.getBoolean(KEY_REQUIRES_MASTER_SECRET, false)) {
      met &= new MasterSecretRequirement(getApplicationContext()).isPresent();
    }


    return met;
  }

  private boolean withinRetryLimits(Data data) {
    int  retryCount = data.getInt(KEY_RETRY_COUNT, 0);
    long retryUntil = data.getLong(KEY_RETRY_UNTIL, 0);

    if (retryCount > 0) {
      return getRunAttemptCount() <= retryCount;
    }

    return System.currentTimeMillis() < retryUntil;
  }

  private void log(@NonNull String message) {
    log(getId(), message);
  }

  private void log(@NonNull UUID id, @NonNull String message) {
    Log.i(TAG, buildLog(id, message));
  }

  private void warn(@NonNull String message) {
    warn(message, null);
  }

  private void warn(@NonNull String message, @Nullable Throwable t) {
    Log.w(TAG, buildLog(getId(), message), t);
  }

  private String buildLog(@NonNull UUID id, @NonNull String message) {
    return "[" + id + "] " + getClass().getSimpleName() + " :: " + message;
  }

  private String logSuffix() {
    long timeSinceSubmission = System.currentTimeMillis() - getInputData().getLong(KEY_SUBMIT_TIME, 0);
    return " (Time since submission: " + timeSinceSubmission + " ms, Run attempt: " + getRunAttemptCount() + ")";
  }
}