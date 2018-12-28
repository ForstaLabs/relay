package io.forsta.securesms.jobs.requirements;

import android.content.Context;

import io.forsta.securesms.jobmanager.dependencies.ContextDependent;
import io.forsta.securesms.jobmanager.requirements.NetworkRequirement;
import io.forsta.securesms.jobmanager.requirements.SimpleRequirement;

import io.forsta.securesms.jobmanager.requirements.SimpleRequirement;

public class NetworkOrServiceRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public NetworkOrServiceRequirement(Context context) {
    this.context = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    NetworkRequirement networkRequirement = new NetworkRequirement(context);
    ServiceRequirement serviceRequirement = new ServiceRequirement(context);

    return networkRequirement.isPresent() || serviceRequirement.isPresent();
  }
}
