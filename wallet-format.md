# .erg wallet format

.erg wallets are a custom binary format used by Satergo to store the details of a wallet.

The byte order is big endian. The files are encrypted with the AES/GCM/NoPadding cipher with authentication tag length 128, see [AESEncryption.java](src/main/java/com/satergo/extra/AESEncryption.java).

## formatVersion 0

| Data type                                           | Value                     |
|-----------------------------------------------------|---------------------------|
| unsigned long                                       | format version            |
| utf-8 zt string                                     | wallet name               |
| utf-8 zt string                                     | mnemonic phrase           |
| utf-8 zt string                                     | mnemonic password         |
| unsigned int                                        | amount of derived address |
| [derived address entry](#derived-address-entry-0)[] | derived address entry     |
| unsigned int                                        | address book size         |
| [address book entry](#address-book-entry-0)[]       | address book entry        |

### Derived address entry (0)
| Data type       | Value         |
|-----------------|---------------|
| int             | address index |
| utf-8 zt string | address label |

### Address book entry (0)
| Data type       | Value   |
|-----------------|---------|
| utf-8 zt string | name    |
| utf-8 zt string | address |