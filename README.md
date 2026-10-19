# tv-launcher

A simple, ad-free home screen for an Android TV (built for a Sony X900H on Android TV 9/10).
It shows your apps, favorites, HDMI inputs and Settings. It has no recommendation rows,
no sponsored banner, and no trailers.

## Two builds

| APK | What it does |
| --- | --- |
| `tv-launcher-test.apk` | Appears as **Home (test)** in the TV's apps list. Open it yourself to try it. The Home button and the family's normal home screen are not affected. |
| `tv-launcher-home.apk` | The real thing. It registers as a home screen. Only install it with `scripts/switch-to-custom.sh`. |

Both can be installed at the same time. They have separate favorites.

CI builds both on every push. The newest builds from `main` are on the
[latest release](../../releases/tag/latest). PR builds are attached to their workflow run as the `apks` artifact.

## One-time TV setup

1. On the TV: **Settings > Device Preferences > About**, then select **Build** 7 times to unlock Developer options.
2. **Settings > Device Preferences > Developer options**: turn on **USB debugging** (on this TV it also enables debugging over the network).
3. Find the TV's IP address under **Settings > Network & Internet**.
4. On a computer with [adb](https://developer.android.com/tools/releases/platform-tools) installed, run `adb connect <tv-ip>` and accept the prompt that appears on the TV.

## Trying it out (safe)

```sh
scripts/install-test.sh <tv-ip> tv-launcher-test.apk
```

Using it:
- Select a tile to open it.
- Long-press OK (or press Menu) on a tile to add it to Favorites, move it, or open App info.

## Switching the TV over

```sh
scripts/switch-to-custom.sh <tv-ip> tv-launcher-home.apk
```

This installs the home build and turns off the stock home screen (`com.google.android.tvlauncher`).
Nothing is deleted. A big firmware update may turn the stock screen back on. If that happens, run the script again.

To undo:

```sh
scripts/switch-to-stock.sh <tv-ip>
```

## Building locally

Use Android Studio, or run `./gradlew assemblePreviewRelease assembleHomeRelease` with the Android SDK installed.
APKs are signed with `keystore/sideload.keystore`, a throwaway key committed on purpose so that every build,
from CI or locally, can install over the last one.
