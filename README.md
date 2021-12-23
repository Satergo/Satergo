# Satergo
Desktop wallet for the cryptocurrency Ergo with embedded node functionality. Downloads can be found at [the website](https://satergo.org).

## Supported platforms
More can easily be added.
- Windows (x64)
- Linux (x64, aarch64)
- Mac (x64 and aarch64)
- Universal (Java 17+)

## Translating
To translate the program, open the directory src/main/resources/lang and duplicate Lang.properties.
Change the file name to Lang_ and the 3-letter code of your language (ISO-639-3) (for example Lang_ita.properties) and translate everything in the file.

Then, in the same folder, open the index.json file, add a comma to the last entry and make a new line inside the list like:
```json
	"???": { "name": "Name of the language in the language", "credit": "Your contact details (socials, etc.), or your name" }
```

## Build setup
If you don't want to download the prebuilt runtimes, you can build the wallet for yourself.

Java is not needed for running the prebuilt runtimes, because it is included in the runtime. But for building or running from sources, Java 17+ is required. The easiest way to download and install it is from [adoptium.net](https://adoptium.net).

To run the wallet from the sources, run the command `./gradlew run`.

To build a runtime for your platform, run `./gradlew runtimeZip`. The archive will appear in the runtimes directory.

To build a runtime for a specific platform, run `./gradlew runtimeZip -Pplatform=<platform>`, where platform can be any of `win linux linux-aarch64 linux-arm32 mac mac-aarch64`.

To build runtimes for all platforms, run `./build-all-runtimes.sh`.

## License
- Project: (see [LICENSE](LICENSE))
- Some icons: The Monero Project (see [m-images/LICENSE](src/main/resources/m-images/LICENSE))
- Settings gear icon: https://www.flaticon.com/authors/google