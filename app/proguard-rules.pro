# Add project specific ProGuard rules here.
-keep class com.rndisquicktoggle.** { *; }
-keepnames class * implements android.os.Parcelable {
  public static final ** CREATOR;
}
