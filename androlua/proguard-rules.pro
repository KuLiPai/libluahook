# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class io.kulipai.sora.luaj.** { *; }
-dontwarn io.kulipai.sora.luaj.**
-keep class io.dingyi222666.sora.lua.tool.** { *; }
-keep class com.myopicmobile.** { *; }
-dontwarn org.luaj.**
-dontwarn com.androlua.**
-keep class org.joni.ast.** { *; }
-keep class dx.** { *; }
-keep class org.luaj.** { *; }
-keepclassmembers class org.luaj.** { *; }
-keep class org.luaj.vm2.** { *; }
-keepclassmembers class org.luaj.vm2.** { *; }