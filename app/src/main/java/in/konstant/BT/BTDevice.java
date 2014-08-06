package in.konstant.BT;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class BTDevice {
    // Debug
    private static final String TAG = "BTDevice";
    private static final boolean DBG = true;

    // Name for SDP record for Server Socket
    private static final String NAME = "BTConnection";

    // Standard Serial Port UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private String mAddress;
    private String mName;
    private String mManufacturer;
    private String mCompany;

    private final Context mContext;
    private final Handler mHandler;

    public static final class Notification {
        public static final int CONNECTED = 1;
        public static final int DISCONNECTED = 2;
        public static final int CONNECTION_LOST = 3;
        public static final int CONNECTION_FAILED = 4;
        public static final int DATA_SENT = 5;
        public static final int DATA_RECEIVED = 6;
    }

    public static final String EXTRA_ADDRESS = "in.konstant.BT.device.extra.ADDRESS";
    public static final String EXTRA_DATA = "in.konstant.BT.device.extra.DATA";

//    private final Object mBluetoothService;
//    private final Class mBluetoothServiceClass;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private int mState;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    BTDevice(Context context, Handler handler) {
        if (DBG) Log.d(TAG, "BTDevice()");

        mContext = context;
        mHandler = handler;

//        mBluetoothService = mContext.getSystemService(Context.BLUETOOTH_SERVICE);
//        mBluetoothServiceClass = mBluetoothService.getClass();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mState = STATE_DISCONNECTED;
    }

    BTDevice(Context context, Handler handler, String address) {
        this(context, handler);

        if (DBG) Log.d(TAG, "BTDevice(" + address + ")");

        connect(address);
    }

    public void destroy() {
        if (DBG) Log.d(TAG, "destroy()");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mState = STATE_DISCONNECTED;
    }

    // Interface -----------------------------------------------------------------------------------

    public void connect(String address) {
        if (DBG) Log.d(TAG, "connect(" + address + ")");

        mAddress = address;
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mAddress);

        mName = mBluetoothDevice.getName();
        mManufacturer = ""; // TODO getManufacturer();
        mCompany = ""; // TODO getCompany();

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(mBluetoothDevice);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    public void send(byte[] data) {
        if (DBG) Log.d(TAG, "send()");
        ConnectedThread ct;

        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            ct = mConnectedThread;
        }

        ct.write(data);
    }

    public void disconnect() {
        if (DBG) Log.d(TAG, "disconnect()");

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null)
            {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.close();
            mConnectedThread = null;
        }

        notifyService(Notification.DISCONNECTED, null);

        setState(STATE_DISCONNECTED);
    }

    // Setter & Getter -----------------------------------------------------------------------------

    public BluetoothClass getBluetoothClass() {
        return mBluetoothDevice.getBluetoothClass();
    }

    public String getAddress() {
        return mAddress;
    }

    public String getName() {
        return mName;
    }

    public UUID[] getUUIDs() {
        ParcelUuid[] parcels = mBluetoothDevice.getUuids();

        ArrayList<UUID> list = new ArrayList<UUID>();

        for (ParcelUuid parcel : parcels) {
            list.add(parcel.getUuid());
        }

        return list.toArray(new UUID[list.size()]);
    }

    public boolean isConnected() {
        if (mState == STATE_CONNECTED) {
            return true;
        } else {
            return false;
        }
    }

    // Helpers -------------------------------------------------------------------------------------

    private void setState(int state) {
        if (DBG) Log.d(TAG, "setState(" + state + ")");
        mState = state;
    }

    private void notifyService(int event, byte[] data) {
        if (DBG) Log.d(TAG, "notifyService(" + event + ")");

        Message msg = mHandler.obtainMessage(event);
        Bundle b = new Bundle();
        b.putString(EXTRA_ADDRESS, mAddress);

        if (data != null && data.length > 0) {
            b.putByteArray(EXTRA_DATA, data);
        }

        msg.setData(b);
        mHandler.sendMessage(msg);
    }

    public static String getServiceMajorClassName(int majorClass) {
        switch (majorClass) {
            case BluetoothClass.Service.AUDIO:
                return "Audio";
            case BluetoothClass.Service.CAPTURE:
                return "Capture";
            case BluetoothClass.Service.INFORMATION:
                return "Information";
            case BluetoothClass.Service.NETWORKING:
                return "Networking";
            case BluetoothClass.Service.OBJECT_TRANSFER:
                return "Transfer";
            case BluetoothClass.Service.POSITIONING:
                return "Positioning";
            case BluetoothClass.Service.RENDER:
                return "Render";
            case BluetoothClass.Service.TELEPHONY:
                return "Telephony";
            default:
                return "Unknown";
        }
    }

    public static String getDeviceMajorClassName(int majorClass) {
        switch (majorClass) {
            case BluetoothClass.Device.Major.COMPUTER:
                return "PC";
            case BluetoothClass.Device.Major.PHONE:
                return "Phone";
            case BluetoothClass.Device.Major.IMAGING:
                return "Imaging";
            case BluetoothClass.Device.Major.NETWORKING:
                return "Networking";
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return "AV";
            case BluetoothClass.Device.Major.HEALTH:
                return "Health";
            case BluetoothClass.Device.Major.PERIPHERAL:
                return "Peripheral";
            case BluetoothClass.Device.Major.TOY:
                return "Toy";
            case BluetoothClass.Device.Major.WEARABLE:
                return "Wearable";
            case BluetoothClass.Device.Major.MISC:
                return "Misc";
            case BluetoothClass.Device.Major.UNCATEGORIZED:
                return "Uncategorized";
            default:
                return "Unknown";
        }
    }

    // State Changers ------------------------------------------------------------------------------

    private void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (DBG) Log.d(TAG, "connected(" + socket + ", " + device + ")");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        notifyService(Notification.CONNECTED, null);

        setState(STATE_CONNECTED);
    }

    private void connectionLost() {
        if (DBG) Log.d(TAG, "connectionLost()");
        notifyService(Notification.CONNECTION_LOST, null);
        setState(STATE_DISCONNECTED);
    }

    private void connectionFailed() {
        if (DBG) Log.d(TAG, "connectionFailed()");
        notifyService(Notification.CONNECTION_FAILED, null);
        setState(STATE_DISCONNECTED);
    }

    // Threads -------------------------------------------------------------------------------------

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            if (DBG) Log.d(TAG, "ConnectThread()");
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectThread() Socket create() failed", e);
            }

            mmSocket = tmp;
        }

        public void run() {
            if (DBG) Log.d(TAG, "BEGIN ConnectThread");
            setName("ConnectThread");

            mBluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {

                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    if (DBG) Log.d (TAG, "ConnectThread run() Socket close() failed", e1);
                }

                connectionFailed();

                return;
            }

            // Reset ConnectThread
            synchronized (BTDevice.this) {
                mConnectThread = null;
            }

            // Start ConnectedThread
            connected(mmSocket, mmDevice);

            if (DBG) Log.d(TAG, "END ConnectThread");
        }

        public void cancel() {
            if (DBG) Log.d(TAG, "ConnectThread cancel()");
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectThread cancel() Socket close() failed", e);
            }
        }
    }

//##################################################################################################

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private boolean mmConnected = false;

        public ConnectedThread(BluetoothSocket socket) {
            if (DBG) Log.d(TAG, "ConnectedThread()");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "Connected Thread() Socket getStream() failed", e);
            }

            mmConnected = true;

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            if (DBG) Log.d(TAG, "BEGIN ConnectedThread");
            setName("ConnectedThread");

            byte[] buffer;
            int bytes;

            while (mmConnected) {
                try {
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);
                    notifyService(Notification.DATA_RECEIVED, buffer);
                } catch (IOException e) {
                    if (DBG) Log.d(TAG, "ConnectedThread run() inStream read() failed", e);
                    if (mmConnected) {
                        // Only report connection loss if unintentional disconnect
                        connectionLost();
                    }
                    break;
                }
            }

            if (DBG) Log.d(TAG, "END ConnectedThread");
        }

        public void write(byte[] buffer) {
            if (DBG) Log.d(TAG, "ConnectedThread write()");
            try {
                mmOutStream.write(buffer);
                notifyService(Notification.DATA_SENT, buffer);
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectedThread write() outStream write() failed", e);
            }
        }

        public void close() {
            if (DBG) Log.d(TAG, "ConnectedThread close()");

            mmConnected = false;

            cancel();
        }

        public void cancel() {
            if (DBG) Log.d(TAG, "ConnectedThread cancel()");

            try {
                mmSocket.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectedThread cancel() Socket close() failed", e);
            }
        }
    }

}
