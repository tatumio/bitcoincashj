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

import org.bitcoincashj.core.*;
import org.bitcoincashj.core.bip47.BIP47Channel;
import org.bitcoincashj.kits.BIP47AppKit;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.wallet.SendRequest;
import org.bitcoincashj.wallet.Wallet;

import java.io.File;

/**
 * The following example shows how to use the by bitcoinj provided WalletAppKit.
 * The WalletAppKit class wraps the boilerplate (Peers, BlockChain, BlockStorage, Wallet) needed to set up a new SPV bitcoinj app.
 * <p>
 * In this example we also define a WalletEventListener class with implementors that are called when the wallet changes (for example sending/receiving money)
 */
public class SendingToBip47 {
    private static BIP47AppKit kit;
    public static void main(String[] args) throws InsufficientMoneyException {

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

        // Now we initialize a new WalletAppKit. The kit handles all the boilerplate for us and is the easiest way to get everything up and running.
        // Have a look at the WalletAppKit documentation and its source to understand what's happening behind the scenes: https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/kits/WalletAppKit.java
        kit = new BIP47AppKit(params, new File("."), "walletappkit-example");

        // In case you want to connect with your local bitcoind tell the kit to connect to localhost.
        // You must do that in reg test mode.
        //kit.connectToLocalHost();

        // Now we start the kit and sync the blockchain.
        // bitcoinj is working a lot with the Google Guava libraries. The WalletAppKit extends the AbstractIdleService. Have a look at the introduction to Guava services: https://github.com/google/guava/wiki/ServiceExplained
        kit.startAsync();
        kit.awaitRunning();

        System.out.println("receiving payment code: " + kit.getPaymentCode());

        /* The next part assumes we have coins to spend */
        String destination = "";
        Coin sendAmount = Coin.valueOf(1000); //For example, we're going to send 1,000 satoshis.
        boolean canSendToPaymentCode = kit.canSendToPaymentCode(destination);
        if (canSendToPaymentCode) {
            attemptBip47Payment(destination, sendAmount);
        } else {
            SendRequest notificationReq = kit.makeNotificationTransaction(destination, true);
            if (notificationReq != null) {
                kit.broadcastTransaction(notificationReq.tx);
                kit.putPaymenCodeStatusSent(destination, notificationReq.tx);
                attemptBip47Payment(destination, sendAmount);
            }

        }
    }

    private static void attemptBip47Payment(String destination, Coin amount) throws InsufficientMoneyException {
        BIP47Channel paymentChannel = kit.getBip47MetaForPaymentCode(destination);
        String depositAddress = null;
        if (paymentChannel != null) {
            if (paymentChannel.isNotificationTransactionSent()) {
                depositAddress = kit.getCurrentOutgoingAddress(paymentChannel);
                if (depositAddress != null) {
                    System.out.println("Received user's deposit address " + depositAddress);
                    paymentChannel.incrementOutgoingIndex();
                    kit.saveBip47MetaData();
                    processNormalTransaction(depositAddress, amount);
                }
            } else {
                SendRequest notificationReq = kit.makeNotificationTransaction(destination, true);
                kit.broadcastTransaction(notificationReq.tx);
                kit.putPaymenCodeStatusSent(destination, notificationReq.tx);
                attemptBip47Payment(destination, amount);
            }
        }
    }

    private static void processNormalTransaction(String address, Coin amount) throws InsufficientMoneyException {
        Address to = AddressFactory.create().getAddress(kit.params(), address);
        Wallet.SendResult result = kit.wallet().sendCoins(kit.peerGroup(), to, amount);
        System.out.println("coins sent. transaction hash: " + result.tx.getTxId());
    }
}
