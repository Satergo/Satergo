# .erg wallet format

.erg wallets are a custom binary format used by Satergo to store the details of a wallet.

The byte order is big endian for all versions. The data types are described using Java's data types.

Versions:

| formatVersion         | Introduced              |
|-----------------------|-------------------------|
| [2](#formatversion-2) | v1.9.0 (in development) |
| [1](#formatversion-1) | v0.0.3 (2022-06-22)     |

## formatVersion 2
This formatVersion makes changes to the encryption and the key derivation function. The version of Argon2 used is hardcoded because using a new Argon2 version without changing the formatVersion would mean older wallet readers would not notice the incompatibility, and adding a specific check just for the KDF version is not something I want to do.

This formatVersion also adds support for custom extra fields.

The cipher used for the encrypted wallet key data is AES/GCM/NoPadding with GCM authentication tag length 128. AES-256 is used. Argon2 type argon2id and version 0x13 is used with the parameters found in the wallet file. The current defaults for those are: memory=19456 KiB, iterations=2, parallelism=1.

| Data type | Value                                            |
|-----------|--------------------------------------------------|
| integer   | magic = 0x36003600 (dec 905983488) (36 00 36 00) |
| long      | format version                                   |
| integer   | argon2id memory parameter (in KiB)               |
| byte      | argon2id iterations parameter                    |
| byte      | argon2id parallelism parameter                   |
| byte[16]  | argon2id salt (16 bytes)                         |
| byte[12]  | initialization vector (12 bytes)                 |
| integer   | length of private key section                    |
| data      | encrypted wallet private key data                |
| byte[16]  | argon2id salt (16 bytes)                         |
| byte[12]  | initialization vector (12 bytes)                 |
| integer   | length of encrypted details section              |
| data      | encrypted details                                |

Encrypted wallet private key data: `(short) wallet key type id` followed by the serialized data.

| Wallet key type ID | Value | Details                                                                                                                                            |
|--------------------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| 0                  | LOCAL | The seed is stored inside the file. The format of it is the same as the one for formatVersion 1, but there is a random number of zeros at the end. |

Encrypted wallet details data:

| Data type                                         | Value                      |
|---------------------------------------------------|----------------------------|
| short                                             | byte length of next field  |
| utf-8 string bytes                                | wallet name                |
| int                                               | number of derived address  |
| [derived address entry](#derived-address-entry)[] | derived address entries    |
| int                                               | number of extra fields     |
| [extra field entry](#extra-field-entry)[]         | extra field entries        |

### Extra field entry
A wallet can only contain 1 of each extra field entry type. If duplicates are found while deserializing, the deserialization must be aborted.

| Data type | Value                                           |
|-----------|-------------------------------------------------|
| int       | an id that defines the type of this extra field |
| int       | byte length of next field                       |
| data      | data of the extra field                         |

## formatVersion 1
Since this version there is a magic number to determine the filetype and the format version is unencrypted.

The key data and information are stored in two different encrypted sections.

The two sections must be encrypted with the same password and must use different nonce values. The same 12 bytes are used for the PBKDF2 salt and initialization vector.

The cipher used is AES/GCM/NoPadding with GCM authentication tag length 128. AES-128 is used. PBKDF2WithHmacSHA1 is used with 65535 iterations of PBKDF2. See [this old file](https://github.com/Satergo/Satergo/blob/v1.8.0/src/main/java/com/satergo/extra/AESEncryption.java) for an implementation.

| Data type | Value                                                          |
|-----------|----------------------------------------------------------------|
| integer   | magic = 0x36003600 (dec 905983488) (36 00 36 00)               |
| long      | format version                                                 |
| integer   | length of private key section (including 12 nonce bytes)       |
| byte[12]  | nonce/initialization vector (12 bytes) and PBKDF2 salt         |
| data      | encrypted wallet private key data                              |
| integer   | length of encrypted details section (including 12 nonce bytes) |
| byte[12]  | nonce/initialization vector (12 bytes) and PBKDF2 salt         |
| data      | encrypted details                                              |

Encrypted wallet private key data: `(short) wallet key type id` followed by the serialized data.

| Wallet key type ID | Value | Details                            |
|--------------------|-------|------------------------------------|
| 0                  | LOCAL | The seed is stored inside the file |

`LOCAL` structure:

| Data type          | Value                                                                               |
|--------------------|-------------------------------------------------------------------------------------|
| short              | The key type ID of LOCAL, 0.                                                        |
| boolean            | [non-standard address derivation](https://github.com/ergoplatform/ergo/issues/1627) |
| short              | byte length of next field                                                           |
| utf-8 string bytes | seed phrase                                                                         |
| short              | byte length of next field                                                           |
| utf-8 string bytes | mnemonic password                                                                   |

Encrypted wallet details data:

| Data type                                         | Value                     |
|---------------------------------------------------|---------------------------|
| short                                             | byte length of next field |
| utf-8 string bytes                                | wallet name               |
| int                                               | number of derived address |
| [derived address entry](#derived-address-entry)[] | derived address entries   |
| int                                               | the number 0              |

### Derived address entry
This is the same for all wallet formatVersions (0, 1 and 2).

| Data type          | Value                     |
|--------------------|---------------------------|
| int                | address index             |
| short              | byte length of next field |
| utf-8 string bytes | address label             |

<details>
	<summary><h2 style="display: inline;">LEGACY FORMAT (DO NOT IMPLEMENT) - formatVersion 0</h2></summary>

**This version should not be implemented.**

**Note: This version contains an unnecessary header. To get to the data, skip 6 bytes.**

Encrypted data: (everything is encrypted in this version, even the version number)

The cipher used is the same as the one used in formatVersion 1.

| Data type                                         | Value                     |
|---------------------------------------------------|---------------------------|
| short                                             | (-21267, skip)            |
| short                                             | (5, skip)                 |
| short                                             | (skip)                    |
| long                                              | format version            |
| short                                             | byte length of next field |
| utf-8 string bytes                                | wallet name               |
| short                                             | byte length of next field |
| utf-8 string bytes                                | seed phrase               |
| short                                             | byte length of next field |
| utf-8 string bytes                                | mnemonic password         |
| int                                               | number of derived address |
| [derived address entry](#derived-address-entry)[] | derived address entries   |
| int                                               | the number 0              |

</details>