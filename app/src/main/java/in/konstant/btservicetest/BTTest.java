package in.konstant.btservicetest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.konstant.BT.BTDeviceList;
import in.konstant.BT.BTService;

public class BTTest extends Activity {
    // Debug
    private static final String TAG = "BTTest";
    private static final boolean DBG = true;

    private ExpandableListAdapter listAdapter;
    private ExpandableListView expandableListView;
    private List<String> listDataGroup;
    private HashMap<String, List<String>> listDataChild;

    private boolean mBTEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onCreate()");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bttest);

        initListView();

        serviceStart();
    }

    @Override
    public void onStart() {
        if (DBG) Log.d(TAG, "onStart()");
        super.onStart();

        sendCommand(BTService.Command.GET_STATE, null);
    }

    @Override
    public void onResume() {
        if (DBG) Log.d(TAG, "onResume()");
        super.onResume();

        sendCommand(BTService.Command.GET_STATE, null);
    }

    @Override
    public void onStop() {
        if (DBG) Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy()");

        serviceStop();

        super.onDestroy();
    }

    // Menu ----------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bttest, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_btenable:
                if (!mBTEnabled) {
                    sendCommand(BTService.Command.ENABLE, null);
                } else {
                    sendCommand(BTService.Command.DISABLE, null);
                }
                return true;

            case R.id.menu_item_devicelist:
                BTDeviceList.show(this);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.findItem(R.id.menu_item_btenable).setChecked(mBTEnabled);
        menu.findItem(R.id.menu_item_devicelist).setVisible(mBTEnabled);

        return super.onPrepareOptionsMenu(menu);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(TAG, "onActivityResult(" + requestCode + ", " + resultCode + ")");

        switch (requestCode) {
            case BTDeviceList.REQ_DEVICE_LIST:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(BTDeviceList.EXTRA_DEVICE_ADDRESS);
                    Bundle b = new Bundle();
                    b.putString(BTService.EXTRA_ADDRESS, address);
                    sendCommand(BTService.Command.CONNECT, b);
                }
                break;
        }
    }

    // List ----------------------------------------------------------------------------------------

    private void initListView() {
        if (DBG) Log.d(TAG, "initListView()");
        expandableListView = (ExpandableListView) findViewById(R.id.listExp);

        listDataGroup = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();

        listAdapter = new in.konstant.btservicetest.ExpandableListAdapter(this, listDataGroup, listDataChild);

        expandableListView.setAdapter(listAdapter);

        expandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {

                String address = listDataGroup.get(groupPosition);

                Bundle b = new Bundle();
                b.putString(BTService.EXTRA_ADDRESS, address);
                b.putString(BTService.EXTRA_DATA, "{a} ");

                sendCommand(BTService.Command.SEND, b);

                return false;
            }
        });
    }

    private void refreshListView(String[] headers) {
        if (DBG) Log.d(TAG, "refreshListView()");
        listDataGroup.clear();

        List <String> child = new ArrayList<String>();
        child.add("1");
        child.add("2");
        child.add("3");

        for (String header : headers) {
            if (DBG) Log.d(TAG, "Connected Device " + header);
            listDataGroup.add(header);
            listDataChild.put(header, child);
        }
    }

    // Connection to Service -----------------------------------------------------------------------

    private Messenger mService = null;
    private boolean mServiceConnected = false;
    private final Messenger mIncomingMessenger = new Messenger(new ReplyHandler());

    private void serviceStart() {
        if (DBG) Log.d(TAG, "serviceStart()");

        Intent bindIntent = new Intent(this, BTService.class);
        bindIntent.putExtra(BTService.EXTRA_MESSENGER, mIncomingMessenger);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        startService(new Intent(this, BTService.class));
    }

    private void serviceStop() {
        if (DBG) Log.d(TAG, "serviceStop()");
        if (mServiceConnected) {
            unbindService(mServiceConnection);
            stopService(new Intent(this, BTService.class));
            mServiceConnected = false;
        }
    }

    class ReplyHandler extends Handler {
        // Debug
        private static final String TAG = "BTReplyHandler";
        private static final boolean DBG = true;

        @Override
        public void handleMessage(Message msg) {
            if (DBG) Log.d(TAG, "handleMessage(" + msg + ")");

            Bundle data = msg.getData();

            switch (msg.what) {
                case BTService.Reply.ENABLED:
                    mBTEnabled = true;
                    break;

                case BTService.Reply.DISABLED:
                    mBTEnabled = false;
                    break;

                case BTService.Reply.CONNECTED:
                    toast(R.string.toast_connected);
                    sendCommand(BTService.Command.GET_DEVICES, null);
                    break;

                case BTService.Reply.CONNECT_FAILED:
                    toast(R.string.toast_connect_failed);
                    break;

                case BTService.Reply.DISCONNECTED:
                    toast(R.string.toast_disconnected);
                    break;

                case BTService.Reply.DEVICES:
                    String[] devices = data.getStringArray(BTService.EXTRA_ADDRESSES);
                    refreshListView(devices);
                    break;

                case BTService.Reply.SENT:
                    break;

                case BTService.Reply.RECEIVED:
                    String received = data.getString(BTService.EXTRA_DATA);
                    if (DBG) Log.d(TAG, "Received: " + received);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected()");
            mService = new Messenger(service);
            mServiceConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected()");
            mService = null;
            mServiceConnected = false;
        }
    };

    private void toast(int ResID) {
        Toast.makeText(this, ResID, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void sendCommand(int cmd, Bundle data) {
        if (DBG) Log.d(TAG, "sendCommand(" + cmd + ")");

        Message msg = Message.obtain(null, cmd);

        if (data != null) {
            msg.setData(data);
        }

        if (mServiceConnected) {
            try {
                mService.send(msg);
            } catch (RemoteException e) {
                if (DBG) Log.d(TAG, "sendCommand(): Service no longer exists");
            }
        } else {
            if (DBG) Log.d(TAG, "sendCommand(): Not connected to service");
        }
    }

}
