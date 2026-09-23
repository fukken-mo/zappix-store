# Zappix Android Store

Lightweight Android/Android TV app store with exactly two sections:

- Free Apps
- Subscription Apps

Live API:
`https://panelsandapps.com/panels/Zappix/api/`

Package: `com.zappix.store`

The app loads its catalog from the Zappix PHP API. Selecting an app opens its details page and downloads the APK, then hands it to Android's normal package installer. Zappix does not bypass Android install protections.

Package name and app version are intentionally not part of the Zappix catalog in this version.


Premium TV UI build migrated to this repository.
