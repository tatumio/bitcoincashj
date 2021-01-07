/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoincashj.core.bip47;

import org.bitcoincashj.core.Address;
import org.bitcoincashj.core.ECKey;
import org.bitcoincashj.core.Sha256Hash;
import org.bitcoincashj.kits.BIP47AppKit;
import org.bitcoincashj.kits.SlpBIP47AppKit;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.wallet.bip47.NotSecp256k1Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import static org.bitcoincashj.utils.BIP47Util.getReceiveAddress;

public class BIP47Channel {
    private static final String TAG = "BIP47Channel";

    private static final int STATUS_NOT_SENT = -1;
    private static final int STATUS_SENT_CFM = 1;

    private static final int LOOKAHEAD = 20;

    private String paymentCode = null;
    private String notificationAddress = null;
    private List<BIP47Address> incomingAddresses = new ArrayList<BIP47Address>();
    private int status = STATUS_NOT_SENT;
    private int currentOutgoingIndex = 0;
    private int currentIncomingIndex = -1;
    private Sha256Hash ntxHash;

    private static final Logger log = LoggerFactory.getLogger(BIP47Channel.class);

    public BIP47Channel() {
    }

    public BIP47Channel(String paymentCode) {
        BIP47Account bip47Account = new BIP47Account(MainNetParams.get(), paymentCode);
        this.paymentCode = paymentCode;
        this.notificationAddress = bip47Account.getNotificationAddress().toString();
    }

    public BIP47Channel(String notificationAddress, Sha256Hash ntxTxid) {
        this.notificationAddress = notificationAddress;

        if (ntxTxid != null) {
            this.setNtxHash(ntxTxid);
        }
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public String getNotificationAddress() {
        return notificationAddress;
    }

    public void setNotificationAddress(String na) {
        notificationAddress = na;
    }

    public void setPaymentCode(String pc) {
        paymentCode = pc;
    }

    public List<BIP47Address> getIncomingAddresses() {
        return incomingAddresses;
    }

    public int getCurrentIncomingIndex() {
        return currentIncomingIndex;
    }

    public void generateKeys(BIP47AppKit wallet) throws NotSecp256k1Exception, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        for (int i = 0; i < LOOKAHEAD; i++) {
            ECKey key = getReceiveAddress(wallet, paymentCode, i).getReceiveECKey();
            Address address = wallet.getAddressOfKey(key);

            System.out.println("New address generated");
            System.out.println(address.toString());
            wallet.importKey(key);
            incomingAddresses.add(i, new BIP47Address(address.toString(), i));
        }

        currentIncomingIndex = LOOKAHEAD - 1;
    }

    public void generateKeys(SlpBIP47AppKit wallet) throws NotSecp256k1Exception, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        for (int i = 0; i < LOOKAHEAD; i++) {
            ECKey key = getReceiveAddress(wallet, paymentCode, i).getReceiveECKey();
            Address address = wallet.getAddressOfKey(key);

            System.out.println("New address generated");
            System.out.println(address.toString());
            wallet.importKey(key);
            incomingAddresses.add(i, new BIP47Address(address.toString(), i));
        }

        currentIncomingIndex = LOOKAHEAD - 1;
    }

    public BIP47Address getIncomingAddress(String address) {
        for (BIP47Address bip47Address : incomingAddresses) {
            if (bip47Address.getAddress().equals(address)) {
                return bip47Address;
            }
        }
        return null;
    }

    public void addNewIncomingAddress(String newAddress, int nextIndex) {
        incomingAddresses.add(nextIndex, new BIP47Address(newAddress, nextIndex));
        currentIncomingIndex = nextIndex;
    }

    public boolean isNotificationTransactionSent() {
        return status == STATUS_SENT_CFM;
    }

    public void setStatusSent() {
        status = STATUS_SENT_CFM;
    }

    public int getCurrentOutgoingIndex() {
        return currentOutgoingIndex;
    }

    public void incrementOutgoingIndex() {
        currentOutgoingIndex++;
    }

    public void setStatusNotSent() {
        status = STATUS_NOT_SENT;
    }

    public Sha256Hash getNtxHash() {
        return ntxHash;
    }

    public void setNtxHash(Sha256Hash ntxHash) {
        this.ntxHash = ntxHash;
    }
}
