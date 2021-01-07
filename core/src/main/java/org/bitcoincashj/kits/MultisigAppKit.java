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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoincashj.core.*;
import org.bitcoincashj.core.listeners.DownloadProgressTracker;
import org.bitcoincashj.crypto.DeterministicKey;
import org.bitcoincashj.net.discovery.DnsDiscovery;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.store.BlockStoreException;
import org.bitcoincashj.store.SPVBlockStore;
import org.bitcoincashj.wallet.*;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
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
public class MultisigAppKit extends WalletKitCore {
    private List<DeterministicKey> followingKeys;
    private int m;

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public MultisigAppKit(NetworkParameters params, File directory, String filePrefix, List<DeterministicKey> followingKeys, int m) {
        this(new Context(params), Script.ScriptType.P2PKH, null, directory, filePrefix, followingKeys, m);
    }

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public MultisigAppKit(NetworkParameters params, Script.ScriptType preferredOutputScriptType,
                          @Nullable KeyChainGroupStructure structure, File directory, String filePrefix, List<DeterministicKey> followingKeys, int m) {
        this(new Context(params), preferredOutputScriptType, structure, directory, filePrefix, followingKeys, m);
    }

    /**
     * Creates a new WalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public MultisigAppKit(Context context, Script.ScriptType preferredOutputScriptType,
                          @Nullable KeyChainGroupStructure structure, File directory, String filePrefix, List<DeterministicKey> followingKeys, int m) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.preferredOutputScriptType = checkNotNull(preferredOutputScriptType);
        this.structure = structure != null ? structure : KeyChainGroupStructure.DEFAULT;
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
        this.followingKeys = followingKeys;
        this.m = m;
    }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Starting up with directory = {}", directory);
        try {
            File chainFile = new File(directory, filePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();
            vWalletFile = new File(directory, filePrefix + ".wallet");
            boolean shouldAddMarriedKeychain = !vWalletFile.exists() || restoreFromSeed != null || restoreFromKey != null;
            boolean shouldReplayWallet = (vWalletFile.exists() && !chainFileExists) || restoreFromSeed != null || restoreFromKey != null;
            vWallet = createOrLoadWallet(shouldReplayWallet);
            if (shouldAddMarriedKeychain) {
                MarriedKeyChain marriedKeyChain = MarriedKeyChain.builder()
                        .seed(this.restoreFromSeed == null ? this.wallet().getKeyChainSeed() : this.restoreFromSeed)
                        .followingKeys(followingKeys)
                        .threshold(this.m).build();
                vWallet.addAndActivateHDChain(marriedKeyChain);
            }
            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = new SPVBlockStore(params, chainFile);
            if (!chainFileExists || restoreFromSeed != null || restoreFromKey != null) {
                if (checkpoints == null && !Utils.isAndroidRuntime()) {
                    checkpoints = CheckpointManager.openStream(params);
                }

                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;
                    if (restoreFromSeed != null) {
                        time = restoreFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation for restore.");
                            vStore.clear();
                        }
                    } else if (restoreFromKey != null) {
                        time = restoreFromKey.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation for restore.");
                            vStore.clear();
                        }
                    } else {
                        time = vWallet.getEarliestKeyCreationTime();
                    }
                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Clearing the chain file in preparation for restore.");
                    vStore.clear();
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();
            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                vPeerGroup.setMaxConnections(peerAddresses.length);
                peerAddresses = null;
            } else if (!params.getId().equals(NetworkParameters.ID_REGTEST)) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vWallet);
            vPeerGroup.addWallet(vWallet);
            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();

                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadProgressTracker listener = new DownloadProgressTracker();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        final DownloadProgressTracker l = downloadListener == null ? new DownloadProgressTracker() : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);

                    }
                }, MoreExecutors.directExecutor());
            }
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    public Transaction makeIndividualMultisigTransaction(Address address, Coin amount) throws InsufficientMoneyException {
        Transaction spendTx = wallet().createSendDontSign(address, amount, true);
        spendTx.getInputs().sort(new Comparator<TransactionInput>() {
            public int compare(TransactionInput o1, TransactionInput o2) {
                int o1Index = (int)o1.getOutpoint().getIndex();
                int o2Index = (int)o2.getOutpoint().getIndex();
                return o1Index - o2Index;
            }
        });
        for (TransactionInput input : spendTx.getInputs()) {
            RedeemData redeemData = input.getConnectedRedeemData(wallet());
            if (redeemData != null) {
                TransactionOutput utxo = input.getConnectedOutput();
                Script script = Objects.requireNonNull(utxo).getScriptPubKey();
                input.setScriptSig(script.createEmptyInputScript(null, redeemData.redeemScript));
            }
        }
        return spendTx;
    }

    public Transaction importMultisigPayload(String hex) throws InsufficientMoneyException {
        Transaction currentRawTx = new Transaction(params(), Hex.decode(hex));
        SendRequest req = SendRequest.forTx(new Transaction(params()));
        req.feePerKb = Coin.valueOf(1000L);
        req.signInputs = false;
        req.shuffleOutputs = false;
        req.allowUnconfirmed();
        for(TransactionInput input : currentRawTx.getInputs()) {
            TransactionOutput utxo = input.findConnectedOutput(wallet());
            req.tx.addInput(utxo);
        }

        for(TransactionOutput output : currentRawTx.getOutputs()) {
            req.tx.addOutput(output);
        }

        for(int x = 0; x < req.tx.getInputs().size(); x++) {
            Script scriptSigToInsert = currentRawTx.getInput(x).getScriptSig();
            req.tx.getInput(x).setScriptSig(scriptSigToInsert);
        }

        return req.tx;
    }

    public int getSigsRequiredToSpend() {
        MarriedKeyChain marriedKeyChain = (MarriedKeyChain) this.vWallet.getActiveKeyChain();
        return marriedKeyChain.getSigsRequiredToSpend();
    }
}
