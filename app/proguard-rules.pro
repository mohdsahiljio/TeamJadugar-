-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-flattenpackagehierarchy ''
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.teamjadugar.apk.MainActivity { *; }
-keep class com.teamjadugar.apk.FloatingService { *; }
-keep class com.teamjadugar.apk.VpnService { *; }
-keep class com.teamjadugar.apk.KeyVault { *; }
