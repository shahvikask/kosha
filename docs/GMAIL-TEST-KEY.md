# Gmail trial signing key

Package name: `in.growurdhan.app`

SHA-1 of the saved device-test key (paste this into the Google Cloud Android OAuth client):

```
C6:7F:A0:CE:F9:55:DC:B7:E9:BE:F5:F3:2C:26:D9:60:91:ED:49:21
```

SHA-256:

```
3E:9C:28:ED:B5:07:4D:F9:02:02:99:75:43:A3:74:5E:D2:3B:CF:20:8E:4F:B2:DB:87:68:3D:BF:01:24:B4:2C
```

This key is not the Play upload key. The file is `src-tauri/keystore/gud-test.keystore`. It is force-added because `.gitignore` ignores `*.keystore`. Every debug APK built from `GUD-Android-Gmail` copies it to `~/.android/debug.keystore` before Gradle runs, so the fingerprint does not change between test builds.

Alias `androiddebugkey`, store password `android`. That is the debug password Gradle already expects. It is only for sideload test builds.
