### Welcome to bitcoincashj

The bitcoincashj library is a Java implementation of the Bitcoin Cash (BCH) protocol. This library is a fork of Mike Hearn's original bitcoinj library aimed at supporting the Bitcoin Cash eco-system.

This bitcoincashj is a fork of ProtocolCash's, which is a fork of the original bitcoincashj, which forked from Mike Hearn's original bitcoinj. Confusing, right?

This fork of bitcoincashj has many changes and fixes since the original bitcoincashj (bitcoinj.cash) library, like:

- CTOR support
- 32MB block support
- Native Cash Account integration (trustless registration and sending)
- SLP tokens
- Standard BIP44 derivation (m/44'/145'/0' for BCH, m/44'/245'/0' for SLP)
- UTXO management when sending coins using SendRequest.utxos
- BIP47 Reusable Payment Codes support
- Up-to-date hardfork checkpoints

It allows maintaining a wallet and sending/receiving transactions without needing a full blockchain node. It comes with full documentation and some example apps showing how to use it.

### Technologies

* Java 7 for the core modules, Java 8 for everything else (also compiles and runs under JRE/JDK 11 or 12)
* [Maven 3+](http://maven.apache.org) - for building the project
* [Google Protocol Buffers](https://github.com/google/protobuf) - for use with serialization and hardware communications

#### Building from the command line

To perform a full build use
```
mvn clean package
```
You can also run
```
mvn site:site
```
to generate a website with useful information like JavaDocs.

The outputs are under the `target` directory.

#### Building from an IDE

Alternatively, just import the project using your IDE. [IntelliJ](http://www.jetbrains.com/idea/download/) has Maven integration built-in and has a free Community Edition. Simply use `File | Import Project` and locate the `pom.xml` in the root of the cloned project source tree.

If using JDK 11 or above, the WalletTemplate demo you will JavaFX-SDK installed and referenced seperately. In IntelliJ, add javafx-lib path to File > Project Structure > Libraries. Under Run > Edit Configurations, add '--module-path "PATH_TO_FX" --add-modules=javafx.controls,javafx.fxml' to VM options. Replace PATH_TO_FX with javafx lib path, leaving the quotations.

### Contributing to bitcoincashj

If you would like to help contribute to bitcoincashj, feel free to make changes and submit pull requests.

Not a programmer? Not a problem. You can donate Bitcoin Cash to the addresses below:

bitcoincash:qptnypuugy29lttleggl7l0vpls0vg295q9nsavw6g

PM8TJaArHqgRL78aUwxCADZneMYjxQT9pv3NhhjRo2HjNYcFysc1rpf3U9rJV78kayJeDU6FcxZBiAxFxfGxapa9fAFCLLXCNj7Qzjw8bpgDof5a4JbC

pokkst#66280
