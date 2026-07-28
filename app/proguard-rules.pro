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

# ---------------------------------------------------------------------------
# WorkManager: the default WorkerFactory instantiates workers reflectively by
# class name, so the class and its (Context, WorkerParameters) constructor must
# survive shrinking. (androidx.work ships a consumer rule for this; this is
# defensive and explicit about the one worker we have.)
# ---------------------------------------------------------------------------
-keep class com.slskdandroid.notifications.MessageCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
