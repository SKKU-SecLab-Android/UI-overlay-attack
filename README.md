# TZPI: A TrustZone-Based Privacy Indicator System for Trustworthy Privacy Warnings on Android

This repository contains the UI overlay attack Android application implemented on top of 3rd-party app 'Privacy Indicators' which has public github and MIT license(https://github.com/NitishGadangi/Privacy-Indicator-App/).

UI overlay attack involves creating a higher z-order and deceptive status bar to hide the Android privacy indicator whenever the camera is in use, impeding recognition.


---
<br>

## Prerequisites

We confirmed that UI overlay attack app build runs on a 64-bit Windows 11 system with the below versions. (Deault target device : PIXEL6)

* Android Studio Bumblebee 2021.1.1 Patch 2

* Android SDK version 30 (API 30: Android 11.0 (R))

* Build Tool Version 30.0.2
 
* Android Gradle Plugin Version 4.0.1

* Gradle Version 6.1.1

* To access the UI overlay attack source code, clone this repository using the following command:
  ```bash
  git clone https://github.com/Android-SecLab/UI-overlay-attack.git
  ```
  
* To build the UI overlay attack app, simply import and run build project 'Privacy-Indicator-App-master' in Android Studio.

<br>

## How to use UI overlay attack app

1. Build and install the UI overlay attack app on the target device.

2. Launch the app and enable the function that leads you to consent to the Accessibility Service permission.

3. After granting accessibility permission to the UI overlay attack app and executing the camera, a higher z-order and a deceptive status bar appear at the top of the device.





