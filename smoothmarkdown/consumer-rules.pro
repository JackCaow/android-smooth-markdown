# The owned JNI library exports these exact class/method names.
# Keep the transport entry points stable when the consuming app enables R8.
-keep,allowoptimization class com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge {
    native <methods>;
}

# Rust calls these synchronous extension adapters through GetMethodID.
-keepclassmembers class com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge$Hooks {
    long[] inlineMatch(java.lang.String, int, int);
    long[] blockMatch(java.lang.String[], int, int);
}
