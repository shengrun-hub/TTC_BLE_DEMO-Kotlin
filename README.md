# TTC_BLE_DEMO-Kotlin

系统要求：不低于Android5.0。

# Android Studio添加依赖：

1. 工程根目录下的build.gradle中添加

```
allprojects {
    repositories {
        ...
        maven { url 'https://dl.bintray.com/android-ttcble/maven' }
    }
}
```

2. 在引用的module的build.gradle中添加

```
dependencies {
    ...
    implementation 'com.ttcble.android:blebase:1.1.8'
}
```
API文档可[下载SDK](https://pan.baidu.com/s/10NHbFRBLmjt7Sg3lpqA3dA)查看，提取码: rssb

# 更新记录
--- V1.1.8 ---
1、BleCallBack增加方法：onPhyUpdate、onPhyRead；
2、BleService增加方法：readPhy、setPreferredPhy；
3、PhyOADProxy增加方法：setOTACmd（设置从普通模式切换到OAD模式的指令）。

--- V1.1.7 ---
1、修复Android5.0以下系统使用LeScanner扫描设备闪退的问题；
2、修复在子线程调用LeScanner的扫描方法闪退的问题；
3、支持TI大包OAD升级；
4、LeScanner增加方法：hasLocationPermission()；
5、修复连接时定时器异常导致的闪退问题（同步了DeviceRespresent的connect()跟cancelConnectTimer()方法）。
