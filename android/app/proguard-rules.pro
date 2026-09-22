# kotlinx.serialization keeps its generated serializers via @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.notchhud.island.**$$serializer { *; }
-keepclassmembers class com.notchhud.island.** { *** Companion; }
-keepclasseswithmembers class com.notchhud.island.** { kotlinx.serialization.KSerializer serializer(...); }

# Services referenced only from the manifest.
-keep class com.notchhud.island.service.** { *; }

# OkHttp / Okio ship their own consumer rules; these silence the known warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
