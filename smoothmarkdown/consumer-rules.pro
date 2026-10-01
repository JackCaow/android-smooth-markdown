# The owned JNI library exports these exact class/method names.
# Keep the transport entry points stable when the consuming app enables R8.
-keep,allowoptimization class com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge {
    native <methods>;
}
