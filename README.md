# Cordova Background Mode Plugin (2025 Edition)

Plugin for the Cordova framework to perform **infinite background execution** on Android & iOS.

Fully modernized, Google Play & App Store compliant, survives Samsung One UI 7, Xiaomi HyperOS 2, Android 15, iOS 18.

#### Store Compliance Warning
Infinite background execution is **not officially supported** by Google Play or Apple App Store.  
A successful submission is possible but **not guaranteed**. Use at your own risk.

## Supported Platforms
- Android (6.0+)
- iOS (12.0+)

## Installation

```bash
cordova plugin add @globules-io/cordova-plugin-background-mode
# or from this repo
cordova plugin add https://github.com/globules-io/cordova-plugin-background-mode.git
```

## Usage

### Enable/Disable
```js
cordova.plugins.backgroundMode.enable();
cordova.plugins.backgroundMode.disable();
```

### Check Status
```js
cordova.plugins.backgroundMode.isEnabled();  // true if plugin is enabled
cordova.plugins.backgroundMode.isActive();   // true if app is in background
```

### Events
```js
cordova.plugins.backgroundMode.on('activate', () => {
    console.log('Background mode activated');
});

cordova.plugins.backgroundMode.on('deactivate', () => {
    console.log('Background mode deactivated');
});

cordova.plugins.backgroundMode.un('activate', callback); // Remove listener
```

### Notification (Android)
```js
cordova.plugins.backgroundMode.configure({
    title:  'My App',
    text:   'Running in background...',
    icon:   'icon',           // res/drawable/icon.png
    color:  'F14F4D',         // Android 5.0+ accent color
    silent: false,            // Set true to hide notification (not recommended)
    resume: true,             // Tap notification brings app to foreground
    hidden: true,             // Android 5.0+: hide on lockscreen
    bigText: false
});
```

### Silent Mode
Warning: Android may kill silent background apps aggressively.
```js
cordova.plugins.backgroundMode.configure({ silent: true });
```