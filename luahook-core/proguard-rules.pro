# luahook-core consumer proguard rules
-keep class io.github.kulipai.luahook.** { *; }
-keepclassmembers class io.github.kulipai.luahook.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
