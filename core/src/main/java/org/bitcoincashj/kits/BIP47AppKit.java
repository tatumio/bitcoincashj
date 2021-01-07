/*
 * Copyright 2013 Google Inc.
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

package org.bitcoincashj.kits;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoincashj.core.*;
import org.bitcoincashj.core.bip47.BIP47Account;
import org.bitcoincashj.core.bip47.BIP47Address;
import org.bitcoincashj.core.bip47.BIP47Channel;
import org.bitcoincashj.core.bip47.BIP47PaymentCode;
import org.bitcoincashj.crypto.BIP47SecretPoint;
import org.bitcoincashj.crypto.ChildNumber;
import org.bitcoincashj.crypto.DeterministicKey;
import org.bitcoincashj.crypto.HDKeyDerivation;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptBuilder;
import org.bitcoincashj.script.ScriptException;
import org.bitcoincashj.script.ScriptPattern;
import org.bitcoincashj.store.BlockStoreException;
import org.bitcoincashj.store.SPVBlockStore;
import org.bitcoincashj.utils.BIP47Util;
import org.bitcoincashj.wallet.KeyChainGroupStructure;
import org.bitcoincashj.wallet.RedeemData;
import org.bitcoincashj.wallet.SendRequest;
import org.bitcoincashj.wallet.Wallet;
import org.bitcoincashj.wallet.bip47.listeners.BlockchainDownloadProgressTracker;
import org.bitcoincashj.wallet.bip47.listeners.TransactionEventListener;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoincashj.utils.BIP47Util.getReceiveAddress;
import static org.bitcoincashj.utils.BIP47Util.getSendAddress;

/**
 * <p>Utility class that wraps the boilerplate needed to set up a new SPV bitcoinj app. Instantiate it with a directory
 * and file prefix, optionally configure a few things, then use startAsync and optionally awaitRunning. The object will
 * construct and configure a {@link BlockChain}, {@link SPVBlockStore}, {@link Wallet} and {@link PeerGroup}. Depending
 * on the value of the blockingStartup property, startup will be considered complete once the block chain has fully
 * synchronized, so it can take a while.</p>
 *
 * <p>To add listeners and modify the objects that are constructed, you can either do that by overriding the
 * {@link #onSetupCompleted()} method (which will run on a background thread) and make your changes there,
 * or by waiting for the service to start and then accessing the objects from wherever you want. However, you cannot
 * access the objects this class creates until startup is complete.</p>
 *
 * <p>The asynchronous design of this class may seem puzzling (just use {@link #awaitRunning()} if you don't want that).
 * It is to make it easier to fit bitcoinj into GUI apps, which require a high degree of responsiveness on their main
 * thread which handles all the animation and user interaction. Even when blockingStart is false, initializing bitcoinj
 * means doing potentially blocking file IO, generating keys and other potentially intensive operations. By running it
 * on a background thread, there's no risk of accidentally causing UI lag.</p>
 *
 * <p>Note that {@link #awaitRunning()} can throw an unchecked {@link IllegalStateException}
 * if anything goes wrong during startup - you should probably handle it and use {@link Exception#getCause()} to figure
 * out what went wrong more precisely. Same thing if you just use the {@link #startAsync()} method.</p>
 */
public class BIP47AppKit extends WalletKitCore {
    // Support for BIP47-type accounts. Only one account is currently handled in this wallet.
    protected List<BIP47Account> mAccounts = new ArrayList<BIP47Account>(1);

    // The progress tracker will callback the listener with a porcetage of the blockchain that it has downloaded, while downloading..
    private BlockchainDownloadProgressTracker mBlockchainDownloadProgressTracker;

    // This wallet allows one listener to be invoked when there are coins received and
    private TransactionEventListener mTransactionsEventListener = null;

    private boolean mBlockchainDownloadStarted = false;

    // The payment channels indexed by payment codes.
    // A payment channel is created and saved if:
    //   - someone sends a notification transaction to this wallet's notifiction address
    //   - this wallet creates a notification transaction to a payment channel.
    //
    // It doesn't check if the notification transactions are mined before adding a payment code.
    // If you want to know a transaction's confidence, see #{@link Transaction.getConfidence()}
    private ConcurrentHashMap<String, BIP47Channel> bip47MetaData = new ConcurrentHashMap<String, BIP47Channel>();
    private Runnable onReceiveRunnable;

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public BIP47AppKit(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), Script.ScriptType.P2PKH, null, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public BIP47AppKit(NetworkParameters params, Script.ScriptType preferredOutputScriptType,
                       @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this(new Context(params), preferredOutputScriptType, structure, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public BIP47AppKit(Context context, Script.ScriptType preferredOutputScriptType,
                       @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.preferredOutputScriptType = checkNotNull(preferredOutputScriptType);
        this.structure = structure != null ? structure : KeyChainGroupStructure.DEFAULT;
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        this.setAccount();
        this.loadBip47MetaData();
        Address notificationAddress = this.mAccounts.get(0).getNotificationAddress();
        System.out.println("BIP47AppKit notification address: " + notificationAddress.toString());

        if (!this.wallet().isAddressWatched(notificationAddress)) {
            this.wallet().addWatchedAddress(notificationAddress);
        }

        String notifAsCashAddr = notificationAddress.toString();
        this.grabNotificationAddressUtxos(notifAsCashAddr);
    }

    private void grabNotificationAddressUtxos(final String cashAddr) {
        new Thread() {
            @Override
            public void run() {
                ArrayList<String> txids = new ArrayList<String>();
                String apiUrl = "https://rest.bitcoin.com/v2/address/utxo/" + cashAddr;
                JSONObject utxosJson = getJSONObject(apiUrl);
                try {
                    JSONArray utxos = utxosJson.getJSONArray("utxos");
                    for (int x = 0; x < utxos.length(); x++) {
                        JSONObject utxo = utxos.getJSONObject(x);
                        String txid = utxo.getString("txid");
                        txids.add(txid);
                    }

                    for (String txid : txids) {
                        grabTransactionAndProcessNotificationTransaction(txid);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void grabTransactionAndProcessNotificationTransaction(String txid) {
        String url = "https://rest.bitcoin.com/v2/rawtransactions/getRawTransaction/" + txid + "?verbose=true";

        JSONObject txJson = getJSONObject(url);
        if (txJson != null) {
            try {
                String txHexVariable = "hex";
                String txHex = txJson.getString(txHexVariable);
                Transaction tx = new Transaction(this.params, Hex.decode(txHex));
                if (isNotificationTransaction(tx)) {
                    BIP47PaymentCode BIP47PaymentCode = getPaymentCodeInNotificationTransaction(tx);
                    if (BIP47PaymentCode != null) {
                        boolean needsSaving = savePaymentCode(BIP47PaymentCode);
                        if (needsSaving) {
                            saveBip47MetaData();
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private JSONObject getJSONObject(String url) {
        InputStream is = null;
        try {
            is = new URL(url).openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            String jsonText = "{}";
            BufferedReader rd = null;
            if (is != null) {
                rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                int cp;
                while ((cp = rd.read()) != -1) {
                    sb.append((char) cp);
                }
                jsonText = sb.toString();
            }
            return new JSONObject(jsonText);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    // BIP47-specific listener
    // When a new *notification* transaction is received:
    //  - new keys are generated and imported for incoming payments in the bip47 account/contact payment channel
    //  - the chain is rolled back 2 blocks so that payment transactions are not missed if in the same block as the notification transaction.
    //
    // When a new *payment* transaction is received:
    //  - a new key is generated and imported to the wallet
    private void addTransactionsListener(final Runnable runnable) {
        this.addTransactionEvenListener(new TransactionEventListener() {
            @Override
            public void onTransactionReceived(WalletKitCore bip47AppKit, Transaction transaction) {
                if (isNotificationTransaction(transaction)) {
                    BIP47PaymentCode BIP47PaymentCode = getPaymentCodeInNotificationTransaction(transaction);
                    if (BIP47PaymentCode != null) {
                        boolean needsSaving = savePaymentCode(BIP47PaymentCode);
                        if (needsSaving) {
                            try {
                                rescanTxBlock(transaction);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }
                            saveBip47MetaData();
                        }
                    }
                } else if (isToBIP47Address(transaction)) {
                    boolean needsSaving = generateNewBip47IncomingAddress(getAddressOfReceived(transaction).toString());
                    if (needsSaving) {
                        saveBip47MetaData();
                    }
                }

                if (runnable != null)
                    runnable.run();
            }

            @Override
            public void onTransactionSent(WalletKitCore wallet, Transaction transaction) {
                if (isNotificationTransactionTo(transaction)) {
                    String notificationAddress = getOutgoingNtxAddress(transaction);

                    if (notificationAddress != null) {
                        boolean needsSaving = saveOutgoingChannel(notificationAddress, transaction);
                        if (needsSaving) {
                            try {
                                rescanTxBlock(transaction);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }

                            saveBip47MetaData();
                        }
                    }
                }

                if (runnable != null)
                    runnable.run();
            }
        });
    }

    /**
     * <p>Create the account M/47'/0'/0' from the seed as a Bip47Account.</p>
     *
     * <p>After deriving, this wallet's payment code is available in @{link Bip47Wallet.getPaymentCode()}</p>
     */
    public void setAccount() {
        byte[] hd_seed = this.restoreFromSeed != null ?
                this.restoreFromSeed.getSeedBytes() :
                this.vWallet.getKeyChainSeed().getSeedBytes();


        DeterministicKey mKey = HDKeyDerivation.createMasterPrivateKey(hd_seed);
        DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(mKey, 47 | ChildNumber.HARDENED_BIT);
        DeterministicKey coinKey = HDKeyDerivation.deriveChildKey(purposeKey, ChildNumber.HARDENED_BIT);

        BIP47Account account = new BIP47Account(params, coinKey, 0);

        mAccounts.clear();
        mAccounts.add(account);
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public boolean loadBip47MetaData() {
        String jsonString = readBip47MetaDataFile();

        if (StringUtils.isEmpty(jsonString)) {
            return false;
        }

        return importBip47MetaData(jsonString);
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public String readBip47MetaDataFile() {
        File file = new File(directory, this.filePrefix.concat(".bip47"));
        String jsonString;
        try {
            jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e) {
            System.out.println("Creating BIP47 wallet file at " + file.getAbsolutePath() + "  ...");
            saveBip47MetaData();
            loadBip47MetaData();
            return null;
        }

        return jsonString;
    }

    /**
     * <p>Load channels from json. Return true if any payment code was loaded. </p>
     */
    public boolean importBip47MetaData(String jsonString) {
        Gson gson = new Gson();
        Type collectionType = new TypeToken<Collection<BIP47Channel>>() {
        }.getType();
        try {
            List<BIP47Channel> BIP47ChannelList = gson.fromJson(jsonString, collectionType);
            if (BIP47ChannelList != null) {
                for (BIP47Channel channel : BIP47ChannelList) {
                    if (channel.getNotificationAddress() == null && channel.getPaymentCode() != null) {
                        BIP47Account bip47Account = new BIP47Account(params(), channel.getPaymentCode());
                        channel.setNotificationAddress(bip47Account.getNotificationAddress().toString());
                    }
                    bip47MetaData.put(channel.getNotificationAddress(), channel);
                }
            }
        } catch (JsonSyntaxException e) {
            return true;
        }
        return false;
    }

    /**
     * <p>Persists the .bip47 file with the channels. </p>
     */
    public synchronized void saveBip47MetaData() {
        try {
            vWallet.saveToFile(vWalletFile);
        } catch (IOException io) {
            log.error("Failed to save wallet file", io);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(bip47MetaData.values());

        File file = new File(directory, this.filePrefix.concat(".bip47"));

        try {
            FileUtils.writeStringToFile(file, json, Charset.defaultCharset(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>A listener is added to be invoked when the wallet sees an incoming transaction. </p>
     */
    public void addTransactionEvenListener(TransactionEventListener transactionEventListener) {
        if (this.mTransactionsEventListener != null)
            vWallet.removeCoinsReceivedEventListener(mTransactionsEventListener);

        transactionEventListener.setWallet(this);
        vWallet.addCoinsReceivedEventListener(transactionEventListener);
        vWallet.addCoinsSentEventListener(transactionEventListener);
        mTransactionsEventListener = transactionEventListener;
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH) and compares it with the notification address of this wallet. </p>
     */
    public boolean isNotificationTransaction(Transaction tx) {
        Address address = getAddressOfReceived(tx);
        Address myNotificationAddress = mAccounts.get(0).getNotificationAddress();
        return address != null && address.toString().equals(myNotificationAddress.toString());
    }

    public boolean isNotificationTransactionTo(Transaction tx) {
        if (tx.getValue(wallet()).isNegative()) {
            for (TransactionOutput utxo : tx.getOutputs()) {
                if (ScriptPattern.isOpReturn(utxo.getScriptPubKey()) && BIP47Util.isValidNotificationTransactionOpReturn(utxo)) {
                    return true;
                }
            }
        }

        return false;
    }

    public String getOutgoingNtxAddress(Transaction ntx) {
        if (isNotificationTransactionTo(ntx)) {
            for (TransactionOutput utxo : ntx.getOutputs()) {
                if (!utxo.isMine(wallet()) && !ScriptPattern.isOpReturn(utxo.getScriptPubKey()) && utxo.getValue().value == 546L) {
                    return Objects.requireNonNull(utxo.getAddressFromP2PKHScript(params())).toString();
                }
            }
        }
        return null;
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH), return true if any key in this wallet translates to it. </p>
     */
    // TODO: return true if and only if it is a channel address.
    public boolean isToBIP47Address(Transaction transaction) {
        List<ECKey> keys = vWallet.getImportedKeys();
        for (ECKey key : keys) {
            Address address = key.toAddress(params());
            if (address == null) {
                continue;
            }
            Address addressOfReceived = getAddressOfReceived(transaction);
            if (addressOfReceived != null && address.toString().equals(addressOfReceived.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the address that received the transaction (P2PKH or P2PSH output)
     */
    public Address getAddressOfReceived(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                boolean isMineOrWatched = output.isMineOrWatched(vWallet);
                Address address = output.getScriptPubKey().getToAddress(params, true);
                String cashAddress = address.toCash().toString();
                String notifCashAddress = getAccount(0).getNotificationAddress().toCash().toString();
                if (isMineOrWatched) {
                    if(cashAddress.equals(notifCashAddress)) {
                        return address;
                    }
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    /**
     * Given a notification transaction, extracts a valid payment code
     */
    public BIP47PaymentCode getPaymentCodeInNotificationTransaction(Transaction tx) {
        byte[] privKeyBytes = mAccounts.get(0).getNotificationKey().getPrivKeyBytes();

        return BIP47Util.getPaymentCodeInNotificationTransaction(privKeyBytes, tx);
    }

    // <p> Receives a payment code and returns true iff there is already an incoming address generated for the channel</p>
    public boolean savePaymentCode(BIP47PaymentCode bip47PaymentCode) {
        boolean save = true;
        BIP47Account bip47Account = new BIP47Account(params(), bip47PaymentCode.toString());
        String notificationAddress = bip47Account.getNotificationAddress().toString();
        for (BIP47Channel channel : bip47MetaData.values()) {
            if (channel.getNotificationAddress().equals(notificationAddress) && channel.getPaymentCode() != null && channel.getIncomingAddresses().size() != 0) {
                save = false;
                break;
            }
        }

        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if (bip47Channel.getIncomingAddresses().size() != 0) {
                save = false;
            } else {
                try {
                    if (bip47Channel.getPaymentCode() == null)
                        bip47Channel.setPaymentCode(bip47PaymentCode.toString());

                    bip47Channel.generateKeys(this);
                    save = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    save = false;
                }
            }
        }

        if (save) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if (bip47Channel == null) {
                bip47Channel = new BIP47Channel(bip47PaymentCode.toString());
            } else {
                if (bip47Channel.getPaymentCode() == null)
                    bip47Channel.setPaymentCode(bip47PaymentCode.toString());
            }

            try {
                bip47Channel.generateKeys(this);
                bip47MetaData.put(notificationAddress, bip47Channel);
            } catch (Exception e) {
                e.printStackTrace();
                save = false;
            }
        }

        return save;
    }

    public boolean saveOutgoingChannel(String notificationAddress, Transaction ntx) {
        boolean save = true;
        for (BIP47Channel channel : bip47MetaData.values()) {
            if (channel.getNotificationAddress().equals(notificationAddress) && channel.isNotificationTransactionSent()) {
                save = false;
                break;
            }
        }

        if (save) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if (bip47Channel == null) {
                bip47Channel = new BIP47Channel(notificationAddress, ntx.getTxId());
            } else {
                bip47Channel.setNtxHash(ntx.getTxId());
            }
            bip47Channel.setStatusSent();

            try {
                bip47MetaData.put(notificationAddress, bip47Channel);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        return save;
    }

    public void rescanTxBlock(Transaction tx) throws BlockStoreException {
        try {
            if (tx.getConfidence().getAppearedAtChainHeight() - 2 > this.vChain.getBestChainHeight()) {
                System.out.println("Transaction is from block " + tx.getConfidence().getAppearedAtChainHeight() + " which is above our local chain height " + this.vChain.getBestChainHeight());
            } else {
                int blockHeight = tx.getConfidence().getAppearedAtChainHeight() - 2;
                this.vChain.rollbackBlockStore(blockHeight);
            }
        } catch (IllegalStateException e) {
            //fail silently, we dont need to rollback as it works when txs are in mempool
        }
    }

    public BIP47Account getAccount(int i) {
        return mAccounts.get(i);
    }

    public Address getAddressOfKey(ECKey key) {
        return key.toAddress(params());
    }

    public void importKey(ECKey key) {
        vWallet.importKey(key);
    }

    /**
     * Return true if this is the first time the address is seen used
     */
    public boolean generateNewBip47IncomingAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (!bip47Address.getAddress().equals(address)) {
                    continue;
                }
                if (bip47Address.isSeen()) {
                    return false;
                }

                int nextIndex = BIP47Channel.getCurrentIncomingIndex() + 1;
                try {
                    ECKey key = getReceiveAddress(this, BIP47Channel.getPaymentCode(), nextIndex).getReceiveECKey();
                    vWallet.importKey(key);
                    Address newAddress = getAddressOfKey(key);
                    BIP47Channel.addNewIncomingAddress(newAddress.toString(), nextIndex);
                    bip47Address.setSeen(true);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        return false;
    }

    public BIP47Channel getBip47MetaForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel;
                }
            }
        }
        return null;
    }

    public String getPaymentCodeForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel.getPaymentCode();
                }
            }
        }
        return null;
    }

    public BIP47Channel getBip47MetaForPaymentCode(String paymentCode) {
        BIP47Account bip47Account = new BIP47Account(params(), paymentCode);
        return getBip47MetaForNotificationAddress(bip47Account.getNotificationAddress().toString());
    }

    public BIP47Channel getBip47MetaForNotificationAddress(String notificationAddress) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            if (BIP47Channel.getNotificationAddress().equals(notificationAddress)) {
                return BIP47Channel;
            }
        }
        return null;
    }

    /*If true, it means we have the ntx stored, and can freely get a receiving address.
    If false, it means we need to create a ntx.*/
    public boolean canSendToPaymentCode(String paymentCode) {
        if (Address.isValidPaymentCode(paymentCode)) {
            BIP47Account bip47Account = new BIP47Account(params(), paymentCode);
            String notificationAddress = bip47Account.getNotificationAddress().toString();
            BIP47Channel bip47Channel = getBip47MetaForNotificationAddress(notificationAddress);
            if (bip47Channel != null && bip47Channel.getPaymentCode() == null) {
                bip47Channel.setPaymentCode(paymentCode);
                saveBip47MetaData();
            }

            if (bip47Channel != null) {
                return bip47Channel.isNotificationTransactionSent();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public String getPaymentCode() {
        return getAccount(0).getStringPaymentCode();
    }

    private static Coin getDefaultFee(NetworkParameters params) {
        return Transaction.DEFAULT_TX_FEE;
    }

    public Transaction createSend(Address address, long amount) throws InsufficientMoneyException {
        SendRequest sendRequest = SendRequest.to(this.params(), address.toString(), Coin.valueOf(amount));

        sendRequest.feePerKb = getDefaultFee(params());

        vWallet.completeTx(sendRequest);
        return sendRequest.tx;
    }

    public SendRequest makeNotificationTransaction(String paymentCode, boolean allowUnconfirmedSpends) throws InsufficientMoneyException {
        BIP47Account toAccount = new BIP47Account(params(), paymentCode);
        Coin ntValue = params().getMinNonDustOutput();
        Address ntAddress = toAccount.getNotificationAddress();

        System.out.println("Balance: " + vWallet.getBalance());
        System.out.println("To notification address: " + ntAddress.toString());
        System.out.println("Value: " + ntValue.toFriendlyString());

        SendRequest sendRequest = SendRequest.to(this.params(), ntAddress.toString(), ntValue);

        if (allowUnconfirmedSpends)
            sendRequest.allowUnconfirmed();

        sendRequest.feePerKb = Coin.valueOf(1000L);
        sendRequest.memo = "notification_transaction";

        org.bitcoincashj.utils.BIP47Util.FeeCalculation feeCalculation = BIP47Util.calculateFee(vWallet, sendRequest, ntValue, vWallet.calculateAllSpendCandidates());

        for (TransactionOutput output : feeCalculation.bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }

        if (sendRequest.tx.getInputs().size() > 0) {
            TransactionInput txIn = sendRequest.tx.getInput(0);
            RedeemData redeemData = txIn.getConnectedRedeemData(vWallet);
            checkNotNull(redeemData, "StashTransaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            System.out.println("Keys: " + redeemData.keys.size());
            System.out.println("Private key 0?: " + redeemData.keys.get(0).hasPrivKey());
            byte[] privKey = redeemData.getFullKey().getPrivKeyBytes();
            System.out.println("Private key: " + Utils.HEX.encode(privKey));
            byte[] pubKey = toAccount.getNotificationKey().getPubKey();
            System.out.println("Public Key: " + Utils.HEX.encode(pubKey));
            byte[] outpoint = txIn.getOutpoint().bitcoinSerialize();

            byte[] mask = null;
            try {
                BIP47SecretPoint BIP47SecretPoint = new BIP47SecretPoint(privKey, pubKey);
                System.out.println("Secret Point: " + Utils.HEX.encode(BIP47SecretPoint.ECDHSecretAsBytes()));
                System.out.println("Outpoint: " + Utils.HEX.encode(outpoint));
                mask = BIP47PaymentCode.getMask(BIP47SecretPoint.ECDHSecretAsBytes(), outpoint);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("My payment code: " + mAccounts.get(0).getPaymentCode().toString());
            System.out.println("Mask: " + Utils.HEX.encode(mask));
            byte[] op_return = BIP47PaymentCode.blind(mAccounts.get(0).getPaymentCode().getPayload(), mask);

            sendRequest.tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(op_return));
        }

        vWallet.completeTx(sendRequest);

        System.out.println("Completed SendRequest");

        sendRequest.tx.verify();

        return sendRequest;
    }

    public Transaction getSignedNotificationTransaction(SendRequest sendRequest, String paymentCode) {
        //BIP47Account toAccount = new BIP47Account(getParams(), paymentCode);

        // notification address pub key
        //BIP47Util.signTransaction(vWallet, sendRequest, toAccount.getNotificationKey().getPubKey(), mAccounts.get(0).getPaymentCode());

        vWallet.commitTx(sendRequest.tx);

        return sendRequest.tx;
    }

    public ListenableFuture<Transaction> broadcastTransaction(Transaction transactionToSend) {
        vWallet.commitTx(transactionToSend);
        return vPeerGroup.broadcastTransaction(transactionToSend).future();
    }

    public boolean putBip47Meta(String notificationAddress, String paymentCode, @Nullable Transaction ntx) {
        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel BIP47Channel = bip47MetaData.get(notificationAddress);
            if (ntx != null)
                BIP47Channel.setNtxHash(ntx.getHash());
        } else {
            bip47MetaData.put(notificationAddress, new BIP47Channel(paymentCode));
            if (ntx != null)
                bip47MetaData.get(notificationAddress).setNtxHash(ntx.getHash());
            return true;
        }
        return false;
    }

    /* Mark a channel's notification transaction as sent*/
    public void putPaymenCodeStatusSent(String paymentCode, Transaction ntx) {
        BIP47Account bip47Account = new BIP47Account(params(), paymentCode);
        String notificationAddress = bip47Account.getNotificationAddress().toString();
        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            bip47Channel.setNtxHash(ntx.getTxId());
            bip47Channel.setStatusSent();
        } else {
            putBip47Meta(notificationAddress, paymentCode, ntx);
            putPaymenCodeStatusSent(paymentCode, ntx);
        }
    }

    /* Return the next address to send a payment to */
    public String getCurrentOutgoingAddress(BIP47Channel bip47Channel) {
        try {
            ECKey key = getSendAddress(this, new BIP47PaymentCode(bip47Channel.getPaymentCode()), bip47Channel.getCurrentOutgoingIndex()).getSendECKey();
            return key.toAddress(params()).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setOnReceiveTxRunnable(Runnable runnable) {
        this.onReceiveRunnable = runnable;
    }
}
