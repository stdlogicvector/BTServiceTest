package in.konstant.BT;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BTService extends Service {
    // Debug
    private static final String TAG = "BTService";
    private static final boolean DBG = true;

    // Message Handler
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());
    private Messenger mOutgoingMessenger = null;

    // Messages
    public static final class Command {
        public static final int ENABLE = 1000;
        public static final int DISABLE = 1001;
        public static final int CONNECT = 1002;
        public static final int DISCONNECT = 1003;
        public static final int GET_STATE = 1004;
        public static final int LISTEN = 1005;
        public static final int DONT_LISTEN = 1006;
        public static final int GET_DEVICES = 1007;
        public static final int SEND = 1008;
    }

    public static final class Reply {
        public static final int ENABLED = 2000;
        public static final int DISABLED = 2001;
        public static final int CONNECTED = 2002;
        public static final int CONNECT_FAILED = 2003;
        public static final int DISCONNECTED = 2004;
        public static final int LISTENING = 2005;
        public static final int NOT_LISTENING = 2006;
        public static final int DEVICES = 2007;
        public static final int SENT = 2008;
        public static final int RECEIVED = 2009;
    }

    public static final String EXTRA_MESSENGER = "in.konstant.BT.service.extra.MESSENGER";
    public static final String EXTRA_ADDRESS = "in.konstant.BT.service.extra.ADDRESS";
    public static final String EXTRA_ADDRESSES = "in.konstant.BT.service.extra.ADDRESSES";
    public static final String EXTRA_DATA = "in.konstant.BT.service.extra.DATA";

    private Context mContext;
    private BTBroadcastReceiver mBTBroadcastReceiver;

    // Bluetooth
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mBTAvailable = false;
    private boolean mBTEnabled = false;

    private HashMap<String, BTDevice> mConnectedDevices;

    // Lifecycle ----------------------------------------------------------------------------

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "onCreate()");
        mContext = this;

        mConnectedDevices = new HashMap<String, BTDevice>();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            mBTAvailable = true;
            mBTEnabled = mBluetoothAdapter.isEnabled();
        }

        mBTBroadcastReceiver = new BTBroadcastReceiver(mContext);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind(" + intent + ")");

        mOutgoingMessenger = (Messenger) intent.getParcelableExtra(EXTRA_MESSENGER);

        if (mOutgoingMessenger == null) {
            if (DBG) Log.e(TAG, "Reply Handler missing");
        }

        return mIncomingMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy()");

        for (ArrayMap.Entry<String, BTDevice> entry : mConnectedDevices.entrySet()) {
            entry.getValue().destroy();
        }

        mConnectedDevices.clear();
        mBTBroadcastReceiver.close();

        super.onDestroy();
    }

    // Messaging to Activity -----------------------------------------------------------------------

    private void sendReply(int reply, Bundle data) {
        if (DBG) Log.d(TAG, "sendReply(" + reply + ")");

        Message msg = Message.obtain(null, reply);
        msg.setData(data);

        if (mOutgoingMessenger == null) {
            if (DBG) Log.e(TAG, "Reply Handler missing");
        } else {
            try {
                mOutgoingMessenger.send(msg);
            } catch (RemoteException e) {
                if (DBG) Log.e(TAG, "Reply Handler no longer exists");
            }
        }
    }

    private class IncomingHandler extends Handler {
        // Debug
        private static final String TAG = "BTServiceIncomingHandler";
        private static final boolean DBG = true;

        public void handleMessage(Message msg) {
            if (DBG) Log.d(TAG, "handleMessage(" + msg + ")");

            Bundle replydata = new Bundle();
            Bundle data = msg.getData();

            switch (msg.what) {
                case Command.ENABLE:
                    if (mBTAvailable && !mBTEnabled) {
                        if (DBG) Log.d(TAG, "Enable Bluetooth");
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(enableIntent);
                        }
                    break;

                case Command.DISABLE:
                    if (mBTAvailable && mBTEnabled) {
                        if (DBG) Log.d(TAG, "Disable Bluetooth");
                        mBluetoothAdapter.disable();
                    }
                    break;

                case Command.GET_STATE:
                    if (mBTAvailable && mBTEnabled) {
                        sendReply(Reply.ENABLED, null);
                    } else {
                        sendReply(Reply.DISABLED, null);
                    }
                    break;

                case Command.CONNECT:
                    String connectTo = data.getString(EXTRA_ADDRESS);

                    // Device address not already connected
                    if (!mConnectedDevices.containsKey(connectTo)) {
                        if (DBG) Log.d(TAG, "Connect to " + connectTo);
                        BTDevice device = new BTDevice(mContext, mDeviceHandler, connectTo);
                        mConnectedDevices.put(connectTo, device);
                    }
                    // Remove device from list when it answers if connection was not successful
                    break;

                case Command.DISCONNECT:
                    String disconnectFrom = data.getString(EXTRA_ADDRESS);

                    // Device address is connected
                    if (mConnectedDevices.containsKey(disconnectFrom)) {
                        if (DBG) Log.d(TAG, "Disconnect from " + disconnectFrom);
                        mConnectedDevices.get(disconnectFrom).disconnect();
                    }
                    // Remove device from list when it answers if disconnection was successful
                    break;

                case Command.GET_DEVICES:
                    if (DBG) Log.d(TAG, "Get connected devices");

                    String[] devices = getDevices();

                    replydata.putStringArray(EXTRA_ADDRESSES, devices);
                    sendReply(Reply.DEVICES, replydata);

                    break;

                case Command.SEND:
                    String sendTo = data.getString(EXTRA_ADDRESS);
                    String text = data.getString(EXTRA_DATA);

                    if (DBG) Log.d(TAG, "Send '" + text + "' to " + sendTo);

                    mConnectedDevices.get(sendTo).send(text.getBytes());

                default:
                    super.handleMessage(msg);
            }
        }
    }

    // Bluetooth Handling --------------------------------------------------------------------------

    private final class BTBroadcastReceiver extends BroadcastReceiver {
        private static final String TAG = "BTBroadcastReceiver";
        private static final boolean DBG = true;

        private Context mmContext;

        private boolean mmRegistered = false;

        BTBroadcastReceiver(Context context)
        {
            if (!mmRegistered) {
                if (DBG) Log.d(TAG, "BTBroadcastReceiver()");

                mmContext = context;

                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
                intentFilter.addAction(BluetoothDevice.ACTION_UUID);
                intentFilter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
                mmContext.registerReceiver(this, intentFilter);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "onReceive(" + intent + ")");
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                processStateChange(intent);
            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                processBondStateChange(intent);
            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                processPairingRequest(intent);
            } else if (action.equals(BluetoothDevice.ACTION_NAME_CHANGED)) {
                processNameChanged(intent);
            } else if (action.equals(BluetoothDevice.ACTION_UUID)) {
                processUUID(intent);
            }
        }

        void close() {
            if (DBG) Log.d(TAG, "close()");
            try {
                mmContext.unregisterReceiver(this);
            } catch (Exception e) {
            } finally {
                mmRegistered = false;
            }
        }

        private void processStateChange(Intent intent) {
            int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
            int currentState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

            if (DBG) Log.d(TAG, "processStateChange(" + previousState + " -> " + currentState + ")");

            switch (currentState) {
                case BluetoothAdapter.STATE_ON:
                    mBTEnabled = true;
                    sendReply(Reply.ENABLED, null);
                    break;
                case BluetoothAdapter.STATE_OFF:
                    mBTEnabled = false;
                    sendReply(Reply.DISABLED, null);
                    break;
            }

        }

        private void processBondStateChange(Intent intent) {

        }

        private void processPairingRequest(Intent intent) {

        }

        private void processNameChanged(Intent intent) {

        }

        private void processUUID(Intent intent) {
            if (DBG) Log.d(TAG, "processUUID()");

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

            for (Parcelable uuid : uuids) {
                if (DBG) Log.d(TAG, uuid.toString());
            }
        }
    }

    private final Handler mDeviceHandler = new Handler() {
        private static final String TAG = "BTDeviceHandler";

        @Override
        public void handleMessage(Message msg) {
            if (DBG) Log.d(TAG, "handleMessage(" + msg + ")");

            String address = msg.getData().getString(BTDevice.EXTRA_ADDRESS);
            Bundle data = new Bundle();

            switch (msg.what) {
                case BTDevice.Notification.CONNECTED:
                    data.putString(EXTRA_ADDRESS, address);
                    sendReply(Reply.CONNECTED, data);
                    break;

                case BTDevice.Notification.CONNECTION_FAILED:
                    if (mConnectedDevices.containsKey(address)) {
                        mConnectedDevices.remove(address);
                    }
                    break;

                case BTDevice.Notification.CONNECTION_LOST:
                    if (mConnectedDevices.containsKey(address)) {
                        mConnectedDevices.remove(address);
                    }
                   break;

                case BTDevice.Notification.DISCONNECTED:
                    if (mConnectedDevices.containsKey(address)) {
                        mConnectedDevices.remove(address);
                    }

                    data.putString(EXTRA_ADDRESS, address);
                    sendReply(Reply.DISCONNECTED, data);
                    break;

                case BTDevice.Notification.DATA_RECEIVED:
                    byte[] received = msg.getData().getByteArray(BTDevice.EXTRA_DATA);

                    data.putString(EXTRA_ADDRESS, address);
                    data.putString(EXTRA_DATA, new String(received));

                    sendReply(Reply.RECEIVED, data);
                    break;

                case BTDevice.Notification.DATA_SENT:
                    break;
            }
        }
    };

    // Helper Functions ----------------------------------------------------------------------------

    private String[] getDevices() {
        List<String> devices = new ArrayList<String>();

        for (ArrayMap.Entry<String, BTDevice> entry : mConnectedDevices.entrySet()) {
            devices.add(entry.getKey());
        }

        return devices.toArray(new String[devices.size()]);
    }
}

