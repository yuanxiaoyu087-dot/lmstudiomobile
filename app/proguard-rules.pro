# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-keepclassnames class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class com.lmstudio.mobile.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.lmstudio.mobile.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.lmstudio.mobile.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

