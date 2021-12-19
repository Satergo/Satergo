# Satergo
Desktop wallet for the cryptocurrency Ergo with embedded node functionality. Downloads can be found at [the website](https://satergo.org).

## Supported platforms
More can easily be added.
- Windows (x64)
- Linux (x64, aarch64 and arm32)
- Mac (x64 and aarch64)
- Universal (Java 17+)

## Translating
To translate the program, open the directory src/main/resources/lang and duplicate Lang.properties.
Change the file name to Lang_ and the 3-letter code of your language (ISO-639-3) (for example Lang_ita.properties) and translate everything in the file.

Then, in the same folder, open the index.json file, add a comma to the last entry and make a new line inside the list like:
```json
	"???": { "name": "Name of the language in the language", "credit": "Your contact details (socials, etc.), or your name" }
```

## License
- Project: (see [LICENSE](LICENSE))
- Some icons: The Monero Project (see [m-images/LICENSE](src/main/resources/m-images/LICENSE))
- Settings gear icon: https://www.flaticon.com/authors/google