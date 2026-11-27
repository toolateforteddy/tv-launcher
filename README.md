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

## Kid remote (phone or tablet)

The TV app also serves a simple remote page on your home Wi-Fi. It has big picture buttons that each open
a specific show, plus one pause/play button. There's no pairing and no Google account involved.

1. On the TV, select the **Phone remote** tile to see the address (for example `http://192.168.1.20:8766` for the
   test build; the home build uses port `8765`).
2. Open it on a phone or tablet on the same Wi-Fi, then use the browser's **Add to Home screen**.
3. Hold the gear in the bottom corner for 2 seconds to add shows. Paste a share link from the Prime Video,
   Netflix or YouTube app, and select **Get name and picture**.
4. Run this one-time setup so show buttons also work while something is already playing (Android 10 blocks this by default):

   ```sh
   scripts/enable-kid-remote.sh <tv-ip> test   # or: home
   ```

Anyone on your Wi-Fi can open the page, so it's only meant for a home network.

## Kid Remote phone app (nothing installed on the TV)

`kid-remote-phone.apk` is an Android phone app that works like the Google TV phone remote, but made for a
toddler: big picture buttons that each open a show, and one big pause/play button. It talks to the TV's
built-in remote service (the same one the Google TV app uses), so it works with the stock home screen and
doesn't need this launcher or anything else installed on the TV.

Setup, once:

1. Install `kid-remote-phone.apk` on the phone from the [latest release](../../releases/tag/latest)
   (Android will ask you to allow installs from your browser or file manager).
2. Open **Kid Remote**. It starts on the grown-up screen and lists the TVs it finds on your Wi-Fi.
   Tap **Pair** next to your TV (or type its address), then type the 6-character code the TV shows.
   The phone remembers the pairing, so you never do this again.
3. Add shows: in the Prime Video, Netflix or YouTube app on the phone, open the show, tap **Share**, copy the
   link and paste it in. **Get name and picture** fills in the rest, or tap the picture box to pick a photo from the phone.
   **Try on TV** checks a link before you save it.
4. Tap **Done** for the kid screen.

Using it:
- Tap a picture to open that show on the TV. Tap the round button to pause or play.
- Hold the gear in the bottom corner for 2 seconds to get back to the grown-up screen. A quick tap does nothing,
  and the Back gesture is ignored on the kid screen.
- The dot in the top corner is green when the phone is connected to the TV.
- To stop little fingers leaving the app, turn on **App pinning** in the phone's Settings (Security) and pin Kid Remote.

What a show button opens depends on the TV app that handles the link. Some links (often Prime Video) open the
show's page rather than starting it; press **OK** with the grown-up screen's test buttons (or the TV remote) to
start it. Use **Try on TV** to see what a link does before saving it.

The phone app's code is in `phone/`. `./gradlew :phone:testDebugUnitTest` runs its tests, including pairing
with a stand-in TV. `phone/src/test/snapshots/` has rendered pictures of the kid screen.

## Building locally

Use Android Studio, or run `./gradlew assemblePreviewRelease assembleHomeRelease` with the Android SDK installed.
APKs are signed with `keystore/sideload.keystore`, a throwaway key committed on purpose so that every build,
from CI or locally, can install over the last one.
