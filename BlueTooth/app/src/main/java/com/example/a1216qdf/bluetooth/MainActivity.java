package com.example.a1216qdf.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final static int REQUEST_ENABLE_BT = 1;

    static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    static final UUID uuid = UUID.fromString(SPP_UUID);
    static final String TAG = "BTSPP";


    private boolean sppConnected = false;
    private OutputStream BTOutputStream;
    private BluetoothAdapter mBluetoothAdapter;
    private String devAddress = null;
    private String devNameAddress = null;
    private SppConnect sppConnect;
    private Timer timer;
    private IntentFilter intentfilter;
    private ArrayList<Map<String, String>> devices = new ArrayList<Map<String, String>>();
    private SimpleAdapter adapter1;
    private Spinner spinner1;
    public final static int REQUEST_CODE_GPS_PERMISSIONS = 0;
    private Button btnScan,btnConnect,btnDisconnect;
    private TextView tvState;
    private byte[] buffer;
    String decodes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        btnScan = (Button)findViewById(R.id.Scanbtn);
        btnConnect = (Button)findViewById(R.id.Connectbtn);
        btnDisconnect = (Button)findViewById(R.id.DisConnectbtn);
        spinner1 = (Spinner)findViewById(R.id.ScanDeviceSpinner);
        tvState = (TextView)findViewById(R.id.tvState);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//初始化藍芽
        adapter1 = new SimpleAdapter(MainActivity.this, devices, R.layout.spinner_list_item_2,
                new String[]{"BTName", "BTMac"},
                new int[]{android.R.id.text1,
                        android.R.id.text2});

        spinner1.setAdapter(adapter1);


        //此手機不支援藍芽時，顯示訊息出來
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available !", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        //沒有開啟藍芽時，跳到開啟視窗
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        intentfilter = new IntentFilter();
        intentfilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentfilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentfilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intentfilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);


        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                devices.clear();
                adapter1.notifyDataSetChanged(); // 通知adapter1 devices有更新
                registerReceiver(mReceiver, intentfilter);
                mBluetoothAdapter.cancelDiscovery();

                //6.0需要定位權限
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_CODE_GPS_PERMISSIONS);
                    return;
                }
                mBluetoothAdapter.startDiscovery();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (sppConnect != null) {
                    sppConnect.cancel();
                    sppConnect = null;
                }
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                sppConnect = new SppConnect();
                sppConnect.start();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                unregisterReceiver(mReceiver);
                devices.clear();
                adapter1.notifyDataSetChanged(); // 通知adapter1 devices有更新
                SppConnecthandler.sendEmptyMessage(2);

                if (sppConnect != null) {
                    sppConnect.cancel();
                    sppConnect = null;
                }
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                sppConnected =false;
            }
        });

        spinner1.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view, int position, long id) {
                Log.d(TAG, "--- 已找到裝置 ");
                devNameAddress = devices.get(position).get("BTName");
                devAddress = devices.get(position).get("BTMac");
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle bundle = intent.getExtras();
            Object[] listName = bundle.keySet().toArray();

            // 顯示所有收到的資訊及細節
            for (int i = 0; i < listName.length; i++) {
                String keyName = listName[i].toString();
                Log.e(TAG,
                        "+ BroadcastReceiver KeyNAme : "
                                + String.valueOf(bundle.get(keyName)));
            }
            BluetoothDevice device = null;
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.d(TAG, "發現囉...");
                device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Map<String, String> datum = new HashMap<String, String>(2);
                datum.put("BTName", device.getName());
                datum.put("BTMac", device.getAddress());
                devices.add(datum);
                adapter1.notifyDataSetChanged(); // 通知adapter1 devices有更新
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "正在配對...");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "完成配對");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "取消配對");
                        break;
                    default:
                        break;
                }
            }

        }
    };

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();

        this.unregisterReceiver(mReceiver);

    }

    public void connected(BluetoothSocket BTSocketIn) throws IOException {
        // TODO Auto-generated method stub
        sppConnected = true;
        Log.e(TAG, "++ connected() : BTSocketIn = " + BTSocketIn);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        BTOutputStream = BTSocketIn.getOutputStream();
        timer = new Timer(true);
        timer.schedule(new SppReceiver(BTSocketIn), 1000, 500);
        Log.e(TAG, "++ connected() : sppReceiver.start();");
        Log.e(TAG, "++ connected() 成功，連線中");
        SppConnecthandler.sendEmptyMessage(1);
    }


    private class SppConnect extends Thread {

        private final BluetoothSocket mBluetoothSocket;

        public SppConnect() {
            BluetoothSocket tmpBluetoothSocket = null;

            try {
                tmpBluetoothSocket = mBluetoothAdapter.getRemoteDevice(
                        devAddress).createRfcommSocketToServiceRecord(uuid);
                Log.d(TAG, "SppConnect(): createRfcommSocketToServiceRecord ");
            } catch (Exception e) {
                // TODO: handle exception
            }
            mBluetoothSocket = tmpBluetoothSocket;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "SppConnect(): mBluetoothAdapter.cancelDiscovery(); ");

            try {
                mBluetoothSocket.connect();
                Log.d(TAG, "SppConnect():mBluetoothSocket.connect(); ");
                synchronized (MainActivity.this) {
                    if (sppConnected) {
                        return;
                    }
                    connected(mBluetoothSocket);
                }
            } catch (IOException e) {
                // TODO: handle exception
                Log.d(TAG,
                        "--- SppConnect():mBluetoothSocket.connect(); Failed!!! ");
                try {
                    mBluetoothSocket.close();
                    Log.d(TAG, "--- SppConnect():mBluetoothSocket.close() ");
                } catch (IOException e2) {
                    // TODO: handle exception
                    Log.d(TAG, "mBluetoothSocket.close() failed!");
                }
                connectionFailed(0);
            }
        }

        public void cancel() {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                // TODO: handle exception
            }
        }
    }

    private class SppReceiver extends TimerTask {

        private final InputStream input;
        private final BluetoothSocket mBluetoothSocket;

        public SppReceiver(BluetoothSocket socketIn) {

            mBluetoothSocket = socketIn;
            InputStream tmpIn = null;
            try {
                tmpIn = socketIn.getInputStream();

            } catch (IOException e) {
                // TODO: handle exception
                Log.i(TAG, "SppReceiver : tmpIn is empty");
            }
            input = tmpIn;
        }

        @Override
        public void run() {

            if (input == null) {
                Log.d(TAG, "-- SppReceiver : InputStream NULL");
                return;
            }
            if (true) {

                buffer = new byte[512];
                int read;
                try {
                    read = input.read(buffer);
//                    Log.i(TAG, "READDDD!!!!!!!!BYTES:" + String.valueOf(read));
                    if (read != -1) {
                        byte[] tempdata = new byte[read];
                        System.arraycopy(buffer, 0, tempdata, 0, read);
                        //tempdata從這裡接
                        Log.i(TAG, "測試:" + tempdata);

                    }

                } catch (IOException e) {
                    // TODO: handle exception
                    e.printStackTrace();
                    Log.e(TAG, "SppReceiver :　read Data FAILED , SppReveiver disconnect!");
                    connectionFailed(0);
                }
            }
        }
    }

    Handler SppConnecthandler = new Handler() {
        public void handleMessage(Message message) {
            switch (message.what) {
                case 0:
                    tvState.setText("連線失敗");
                    break;
                case 1:
                    tvState.setText("已連線");
                    break;
                case 2:
                    tvState.setText("未連線");
                    break;
            }
        }

    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_GPS_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    mBluetoothAdapter.startDiscovery();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "LOCATION Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void connectionFailed(int type) {
        // TODO Auto-generated method stub

            Log.d("TAG", "++ connectionFailed()");
            SppConnecthandler.sendEmptyMessage(type);
            sppConnected = false;
            if (sppConnect != null) {
                sppConnect.cancel();
                sppConnect = null;
            }
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
    }

}
