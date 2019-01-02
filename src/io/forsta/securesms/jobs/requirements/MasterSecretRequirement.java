package io.forsta.securesms.jobs.requirements;

import android.content.Context;

import io.forsta.securesms.jobmanager.requirements.SimpleRequirement;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.jobmanager.dependencies.ContextDependent;

public class MasterSecretRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return KeyCachingService.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
