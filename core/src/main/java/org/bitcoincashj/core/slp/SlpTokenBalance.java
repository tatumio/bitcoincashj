package org.bitcoincashj.core.slp;

public class SlpTokenBalance {
    private String tokenId;
    private double balance;

    public SlpTokenBalance(String tokenId, double initialBalance) {
        this.tokenId = tokenId;
        this.balance = initialBalance;
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public double getBalance() {
        return this.balance;
    }

    public void addToBalance(double amount) {
        this.balance += amount;
    }

    public void subtractFromBalance(double amount) {
        this.balance -= amount;
    }

    public void setBalance(double newBalance) {
        this.balance = newBalance;
    }
}
