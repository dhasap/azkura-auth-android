# Azkura Auth — R8 / ProGuard rules
#
# Goal: obfuscate internal implementation details (crypto helpers, key aliases,
# validation logic) while keeping data models, DI, and third-party libraries
# functioning correctly. See SECURITY.md / GitHub issue #12.

# ── General Android/Kotlin housekeeping ─────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Keep serializer() companions and @Serializable classes used for backups/DB.
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class id.azkura.auth.**$$serializer { *; }
-keepclassmembers class id.azkura.auth.** {
    *** Companion;
}
-keepclasseswithmembers class id.azkura.auth.data.model.** {
    <fields>;
    <init>(...);
}
-keepclasseswithmembers class id.azkura.auth.util.LocalBackup* {
    <fields>;
    <init>(...);
}

# ── Room ─────────────────────────────────────────────────────────────────
-keep class id.azkura.auth.data.local.db.** { *; }
-dontwarn androidx.room.**

# ── Hilt / Dagger (consumer rules are bundled, this is defense-in-depth) ───
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# ── Retrofit / OkHttp ───────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class id.azkura.auth.data.remote.** { *; }

# ── Google Sign-In / Credential Manager ─────────────────────────────────────
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.**

# ── CameraX / ML Kit barcode scanning ───────────────────────────────────────
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**

# ── Biometric / Security-crypto ─────────────────────────────────────────────
-dontwarn androidx.biometric.**
-dontwarn androidx.security.crypto.**

# ── Keep crypto class shapes but allow member obfuscation (names hidden,
# behaviour preserved) — do NOT use -keep here, only prevent removal of
# entry points that Hilt needs to inject.
-keepclassmembers class id.azkura.auth.data.local.crypto.CryptoManager {
    <init>(...);
}
-keepclassmembers class id.azkura.auth.data.local.crypto.SecretEncryptor {
    <init>(...);
}
-keepclassmembers class id.azkura.auth.data.local.crypto.VaultManager {
    <init>(...);
}

# ── Parcelize / Serializable stack traces for crash reports (optional) ─────
-renamesourcefileattribute SourceFile
