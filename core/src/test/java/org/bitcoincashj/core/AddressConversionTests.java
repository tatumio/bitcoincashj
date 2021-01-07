package org.bitcoincashj.core;

import org.bitcoincashj.params.MainNetParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AddressConversionTests {

    @Test
    public void validateCashAddrs() {
        NetworkParameters params = MainNetParams.get();
        String legacyAddressP2PKH = "1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk";
        assertEquals("bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff", CashAddressFactory.create().getFromBase58(params, legacyAddressP2PKH).toString());

        String cashAddressP2PKH = "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff";
        String legacyAddress = AddressFactory.create().getAddress(params, cashAddressP2PKH).toBase58();
        assertEquals("1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk", legacyAddress);

        String legacyAddressP2SH = "36JVCGHDv8KaT3aMzrkS8Y2Qh2CHm8Z129";
        String cashAddressP2SH = CashAddressFactory.create().getFromBase58(params, legacyAddressP2SH).toString();
        System.out.println(cashAddressP2SH);
        assertEquals("bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz", cashAddressP2SH);

        System.out.println(SlpAddressFactory.create().getFromCashAddress(params, "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff").toString());
        System.out.println(CashAddressFactory.create().getFromFormattedAddress(params, "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff").getOutputScriptType());
        System.out.println(CashAddressFactory.create().getFromFormattedAddress(params, "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz").getOutputScriptType());
        System.out.println(CashAddressFactory.create().getFromSlpAddress(params, "simpleledger:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu5eccncxhh").toString());
    }
}
