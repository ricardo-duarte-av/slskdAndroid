# ============================================================================
# slskdAndroid — R8 / ProGuard rules for release (minify + resource shrinking)
# ============================================================================

# ---------------------------------------------------------------------------
# Strip verbose/debug/info logging from release builds.
# OkHttp's HttpLoggingInterceptor is already gated to Level.NONE in release, but
# this also removes any android.util.Log.v/d/i calls (and their string-building
# arguments) so logcat stays quiet. Warnings and errors are kept.
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}

# ---------------------------------------------------------------------------
# kotlinx.serialization
# Keep generated serializers and the @Serializable types' companions so the
# Retrofit kotlinx-serialization converter can resolve serializers at runtime.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the Companion objects that hold the generated serializer() for any
# @Serializable class in our model packages.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the synthetic $serializer classes themselves.
-keep,includedescriptorclasses class com.slskdandroid.**$$serializer { *; }
-keepclassmembers class com.slskdandroid.** {
    *** Companion;
}

# ---------------------------------------------------------------------------
# SignalR hub wire types — deserialized by the Microsoft SignalR Java client
# with Gson via reflection (Unsafe), so field names must match slskd's JSON.
# R8 must not rename or strip these members. See SearchHubEvent.kt.
# ---------------------------------------------------------------------------
-keep class com.slskdandroid.core.network.Hub** { *; }
-keepclassmembers class com.slskdandroid.core.network.** {
    <fields>;
}

# Gson (used by SignalR) — keep generic signatures and reflective access.
-keepattributes Signature, *Annotation*, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------------------------------------------------------------------------
# Retrofit / OkHttp (mostly covered by their bundled consumer rules; these are
# defensive for the bleeding-edge versions in use).
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# SignalR pulls in SLF4J / reactor; silence missing optional deps.
-dontwarn org.slf4j.**
-dontwarn io.reactivex.**
-dontwarn reactor.**
