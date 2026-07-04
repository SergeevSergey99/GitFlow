# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide original source file name
-renamesourcefileattribute SourceFile

# ==================== Strip Log calls in release ====================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ==================== Timber ====================
-dontwarn org.jetbrains.annotations.**

# ==================== JGit ====================
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn com.jcraft.jsch.**

# ==================== Retrofit ====================
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ==================== Gson ====================
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== Data models ====================
-keep class com.gitflow.android.data.models.** { *; }

# All auth-layer Gson DTOs (GitHub/GitLab/Bitbucket/Gitea/Azure OAuth responses,
# users, repositories, emails, paged responses). R8 must not rename their fields —
# Gson maps JSON keys to field names by reflection. Without this, minified release
# builds silently return empty objects for Bitbucket/Gitea/Azure providers.
-keep class com.gitflow.android.data.auth.** { *; }

# ==================== Kotlin Serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gitflow.android.**$$serializer { *; }
-keepclassmembers class com.gitflow.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.gitflow.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== Koin ====================
-keep class org.koin.** { *; }
-keep class com.gitflow.android.di.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ==================== AndroidX Security Crypto ====================
-keep class androidx.security.crypto.** { *; }
