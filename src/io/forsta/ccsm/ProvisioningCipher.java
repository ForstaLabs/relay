package io.forsta.ccsm;

import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.signalservice.internal.push.ProvisioningProtos;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by john on 2/1/2018.
 */

public class ProvisioningCipher {
  private static final String TAG = ProvisioningCipher.class.getSimpleName();

  private ECPublicKey theirPublicKey;

  public ProvisioningCipher(ECPublicKey theirPublicKey) {
    this.theirPublicKey = theirPublicKey;
  }

  public ProvisioningProtos.ProvisionMessage decrypt(ProvisioningProtos.ProvisionEnvelope envelope, ECPrivateKey privateKey) {

    try {
      byte[] key = envelope.getPublicKey().toByteArray();
      byte[] body = envelope.getBody().toByteArray();
      int version = body[0];
      if (version != 1) {
        Log.w(TAG, "Invalid ProvisionMessage version");
      }
      byte[] iv = Arrays.copyOfRange(body, 1, 16 + 1);
      byte[] mac = Arrays.copyOfRange(body, body.length - 32, body.length);
      byte[] ivAndCiphertext = Arrays.copyOfRange(body, 0, body.length - 32);
      byte[] ciphertext = Arrays.copyOfRange(body, 16 + 1, body.length - 32);
      ECPublicKey pubKey = Curve.decodePoint(key, 0);
      byte[] ec = Curve.calculateAgreement(pubKey, privateKey);
      byte[] keys = new HKDFv3().deriveSecrets(ec, "TextSecure Provisioning Message".getBytes(), 64);
      byte[][]  parts = org.whispersystems.signalservice.internal.util.Util.split(keys, 32, 32);
      Cipher cipher = getCipher(Cipher.DECRYPT_MODE, new SecretKeySpec(parts[0], "AES"), new IvParameterSpec(iv));
      //This is crashing. Keys must not be correct.
      byte[] plainText = getPlaintext(cipher, ciphertext);
      org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage provisionMessage = org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage.parseFrom(plainText);
      return provisionMessage;
    } catch (InvalidMessageException e) {
      e.printStackTrace();
    } catch (InvalidKeyException e) {
      e.printStackTrace();
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }
    return  null;
  }

  private byte[] getPlaintext(Cipher cipher, byte[] cipherText)
      throws InvalidMessageException
  {
    try {
      return cipher.doFinal(cipherText);
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new InvalidMessageException(e);
    }
  }

  private Cipher getCipher(int mode, SecretKeySpec key, IvParameterSpec iv) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(mode, key, iv);
      return cipher;
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException |
        InvalidAlgorithmParameterException e)
    {
      throw new AssertionError(e);
    }
  }

  private byte[] getCiphertext(byte[] key, byte[] message) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

      return Util.join(cipher.getIV(), cipher.doFinal(message));
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getMac(byte[] key, byte[] message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));

      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}

