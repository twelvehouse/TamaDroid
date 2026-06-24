# Keep JNI entry points (native methods are resolved by name at runtime).
-keepclasseswithmembernames class * {
    native <methods>;
}
