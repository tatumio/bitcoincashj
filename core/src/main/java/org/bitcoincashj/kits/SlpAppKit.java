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

import org.bitcoincashj.core.*;
import org.bitcoincashj.core.slp.*;
import org.bitcoincashj.net.SlpDbProcessor;
import org.bitcoincashj.net.SlpDbTokenDetails;
import org.bitcoincashj.net.SlpDbValidTransaction;
import org.bitcoincashj.protocols.payments.slp.SlpPaymentSession;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.store.SPVBlockStore;
import org.bitcoincashj.wallet.KeyChainGroupStructure;
import org.bitcoincashj.wallet.SendRequest;
import org.bitcoincashj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

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
public class SlpAppKit extends WalletKitCore {
    private File tokensFile;
    private long MIN_DUST = 546L;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();
    private ArrayList<SlpToken> slpTokens = new ArrayList<>();
    private ArrayList<SlpTokenBalance> slpBalances = new ArrayList<>();
    private ArrayList<String> verifiedSlpTxs = new ArrayList<>();
    private SlpDbProcessor slpDbProcessor;
    private boolean recalculatingTokens = false;

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), Script.ScriptType.P2PKH, null, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(NetworkParameters params, Script.ScriptType preferredOutputScriptType,
                     @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this(new Context(params), preferredOutputScriptType, structure, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(Context context, Script.ScriptType preferredOutputScriptType,
                     @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.preferredOutputScriptType = checkNotNull(preferredOutputScriptType);
        this.structure = structure != null ? structure : KeyChainGroupStructure.SLP;
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        File txsDataFile = new File(this.directory(), this.filePrefix + ".txs");
        if (txsDataFile.exists()) {
            this.loadRecordedTxs();
        }
        File tokenDataFile = new File(this.directory(), this.filePrefix + ".tokens");
        this.tokensFile = tokenDataFile;
        if (tokenDataFile.exists()) {
            this.loadTokens();
        }

        this.slpDbProcessor = new SlpDbProcessor();
    }

    private void saveTokens(ArrayList<SlpToken> slpTokens) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.directory(), tokensFile.getName())), StandardCharsets.UTF_8))) {
            JSONArray json = new JSONArray();
            for (SlpToken slpToken : slpTokens) {
                JSONObject tokenObj = new JSONObject();
                tokenObj.put("tokenId", slpToken.getTokenId());
                tokenObj.put("ticker", slpToken.getTicker());
                tokenObj.put("decimals", slpToken.getDecimals());
                json.put(tokenObj);
            }
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadTokens() {
        BufferedReader br = null;
        try {
            FileInputStream is = new FileInputStream(new File(this.directory(), this.tokensFile.getName()));
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String jsonString = sb.toString();

            try {
                JSONArray tokensJson = new JSONArray(jsonString);
                for (int x = 0; x < tokensJson.length(); x++) {
                    JSONObject tokenObj = tokensJson.getJSONObject(x);
                    String tokenId = tokenObj.getString("tokenId");
                    String ticker = tokenObj.getString("ticker");
                    int decimals = tokenObj.getInt("decimals");
                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    if (!this.tokenIsMapped(tokenId)) {
                        this.slpTokens.add(slpToken);
                    }
                }
            } catch (Exception e) {
                this.slpTokens = new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveVerifiedTxs(ArrayList<String> recordedSlpTxs) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.directory(), this.filePrefix + ".txs")), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            for (String txHash : recordedSlpTxs) {
                text.append(txHash).append("\n");
            }
            writer.write(text.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadRecordedTxs() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(new File(this.directory(), this.filePrefix + ".txs")));
            String line = br.readLine();
            while (line != null) {
                String txHash = line;
                this.verifiedSlpTxs.add(txHash);
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ArrayList<SlpTokenBalance> getSlpBalances() {
        return this.slpBalances;
    }

    public ArrayList<SlpToken> getSlpTokens() {
        return this.slpTokens;
    }

    public ArrayList<SlpUTXO> getSlpUtxos() {
        return this.slpUtxos;
    }

    public SlpAddress currentSlpReceiveAddress() {
        return this.wallet().currentReceiveAddress().toSlp();
    }

    public SlpAddress currentSlpChangeAddress() {
        return this.wallet().currentChangeAddress().toSlp();
    }

    public SlpAddress freshSlpReceiveAddress() {
        return this.wallet().freshReceiveAddress().toSlp();
    }

    public SlpAddress freshSlpChangeAddress() {
        return this.wallet().freshChangeAddress().toSlp();
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        return this.createSlpTransaction(slpDestinationAddress, tokenId, numTokens, aesKey, true);
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTx(tokenId, numTokens, slpDestinationAddress, this, aesKey, allowUnconfirmed).blockingGet();
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession) throws InsufficientMoneyException {
        return this.createSlpTransactionBip70(tokenId, aesKey, rawTokens, addresses, paymentSession, true);
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTxBip70(tokenId, this, aesKey, rawTokens, addresses, paymentSession, allowUnconfirmed).blockingGet();
    }

    public Transaction createSlpGenesisTransaction(String ticker, String name, String url, int decimals, long tokenQuantity, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        SendRequest req = SendRequest.createSlpTransaction(this.params());
        req.aesKey = aesKey;
        req.shuffleOutputs = false;
        req.feePerKb = Coin.valueOf(1000L);
        SlpOpReturnOutputGenesis slpOpReturn = new SlpOpReturnOutputGenesis(ticker, name, url, decimals, tokenQuantity);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());
        req.tx.addOutput(this.wallet().getParams().getMinNonDustOutput(), this.wallet().currentChangeAddress());
        return wallet().sendCoinsOffline(req);
    }

    public void recalculateSlpUtxos() {
        if (!recalculatingTokens) {
            recalculatingTokens = true;
            this.slpUtxos.clear();
            this.slpBalances.clear();
            List<TransactionOutput> utxos = this.wallet().getAllDustUtxos(false, false);
            ArrayList<SlpUTXO> slpUtxosToAdd = new ArrayList<>();

            for (TransactionOutput utxo : utxos) {
                Transaction tx = utxo.getParentTransaction();
                if (tx != null) {
                    if (SlpOpReturn.isSlpTx(tx)) {
                        SlpOpReturn slpOpReturn = new SlpOpReturn(tx);
                        String tokenId = slpOpReturn.getTokenId();

                        if (!hasTransactionBeenRecorded(tx.getTxId().toString())) {
                            SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getTxId().toString());
                            boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded());
                            if (valid) {
                                SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                                slpUtxosToAdd.add(slpUTXO);
                                if (!this.tokenIsMapped(tokenId)) {
                                    this.tryCacheToken(tokenId);
                                } else {
                                    SlpToken slpToken = this.getSlpToken(tokenId);
                                    this.calculateSlpBalance(slpUTXO, slpToken);
                                }
                                this.verifiedSlpTxs.add(tx.getTxId().toString());
                            }
                        } else {
                            SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                            slpUtxosToAdd.add(slpUTXO);
                            if (!this.tokenIsMapped(tokenId)) {
                                this.tryCacheToken(tokenId);
                            } else {
                                SlpToken slpToken = this.getSlpToken(tokenId);
                                this.calculateSlpBalance(slpUTXO, slpToken);
                            }
                        }
                    }
                }
            }

            this.slpUtxos.addAll(slpUtxosToAdd);
            this.saveVerifiedTxs(this.verifiedSlpTxs);
            recalculatingTokens = false;
        }
    }

    private void calculateSlpBalance(SlpUTXO slpUTXO, SlpToken slpToken) {
        String tokenId = slpToken.getTokenId();
        double tokenAmount = BigDecimal.valueOf(slpUTXO.getTokenAmountRaw()).scaleByPowerOfTen(-slpToken.getDecimals()).doubleValue();
        if (this.isBalanceRecorded(tokenId)) {
            Objects.requireNonNull(this.getTokenBalance(tokenId)).addToBalance(tokenAmount);
        } else {
            this.slpBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
        }
    }

    private SlpUTXO processSlpUtxo(SlpOpReturn slpOpReturn, TransactionOutput utxo) {
        long tokenRawAmount = slpOpReturn.getRawAmountOfUtxo(utxo.getIndex() - 1);
        return new SlpUTXO(slpOpReturn.getTokenId(), tokenRawAmount, utxo, SlpUTXO.SlpUtxoType.NORMAL);
    }

    private void tryCacheToken(String tokenId) {
        if (!this.tokenIsMapped(tokenId)) {
            SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
            JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());

            if (tokenData != null) {
                int decimals = tokenData.getInt("decimals");
                String ticker = tokenData.getString("ticker");
                SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                this.slpTokens.add(slpToken);
                this.saveTokens(this.slpTokens);
            }
        }
    }

    private boolean isBalanceRecorded(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.slpBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private SlpTokenBalance getTokenBalance(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.slpBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }

        return null;
    }

    private boolean tokenIsMapped(String tokenId) {
        for (SlpToken slpToken : this.slpTokens) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public SlpToken getSlpToken(String tokenId) {
        for (SlpToken slpToken : this.slpTokens) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return slpToken;
                }
            }
        }

        return null;
    }

    public boolean hasTransactionBeenRecorded(String txid) {
        return this.verifiedSlpTxs.contains(txid);
    }
}
