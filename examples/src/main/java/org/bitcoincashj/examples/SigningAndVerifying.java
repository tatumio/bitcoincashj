/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
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
import org.bitcoincashj.params.MainNetParams;

import java.math.BigInteger;
import java.security.SignatureException;

/**
 * This example shows how to solve the challenge Hal posted here:<p>
 *
 * <a href="http://www.bitcoin.org/smf/index.php?topic=3638.0">http://www.bitcoin.org/smf/index.php?topic=3638
 * .0</a><p>
 * <p>
 * in which a private key with some coins associated with it is published. The goal is to import the private key,
 * claim the coins and then send them to a different address.
 */
public class SigningAndVerifying {
    public static void main(String[] args) throws Exception {
        NetworkParameters params = MainNetParams.get();

        String privateKey = "5KLhQFv6wq6yNN7LMawKiMo6B6m2TjJxoNqXnoE45VN66ZMV6Fd";
        String message = "Hello world!";
        ECKey key = null;
        try {
            // Decode the private key from Satoshis Base58 variant. If 51 characters long then it's from Bitcoins
            // dumpprivkey command and includes a version byte and checksum, or if 52 characters long then it has
            // compressed pub key. Otherwise assume it's a raw key.
            if (privateKey.length() == 51 || privateKey.length() == 52) {
                DumpedPrivateKey dumpedPrivateKey = DumpedPrivateKey.fromBase58(params, privateKey);
                key = dumpedPrivateKey.getKey();
            } else {
                BigInteger privKey = Base58.decodeToBigInteger(privateKey);
                key = ECKey.fromPrivate(privKey);
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("First arg should be private key in Base58 format. Second argument should be address " +
                    "to send to.");
        }
        String signature = signMessage(key, message);
        System.out.println(key.toAddress(params).getOutputScriptType());
        String address = key.toAddress(params).toBase58();
        System.out.println(address);

        boolean valid = isSignatureValid(params, address, message, signature);
        System.out.println(valid);
    }

    private static String signMessage(ECKey privateKey, String message) {
        String signature = privateKey.signMessage(message);
        return signature;
    }

    private static boolean isSignatureValid(NetworkParameters params, String address, String message, String signature) {
        try {
            String signedAddress = "";
            if (Address.isValidLegacyAddress(params, address)) {
                signedAddress = ECKey.signedMessageToKey(message, signature).toAddress(params).toBase58();
            } else if (Address.isValidCashAddr(params, address)) {
                signedAddress = ECKey.signedMessageToKey(message, signature).toAddress(params).toString();
            }

            return signedAddress.equals(address);
        } catch (SignatureException e) {
            e.printStackTrace();
            return false;
        }
    }
}
