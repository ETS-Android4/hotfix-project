###Fixed official low version gradle compilation problems and Java9 compilation errors. Two plug-ins are packaged for the problematic version. You only need to adjust the original

```
classpath'com.tokopedia.stability:gradle-plugin:0.4.99'
classpath'com.tokopedia.stability:auto-patch-plugin:0.4.99'
```

Change to

```
classpath'com.effective.android:gradle-plugin-fix:0.4.99.1'
classpath'com.effective.android:auto-patch-plugin-fix:0.4.99.1'
```

Add repositories

```
    repositories {
        //...
        maven {url'https://dl.bintray.com/yummylau/maven'}
    }
```

All other usages remain unchanged.

[Official document](https://github.com/Meituan-Dianping/Robust/blob/master/README-zh.md)

