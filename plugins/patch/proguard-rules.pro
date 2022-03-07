-keep class com.tokopedia.stability.**{*;}
-keep class com.tokopedia.stability.**{*;}
-keep class com.google.gson.**{*;}
-keepattributes *Annotation*
-keepclassmembers class **{
public static com.tokopedia.stability.ChangeDelegate *;
}

