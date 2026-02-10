# ProGuard configuration for Zeelo Location SDK and other dependencies

# Zeelo SDK ProGuard Rules
-dontwarn com.cherrypicks.zeelosdk.lite.location.**
-keep class com.cherrypicks.zeelosdk.lite.location.** {*;}

-dontwarn com.cherrypicks.starbeacon.locationsdk.**
-keep class com.cherrypicks.starbeacon.locationsdk.** {*;}

# StarBeacon Library
-dontwarn org.altbeacon.**
-keep class org.altbeacon.** {*;}
-keep interface org.altbeacon.** {*;}

# Proj4J Library
-dontwarn org.osgeo.proj4j.**
-keep class org.osgeo.proj4j.** {*;}
-keep interface org.osgeo.proj4j.** {*;}

# IndoorAtlas Library
-dontwarn com.indooratlas.**
-keep class com.indooratlas.** {*;}
-keep interface com.indooratlas.** {*;}

# EventBus
-dontwarn org.greenrobot.eventbus.**
-keep class org.greenrobot.eventbus.** {*;}

# MQTT Paho
-dontwarn org.eclipse.paho.**
-keep class org.eclipse.paho.** {*;}

# GSON
-dontwarn com.google.gson.**
-keep class com.google.gson.** {*;}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** {*;}
-dontwarn okio.**
-keep class okio.** {*;}

# Keep all model classes and interfaces
-keep class com.example.temiphone.** {*;}
-keep interface com.example.temiphone.** {*;}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
