package io.forsta.securesms.crypto;

import org.junit.Before;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import io.forsta.securesms.BaseUnitTest;
import org.whispersystems.libsignal.InvalidMessageException;

@PowerMockIgnore("javax.crypto.*")
public class MasterCipherTest extends BaseUnitTest {
  private MasterCipher masterCipher;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    masterCipher = new MasterCipher(masterSecret);
  }

  @Test(expected = InvalidMessageException.class)
  public void testEncryptBytesWithZeroBody() throws Exception {
    masterCipher.decryptBytes(new byte[]{});
  }
}
