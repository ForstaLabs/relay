package io.forsta.securesms.webrtc;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import io.forsta.securesms.util.concurrent.SettableFuture;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PeerConnectionWrapper {
  private static final String TAG = PeerConnectionWrapper.class.getSimpleName();

  private static final PeerConnection.IceServer STUN_SERVER = new PeerConnection.IceServer("stun:stun1.l.google.com:19302");

  @NonNull  private final PeerConnection peerConnection;

  public PeerConnectionWrapper(@NonNull Context context,
                               @NonNull PeerConnectionFactory factory,
                               @NonNull PeerConnection.Observer observer,
                               @NonNull MediaStream localMediaStream,
                               @NonNull List<PeerConnection.IceServer> turnServers)
  {
    List<PeerConnection.IceServer> iceServers = new LinkedList<>();
//    iceServers.add(STUN_SERVER);
    iceServers.addAll(turnServers);

    MediaConstraints                constraints      = new MediaConstraints();
    MediaConstraints                audioConstraints = new MediaConstraints();
    PeerConnection.RTCConfiguration configuration    = new PeerConnection.RTCConfiguration(iceServers);

    configuration.bundlePolicy  = PeerConnection.BundlePolicy.MAXBUNDLE;
    configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

    constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    audioConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    this.peerConnection = factory.createPeerConnection(configuration, constraints, observer);
    this.peerConnection.addStream(localMediaStream);
  }

  public DataChannel createDataChannel(String name) {
    DataChannel.Init dataChannelConfiguration = new DataChannel.Init();
    dataChannelConfiguration.ordered = true;
    return this.peerConnection.createDataChannel(name, dataChannelConfiguration);
  }

  public SessionDescription createOffer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createOffer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public SessionDescription createAnswer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createAnswer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setRemoteDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setRemoteDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {}

      @Override
      public void onCreateFailure(String error) {}

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setLocalDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setLocalDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        throw new AssertionError();
      }

      @Override
      public void onCreateFailure(String error) {
        throw new AssertionError();
      }

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void dispose(MediaStream localStream) {
    this.peerConnection.close();
    this.peerConnection.removeStream(localStream);
    this.peerConnection.dispose();
  }

  public boolean addIceCandidate(IceCandidate candidate) {
    return this.peerConnection.addIceCandidate(candidate);
  }

  public SessionDescription getRemoteDescription() {
    return peerConnection.getRemoteDescription();
  }

  private SessionDescription correctSessionDescription(SessionDescription sessionDescription) {
    String updatedSdp = sessionDescription.description.replaceAll("(a=fmtp:111 ((?!cbr=).)*)\r?\n", "$1;cbr=1\r\n");
    updatedSdp = updatedSdp.replaceAll(".+urn:ietf:params:rtp-hdrext:ssrc-audio-level.*\r?\n", "");

    return new SessionDescription(sessionDescription.type, updatedSdp);
  }

  public static class PeerConnectionException extends Exception {
    public PeerConnectionException(String error) {
      super(error);
    }

    public PeerConnectionException(Throwable throwable) {
      super(throwable);
    }
  }
}
