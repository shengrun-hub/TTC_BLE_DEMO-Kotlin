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
