# 版本说明

- 系统要求：不低于Android5.0，如果要兼容Android4.3到5.0，可使用旧版本，下载[AndroidBLE_API_V1.1.6](https://pan.baidu.com/s/10NHbFRBLmjt7Sg3lpqA3dA)，提取码: rssb；  
- 1.1.7开始，工程需要使用AndroidX，如果之前使用的1.1.7以下的版本，可以参照[AndroidXMigrate](https://github.com/shengrun-hub/AndroidXMigrate)将工程迁移到AndroidX。

[SDK使用说明](https://github.com/shengrun-hub/TTC_BLE_DEMO-Kotlin/blob/master/ttc_android_ble_doc.md)

# 更新记录
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
