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

package org.bitcoincashj.core;

import org.bitcoincashj.core.bip47.BIP47PaymentCode;
import org.bitcoincashj.core.slp.SlpAddress;
import org.bitcoincashj.params.Networks;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.Script.ScriptType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>
 * Base class for addresses, e.g. cash address ({@link CashAddress}) or legacy addresses ({@link Address}).
 * </p>
 */
public class Address extends VersionedChecksummedBytes {
    /**
     * An address is a RIPEMD160 hash of a public key, therefore is always 160 bits or 20 bytes.
     */
    public static final int LENGTH = 20;

    private transient NetworkParameters params;

    /**
     * Construct an address from parameters, the address version, and the hash160 form. Example:<p>
     *
     * <pre>new Address(MainNetParams.get(), NetworkParameters.getAddressHeader(), Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));</pre>
     */
    public Address(NetworkParameters params, int version, byte[] hash160) throws WrongNetworkException {
        super(version, hash160);
        checkNotNull(params);
        checkArgument(hash160.length == 20, "Addresses are 160-bit hashes, so you must provide 20 bytes");
        if (!isAcceptableVersion(params, version))
            throw new WrongNetworkException(version, params.getAcceptableAddressCodes());
        this.params = params;
    }

    /**
     * Returns an Address that represents the given P2SH script hash.
     */
    public static Address fromP2PKHHash(NetworkParameters params, byte[] hash160) {
        try {
            return new Address(params, params.getAddressHeader(), hash160);
        } catch (WrongNetworkException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    /**
     * Returns an Address that represents the given P2SH script hash.
     */
    public static Address fromP2SHHash(NetworkParameters params, byte[] hash160) {
        try {
            return new Address(params, params.getP2SHHeader(), hash160);
        } catch (WrongNetworkException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    /**
     * Returns an Address that represents the script hash extracted from the given scriptPubKey
     */
    public static Address fromP2SHScript(NetworkParameters params, Script scriptPubKey) {
        checkArgument(scriptPubKey.isPayToScriptHash(), "Not a P2SH script");
        return fromP2SHHash(params, scriptPubKey.getPubKeyHash());
    }

    /**
     * Construct an address from its Base58 representation.
     *
     * @param params The expected NetworkParameters or null if you don't want validation.
     * @param base58 The textual form of the address, such as "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL".
     * @throws AddressFormatException if the given base58 doesn't parse or the checksum is invalid
     * @throws WrongNetworkException  if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public static Address fromBase58(@Nullable NetworkParameters params, String base58) throws AddressFormatException {
        return new Address(params, base58);
    }

    /**
     * Construct a {@link Address} that represents the public part of the given {@link ECKey}. Note that an address is
     * derived from a hash of the public key and is not the public key itself.
     *
     * @param params network this address is valid for
     * @param key    only the public part is used
     * @return constructed address
     */
    public static Address fromKey(NetworkParameters params, ECKey key) {
        return fromP2PKHHash(params, key.getPubKeyHash());
    }

    /**
     * @param params        The expected NetworkParameters to validate the address against.
     * @param legacyAddress The Bitcoin Cash legacy address. Starts with a "1"
     * @return Whether the address is valid or not.
     */
    @Deprecated
    public static boolean isValidLegacyAddress(NetworkParameters params, String legacyAddress) {
        try {
            fromBase58(params, legacyAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidSlpAddress(NetworkParameters params, String slpAddress) {
        try {
            SlpAddressFactory.create().getFromFormattedAddress(params, slpAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidCashAddr(NetworkParameters params, String cashAddress) {
        try {
            CashAddressFactory.create().getFromFormattedAddress(params, cashAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidPaymentCode(String paymentCode) {
        try {
            new BIP47PaymentCode(paymentCode);
            return true;
        } catch (AddressFormatException | NullPointerException e) {
            return false;
        }
    }

    public static boolean isValidPaymentCode(byte[] paymentCodeBytes) {
        try {
            BIP47PaymentCode paymentCode = new BIP47PaymentCode(paymentCodeBytes);
            new BIP47PaymentCode(paymentCode.toString());
            return true;
        } catch (AddressFormatException | NullPointerException e) {
            return false;
        }
    }

    /**
     * Construct an address from parameters and the hash160 form. Example:<p>
     *
     * <pre>new Address(MainNetParams.get(), Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));</pre>
     */
    public Address(NetworkParameters params, byte[] hash160) {
        super(params.getAddressHeader(), hash160);
        checkArgument(hash160.length == 20, "Addresses are 160-bit hashes, so you must provide 20 bytes");
        this.params = params;
    }

    /**
     * @deprecated Use {@link #fromBase58(NetworkParameters, String)}
     */
    @Deprecated
    public Address(@Nullable NetworkParameters params, String address) throws AddressFormatException {
        super(address);
        if (params != null) {
            if (!isAcceptableVersion(params, version)) {
                throw new WrongNetworkException(version, params.getAcceptableAddressCodes());
            }
            this.params = params;
        } else {
            NetworkParameters paramsFound = null;
            for (NetworkParameters p : Networks.get()) {
                if (isAcceptableVersion(p, version)) {
                    paramsFound = p;
                    break;
                }
            }
            if (paramsFound == null)
                throw new AddressFormatException("No network found for " + address);

            this.params = paramsFound;
        }
    }

    /**
     * The (big endian) 20 byte hash that is the core of a Bitcoin address.
     */
    public byte[] getHash160() {
        return bytes;
    }

    /**
     * Returns true if this address is a Pay-To-Script-Hash (P2SH) address.
     * See also https://github.com/bitcoin/bips/blob/master/bip-0013.mediawiki: Address Format for pay-to-script-hash
     */
    public boolean isP2SHAddress() {
        final NetworkParameters parameters = getParameters();
        return parameters != null && this.version == parameters.p2shHeader;
    }

    public ScriptType getOutputScriptType() {
        return isP2SHAddress() ? ScriptType.P2SH : ScriptType.P2PKH;
    }

    public CashAddress toCash() {
        String cashAddress = CashAddressHelper.encodeCashAddress(getParameters().getCashAddrPrefix(),
                CashAddressHelper.packAddressData(getHash160(), isP2SHAddress() ? CashAddress.CashAddressType.Script.getValue() : CashAddress.CashAddressType.PubKey.getValue()));
        return CashAddressFactory.create().getFromFormattedAddress(getParameters(), cashAddress);
    }

    public SlpAddress toSlp() {
        String slpAddress = SlpAddressHelper.encodeCashAddress(getParameters().getSimpleledgerPrefix(),
                SlpAddressHelper.packAddressData(getHash160(), isP2SHAddress() ? SlpAddress.CashAddressType.Script.getValue() : SlpAddress.CashAddressType.PubKey.getValue()));
        return SlpAddressFactory.create().getFromFormattedAddress(getParameters(), slpAddress);
    }

    /**
     * Examines the version byte of the address and attempts to find a matching NetworkParameters. If you aren't sure
     * which network the address is intended for (eg, it was provided by a user), you can use this to decide if it is
     * compatible with the current wallet. You should be able to handle a null response from this method. Note that the
     * parameters returned is not necessarily the same as the one the Address was created with.
     *
     * @return a NetworkParameters representing the network the address is intended for.
     */
    public NetworkParameters getParameters() {
        return params;
    }

    /**
     * Given an address, examines the version byte and attempts to find a matching NetworkParameters. If you aren't sure
     * which network the address is intended for (eg, it was provided by a user), you can use this to decide if it is
     * compatible with the current wallet.
     *
     * @return a NetworkParameters of the address
     * @throws AddressFormatException if the string wasn't of a known version
     */
    public static NetworkParameters getParametersFromAddress(String address) throws AddressFormatException {
        try {
            return Address.fromBase58(null, address).getParameters();
        } catch (WrongNetworkException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    /**
     * Check if a given address version is valid given the NetworkParameters.
     */
    public static boolean isAcceptableVersion(NetworkParameters params, int version) {
        for (int v : params.getAcceptableAddressCodes()) {
            if (version == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * This implementation narrows the return type to <code>Address</code>.
     */
    @Override
    public Address clone() throws CloneNotSupportedException {
        return (Address) super.clone();
    }

    // Java serialization

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(params.id);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        params = NetworkParameters.fromID(in.readUTF());
    }
}
