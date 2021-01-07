package org.bitcoincashj.core.slp;

import org.bitcoincashj.core.Transaction;
import org.bitcoincashj.core.TransactionOutput;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptChunk;
import org.bitcoincashj.script.ScriptPattern;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class SlpOpReturn {
    public static final String slpProtocolId = "534c5000";
    public static final String tokenTypeId = "01";
    public static final String genesisTxTypeId = "47454e45534953";
    public static final String mintTxTypeId = "4d494e54";
    public static final String sendTxTypeId = "53454e44";
    public static final int opReturnLocation = 0;
    public static final int protocolChunkLocation = 1;
    public static final int slpTokenTypeChunkLocation = 2;
    public static final int slpTxTypeChunkLocation = 3;
    public static final int tokenIdChunkLocation = 4;
    public static final int mintBatonVoutGenesisChunkLocation = 9;
    public static final int mintBatonVoutMintChunkLocation = 5;
    public static final int tokenOutputsStartLocation = 5;

    public enum SlpTxType {
        GENESIS,
        MINT,
        SEND
    }

    private Script opReturn;
    private String tokenId;
    private Transaction tx;
    private SlpTxType slpTxType;
    private int slpUtxos;
    private boolean hasMintingBaton = false;
    private TransactionOutput mintingBatonUtxo = null;
    private int mintingBatonVout = 0;

    public SlpOpReturn(Transaction tx) {
        this.tx = tx;
        opReturn = tx.getOutput(opReturnLocation).getScriptPubKey();

        if (ScriptPattern.isOpReturn(opReturn)) {
            this.setSlpTxType(opReturn);
            this.setTokenId(opReturn);
            this.collectSlpUtxos(opReturn);
            this.findMintingBaton(opReturn);
        } else {
            throw new NullPointerException("Not an SLP transaction.");
        }
    }

    private void setTokenId(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            if (this.getSlpTxType() == SlpTxType.SEND || this.getSlpTxType() == SlpTxType.MINT) {
                ScriptChunk tokenIdChunk = opReturn.getChunks().get(tokenIdChunkLocation);
                assert tokenIdChunk.data != null;
                this.tokenId = new String(Hex.encode(tokenIdChunk.data));
            } else if (this.getSlpTxType() == SlpTxType.GENESIS) {
                this.tokenId = this.tx.getTxId().toString();
            }
        }
    }

    public static boolean isSlpTx(Transaction tx) {
        List<TransactionOutput> outputs = tx.getOutputs();
        TransactionOutput opReturnUtxo = outputs.get(0);
        Script opReturn = opReturnUtxo.getScriptPubKey();
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk tokenTypeChunk = opReturn.getChunks().get(slpTokenTypeChunkLocation);
                    if (tokenTypeChunk != null && tokenTypeChunk.data != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);
                        return tokenType.equals(tokenTypeId);
                    }
                }
            }
        }

        return false;
    }

    private void setSlpTxType(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk slpTxTypeChunk = opReturn.getChunks().get(slpTxTypeChunkLocation);
                    if (slpTxTypeChunk != null && slpTxTypeChunk.data != null) {
                        String txType = new String(Hex.encode(slpTxTypeChunk.data), StandardCharsets.UTF_8);
                        switch (txType) {
                            case genesisTxTypeId:
                                this.slpTxType = SlpTxType.GENESIS;
                                break;
                            case mintTxTypeId:
                                this.slpTxType = SlpTxType.MINT;
                                break;
                            case sendTxTypeId:
                                this.slpTxType = SlpTxType.SEND;
                                break;
                        }
                    }
                }
            }
        }
    }

    private void collectSlpUtxos(Script opReturn) {
        int chunkOffset = 0;
        switch (this.slpTxType) {
            case GENESIS:
                chunkOffset = 10;
                break;
            case MINT:
                chunkOffset = 6;
                break;
            case SEND:
                chunkOffset = 5;
                break;
        }

        slpUtxos = opReturn.getChunks().size() - chunkOffset;
    }

    private void findMintingBaton(Script opReturn) {
        byte[] mintingBatonVoutData = null;
        if (this.getSlpTxType() == SlpTxType.GENESIS) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutGenesisChunkLocation).data;
        } else if (this.getSlpTxType() == SlpTxType.MINT) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutMintChunkLocation).data;
        }

        if (mintingBatonVoutData != null) {
            String voutHex = new String(Hex.encode(mintingBatonVoutData), StandardCharsets.UTF_8);
            if (!voutHex.equals("")) {
                int vout = Integer.parseInt(voutHex, 16);
                this.hasMintingBaton = true;
                this.mintingBatonUtxo = this.getTx().getOutput(vout);
                this.mintingBatonVout = vout;
            }
        }
    }

    public long getRawAmountOfUtxo(int slpUtxoIndex) {
        int chunkOffset = 0;
        switch (this.slpTxType) {
            case GENESIS:
                chunkOffset = 10;
                break;
            case MINT:
                chunkOffset = 6;
                break;
            case SEND:
                chunkOffset = 5;
                break;
        }

        int utxoChunkLocation = slpUtxoIndex + chunkOffset;
        return Long.parseLong(new String(Hex.encode(Objects.requireNonNull(opReturn.getChunks().get(utxoChunkLocation).data))), 16);
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public int getSlpUtxos() {
        return this.slpUtxos;
    }

    public boolean hasMintingBaton() {
        return this.hasMintingBaton;
    }

    public SlpTxType getSlpTxType() {
        return this.slpTxType;
    }

    public int getMintingBatonVout() {
        return this.mintingBatonVout;
    }

    public Transaction getTx() {
        return this.tx;
    }

    public Script getOpReturn() {
        return this.opReturn;
    }

    public TransactionOutput getMintingBatonUtxo() {
        return this.mintingBatonUtxo;
    }
}
