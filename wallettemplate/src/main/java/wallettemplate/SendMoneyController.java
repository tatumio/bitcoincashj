/*
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

package wallettemplate;

import javafx.scene.layout.HBox;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.spongycastle.crypto.params.KeyParameter;
import wallettemplate.controls.BitcoinAddressValidator;
import wallettemplate.utils.TextFieldValidator;
import wallettemplate.utils.WTUtils;

import static com.google.common.base.Preconditions.checkState;
import static wallettemplate.utils.GuiUtils.*;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;
    public TextField amountEdit;
    public Label btcLabel;

    public Main.OverlayUI overlayUI;

    private Wallet.SendResult sendResult;
    private KeyParameter aesKey;
    private DecimalFormat df = new DecimalFormat("#.########");
    // Called by FXMLLoader
    public void initialize() {
        Coin balance = Main.bitcoin.wallet().getBalance();
        checkState(!balance.isZero());
        new TextFieldValidator(amountEdit, text ->
                !WTUtils.didThrow(() -> checkState(Coin.parseCoin(text).compareTo(balance) <= 0)));
        amountEdit.setText(balance.toPlainString());

        System.out.println("INT TEST " + this.randomInt(12, 36, new SecureRandom()));
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void send(ActionEvent event) {
        // Address exception cannot happen as we validated it beforehand.
        try {
            /*Coin amount = Coin.parseCoin(amountEdit.getText());
            SendRequest req;
            if (amount.equals(Main.bitcoin.wallet().getBalance()))
                req = SendRequest.emptyWallet(Main.params, address.getText());
            else
                req = SendRequest.to(Main.params, address.getText(), amount);
            req.aesKey = aesKey;
            sendResult = Main.bitcoin.wallet().sendCoins(req);
            Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(@Nullable Transaction result) {
                    checkGuiThread();
                    overlayUI.done();
                }

                @Override
                public void onFailure(Throwable t) {
                    // We died trying to empty the wallet.
                    crashAlert(t);
                }
            });
            sendResult.tx.getConfidence().addEventListener((tx, reason) -> {
                if (reason == TransactionConfidence.Listener.ChangeReason.SEEN_PEERS)
                    updateTitleForBroadcast();
            });
            sendBtn.setDisable(true);
            address.setDisable(true);
            ((HBox)amountEdit.getParent()).getChildren().remove(amountEdit);
            ((HBox)btcLabel.getParent()).getChildren().remove(btcLabel);
            updateTitleForBroadcast();*/
            Coin amount = Coin.parseCoin(amountEdit.getText());
            if (amount.equals(Main.bitcoin.wallet().getBalance())) {
                SendRequest req = SendRequest.emptyWallet(Main.params, address.getText());
                req.aesKey = aesKey;
                sendResult = Main.bitcoin.wallet().sendCoins(req);
                Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction result) {
                        checkGuiThread();
                        overlayUI.done();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // We died trying to empty the wallet.
                        crashAlert(t);
                    }
                });
                sendResult.tx.getConfidence().addEventListener((tx, reason) -> {
                    if (reason == TransactionConfidence.Listener.ChangeReason.SEEN_PEERS)
                        updateTitleForBroadcast();
                });
                sendBtn.setDisable(true);
                address.setDisable(true);
                ((HBox)amountEdit.getParent()).getChildren().remove(amountEdit);
                ((HBox)btcLabel.getParent()).getChildren().remove(btcLabel);
                updateTitleForBroadcast();
            } else {
                this.runTunnel(amountEdit.getText(), address.getText());
            }

        } catch (InsufficientMoneyException e) {
            informationalAlert("Could not empty the wallet",
                    "You may have too little money left in the wallet to make a transaction.");
            overlayUI.done();
        } catch (ECKey.KeyIsEncryptedException e) {
            askForPasswordAndRetry();
        } catch (NullPointerException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void runTunnel(String amount, String finalRecipient) throws InsufficientMoneyException, InterruptedException {
        /*
        Here we set the max hops. So it'll go: initial hop -> (hop1 ... hop10) -> final hop for a total of 12 hops.
         */
        int hops = 6;
        int currentHop = 0;

        //This is the amount we are sending the actual recipient.
        Coin coin = Coin.parseCoin(amount);
        //This is an array that will hold the current "valid" unspents of our hop transactions. If these hops were genuine transactions,
        //this would be the actual UTXOs of those transactions. Because we own all the UTXOs in these hops, we have to pretend these are the "genuine" ones.
        ArrayList<TransactionOutput> fakeUnspents = new ArrayList<>();
        ArrayList<TransactionOutput> fakeSpents = new ArrayList<>();

        //A list of all txs for this Tunnel. It will always be: initial -> hop1 ... hop10 -> final hop
        List<Transaction> tunnelTxs = new ArrayList<>();

        //Create the initial hop.
        Transaction initialHop = this.createInitialHop(coin, fakeUnspents, fakeSpents);
        //Add hop to tunnel txs list
        tunnelTxs.add(initialHop);
        //Increase hop count by one.
        currentHop++;

        //Here is where we start creating the hops in between the initial and final.
        for(int x = 0; x < hops; x++) {
            //Create the next hop by using data from the previous hop. For x = 0, we use the initial hop as we added it the tunnelTxs array above.
            Transaction nextHop = this.createNextHop(tunnelTxs.get(currentHop - 1), fakeUnspents, fakeSpents);
            //Add the next hop to the array
            tunnelTxs.add(nextHop);
            //Increase current hop by one.
            currentHop++;
        }

        //FINAL TX/HOP
        Transaction finalTx = this.createFinalHop(fakeUnspents, fakeSpents, coin, finalRecipient);
        tunnelTxs.add(finalTx);

        //Here we broadcast each hop one-by-one.
        int hopsTotal = tunnelTxs.size();
        for(int x = 0; x < hopsTotal; x++) {
            //Send the hop to all peers.
            for(Peer peer : Main.bitcoin.peerGroup().getConnectedPeers()) {
                peer.sendMessage(tunnelTxs.get(x));
            }
            System.out.println("COMPLETED HOP: " + (x+1) + "/" + hopsTotal);
            SecureRandom secureRandom = new SecureRandom();
            //Wait some time before sending next hop, so we don't trip time correlation analysis as much.
            Thread.sleep(this.randomLong(2000, 15000, secureRandom));
        }

        overlayUI.done();
    }

    private Transaction createInitialHop(Coin finalCoinAmountToSend, ArrayList<TransactionOutput> fakeUnspents, ArrayList<TransactionOutput> fakeSpents) throws InsufficientMoneyException {
        //This is the initial hop, so no previous hops exist. To get UTXOs to use, we get all UTXOs in the wallet.
        List<TransactionOutput> utxos = Main.bitcoin.wallet().getUtxos();
        //Here we get the UTXOs to use for this hop.
        //This gets the total value of the all UTXOs.
        double inputsTotal = this.getHopUtxosTotal(utxos);
        double minimum = Double.parseDouble(finalCoinAmountToSend.toPlainString());
        //Just setting the random generator here. We use SecureRandom over Random to get rid of any predictability that basic Random has.
        SecureRandom secureRandom = new SecureRandom();
        //Get the fake output amount. The minimum is as described above, the max is the inputs total.
        double randomizedHopSplitAmount = this.randomDouble(minimum, inputsTotal, secureRandom);
        //Just converting it to a string and formatting so it gets rid of potential float precision errors.
        String randomizedToString = df.format(randomizedHopSplitAmount);
        System.out.println("WORKING WITH: " + randomizedToString);
        //Parse the above string to a Coin object so we can work with this when crafting the bitcoin transaction using bitcoincashj.
        Coin randomizedCoin = Coin.parseCoin(randomizedToString);
        //Here's our fake address, the fake recipient. Surprise! It's just us!
        Address fakeAddress = Main.bitcoin.wallet().freshReceiveAddress();
        //Create the SendRequest. Normal bitcoincashj stuff.
        SendRequest req = SendRequest.to(Main.params, fakeAddress.toBase58(), randomizedCoin);
        //Shuffle outputs just so it's not predictable to anyone viewing.
        req.shuffleOutputs = true;
        //Complete the tx. Basically crafting it and signing it.
        Main.bitcoin.wallet().completeTx(req);
        //Commit the tx to our wallet and mark UTXOs as spent and mark change addresses as used.
        Main.bitcoin.wallet().commitTx(req.tx);
        this.updateFakeUnspents(fakeUnspents, fakeSpents, req.tx.getInputs(), req.tx.getOutputs());
        //Return the tx object from the SendRequest.
        return req.tx;
    }

    private Transaction createNextHop(Transaction prevHop, ArrayList<TransactionOutput> fakeUnspents, ArrayList<TransactionOutput> fakeSpents) throws InsufficientMoneyException {
        //Get the UTXOs of the previous hop.
        List<TransactionOutput> prevHopUtxos = prevHop.getOutputs();
        List<TransactionOutput> currentHopInputs = new ArrayList<>();
        currentHopInputs.add(this.getSmallestUtxosForHop(prevHopUtxos));
        //currentHopInputs.add(prevHopUtxos.get(this.randomInt(0, prevHopUtxos.size(), new SecureRandom())));
        //Get the total value of the previous hop's UTXOs.
        double inputsTotalValue = this.getHopUtxosTotal(currentHopInputs);
        double fakeUnspentsTotalValue = this.getHopUtxosTotal(fakeUnspents);
        //Here's where we create another fake output. This is the total value of the previous hop's UTXOs divided by a random double between 2.5 and 4.25.
        //double amountToFakeSendThisHop = prevHopUtxoTotalValue / this.randomDouble(2.5d, 4.25d, new SecureRandom());
        double amountToFakeSendThisHop = this.randomDouble(inputsTotalValue * 0.50d, fakeUnspentsTotalValue, new SecureRandom());

        //Convert the double to a string and format it so we remove any float precision errors.
        String amountToFakeSendString = df.format(amountToFakeSendThisHop);
        //Convert to Coin object so we can use this in bitcoincashj, like we did in the initial hop.
        Coin fakeSendAmount = Coin.parseCoin(amountToFakeSendString);

        Address fakeAddress = Main.bitcoin.wallet().freshReceiveAddress();
        SendRequest req = SendRequest.to(Main.params, fakeAddress.toBase58(), fakeSendAmount);
        req.shuffleOutputs = true;
        req.utxos = fakeUnspents;
        Main.bitcoin.wallet().completeTx(req);
        Main.bitcoin.wallet().commitTx(req.tx);
        this.updateFakeUnspents(fakeUnspents, fakeSpents, req.tx.getInputs(), req.tx.getOutputs());
        return req.tx;
    }

    private Transaction createFinalHop(List<TransactionOutput> fakeUnspents, List<TransactionOutput> fakeSpents, Coin finalCoinAmountToSend, String recipient) throws InsufficientMoneyException {
        //Final hop! Oh boy!
        //Here we get the final amount to actually send the real recipient, and get all of the fake "valid" UTXOs and calculate which ones to use with a threshold of 1.025
        //craft tx as usual
        double finalAmount = Double.parseDouble(finalCoinAmountToSend.toPlainString());
        SendRequest req = SendRequest.to(Main.params, recipient, finalCoinAmountToSend);
        req.shuffleOutputs = true;
        req.utxos = this.getUtxosForFinalHop(finalAmount, fakeUnspents, fakeSpents);
        Main.bitcoin.wallet().completeTx(req);
        Main.bitcoin.wallet().commitTx(req.tx);

        return req.tx;
    }

    private ArrayList<TransactionOutput> updateFakeUnspents(ArrayList<TransactionOutput> fakeUnspents, ArrayList<TransactionOutput> fakeSpents, List<TransactionInput> transactionInputs, List<TransactionOutput> transactionOutputs) {
        for(TransactionInput input : transactionInputs) {
            if(fakeUnspents.indexOf(input.getConnectedOutput()) != -1) {
                fakeUnspents.remove(input.getConnectedOutput());
                fakeSpents.add(input.getConnectedOutput());
            }
        }

        ArrayList<TransactionOutput> tempUtxos = new ArrayList<>(transactionOutputs);
        if(transactionOutputs.size() > 1) {
            for (int i = 0; i < tempUtxos.size(); i++) {
                for (int j = 1; j < (tempUtxos.size() - i); j++) {
                    TransactionOutput utxoBefore = tempUtxos.get(j - 1);
                    TransactionOutput thisUtxo = tempUtxos.get(j);
                    double utxoBeforeValue = Double.parseDouble(utxoBefore.getValue().toPlainString());
                    double thisUtxoValue = Double.parseDouble(thisUtxo.getValue().toPlainString());

                    if (utxoBeforeValue < thisUtxoValue) {
                        TransactionOutput temp = tempUtxos.get(j - 1);
                        tempUtxos.set(j - 1, tempUtxos.get(j));
                        tempUtxos.set(j, temp);
                    }
                }
            }
        }

        fakeUnspents.add(tempUtxos.get(0));

        return fakeUnspents;
    }

    private ArrayList<TransactionOutput> getUtxosForFinalHop(double amountSending, List<TransactionOutput> fakeUnspents, List<TransactionOutput> fakeSpents) {
        ArrayList<TransactionOutput> utxosSelected = new ArrayList<>();
        double utxosTotal = 0;
        for(TransactionOutput fakeUnspent : fakeUnspents) {
            double fakeUnspentValue = Double.parseDouble(fakeUnspent.getValue().toPlainString());

            if(utxosTotal < amountSending) {
                utxosSelected.add(fakeUnspent);
                utxosTotal += fakeUnspentValue;
            }
        }

        ArrayList<TransactionOutput> walletUtxos = new ArrayList<>(Main.bitcoin.wallet().getUtxos());

        for(TransactionOutput utxo : walletUtxos) {
            if(utxosSelected.indexOf(utxo) == -1 && fakeSpents.indexOf(utxo) == -1) {
                double utxoValue = Double.parseDouble(utxo.getValue().toPlainString());
                if(utxosTotal < amountSending) {
                    utxosSelected.add(utxo);
                    utxosTotal += utxoValue;
                }
            }
        }

        return utxosSelected;
    }

    private double getHopUtxosTotal(List<TransactionOutput> hopInputs) {
        double total = 0;
        for(int x = 0; x < hopInputs.size(); x++) {
            total += Double.parseDouble(hopInputs.get(x).getValue().toPlainString());
        }

        return total;
    }

    private TransactionOutput getSmallestUtxosForHop(List<TransactionOutput> utxos) {
        int utxosCount = utxos.size();

        //Bubble sort because I like bubble sort.
        ArrayList<TransactionOutput> tempUtxos = new ArrayList<>(utxos);
        if(utxos.size() > 1) {
            for (int i = 0; i < utxosCount; i++) {
                for (int j = 1; j < (utxosCount - i); j++) {
                    TransactionOutput utxoBefore = tempUtxos.get(j - 1);
                    TransactionOutput thisUtxo = tempUtxos.get(j);
                    double utxoBeforeValue = Double.parseDouble(utxoBefore.getValue().toPlainString());
                    double thisUtxoValue = Double.parseDouble(thisUtxo.getValue().toPlainString());

                    if (utxoBeforeValue > thisUtxoValue) {
                        TransactionOutput temp = tempUtxos.get(j - 1);
                        tempUtxos.set(j - 1, tempUtxos.get(j));
                        tempUtxos.set(j, temp);
                    }
                }
            }
        }

        return tempUtxos.get(0);
    }

    private double randomDouble(double min, double max, SecureRandom secureRandom) {
        return min + (max - min) * secureRandom.nextDouble();
    }

    private long randomLong(long min, long max, SecureRandom secureRandom) {
        return min + (long)(secureRandom.nextDouble()*(max - min));
    }

    private int randomInt(int min, int max, SecureRandom secureRandom) {
        return min + (int)(secureRandom.nextDouble()*(max - min));
    }

    private void askForPasswordAndRetry() {
        Main.OverlayUI<WalletPasswordController> pwd = Main.instance.overlayUI("wallet_password.fxml");
        final String addressStr = address.getText();
        final String amountStr = amountEdit.getText();
        pwd.controller.aesKeyProperty().addListener((observable, old, cur) -> {
            // We only get here if the user found the right password. If they don't or they cancel, we end up back on
            // the main UI screen. By now the send money screen is history so we must recreate it.
            checkGuiThread();
            Main.OverlayUI<SendMoneyController> screen = Main.instance.overlayUI("send_money.fxml");
            screen.controller.aesKey = cur;
            screen.controller.address.setText(addressStr);
            screen.controller.amountEdit.setText(amountStr);
            screen.controller.send(null);
        });
    }

    private void updateTitleForBroadcast() {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }
}
