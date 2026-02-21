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
-keep class com.gitflow.android.data.auth.GitHubOAuthResponse { *; }
-keep class com.gitflow.android.data.auth.GitHubUser { *; }
-keep class com.gitflow.android.data.auth.GitHubRepository { *; }
-keep class com.gitflow.android.data.auth.GitHubOrganization { *; }
-keep class com.gitflow.android.data.auth.GitLabOAuthResponse { *; }
-keep class com.gitflow.android.data.auth.GitLabUser { *; }
-keep class com.gitflow.android.data.auth.GitLabRepository { *; }
-keep class com.gitflow.android.data.auth.GitLabNamespace { *; }
-keep class com.gitflow.android.data.auth.GitLabStatistics { *; }
-keep class com.gitflow.android.data.auth.GitLabGroup { *; }

# ==================== Retrofit interfaces ====================
-keep interface com.gitflow.android.data.auth.GitHubApi { *; }
-keep interface com.gitflow.android.data.auth.GitLabApi { *; }

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

# ==================== AndroidX Security Crypto ====================
-keep class androidx.security.crypto.** { *; }
