/*
 * Copyright 2018 the bitcoinj-cash developers
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
package org.bitcoincashj.core;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.bitcoincashj.core.slp.SlpAddress;
import org.bitcoincashj.params.Networks;

import javax.annotation.Nullable;

import static org.bitcoincashj.core.Address.isAcceptableVersion;
import static org.bitcoincashj.core.CashAddressHelper.ConvertBits;
import static org.bitcoincashj.core.SlpAddressHelper.convertBits;

/**
 * This is a factory class that creates CashAddress objects from several types of inputs.
 */
public class SlpAddressFactory {

    public static SlpAddressFactory create() {
        return new SlpAddressFactory();
    }

    public SlpAddress getFromCashAddress(NetworkParameters params, String address) {
        String addressPrefix = CashAddressHelper.getPrefix(address);
        if (params != null) {
            if (addressPrefix != null && !isAcceptableCashAddrPrefix(params, addressPrefix)) {
                throw new WrongNetworkException(addressPrefix, params.getCashAddrPrefix());
            }
        } else {
            for (NetworkParameters p : Networks.get()) {
                if (isAcceptableCashAddrPrefix(p, addressPrefix)) {
                    params = p;
                    break;
                }
            }
            if (params == null) {
                throw new AddressFormatException("No network found for " + addressPrefix);
            }
        }
        CashAddressValidator cashAddressValidator = CashAddressValidator.create();

        ImmutablePair<String, byte[]> pair = CashAddressHelper.decodeCashAddress(address, params.getCashAddrPrefix());
        String prefix = pair.getKey();
        byte[] payload = pair.getValue();

        cashAddressValidator.checkValidPrefix(params, prefix);
        cashAddressValidator.checkNonEmptyPayload(payload);

        byte extraBits = (byte) (payload.length * 5 % 8);
        cashAddressValidator.checkAllowedPadding(extraBits);

        byte last = payload[payload.length - 1];
        byte mask = (byte) ((1 << extraBits) - 1);
        cashAddressValidator.checkNonZeroPadding(last, mask);

        byte[] data = new byte[payload.length * 5 / 8];
        ConvertBits(data, payload, 5, 8, false);

        byte versionByte = data[0];
        cashAddressValidator.checkFirstBitIsZero(versionByte);

        int hashSize = calculateHashSizeFromVersionByte(versionByte);
        cashAddressValidator.checkDataLength(data, hashSize);

        byte[] result = new byte[data.length - 1];
        System.arraycopy(data, 1, result, 0, data.length - 1);
        SlpAddress.CashAddressType type = getAddressTypeFromVersionByte(versionByte);

        return new SlpAddress(params, type, result);
    }

    public SlpAddress fromP2PKHHash(NetworkParameters params, byte[] hash160) {
        CashAddress cashAddress = CashAddress.fromP2PKHHash(params, hash160);
        return getFromCashAddress(params, cashAddress.toString());
    }

    public SlpAddress fromP2SHHash(NetworkParameters params, byte[] hash160) {
        CashAddress cashAddress = CashAddress.fromP2SHHash(params, hash160);
        return getFromCashAddress(params, cashAddress.toString());
    }

    /**
     * Construct an address from its Base58 representation.
     *
     * @param params The expected NetworkParameters or null if you don't want validation.
     * @param base58 The textual form of the address, such as "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL".
     * @throws AddressFormatException if the given base58 doesn't parse or the checksum is invalid
     * @throws WrongNetworkException  if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public SlpAddress getFromBase58(@Nullable NetworkParameters params, String base58)
            throws AddressFormatException {
        VersionedChecksummedBytes parsed = new VersionedChecksummedBytes(base58);
        NetworkParameters addressParams = null;
        if (params != null) {
            if (!isAcceptableVersion(params, parsed.version)) {
                throw new WrongNetworkException(parsed.version, params.getAcceptableAddressCodes());
            }
            addressParams = params;
        } else {
            for (NetworkParameters p : Networks.get()) {
                if (isAcceptableVersion(p, parsed.version)) {
                    addressParams = p;
                    break;
                }
            }
            if (addressParams == null) {
                throw new AddressFormatException("No network found for " + base58);
            }
        }
        return new SlpAddress(addressParams, parsed.version, parsed.bytes);
    }

    /**
     * Construct an address from its cashaddr representation.
     *
     * @param params The expected NetworkParameters or null if you don't want validation.
     * @param addr   The textual form of the address, such as "bitcoincash:qpk4hk3wuxe2uqtqc97n8atzrrr6r5mleczf9sur4h".
     * @throws AddressFormatException if the given base58 doesn't parse or the checksum is invalid
     * @throws WrongNetworkException  if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public SlpAddress getFromFormattedAddress(@Nullable NetworkParameters params, String addr)
            throws AddressFormatException {
        String addressPrefix = CashAddressHelper.getPrefix(addr);
        if (params != null) {
            if (addressPrefix != null && !isAcceptablePrefix(params, addressPrefix)) {
                throw new WrongNetworkException(addressPrefix, params.getCashAddrPrefix());
            }
        } else {
            for (NetworkParameters p : Networks.get()) {
                if (isAcceptablePrefix(p, addressPrefix)) {
                    params = p;
                    break;
                }
            }
            if (params == null) {
                throw new AddressFormatException("No network found for " + addressPrefix);
            }
        }
        SlpAddressValidator slpAddressValidator = SlpAddressValidator.create();

        ImmutablePair<String, byte[]> pair = SlpAddressHelper.decodeCashAddress(addr, params.getSimpleledgerPrefix());
        String prefix = pair.getKey();
        byte[] payload = pair.getValue();

        slpAddressValidator.checkValidPrefix(params, prefix);
        slpAddressValidator.checkNonEmptyPayload(payload);

        byte extraBits = (byte) (payload.length * 5 % 8);
        slpAddressValidator.checkAllowedPadding(extraBits);

        byte last = payload[payload.length - 1];
        byte mask = (byte) ((1 << extraBits) - 1);
        slpAddressValidator.checkNonZeroPadding(last, mask);

        byte[] data = new byte[payload.length * 5 / 8];
        convertBits(data, payload, 5, 8, false);

        byte versionByte = data[0];
        slpAddressValidator.checkFirstBitIsZero(versionByte);

        int hashSize = calculateHashSizeFromVersionByte(versionByte);
        slpAddressValidator.checkDataLength(data, hashSize);

        byte[] result = new byte[data.length - 1];
        System.arraycopy(data, 1, result, 0, data.length - 1);
        SlpAddress.CashAddressType type = getAddressTypeFromVersionByte(versionByte);

        return new SlpAddress(params, type, result);
    }

    private SlpAddress.CashAddressType getAddressTypeFromVersionByte(byte versionByte)
            throws AddressFormatException {
        switch (versionByte >> 3 & 0x1f) {
            case 0:
                return SlpAddress.CashAddressType.PubKey;
            case 1:
                return SlpAddress.CashAddressType.Script;
            default:
                throw new AddressFormatException("Unknown Type");
        }
    }

    private static int calculateHashSizeFromVersionByte(byte version) {
        int hash_size = 20 + 4 * (version & 0x03);
        if ((version & 0x04) != 0) {
            hash_size *= 2;
        }
        return hash_size;
    }

    private boolean isAcceptablePrefix(NetworkParameters params, String prefix) {
        return params.getSimpleledgerPrefix().equals(prefix.toLowerCase());
    }

    private boolean isAcceptableCashAddrPrefix(NetworkParameters params, String prefix) {
        return params.getCashAddrPrefix().equals(prefix.toLowerCase());
    }
}
