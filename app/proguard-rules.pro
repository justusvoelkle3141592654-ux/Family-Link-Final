# Keep the DeviceAdminReceiver and services referenced from the manifest / system.
-keep class com.familylink.ios.admin.DeviceAdmin { *; }
-keep class com.familylink.ios.service.** { *; }
-keep class com.familylink.ios.lock.** { *; }
