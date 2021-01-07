package org.bitcoincashj.core;

import org.bitcoincashj.core.bip47.BIP47PaymentCode;
import org.bitcoincashj.core.slp.SlpAddress;
import org.bitcoincashj.net.NetHelper;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.params.TestNet3Params;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import static org.junit.Assert.*;

public class AddressValidationTests {

    @Test
    public void paymentCodeTests() {
        String paymentCode = "PM8TJR38ZQu4JyFWWRrLZj7fKQUEDmi3bfyWDc4og136vaH8pWYq2xhcbwFK8dsf1XujMKRs3yecGRVV35MMqtoHwchnemRSy7WMzzqYs2qjMPZtGrsc";
        assertTrue(Address.isValidPaymentCode(paymentCode));

        byte[] paymentCodeHash160 = Hex.decode("01000288931be8bbb769268c11345806e7b4006a3649c498216df044cc41066ddfd56215c06a34a8cedb5d10e7e0757252c7a57a60d33a231f24ac5ee81e6a7e7be7c300000000000000000000000000");
        assertTrue(Address.isValidPaymentCode(paymentCodeHash160));
        BIP47PaymentCode bip47PaymentCode = new BIP47PaymentCode(paymentCodeHash160);
        BIP47PaymentCode bip47PaymentCode2 = new BIP47PaymentCode("PM8TJR38ZQu4JyFWWRrLZj7fKQUEDmi3bfyWDc4og136vaH8pWYq2xhcbwFK8dsf1XujMKRs3yecGRVV35MMqtoHwchnemRSy7WMzzqYs2qjMPZtGrsc");
        assertEquals("PM8TJR38ZQu4JyFWWRrLZj7fKQUEDmi3bfyWDc4og136vaH8pWYq2xhcbwFK8dsf1XujMKRs3yecGRVV35MMqtoHwchnemRSy7WMzzqYs2qjMPZtGrsc", bip47PaymentCode.toString());
        assertEquals(bip47PaymentCode.toString(), bip47PaymentCode2.toString());
    }

    @Test
    public void cashAccountTests() {
        String address = new NetHelper().getCashAccountAddress(MainNetParams.get(), "cyberpunk#66149", false);
        assertEquals("PM8TJLAWsHQeTmCzXmUQDsW7ZvBY5v16xMgVHCVq68jyY6miSVhzhYXMwC2fcDz8MY8UQxyAfFzJsGuATyfXkbVAnUu2EYuxfAjLTfk1QBCq9rF7Bz1o", address);

        String p2shTest = new NetHelper().getCashAccountAddress(MainNetParams.get(), "ABCTAX2#84938", false);
        assertEquals("bitcoincash:pqnqv9lt7e5vjyp0w88zf2af0l92l8rxdgnlxww9j9", p2shTest);
    }

    @Test
    public void validateCashAddrs() {
        String cashAddrP2PKHValid = "bitcoincash:qzhr268ppgwtr36h20apc6ahzx6lsmlmcy40qejee0";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHValid));

        String cashAddrP2PKHNoPrefixValid = "qzhr268ppgwtr36h20apc6ahzx6lsmlmcy40qejee0";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixValid));

        String cashAddrP2SHValid = "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHValid));

        String cashAddrP2SHNoPrefixValid = "pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixValid));

        String cashAddrP2PKHInvalid = "bitcoincash:qzhr268ppgwtr36h2smlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHInvalid));

        String cashAddrP2PKHNoPrefixInvalid = "qzhr268ppgwtr36xclsmlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixInvalid));

        String cashAddrP2SHInvalid = "bitcoincash:pqef5axxnrq853nc5cuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHInvalid));

        String cashAddrP2SHNoPrefixInvalid = "pqeft5ukkg9ew2fgt5axxncuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixInvalid));
    }

    @Test
    public void validateLegacy() {
        String legacyP2PKHValid = "1N2LSmQCdL6Sv2B1avm6joiQCzZeeUqDyL";
        String legacyP2PKHInvalid = "1N2LSmQvdL6Sv2B1avm6joiQCzZeeUqDyL";

        assertTrue(Address.isValidLegacyAddress(MainNetParams.get(), legacyP2PKHValid));
        assertFalse(Address.isValidLegacyAddress(MainNetParams.get(), legacyP2PKHInvalid));

        String legacyToCashAddrValid = CashAddressFactory.create().getFromBase58(MainNetParams.get(), legacyP2PKHValid).toString();
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), legacyToCashAddrValid));
    }

    @Test
    public void validateSlp() {
        String legacyP2PKHValid = "1N2LSmQCdL6Sv2B1avm6joiQCzZeeUqDyL";
        String slpP2PKHValid = "qrnfkj0ya6sju73qf2m0cn05j23d5ztlyqteejq984";
        String slpP2SHValid = "simpleledger:pphd9qsajfgwlcx706ed8d7l650f6q36c57lleqapv";
        String slpP2PKHInvalid = "simpleledger:qrnfkj0ya6sju73qf2m0cz05j23d5ztlyqteejq984";
        SlpAddress validSlpFromLegacy = SlpAddressFactory.create().getFromBase58(MainNetParams.get(), legacyP2PKHValid);
        SlpAddress validSlpP2PKH = SlpAddressFactory.create().getFromFormattedAddress(MainNetParams.get(), slpP2PKHValid);
        SlpAddress validSlpP2SH = SlpAddressFactory.create().getFromFormattedAddress(MainNetParams.get(), slpP2SHValid);
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), validSlpP2PKH.toCash().toString()));
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), validSlpP2SH.toCash().toString()));
        assertFalse(Address.isValidSlpAddress(MainNetParams.get(), slpP2PKHInvalid));
        assertEquals("bitcoincash:qrnfkj0ya6sju73qf2m0cn05j23d5ztlyq8zjf49et", validSlpP2PKH.toCash().toString());
        assertEquals("bitcoincash:pphd9qsajfgwlcx706ed8d7l650f6q36c5jy5z4alj", validSlpP2SH.toCash().toString());

        System.out.println(validSlpFromLegacy.toString());
        System.out.println(validSlpP2PKH.toString());
        System.out.println(validSlpP2PKH.toCash().toString());
        System.out.println(validSlpP2SH.toCash().toString());
        System.out.println(validSlpP2PKH.getOutputScriptType());
        System.out.println(validSlpP2SH.getOutputScriptType());
    }

    @Test
    public void validateSlpTestnet() {
        String slpP2PKHValid = "slptest:qz0e574avqxqe2srnqa80jxrm78qvt9jlgdle7uqgt";
        String slpP2SHValid = "slptest:pp2fu0f6tmzrsw48k2n563ecfhgp7ryh3ywhg8py7v";
        SlpAddress validSlpP2PKH = SlpAddressFactory.create().getFromFormattedAddress(TestNet3Params.get(), slpP2PKHValid);
        SlpAddress validSlpP2SH = SlpAddressFactory.create().getFromFormattedAddress(TestNet3Params.get(), slpP2SHValid);
        assertTrue(Address.isValidSlpAddress(TestNet3Params.get(), validSlpP2PKH.toString()));
        assertTrue(Address.isValidCashAddr(TestNet3Params.get(), validSlpP2PKH.toCash().toString()));
        assertTrue(Address.isValidSlpAddress(TestNet3Params.get(), slpP2SHValid));
        assertTrue(Address.isValidCashAddr(TestNet3Params.get(), validSlpP2SH.toCash().toString()));
        assertEquals("bchtest:qz0e574avqxqe2srnqa80jxrm78qvt9jlgkt79xh6k", validSlpP2PKH.toCash().toString());
        assertEquals("bchtest:pp2fu0f6tmzrsw48k2n563ecfhgp7ryh3y4r0umnv3", validSlpP2SH.toCash().toString());
    }

    @Test
    public void testInvalidSlpP2SH() {
        String slpP2SHInvalid = "slptest:pp2fu0f6tmarsw48k2n563ecfhgp7ryh3ywhg8py7v";
        assertFalse(Address.isValidSlpAddress(TestNet3Params.get(), slpP2SHInvalid));
    }

    @Test
    public void validateLegacyTestnet() {
        String legacyP2PKHValid = "mv4rnyY3Su5gjcDNzbMLKBQkBicCtHUtFB";
        String legacyP2PKHInvalid = "mv4rnyY3vu5gjcDNzbMLKBQkBicCtHUtFB";

        assertTrue(Address.isValidLegacyAddress(TestNet3Params.get(), legacyP2PKHValid));
        assertFalse(Address.isValidLegacyAddress(TestNet3Params.get(), legacyP2PKHInvalid));

        String legacyToCashAddrValid = CashAddressFactory.create().getFromBase58(TestNet3Params.get(), legacyP2PKHValid).toString();
        assertTrue(Address.isValidCashAddr(TestNet3Params.get(), legacyToCashAddrValid));
        assertEquals("bchtest:qz0e574avqxqe2srnqa80jxrm78qvt9jlgkt79xh6k", legacyToCashAddrValid);
    }

    @Test
    public void validateSlpType() {
        String slpP2PKHValid = "simpleledger:qrnfkj0ya6sju73qf2m0cn05j23d5ztlyqteejq984";
        String slpP2SHValid = "simpleledger:pphd9qsajfgwlcx706ed8d7l650f6q36c57lleqapv";

        SlpAddress validSlpP2PKH = SlpAddressFactory.create().getFromFormattedAddress(MainNetParams.get(), slpP2PKHValid);
        SlpAddress validSlpP2SH = SlpAddressFactory.create().getFromFormattedAddress(MainNetParams.get(), slpP2SHValid);

        assertFalse(validSlpP2PKH.isP2SHAddress());
        assertTrue(validSlpP2SH.isP2SHAddress());
        assertEquals("bitcoincash:pphd9qsajfgwlcx706ed8d7l650f6q36c5jy5z4alj", validSlpP2SH.toCash().toString());
    }
}
