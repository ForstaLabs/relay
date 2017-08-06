package io.forsta.securesms.service;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import io.forsta.securesms.BaseUnitTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.when;

public class SmsListenerTest extends BaseUnitTest {
  private static Map<String, String> CHALLENGES = new HashMap<String,String>() {{
      put("Use this code to verify your phone number with Forsta Servers: 337-337",        "337337");
      put("XXX\nUse this code to verify your phone number with Forsta Servers: 1337-1337", "13371337");
      put("XXXUse this code to verify your phone number with Forsta Servers: 337-337",   "337337");
      put("Use this code to verify your phone number with Forsta: 337-337XXX",   "337337");
  }};

  private SmsListener listener;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    listener = new SmsListener();
    when(sharedPreferences.getBoolean(contains("pref_verifying"), anyBoolean())).thenReturn(true);
  }

  @Test
  public void testChallenges() throws Exception {
    for (Entry<String,String> challenge : CHALLENGES.entrySet()) {
      if (!listener.isChallenge(context, challenge.getKey())) {
        throw new AssertionFailedError("SmsListener didn't recognize body as a challenge.");
      }
      String testChallenge = listener.parseChallenge(challenge.getKey());
      assertEquals(listener.parseChallenge(challenge.getKey()), challenge.getValue());
    }
  }
}
