package io.forsta.securesms.jobs;

import org.junit.Before;
import org.junit.Test;
import io.forsta.securesms.BaseUnitTest;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.AttachmentId;
import io.forsta.securesms.jobs.AttachmentDownloadJob.InvalidPartException;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class AttachmentDownloadJobTest extends BaseUnitTest {
  private AttachmentDownloadJob job;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    job = new AttachmentDownloadJob(context, 1L, new AttachmentId(1L, 1L));
  }

  @Test(expected = InvalidPartException.class)
  public void testCreateAttachmentPointerInvalidId() throws Exception {
    Attachment attachment = mock(Attachment.class);
    when(attachment.getLocation()).thenReturn(null);
    when(attachment.getKey()).thenReturn("a long and acceptable valid key like we all want");

    job.createAttachmentPointer(masterSecret, attachment);
  }

  @Test(expected = InvalidPartException.class)
  public void testCreateAttachmentPointerInvalidKey() throws Exception {
    Attachment attachment = mock(Attachment.class);
    when(attachment.getLocation()).thenReturn("something");
    when(attachment.getKey()).thenReturn(null);

    job.createAttachmentPointer(masterSecret, attachment);
  }
}
