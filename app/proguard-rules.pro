-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.orion.store.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep class rikka.shizuku.** { *; }
# Wireless ADB: libadb looks up Conscrypt by reflection, spake2 is JNI
-keep class org.conscrypt.** { *; }
-keep class io.github.muntashirakon.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn com.unity3d.**
