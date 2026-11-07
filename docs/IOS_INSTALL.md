# Installing LUNA on iPhone

LUNA is not on the App Store or TestFlight. The iPhone build is an **unsigned `.ipa`** attached to each [GitHub Release](https://github.com/Lunatic5202/Luna/releases/latest). You sign your own copy with your own Apple ID, so nobody shares credentials.

> [!WARNING]
> The iOS app is new. It passes simulator CI but has **not been verified on every device**. Expect rough edges and please report them as issues.

## What you need

- An iPhone on **iOS 17 or later**.
- A free Apple ID (a paid developer account is not required).
- A Windows PC or Mac with a USB cable (or the same Wi-Fi) for the sideloading tool.
- One of: [AltStore](https://altstore.io) or [Sideloadly](https://sideloadly.io). Check their sites for current OS requirements, as they change.

## Steps

1. Download `LUNA-vX.Y.Z-unsigned.ipa` from the latest release. Optionally verify it against the `.sha256` file.
2. On the iPhone, turn on **Settings → Privacy & Security → Developer Mode** and restart when asked.
3. Open your sideloading tool, sign in with your Apple ID, and install the `.ipa` to your iPhone.
4. If iOS says the developer is untrusted, go to **Settings → General → VPN & Device Management**, tap your Apple ID and choose **Trust**.
5. Open LUNA and add music through the in-app importer. Imported files are copied into LUNA's own storage.

## Limits of free Apple ID signing

- The app **stops opening after 7 days** until it is re-signed. AltStore can refresh it automatically while your computer is on the same network; with Sideloadly, reinstall the same `.ipa`.
- Free accounts allow only **3 sideloaded apps** at a time.
- Your library and settings stay on the device when you refresh, but reinstalling from a different Apple ID gives a new app identity.
- Each person must sign their own copy. Signed copies cannot be passed on.

## Updating

Download the newer `.ipa` and install it the same way, using the same Apple ID, so it installs over the old version.

## Verifying a download

```
shasum -a 256 LUNA-vX.Y.Z-unsigned.ipa
```

The output should match the `.sha256` file on the release.

## Troubleshooting

- **App won't open after a week:** the signature expired. Re-sign it.
- **"Untrusted developer":** see step 4.
- **Tool can't see the phone:** unlock it, tap *Trust this computer*, and use a data-capable cable.

If you buy a paid Apple Developer account later, TestFlight (90-day builds) becomes possible; that is not planned yet.
