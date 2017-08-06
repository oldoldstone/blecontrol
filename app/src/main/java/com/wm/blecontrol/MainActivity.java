package com.wm.blecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private List<BluetoothDevice> devices;
    private DeviceAdapter deviceAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothDevice selectedDevice;
    private ScanCallback leCallback;
    private BluetoothGattCharacteristic characteristic;

    private ListView btList;
    private ProgressBar progressBar;
    private FrameLayout connectLayout,statusLayout;
    private TextView sendText,batteryVoltage,carAngle;
    private ControlView controlView;


    private Handler delayedHandler;     //用于延时操作
    private boolean isScanning;
    private boolean isConnected = false; //用于标记是否连接成功
    private StringBuffer cmdBuffer;

    private static final UUID SERVICE_UUID = UUID.
            fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.
            fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectLayout = (FrameLayout) findViewById(R.id.connect_layout);
        statusLayout = (FrameLayout) findViewById(R.id.status_layout);
        controlView = (ControlView)findViewById(R.id.id_control);
        btList = (ListView) findViewById(R.id.ble_list);
        progressBar = (ProgressBar) findViewById(R.id.progressbar);
        sendText = (TextView)findViewById(R.id.id_send_text);
        batteryVoltage = (TextView)findViewById(R.id.id_battery_voltage);
        carAngle = (TextView)findViewById(R.id.id_angle);
        statusLayout.setVisibility(View.GONE);
        cmdBuffer = new StringBuffer("");

        devices = new ArrayList<BluetoothDevice>();
        delayedHandler = new Handler();
        deviceAdapter = new DeviceAdapter(this, R.layout.list_device, devices);
        btList.setAdapter(deviceAdapter);
        btList.setOnItemClickListener(this);
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "蓝牙不可用", Toast.LENGTH_SHORT).show();
        }
        /*打开蓝牙*/
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
        leCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (!devices.contains(device)) {
                    devices.add(device);
                    deviceAdapter.notifyDataSetChanged();
                    Log.d("SCAN", "new device");
                }

            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.d("ERROR", "搜索失败");
            }
        };

        scanBluetooth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        updateMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        /**
         * 在onCreateOptionsMenu执行后，菜单被显示前调用；如果菜单已经被创建，则在菜单显示前被调用。 同样的，
         * 返回true则显示该menu,false 则不显示; （可以通过此方法动态的改变菜单的状态，比如加载不同的菜单等）
         * Auto-generated method stub
         */
        super.onPrepareOptionsMenu(menu);
        updateMenu(menu);
        return true;
    }

    private void updateMenu(Menu menu) {
        if (!isScanning) {
            menu.findItem(R.id.menu_stop).setEnabled(false);
            menu.findItem(R.id.menu_scan).setEnabled(true);
        } else {
            menu.findItem(R.id.menu_stop).setEnabled(true);
            menu.findItem(R.id.menu_scan).setEnabled(false);
        }
        if (isConnected) {
            menu.findItem(R.id.menu_disconnect).setEnabled(true);
            menu.findItem(R.id.menu_stop).setEnabled(false);
            menu.findItem(R.id.menu_scan).setEnabled(false);
        } else {
            menu.findItem(R.id.menu_disconnect).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                deviceAdapter.clear();
                scanBluetooth();
                break;
            case R.id.menu_stop:
                if (isScanning) {
                    scanner.stopScan(leCallback);
                    isScanning = false;
                    progressBar.setVisibility(View.INVISIBLE);
                }
                break;
            case R.id.menu_disconnect:
                if (isConnected) {
                    bluetoothGatt.disconnect();
                    bleDisconnectUIProcess();
                }
                break;
        }
        return true;
    }

    private boolean bleDisconnectUIProcess(){
        isConnected = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle("未连接");
                connectLayout.setVisibility(View.VISIBLE);
                statusLayout.setVisibility(View.GONE);
                cmdBuffer.setLength(0);
                sendText.setText(cmdBuffer);
                scanBluetooth();
            }
        });

        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (isScanning) {
            scanner.stopScan(leCallback);
            progressBar.setVisibility(View.INVISIBLE);
            isScanning = false;
        }
        if (connectLayout.getVisibility() == View.VISIBLE) {
            connectLayout.setVisibility(View.GONE);
            statusLayout.setVisibility(View.VISIBLE);
            setTitle("待连接");
        }
        selectedDevice = devices.get(position);
        if (selectedDevice != null) {
            if (!TextUtils.isEmpty(selectedDevice.getName())) {
                connect(selectedDevice);
                Toast.makeText(MainActivity.this, "选择了" + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "选择了 Unknown Device", Toast.LENGTH_SHORT).show();
            }
            controlView.setListener(new ControlListener() {
                public void sendMessage(String string) {
                    sendData(string);
                    //cmdBuffer.setLength(20);
                    if (cmdBuffer.length()>20){
                        cmdBuffer.deleteCharAt(0);
                    }
                    cmdBuffer.append(string);
                    Log.d("监听器",string);
                    StringBuffer cmd_out = new StringBuffer(cmdBuffer);
                    cmd_out.reverse();
                    sendText.setText(cmd_out);
                }
            });
        }


    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
        }
    }

    /*对蓝牙设备进行扫描*/
    private void scanBluetooth() {
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        delayedHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bluetoothAdapter.isEnabled() && isScanning) {
                    isScanning = false;
                    scanner.stopScan(leCallback);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isScanning = false;
                            Toast.makeText(MainActivity.this, "搜索完成", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }
        }, 15000);
        isScanning = true;
        scanner.startScan(leCallback);
        progressBar.setVisibility(View.VISIBLE);
    }

    /*连接到指定的蓝牙设备*/
    private void connect(final BluetoothDevice device) {
        if (device == null) {
            Toast.makeText(MainActivity.this, "请选择一个设备", Toast.LENGTH_SHORT).show();
            return;
        }
        setTitle("正在请求连接...");


        bluetoothGatt = device.connectGatt(MainActivity.this, false, new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isConnected) {
                                if (!TextUtils.isEmpty(device.getName())) {
                                    setTitle("已连接上" + device.getName());
                                } else {
                                    setTitle("已经连接上Unknown Device");
                                }
                                try {
                                    Thread.currentThread().sleep(2000); //延时等待蓝牙连接完成
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                sendData("hi");
                            }
                        }
                    });
                    gatt.discoverServices();
                }else if (newState == BluetoothProfile.STATE_DISCONNECTED)
                {
                    bleDisconnectUIProcess();
                    Log.d("BLE","BluetoothDisconnected");
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                System.out.println(gatt.getDevice().getName() + " write " +
                        " -> " + new String(characteristic.getValue()));
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                String value = new String(characteristic.getValue());
                System.out.println(gatt.getDevice().getName() + " read " + " -> " + value);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    String msg = new String(data);
                    updateStaus(msg);

                }
            }

        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && bluetoothGatt != null) {
                bluetoothGatt.disconnect();
        }
    }
    //向蓝牙串口发送命令
    public boolean sendData(String msg){
        BluetoothGattService service=bluetoothGatt.getService(SERVICE_UUID) ;
        if (service!=null) {
            characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            bluetoothGatt.setCharacteristicNotification(characteristic, true);  //设置characteristic的通知，触发bluetoothGatt.onCharacteristicWrite()事件。
            characteristic.setValue(msg);
            if (bluetoothGatt.writeCharacteristic(characteristic)) {
                System.out.println("characteristic写入成功");
                return true;
            }
        }
        return false;
    }
    //根据接受的蓝牙数据，显示
    private boolean updateStaus(String msg){
        if(msg.length()<4){
            return false;
        }
        final String cmd = msg.substring(0,3);
        final String data = msg.substring(3);
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                switch (cmd){
                    case "BAT":
                        batteryVoltage.setText(data);
                        break;
                    case "ANG":
                        carAngle.setText(data);
                    default:
                        break;
                }
            }
        });
            return true;
    }

}
