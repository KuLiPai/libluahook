# luahook-ext-native consumer proguard rules
-keep class io.github.kulipai.luahook.hook.api.Il2CppNativeValue { *; }
-keep class io.github.kulipai.luahook.hook.api.Il2CppLib { *; }
-keep class io.github.kulipai.luahook.hook.api.NativeLib { *; }
-keep class io.github.kulipai.luahook.ext.nativelib.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
