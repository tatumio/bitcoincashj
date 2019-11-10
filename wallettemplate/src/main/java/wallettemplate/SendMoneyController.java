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
        int hops = 10;
        int currentHop = 0;
        Coin coin = Coin.parseCoin(amount);
        ArrayList<TransactionOutput> fakeValidUnspents = new ArrayList<>();
        List<Transaction> innocenceTxs = new ArrayList<>();
        Transaction initialHop = this.createInitialHop(coin);
        innocenceTxs.add(initialHop);
        currentHop++;

        for(int x = 0; x < hops; x++) {
            Transaction nextHop = this.createNextHop(innocenceTxs.get(currentHop - 1), fakeValidUnspents);
            innocenceTxs.add(nextHop);
            currentHop++;
        }

        //FINAL TX
        Transaction finalTx = this.createFinalHop(fakeValidUnspents, coin, finalRecipient);
        innocenceTxs.add(finalTx);

        int hopsTotal = innocenceTxs.size();
        for(int x = 0; x < hopsTotal; x++) {
            for(Peer peer : Main.bitcoin.peerGroup().getConnectedPeers()) {
                peer.sendMessage(innocenceTxs.get(x));
            }
            System.out.println("COMPLETED HOP: " + (x+1) + "/" + hopsTotal);
            SecureRandom secureRandom = new SecureRandom();
            Thread.sleep(this.randomLong(2000, 15000, secureRandom));
        }

        overlayUI.done();
    }

    private Transaction createInitialHop(Coin finalCoinAmountToSend) throws InsufficientMoneyException {
        List<TransactionOutput> utxos = Main.bitcoin.wallet().getUtxos();
        List<TransactionOutput> firstHopInputs = this.getLargestUtxosForHop(finalCoinAmountToSend, utxos, 1.5d);
        double inputsTotal = this.getHopUtxosTotal(firstHopInputs);
        SecureRandom secureRandom = new SecureRandom();
        double randomizedMinDivider = this.randomDouble(1.5d, 5d, secureRandom);
        double randomizedMin = inputsTotal / randomizedMinDivider;
        double randomizedHopSplitAmount = this.randomDouble(randomizedMin, inputsTotal, secureRandom);
        String randomizedToString = df.format(randomizedHopSplitAmount);
        Coin randomizedCoin = Coin.parseCoin(randomizedToString);
        Address fakeAddress = Main.bitcoin.wallet().freshReceiveAddress();
        SendRequest req = SendRequest.to(Main.params, fakeAddress.toBase58(), randomizedCoin);
        req.shuffleOutputs = true;
        req.utxos = firstHopInputs;
        Main.bitcoin.wallet().completeTx(req);
        Main.bitcoin.wallet().commitTx(req.tx);
        return req.tx;
    }

    private Transaction createNextHop(Transaction prevHop, ArrayList<TransactionOutput> fakeValidUnspents) throws InsufficientMoneyException {
        int currentHopAdditionalInput = new SecureRandom().nextInt(2);
        List<TransactionOutput> prevHopUtxos = prevHop.getOutputs();
        double prevHopUtxoTotalValue = this.getHopUtxosTotal(prevHopUtxos);
        double amountToFakeSendThisHop = prevHopUtxoTotalValue / this.randomDouble(2d, 4d, new SecureRandom());
        String amountToFakeSendString = df.format(amountToFakeSendThisHop);
        Coin fakeSendAmount = Coin.parseCoin(amountToFakeSendString);

        List<TransactionOutput> currentHopInputs = this.getLargestUtxosForHop(fakeSendAmount, prevHopUtxos, 0.95d);

        int chanceOfAddingAdditionalInputs = this.randomInt(0, 100, new SecureRandom());

        if(chanceOfAddingAdditionalInputs <= 25) {
            for (int x = 0; x < currentHopAdditionalInput; x++) {
                int randomFakeValidUnspent = this.randomInt(0, fakeValidUnspents.size(), new SecureRandom());
                currentHopInputs.add(fakeValidUnspents.get(randomFakeValidUnspent));
                fakeValidUnspents.remove(randomFakeValidUnspent);
            }
        }

        double inputsTotal = this.getHopUtxosTotal(currentHopInputs);
        SecureRandom secureRandom = new SecureRandom();
        double randomizedMinDivider = this.randomDouble(1.5d, 5d, secureRandom);
        double randomizedMin = inputsTotal / randomizedMinDivider;
        double randomizedHopSplitAmount = this.randomDouble(randomizedMin, inputsTotal, secureRandom);
        String randomizedToString = df.format(randomizedHopSplitAmount);
        Coin randomizedCoin = Coin.parseCoin(randomizedToString);
        Address fakeAddress = Main.bitcoin.wallet().freshReceiveAddress();
        SendRequest req = SendRequest.to(Main.params, fakeAddress.toBase58(), randomizedCoin);
        req.shuffleOutputs = true;
        req.utxos = currentHopInputs;
        this.getFakeValidUtxos(fakeValidUnspents, prevHopUtxos, currentHopInputs);
        Main.bitcoin.wallet().completeTx(req);
        Main.bitcoin.wallet().commitTx(req.tx);
        return req.tx;
    }

    private Transaction createFinalHop(List<TransactionOutput> allUnspentOutputs, Coin finalCoinAmountToSend, String recipient) throws InsufficientMoneyException {
        List<TransactionOutput> currentHopInputs = this.getLargestUtxosForHop(finalCoinAmountToSend, allUnspentOutputs, 1.025d);
        SendRequest req = SendRequest.to(Main.params, recipient, finalCoinAmountToSend);
        req.shuffleOutputs = true;
        req.utxos = currentHopInputs;
        Main.bitcoin.wallet().completeTx(req);
        Main.bitcoin.wallet().commitTx(req.tx);
        return req.tx;
    }

    private ArrayList<TransactionOutput> getFakeValidUtxos(ArrayList<TransactionOutput> fakeValidUnspents, List<TransactionOutput> prevHopOutputs, List<TransactionOutput> currentHopSelectedInputs) {
        for(TransactionOutput utxo : prevHopOutputs) {
            if(currentHopSelectedInputs.indexOf(utxo) == -1) {
                fakeValidUnspents.add(utxo);
            }
        }

        return fakeValidUnspents;
    }

    private double getHopUtxosTotal(List<TransactionOutput> hopInputs) {
        double total = 0;
        for(int x = 0; x < hopInputs.size(); x++) {
            total += Double.parseDouble(hopInputs.get(x).getValue().toPlainString());
        }

        return total;
    }

    private List<TransactionOutput> getLargestUtxosForHop(Coin amountToSend, List<TransactionOutput> utxos, double threshold) {
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

                    if (utxoBeforeValue < thisUtxoValue) {
                        TransactionOutput temp = tempUtxos.get(j - 1);
                        tempUtxos.set(j - 1, tempUtxos.get(j));
                        tempUtxos.set(j, temp);
                    }
                }
            }
        }

        /*
        Here we start establishing the amount of UTXOs to send for our first hop. We want to select as little UTXOs as possible to
        prevent linking addresses and inputs as much as possible in a Tunnel.
         */

        /*
        The threshold is how strict we want the UTXO selection process to be. A larger threshold means more UTXOs might be selected.
        A smaller one means less UTXOs will be selected.
        */
        List<TransactionOutput> selectedUtxosForHop = new ArrayList<>();
        double selectedUtxoTotal = 0;
        for (TransactionOutput utxoCandidate : tempUtxos) {
            double utxoCandidateValue = Double.parseDouble(utxoCandidate.getValue().toPlainString());
            double amountToSendValue = Double.parseDouble(amountToSend.toPlainString());
            double quotientOfCandidateTotalAndAmount = selectedUtxoTotal / amountToSendValue;

            if (quotientOfCandidateTotalAndAmount <= threshold) {
                selectedUtxosForHop.add(utxoCandidate);
                selectedUtxoTotal += utxoCandidateValue;
            }
        }

        return selectedUtxosForHop;
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
