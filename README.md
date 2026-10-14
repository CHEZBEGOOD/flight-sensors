# Flight Sensors

An Android app that turns a phone into a small panel of numbers for the cockpit: GPS ground speed, altitude, vertical speed and track, pitch and roll from the phone's own sensors, and how loud the cabin is. I wanted to see how much a phone can measure on its own in flight.

It's a side screen, not an instrument. More on that at the bottom.

## What's on the screen

| Tile | Where it comes from |
|---|---|
| GS kt, TRACK ° | GPS speed and course |
| GPS ALT ft | GPS altitude. Real MSL on Android 14 and newer, height above the WGS84 ellipsoid on older phones |
| VS fpm | Change in GPS altitude, smoothed and rounded to 10 fpm |
| PITCH °, ROLL ° | Android's rotation vector sensor (gyro, accelerometer and compass fused together) |
| HDG ° (mag) | Same sensor, from the compass |
| GPS ± ft | Android's own accuracy estimate for the fix |
| NOISE dBFS | Microphone level, not calibrated |

Above the numbers there's a simple artificial horizon that banks and pitches with the phone.

## Using it

1. Mount the phone upright with the screen facing you. Portrait or landscape both work.
2. Once you're straight and level, tap **Level**. That zeroes pitch and roll for however the mount is tilted.
3. Leave it running. The screen stays on.

It asks for location (GPS) and microphone (noise meter). If you say no to the mic, the noise tile just shows `--`.

## Building

Open the folder in Android Studio and hit Run, or from a terminal:

```
./gradlew assembleDebug
```

On Mac or Linux you might have to run `chmod +x gradlew` once first. The APK ends up in `app/build/outputs/apk/debug/`. It needs Android 8.0 or newer. There are no libraries, just Kotlin and the regular Android APIs.

## Things to know

- Phone attitude is decent in smooth air, but in a long coordinated turn it slowly decides it's level again. The accelerometer can't tell turning from gravity, and real AHRS units fix that with GPS or airspeed data. This doesn't.
- The compass gets thrown off by all the metal and electronics in a cockpit, so treat heading as a rough number.
- GPS vertical speed lags a few seconds behind the real VSI and jumps around.
- On phones older than Android 14, altitude is ellipsoid height, which reads about 70 ft low around Oregon compared to MSL.
- The noise number is relative to the mic's maximum (dBFS), not real decibels, so it's only good for comparing one moment to another.

## Not a flight instrument

Don't fly the airplane with this. Phone sensors drift, GPS drops out, and none of it is certified or tested for aviation use.
