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
            mBleService.setConnectTimeout(5000);//设置APP端的连接超时时间（单位ms）
            mBleService.initialize();// 必须调用初始化函数
        }
    };
```
# 扫描蓝牙设备
```LeScanner.startScan(mOnLeScanListener);```  
扫描蓝牙设备需要APP获得相关权限，Android12以下系统需要位置权限并且打开位置服务，Android12的权限配置可参考官方文档[Android 12 中的新蓝牙权限](https://developer.android.google.cn/about/versions/12/features/bluetooth-permissions)

# 连接设备
```mBleService.connect(mac, false);```
  
- 参数mac是设备的MAC地址  
- 参数autoConnect用来设定异常断开后是否自动重连，false即断线后不自动重连

# 断开连接
```mBleService.disconnect(mac);```  
- 参数mac是设备的MAC地址  
