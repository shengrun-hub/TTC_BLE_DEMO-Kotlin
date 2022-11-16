# SDK说明

- 系统要求：不低于Android5.0；  
- 工程需要使用AndroidX，如果之前使用的1.1.7以下的版本，可以参照[AndroidXMigrate](https://github.com/shengrun-hub/AndroidXMigrate)将工程迁移到AndroidX。

[SDK使用文档](https://github.com/shengrun-hub/TTC_BLE_DEMO-Kotlin/blob/master/ttc_android_ble_doc.md)

# 更新记录

## V1.2.0  
- 修复Android12蓝牙未打开时请求扫描闪退的问题；  
- 删除对permissionsdispatcher的依赖，完全脱离jcenter。  

## V1.1.9  
- 适配Android12；  
- 由jcenter转移到Maven Central。  
  
## V1.1.8  
- BleCallBack增加方法：onPhyUpdate、onPhyRead；  
- BleService增加方法：readPhy、setPreferredPhy；  
- PhyOADProxy增加方法：setOTACmd（设置从普通模式切换到OAD模式的指令）。  
  
## V1.1.7  
- 修复Android5.0以下系统使用LeScanner扫描设备闪退的问题；  
- 修复在子线程调用LeScanner的扫描方法闪退的问题；  
- 支持TI大包OAD升级；  
- LeScanner增加方法：hasLocationPermission()；  
- 修复连接时定时器异常导致的闪退问题（同步了DeviceRespresent的connect()跟cancelConnectTimer()方法）。  
