package net.easyconn.gwmec;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.harman.connectivity.eanative.AppLaunchMethodType;
import com.harman.connectivity.eanative.NativeEAConnection;
import com.harman.connectivity.eanative.NativeEAListener;
import com.harman.connectivity.eanative.NativeEAManager;
import com.harman.connectivity.iap2.CallStateInfo;
import com.harman.connectivity.iap2.ErrorState;
import com.harman.connectivity.iap2.IAP2Manager;
import com.harman.connectivity.iap2.IAP2NotConnected;
import com.harman.connectivity.iap2.LocationInfoRequest;
import com.harman.connectivity.iap2.MediaLibraryInfo;
import com.harman.connectivity.iap2.MediaMetaData;
import com.harman.connectivity.iap2.PlaybackRepeatModeType;
import com.harman.connectivity.iap2.PlaybackShuffleModeType;
import com.harman.connectivity.iap2.PlaybackStatusData;
import com.harman.connectivity.iap2.PlaybackStatusType;
import com.harman.connectivity.iap2.PowerUpdateInfo;
import com.harman.connectivity.iap2.SyncStage;
import com.harman.connectivity.iap2.TelephonyUpdate;
import com.harman.connectivity.iap2.TrackTimePosition;
import com.harman.connectivity.iap2.TransportType;
import com.harman.connectivity.iap2.UsbDevModeAudioSampleRateType;
import com.harman.connectivity.iap2.WirelessCarPlayType;

import java.nio.ByteBuffer;
import java.util.List;

/**
 *  Usb Device use {@link NativeEAConnection} to read/write on EAP
 *
 *  @author hurricane.hu
 *  create at 2018/12/12
 */
public class ECIOSUsbDevice implements Handler.Callback, IAP2Manager.IAP2ServiceCallback, NativeEAListener {

    private static final String TAG = "Carbit";

    private static final String EAP_PROTOCOL = "cn.carbit.ecprotocol";
    private static final String BUNDLE_ID = "com.carbit.inhouse";

    private NativeEAConnection mEAConn;
    private byte[] mReadBuf = new byte[4096];
    private byte[] mWriteBuf = new byte[4096];
    private boolean connError = false;
    private String deviceId;
    private NativeEAManager mEAManager;
    private Handler mHandler;
    private final static int USB_TIMEOUT_MS = 1000;
    private volatile boolean isOnUpdateEndpointsReady = false;
    private volatile boolean isAuthenticationStatus = false;
    private volatile boolean isIdentificationStatus = false;

    private int readNum = 0;

    ECIOSUsbDevice(String deviceId, Context context) {
        this.deviceId = deviceId;
        mHandler = new Handler(Looper.getMainLooper(), this);
        IAP2Manager iap2Manager = new IAP2Manager();
        mEAManager = new NativeEAManager(context, EAP_PROTOCOL, iap2Manager);
        mEAManager.registerService(deviceId, this);
        iap2Manager.registerListener(deviceId, this);
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.d(TAG, "isOnUpdateEndpointsReady:" + isOnUpdateEndpointsReady + ", isAuthenticationStatus:" + isAuthenticationStatus + ", isIdentificationStatus:" + isIdentificationStatus);
        if (isOnUpdateEndpointsReady && isAuthenticationStatus && isIdentificationStatus) {
            try {
                if (mEAManager != null) {
                    mEAManager.requestAppLaunch(deviceId, BUNDLE_ID, AppLaunchMethodType.WITH_USER_ALERT);
                    Log.d(TAG, "EAManager.openConnection()");
                    mEAConn = mEAManager.openConnection();
                    if (mEAConn == null) {
                        Log.d(TAG, "mEAConn == null");
                    } else {
                        Log.d(TAG, "mEAConn=" + mEAConn.toString());
                    }

                }
            } catch (IAP2NotConnected iap2NotConnected) {
                iap2NotConnected.printStackTrace();
            }
        }

        return false;
    }

    public int open() {
        Log.d(TAG, "IOSUsbDevice open");
        return 0;
    }

    public int read(ByteBuffer byteBuffer, int length) {
        if (mEAManager == null) {
            Log.d(TAG, "read nEAManager == null");
            return -1;
        }
        if (connError) {
            Log.d(TAG, "read connError");
            return -1;
        }
        if (mEAConn == null) {
            Log.d(TAG, "read mEAConn == null");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 0;
        }
        Log.d(TAG, "read num:" + readNum + " begin: " + length);
        int size = mEAConn.readData(mReadBuf, 0, length, USB_TIMEOUT_MS);
        Log.d(TAG, "read num:" + readNum + " return: " + size);

        return size;
    }

    public int write(ByteBuffer byteBuffer, int length) {
        if (mEAManager == null) {
            Log.d(TAG, "write nEAManager == null");
            return -1;
        }

        if (connError) {
            Log.d(TAG, "write connError");
            return -1;
        }
        if (mEAConn == null) {
            Log.d(TAG, "write mEAConn == null");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 0;
        }
        byteBuffer.get(mWriteBuf, 0, length);
        Log.d(TAG, "write num:" + readNum + " begin: " + length);
        int size = mEAConn.writeData(mWriteBuf, 0, length);
        Log.d(TAG, "write num:" + readNum + " return: " + size);

        return size;
    }

    public void close() {
        Log.d(TAG, "close");
        if (mEAConn != null) {
            mEAConn.close();
            mEAConn = null;
        }
    }


    /////////////////////////////////////////////////////////////////////////////
    // NativeEAListener
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public void onUpdateConnectionStatus(boolean b) {
        Log.d(TAG, " NativeEAListener onUpdateConnectionStatus b=" + b);
    }

    @Override
    public void onUpdateEndpointsReady(String s, boolean b) {
        //Log.d("IOSUsbDevice openConnection");
        Log.d(TAG,  " NativeEAListener onUpdateEndpointsReady s=" + s + ", b=" + b);
        isOnUpdateEndpointsReady = true;
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onErrorStateUpdate(String s, String s1) {
        Log.d(TAG,  " NativeEAListener onErrorStateUpdate s=" + s + ", s1=" + s1);
        connError = true;
    }

    /////////////////////////////////////////////////////////////////////////////
    // IAP2Manager.IAP2ServiceCallback
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public void authenticationStatus(String s, boolean b) {
        Log.d(TAG,  "IAP2Manager.IAP2ServiceCallback authenticationStatus s=" + s + ", b=" + b);
        isAuthenticationStatus = true;
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void identificationStatus(String s, boolean b) {
        Log.d(TAG,  "IAP2Manager.IAP2ServiceCallback identificationStatus s=" + s + ", b=" + b);
        isIdentificationStatus = true;
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void powerUpdate(String s, PowerUpdateInfo powerUpdateInfo) {
        Log.d(TAG, "powerUpdate");
    }

    @Override
    public void startVehicleStatusUpdate(String s, boolean b, boolean b1, boolean b2) {
        Log.d(TAG, "startVehicleStatusUpdate");
    }

    @Override
    public void stopVehicleStatusUpdate(String s) {
        Log.d(TAG, "stopVehicleStatusUpdate");
    }

    @Override
    public void startLocationInfoUpdate(String s, LocationInfoRequest locationInfoRequest) {
        Log.d(TAG, "startLocationInfoUpdate");
    }

    @Override
    public void stopLocationInfoUpdate(String s) {
        Log.d(TAG, "stopLocationInfoUpdate");
    }

    @Override
    public void artworkAvailable(String s, String s1) {
        Log.d(TAG, "artworkAvailable");
    }

    @Override
    public void playbackQueueListAvailable(String s, String[] strings) {
        Log.d(TAG, "playbackQueueListAvailable");
    }

    @Override
    public void deviceNameUpdate(String s, String s1) {
        Log.d(TAG, "deviceNameUpdate");
    }

    @Override
    public void deviceLanguageUpdate(String s, String s1) {
        Log.d(TAG, "deviceLanguageUpdate");
    }

    @Override
    public void deviceTimeUpdate(String s, long l, int i, int i1) {
        Log.d(TAG, "deviceTimeUpdate");
    }

    @Override
    public void deviceUUIDUpdate(String s, String s1) {
        Log.d(TAG, "deviceUUIDUpdate");
    }

    @Override
    public void deviceTransportIDUpdate(String s, String s1, String s2) {
        Log.d(TAG, "deviceTransportIDUpdate");
    }

    @Override
    public void wirelessCarPlayUpdate(String s, WirelessCarPlayType wirelessCarPlayType) {
        Log.d(TAG, "wirelessCarPlayUpdate");
    }

    @Override
    public void newEAPSession(String s, String s1, int i, String s2) {
        Log.d(TAG, "newEAPSession");
    }

    @Override
    public void eapDataAvailable(String s, String s1, int i, byte[] bytes, byte b) {
        Log.d(TAG, "eapDataAvailable");
    }

    @Override
    public void closedEAPSession(String s, String s1, int i) {
        Log.d(TAG, "closedEAPSession");
    }

    @Override
    public void telephonyUpdates(String s, TelephonyUpdate telephonyUpdate) {
        Log.d(TAG, "telephonyUpdates");
    }

    @Override
    public void callStateStatusUpdates(String s, CallStateInfo callStateInfo) {
        Log.d(TAG, "callStateStatusUpdates");
    }

    @Override
    public void mediaLibInfoUpdate(String s, List<MediaLibraryInfo> list) {
        Log.d(TAG, "mediaLibInfoUpdate");
    }

    @Override
    public void deviceModeSamplingRateUpdate(String s, UsbDevModeAudioSampleRateType usbDevModeAudioSampleRateType) {
        Log.d(TAG, "deviceModeSamplingRateUpdate");
    }

    @Override
    public void errorStatusEvent(String s, ErrorState errorState) {
        Log.d(TAG, "errorStatusEvent");
    }

    @Override
    public void onUpdateTrackTimePosition(String s, TrackTimePosition trackTimePosition) {
        Log.d(TAG, "onUpdateTrackTimePosition");
    }

    @Override
    public void onUpdateRepeatStatus(String s, PlaybackRepeatModeType playbackRepeatModeType) {
        Log.d(TAG, "onUpdateRepeatStatus");
    }

    @Override
    public void onUpdatePlayBackSpeed(String s, int i) {
        Log.d(TAG, "onUpdatePlayBackSpeed");
    }

    @Override
    public void onUpdateShuffleStatus(String s, PlaybackShuffleModeType playbackShuffleModeType) {
        Log.d(TAG, "onUpdateShuffleStatus");
    }

    @Override
    public void onUpdatePlayBackState(String s, PlaybackStatusType playbackStatusType) {
        Log.d(TAG, "onUpdatePlayBackState");
    }

    @Override
    public void onUpdateNowPlayingPlayBackStatus(String s, PlaybackStatusData playbackStatusData) {
        Log.d(TAG, "onUpdateNowPlayingPlayBackStatus");
    }

    @Override
    public void onUpdateNowPlayingMetaData(String s, MediaMetaData mediaMetaData) {
        Log.d(TAG, "onUpdateNowPlayingMetaData");
    }

    @Override
    public void onUpdateTransportParams(String s, TransportType transportType, boolean b) {
        Log.d(TAG, "onUpdateTransportParams");
    }

    @Override
    public void onUpdateDataBaseStatus(String s, SyncStage syncStage, int i, String s1) {
        Log.d(TAG, "onUpdateDataBaseStatus");
    }
}
