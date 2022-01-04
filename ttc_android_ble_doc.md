# API参考
本页面是TTC Android BLE SDK的使用说明，API参考可下载离线HTML文档进行查看：[点击此处下载](链接: https://pan.baidu.com/s/1dCYpf_4mXmp8ttq26AchjA)，提取码 a93s。

# 初始化蓝牙服务
启动APP时绑定 BleService，然后在绑定成功后初始化 BleService  
```
  private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BleService.LocalBinder) binder).getService(mBleCallBack);
            // mBleService.setMaxConnectedNumber(4);// 设置最大可连接从机数量，默认为4
            // mBleService.setConnectTimeout(3000);//设置APP端的连接超时时间（单位ms），默认3秒
            mBleService.setDecode(false);//不解密默认UUID（0x1002）接收的数据，早期模块默认使用了加密
            mBleService.initialize();// 必须调用初始化函数
        }
    };
```
# 扫描蓝牙设备
## 启动扫描
```LeScanner.startScan(mOnLeScanListener);```  
- 部分手机长时间扫描会扫不到设备，startScan()方法默认最多持续5秒，即如果不调用```LeScanner.stopScan()```，5秒后自动停止扫描；
- 扫描蓝牙设备需要APP获得相关权限，Android12以下系统需要位置权限并且打开位置服务，Android12的权限配置可参考官方文档[Android 12 中的新蓝牙权限](https://developer.android.google.cn/about/versions/12/features/bluetooth-permissions)。

## 广播数据解析
蓝牙扫描回调：
```
    private final OnLeScanListener mOnLeScanListener = new OnLeScanListener() {
        @Override
        public void onScanStart() {
        }

        @Override
        public void onLeScan(LeScanResult leScanResult) {
            //发现蓝牙设备
        }

        @Override
        public void onScanFailed(int errorCode) {
        }

        @Override
        public void onScanStop() {
        }
    };
```
通过 onLeScan 返回的扫描结果获取广播数据：  
（1）获取设备名称、LocalName、MAC：
```
leScanResult.getDevice().getName();//设备名称
leScanResult.getDevice().getAddress();//设备MAC
leScanResult.getLeScanRecord().getLocalName();
```
（2）获取厂商数据：
```
leScanResult.getLeScanRecord().getFirstManufacturerSpecificData();
```

## 停止扫描
```LeScanner.stopScan();```

# 连接设备
```mBleService.connect(mac, false);```
  
- 参数mac是设备的MAC地址  
- 参数autoConnect用来设定异常断开后是否自动重连，false即断线后不自动重连

# 断开连接
```mBleService.disconnect(mac);```  
- 参数mac是设备的MAC地址  

# 蓝牙交互
## 蓝牙事件相关的回调
后面提到的回调方法都是 BleCallBack 的方法
```
    private final BleCallBack mBleCallBack = new BleCallBack() {

        @Override
        public void onConnectTimeout(String address) {
            //连接设备时，未设置自动重连，会触发连接超时的回调
        }

        @Override
        public void onConnectionError(String address, int error, int newState) {
            //error的定义见 com.ble.gatt.Status
        }

        @Override
        public void onDisconnected(String address) {
            //连接已断开
        }

        @Override
        public void onServicesDiscovered(String address) {
            //!!!检索服务成功，到这一步才可以与从机进行数据交互，有些手机可能需要延时几百毫秒才能交互数据
        }
        
        @Override
        public void onCharacteristicChanged(String address, BluetoothGattCharacteristic characteristic) {
            //接收到模块数据
            byte[] data = characteristic.getValue();
            Log.i(TAG, "onCharacteristicChanged() - " + address + " uuid=" + characteristic.getUuid().toString()
                    + "\n len=" + (data == null ? 0 : data.length)
                    + " [" + DataUtil.byteArrayToHex(data) + ']');
        }

        @Override
        public void onCharacteristicRead(String address, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //读取到模块数据
                Log.i(TAG, "onCharacteristicRead() - " + address + " uuid=" + characteristic.getUuid().toString()
                        + "\n len=" + characteristic.getValue().length
                        + " [" + DataUtil.byteArrayToHex(characteristic.getValue()) + ']');
            }
        }

        @Override
        public void onCharacteristicWrite(String address, BluetoothGattCharacteristic characteristic, int status) {
            //调试时可以在这里打印status来看数据有没有发送成功
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String uuid = characteristic.getUuid().toString();
                //如果发送数据加密，可以先把characteristic.getValue()获取的数据解密一下再打印
                //byte[] decodedData = new EncodeUtil().decodeMessage(characteristic.getValue());
                Log.i(TAG, "onCharacteristicWrite() - " + address + ", " + uuid
                        + "\n len=" + characteristic.getValue().length
                        + " [" + DataUtil.byteArrayToHex(characteristic.getValue()) + ']');
            }
        }

        @Override
        public void onDescriptorWrite(String address, BluetoothGattDescriptor descriptor, int status) {
            String charUuid = descriptor.getCharacteristic().getUuid().toString().substring(4, 8);
            String descUuid = descriptor.getUuid().toString().substring(4, 8);
            Log.e(TAG, "onDescriptorWrite() - 0x" + charUuid + "/0x" + descUuid + " -> " + DataUtil.byteArrayToHex(descriptor.getValue())
                    + ", status=" + status);
        }

        @Override
        public void onMtuChanged(String address, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //MTU更新成功
            }
        }
    };
```
## 发送数据
```mBleService.send(mac, data, false);```
- 参数mac是设备的MAC地址
- 参数data是byte[]类型，即要发送的二进制数据，也可以传16进制字符串，即使用另一个同名方法
- 参数encode是设定是否加密数据，该加密是指我司早期模块使用的一种加密方式，一般不用加密数据，传false就行

## 接收数据
接收数据需要在连接设备后打开数据通知，即常说的打开notify，订阅通知
```mBleService.enableNotification(mac);```
该方法调用成功（即返回true），会触发 BleCallBack 的回调：onDescriptorWrite(String address, BluetoothGattDescriptor descriptor, int status)，  
返回的status为0即表示通知已成功打开，之后设备端发送的数据会通过 onCharacteristicChanged(String address, BluetoothGattCharacteristic characteristic) 回调给到APP 

## 如果模块改过UUID，没有使用我司模块的默认UUID，收发数据可通过下面的代码实现
假如使用如下的UUID（如果收发数据的UUID分别在不同的服务下面，需要定义两个服务UUID）：
- 服务UUID：0000fff0-0000-1000-8000-00805f9b34fb  
- 发送数据的UUID：0000fff1-0000-1000-8000-00805f9b34fb  
- 接收数据的UUID：0000fff2-0000-1000-8000-00805f9b34fb  
```
public static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
public static final UUID WRITE_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
public static final UUID NOTIFY_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
```
### 发送数据
```
BluetoothGatt gatt = mBleService.getBluetoothGatt(mac);
BluetoothGattCharacteristic characteristic = GattUtil.getGattCharacteristic(gatt, SERVICE_UUID, WRITE_UUID);
mBleService.send(gatt, characteristic, data, false);
```
### 接收数据
打开数据通知：
```
BluetoothGatt gatt = mBleService.getBluetoothGatt(mac);
BluetoothGattCharacteristic characteristic = GattUtil.getGattCharacteristic(gatt, SERVICE_UUID, NOTIFY_UUID);
mBleService.setCharacteristicNotification(gatt, characteristic, true);//true即表示打开通知，false是关闭通知
```
设备端发送的数据也是通过 onCharacteristicChanged(String address, BluetoothGattCharacteristic characteristic) 回调给到APP

## 更新MTU
默认单次传输的数据最多为20字节，如果需要传输多于20字节的数据，可以通过更新MTU来增加单次传输的数据长度：
```
mBleService.requestMtu(mac, 251);
```
- 参数mac是设备的MAC地址
- 参数mtu是最大传输的字节数加上3，251即每次最多传输248字节的数据  

该方法会触发 onMtuChanged(String address, int mtu, int status) 回调，返回的status为0，即MTU更新成功，返回的mtu即最终使用的MTU（不一定跟请求时传的MTU值一样哦）
MTU是建立连接的两端协商的，两端支持的最大MTU不一样，将使用其中较小的那个值。
