# Tessel

A landscape twin-stick shooter for Android. Left thumb moves, right thumb aims and fires. Geometric enemies pour in from the edges, break into pieces when you hit them hard enough, and drop coins you spend between stages. Every fifth stage ends in a boss. Up to eight friends can play on one team, each on their own phone, with no internet and no account.

![A stage in progress](docs/stage.png)

## How a run goes

Each stage lasts a minute. When it ends you get a few seconds to clear what is left, then the shop opens. You can buy the same upgrade as many times as your coins allow, but every level costs more than the one before, and everything costs more the further you get, because coins are worth more too.

New kinds of enemy keep turning up as the stages go by: ones that keep their distance and shoot, ones that aim a line and charge down it, ones that split in two or in four, turrets that park and spin. Each kind has its own colour and shape. Enemy shots are always hollow orange rings, and anything that is about to fire or charge warns you first.

Stage 5 brings the first boss, and another follows every five stages. After the fourth the line starts over, with tougher versions.

![The Gyre, the boss at stage 20](docs/boss.png)

## Playing together

One phone taps Host and gets a four-letter code. The others tap Join and pick that code from the list. If your phones have NFC, the host can simply hold each friend's phone back to back with theirs and it joins on its own (Tessel has to be open on the friend's phone).

The phones talk to each other directly over Bluetooth and Wi-Fi using Google's Nearby Connections, so they need Google Play services, and Bluetooth and Wi-Fi switched on. Wi-Fi does not have to be connected to a network. More players means more enemies, and bosses with more health.

If a phone drops out, the game waits 20 seconds for it to come back. The host can also choose to play on without it.

![Four players on one team, in the light appearance](docs/team.png)

![The shop between stages](docs/shop.png)

## Install

Download `tesselv0.2.apk` from the [releases page](../../releases) and open it on your phone. Android will ask you to allow installs from your browser or file manager the first time. Tessel needs Android 8 or newer.

All phones in a game need the same version.

## Building it yourself

You need a JDK (17 or newer) and the Android SDK with platform 37.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug, installs next to a release as "Tessel Dev"
./gradlew test                 # the game logic runs on the JVM, no phone needed
./gradlew distApk              # signed release in dist/, needs a keystore.properties
./gradlew shots                # redraws the pictures in docs/ with the game's own renderer
```

`keystore.properties` sits in the project root and is not part of the repo. It holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it, release builds are signed with the debug key.

## Known limits

Co-op has been played on two phones (a Galaxy A71 and an A13). Games with more than two phones and the NFC tap have been tested in code but not yet on real hardware. There is no sound yet, only vibration. The pictures above were drawn on a computer by the game's own rendering code at the A71's resolution, not captured from a phone.
