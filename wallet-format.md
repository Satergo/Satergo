# .erg wallet format

.erg wallets are a custom binary format used by Satergo to store the details of a wallet.

The byte order is big endian. The cipher used is AES/GCM/NoPadding with authentication tag length 128, see [AESEncryption.java](src/main/java/com/satergo/extra/AESEncryption.java).

## formatVersion 1
In this version there is a magic number to determine the filetype and the format version is not encrypted either.

The information is separated from the private key data to not need to keep both of them in-memory.

The two sections must be encrypted with the **same password** and must use **different nonce values**.

| Data type | Value                                                          |
|-----------|----------------------------------------------------------------|
| integer   | magic = 0x36003600 (dec 905983488) (36 00 36 00)               |
| long      | format version                                                 |
| integer   | length of private key section (including 12 nonce bytes)       |
| byte[12]  | nonce/initialization vector (12 bytes)                         |
| data      | encrypted wallet private key data                              |
| integer   | length of encrypted details section (including 12 nonce bytes) |
| byte[12]  | nonce/initialization vector (12 bytes)                         |
| data      | encrypted details                                              |

Encrypted wallet private key data: `(short) wallet key type id` followed by the serialized data.

| Wallet key type ID | Value  | Details                                |
|--------------------|--------|----------------------------------------|
| 0                  | LOCAL  | The mnemonic is stored inside the file |
| 50                 | LEDGER | Ledger                                 |

`LOCAL` structure:

| Data type          | Value                                                                               |
|--------------------|-------------------------------------------------------------------------------------|
| boolean            | [non-standard address derivation](https://github.com/ergoplatform/ergo/issues/1627) |
| short              | byte length of next field                                                           |
| utf-8 string bytes | seed phrase                                                                         |
| short              | byte length of next field                                                           |
| utf-8 string bytes | mnemonic password                                                                   |

`LEDGER` structure:

| Data type | Value          |
|-----------|----------------|
| int       | the product id |
| byte\[33] | the public key |

Encrypted wallet details data:

| Data type                                         | Value                               |
|---------------------------------------------------|-------------------------------------|
| short                                             | byte length of next field           |
| utf-8 string bytes                                | wallet name                         |
| int                                               | amount of derived address           |
| [derived address entry](#derived-address-entry)[] | derived address entry               |
| int                                               | address book size                   |
| [address book entry](#address-book-entry)[]       | address book entry                  |

### Derived address entry
| Data type          | Value                     |
|--------------------|---------------------------|
| int                | address index             |
| short              | byte length of next field |
| utf-8 string bytes | address label             |

### Address book entry
| Data type          | Value                     |
|--------------------|---------------------------|
| short              | byte length of next field |
| utf-8 string bytes | name                      |
| short              | byte length of next field |
| utf-8 string bytes | address                   |

<details>
<summary><h2 style="display: inline;">OLD FORMAT (DO NOT IMPLEMENT)</h2></summary>

## formatVersion 0
**This version should not be implemented.**

**Note: This version contains an unnecessary header. To get to the data, skip 6 bytes.**

Encrypted data: (everything is encrypted in this version, even the format version number)

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
| int                                               | amount of derived address |
| [derived address entry](#derived-address-entry)[] | derived address entry     |
| int                                               | address book size         |
| [address book entry](#address-book-entry)[]       | address book entry        |

</details>