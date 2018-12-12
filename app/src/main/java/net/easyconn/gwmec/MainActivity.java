package net.easyconn.gwmec;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 *  MainActivity
 *
 *  @author hurricane.hu
 *  create at 2018/12/12
 */
public class MainActivity extends AppCompatActivity {

    static final String TAG = "Carbit";
    private Context mContext;

    private MyBroadcastReceiver mReceiver;
    private ECIOSUsbDevice eciosUsbDevice;
    private static volatile boolean isDeviceAttach = false;

    int bufferSize = 4096;
    private final ByteBuffer readByteBuffer = ByteBuffer.allocate(bufferSize); // read buffer
    private final ByteBuffer writeByteBuffer = ByteBuffer.allocate(bufferSize); // write buffer

    TextView writeTv;
    TextView readTv;
    TextView readTvError;

    class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "BroadcastManager: onReceive action=" + action);
            if (action == null) {
                return;
            }
            switch (action) {
                case Constants.HAMAN_IAP2_DEVICE_CONNECTED:
                    String deviceId = intent.getStringExtra(Constants.EXTRA_IAP2_DEVICEID);
                    String modeType = intent.getStringExtra(Constants.EXTRA_IAP2_MODETYPE);
                    String transportType = intent.getStringExtra(Constants.EXTRA_IAP2_TRANSPORTTYPE);
                    Log.d(TAG, "EXTRA_IAP2_DEVICEID: "+deviceId);
                    Log.d(TAG, "EXTRA_IAP2_MODETYPE: "+modeType);
                    Log.d(TAG, "EXTRA_IAP2_TRANSPORTTYPE: "+transportType);
                    eciosUsbDevice = new ECIOSUsbDevice(deviceId, mContext);
                    isDeviceAttach = true;
                    break;
                case Constants.HAMAN_IAP2_DEVICE_DISCONNECTED:
                    isDeviceAttach = false;
                    writeLoop = false;
                    readLoop = false;
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        initView();
        initBroadCast();
    }

    @Override
    protected void onDestroy() {
        sendBroadcast(new Intent(Constants.ACTION_CARBIT_DISABLE));
        sendBroadcast(new Intent(Constants.HAMAN_IAP2_DEVICE_DISCONNECTED));
        unregisterReceiver(mReceiver);
        readLoop = false;
        writeLoop = false;
        super.onDestroy();
    }

    private void initBroadCast() {
        mReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.HAMAN_IAP2_DEVICE_CONNECTED);
        intentFilter.addAction(Constants.HAMAN_IAP2_DEVICE_DISCONNECTED);
        registerReceiver(mReceiver, intentFilter);
        Log.d(TAG, "Send Carbit Enable Broadcast!");
        sendBroadcast(new Intent(Constants.ACTION_CARBIT_ENABLE));
    }

    private static int writeDataIndex = 1;
    private static int readDataIndexLast = 0;
    private static boolean writeLoop = false;
    private static boolean readLoop = false;
    private static long writeDataSum = 0;
    private static long readDataSum = 0;

    private void initView() {
        writeTv = findViewById(R.id.textView_write);
        readTv = findViewById(R.id.textView_read);
        readTvError = findViewById(R.id.textView_read_error);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        findViewById(R.id.start_write).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isDeviceAttach) {
                    Snackbar.make(v,"No Device attach", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                if(writeLoop) {
                    Snackbar.make(v,"Write thread has started", Snackbar.LENGTH_SHORT).show();
                    return;
                } else {
                    writeLoop = true;
                }
                new Thread("write") {
                    @Override
                    public void run() {
                        while (writeLoop && isDeviceAttach) {
                            writeByteBuffer.clear();
                            writeByteBuffer.put(intToBytes(writeDataIndex));
                            int randomLength = getRandomInt();
                            Log.d(TAG, "[writeTask]writeDataIndex is " + writeDataIndex + " ;randomLength is " + randomLength);
                            writeByteBuffer.put(intToBytes(randomLength));
                            for (int i = 0; i < randomLength; i++) {
                                // 填充数据
                                writeByteBuffer.put((byte) 0x31);
                            }
                            writeDataSum = writeDataSum + randomLength;
                            writeTv.setText("INDEX:" + writeDataIndex + " ;write total：" + writeDataSum + " byte");
                            writeByteBuffer.flip();
                            int writeResult = eciosUsbDevice.write(writeByteBuffer, randomLength + 8);
                            if (writeResult < 0) {
                                break;
                            }
                            writeDataIndex = writeDataIndex + 1;

                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }.start();
            }
        });

        findViewById(R.id.start_read).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isDeviceAttach) {
                    Snackbar.make(v,"No Device attach", Snackbar.LENGTH_SHORT).show();
                    return;
                }

                if(readLoop) {
                    Snackbar.make(v,"Read thread has started", Snackbar.LENGTH_SHORT).show();
                    return;
                } else {
                    readLoop = true;
                }
                new Thread("read") {
                    @Override
                    public void run() {
                        while (readLoop && isDeviceAttach) {
                            readByteBuffer.clear();
                            int readRes = eciosUsbDevice.read(readByteBuffer, 8);
                            Log.d(TAG, "[readTask] read header data res is " + readRes);
                            readByteBuffer.flip();
                            byte[] headerData = readByteBuffer.array();
                            int index = fourBytesToInt(headerData, 0);
                            int length = fourBytesToInt(headerData, 4);
                            readDataSum = readDataSum + length;
                            readTv.setText("INDEX:" + index + " ;read total：" + readDataSum + " byte");
                            if (readDataIndexLast != 0 && index - readDataIndexLast > 1) {
                                readTvError.setText("Not receive index:" + (index -1) + " data!");
                            }
                            readDataIndexLast = index;
                            readByteBuffer.clear();
                            readRes = eciosUsbDevice.read(readByteBuffer, length);
                        }
                    }
                }.start();
            }
        });
    }

    private byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }

    private int fourBytesToInt(byte[] data, int offset) {
        int value = 0;
        value |= (data[offset] & 0xFF);
        value |= (data[offset + 1] & 0xFF) << 8;
        value |= (data[offset + 2] & 0xFF) << 16;
        value |= (data[offset + 3] & 0xFF) << 24;
        return value;
    }

    private Random random = new Random();
    private int getRandomInt() {
        int res = random.nextInt(bufferSize) % (bufferSize - bufferSize / 10 + 1) + bufferSize / 10 - 10;
        return res;
    }
}
