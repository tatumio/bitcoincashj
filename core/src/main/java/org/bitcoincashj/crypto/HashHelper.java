package org.bitcoincashj.crypto;

import org.bouncycastle.util.encoders.Hex;

import java.security.MessageDigest;

public class HashHelper {

    public String getCashAccountCollision(String block, String txHash) {
        String collisionNumber;
        String concatenatedTest = block.toLowerCase() + txHash.toLowerCase();
        String hashedConcatenated = SHA256_Hex(concatenatedTest);
        String firstFourBytes = hashedConcatenated.substring(0, 8);
        long decimalNotation = getUnsignedInt(hexToDecimal(firstFourBytes));
        String reverseDecimalNotation = new StringBuilder(decimalNotation + "").reverse().toString();
        String paddedDecimal = padString(reverseDecimalNotation);
        collisionNumber = paddedDecimal;

        return collisionNumber;

    }

    private static String SHA256_Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Hex.decode(value.getBytes()));
            return bytesToHex(md.digest());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    private int hexToDecimal(String hex) {
        String digits = "0123456789ABCDEF";
        hex = hex.toUpperCase();
        int val = 0;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            int d = digits.indexOf(c);
            val = 16 * val + d;
        }
        return val;
    }

    /*
    Using Long.parseUnsignedLong(hexString, 16); instead would require a higher minimum API in the build.gradle file, which means fewer supported devices
    So, we are using this amazing hack.
     */
    private long getUnsignedInt(int x) {
        return x & 0x00000000ffffffffL;
    }

    private String padString(String input) {
        int length = input.length();
        StringBuilder newString = new StringBuilder(input);
        if (length < 10) {
            for (int x = length; x < 10; x++)
                newString.append("0");
        }

        return newString.toString();
    }
}
