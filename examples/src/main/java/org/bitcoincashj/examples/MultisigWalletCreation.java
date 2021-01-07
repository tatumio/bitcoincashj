/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoincashj.examples;

import org.bitcoincashj.core.NetworkParameters;
import org.bitcoincashj.crypto.DeterministicKey;
import org.bitcoincashj.kits.MultisigAppKit;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.wallet.DeterministicKeyChain;

import java.io.File;
import java.util.ArrayList;

/**
 * The following example shows how to use the by bitcoinj provided WalletAppKit.
 * The WalletAppKit class wraps the boilerplate (Peers, BlockChain, BlockStorage, Wallet) needed to set up a new SPV bitcoinj app.
 * <p>
 * In this example we also define a WalletEventListener class with implementors that are called when the wallet changes (for example sending/receiving money)
 */
public class MultisigWalletCreation {

    public static void main(String[] args) {

        // First we configure the network we want to use.
        // The available options are:
        // - MainNetParams
        // - TestNet3Params
        // - RegTestParams
        // While developing your application you probably want to use the Regtest mode and run your local bitcoin network. Run bitcoind with the -regtest flag
        // To test you app with a real network you can use the testnet. The testnet is an alternative bitcoin network that follows the same rules as main network. Coins are worth nothing and you can get coins for example from http://faucet.xeno-genesis.com/
        //
        // For more information have a look at: https://bitcoinj.github.io/testing and https://bitcoin.org/en/developer-examples#testing-applications
        NetworkParameters params = MainNetParams.get();

        ArrayList<DeterministicKey> followingKeys = new ArrayList<>();
        followingKeys.add(DeterministicKey.deserializeB58("xpub6CAT3GcHfSXgjoqDF4E3wCQZYqNm4ymuJtx6Ev7V2e5DT4jfL7DRua2Qo4ssL2HdHZVH4sTwNP5nkoDQwBMjzMGsXHu6v385HXi2T9bHQQB", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH));
        followingKeys.add(DeterministicKey.deserializeB58("xpub6DNBYKiSTFNuxpr3S6LAu1bq38Cg8z9EcdoJejuxEoN54puwjSCaLGC7KdMsgppwb3EBRg78RfpWdGzCJJWSVhrkwQGLcwNNvuGLPjJ1kcP", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH));
        // Now we initialize a new WalletAppKit. The kit handles all the boilerplate for us and is the easiest way to get everything up and running.
        // Have a look at the WalletAppKit documentation and its source to understand what's happening behind the scenes: https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/kits/WalletAppKit.java
        MultisigAppKit kit = new MultisigAppKit(params, new File("."), "walletappkit-example", followingKeys, 2);

        // In case you want to connect with your local bitcoind tell the kit to connect to localhost.
        // You must do that in reg test mode.
        //kit.connectToLocalHost();

        // Now we start the kit and sync the blockchain.
        // bitcoinj is working a lot with the Google Guava libraries. The WalletAppKit extends the AbstractIdleService. Have a look at the introduction to Guava services: https://github.com/google/guava/wiki/ServiceExplained
        kit.startAsync();
        kit.awaitRunning();

        System.out.println("receiving p2sh address: " + kit.wallet().currentReceiveAddress().toCash().toString());
    }

}
