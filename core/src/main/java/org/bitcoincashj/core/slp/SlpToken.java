package org.bitcoincashj.core.slp;

public class SlpToken {
    private String tokenId;
    private String ticker;
    private int decimals;

    public SlpToken(String tokenId, String ticker, int decimals) {
        this.tokenId = tokenId;
        this.ticker = ticker;
        this.decimals = decimals;
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public String getTicker() {
        return this.ticker;
    }

    public int getDecimals() {
        return this.decimals;
    }

    @Override
    public String toString() {
        return "SLP TOKEN [" +
                this.getTokenId() + ", " +
                this.getTicker() + ", " +
                this.getDecimals() + "" +
                "]";
    }
}
