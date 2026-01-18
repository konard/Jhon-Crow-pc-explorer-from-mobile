# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Hilt generated classes
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier
-keep class * extends dagger.hilt.android.internal.modules.ModuleInstallations

# Keep USB related classes
-keep class android.hardware.usb.** { *; }

# Keep data classes
-keepclassmembers class com.pcexplorer.**.model.** {
    <fields>;
    <init>(...);
}
