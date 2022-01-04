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
```LeScanner.startScan();```
