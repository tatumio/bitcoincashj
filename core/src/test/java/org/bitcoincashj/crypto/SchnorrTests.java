package org.bitcoincashj.crypto;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Scanner;

public class SchnorrTests {
    /*
    Credit goes to:
    https://github.com/miketwk/bip-schnorr-java
     */
    @Test
    public void runSchnorrTests() {
        boolean all_passed=true;
        Scanner scanner=null;
        try {
            scanner=new Scanner(new File(SchnorrTests.class.getResource("schnorr-test-vectors.csv").getPath()));
            scanner.nextLine(); //heading
            while(scanner.hasNextLine()) {
                String row=scanner.nextLine();
                int pos=row.indexOf(",");

                String index=row.substring(0, pos).trim();
                String seckey=row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim();
                byte[] pubkey=SchnorrSignature.hexStringToByteArray(row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim());
                byte[] msg=SchnorrSignature.hexStringToByteArray(row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim());
                String sig=row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim();
                boolean result="TRUE".equals(row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim());
                String comment=row.indexOf(",", pos+1)==-1 ? "" : row.substring(pos+1, pos=row.indexOf(",", pos+1)).trim();

                System.out.println("\nTest vector "+index+":");
                if(!"".equals(seckey)) {
                    BigInteger seckeyNum=new BigInteger(seckey,16);
                    String sig_actual=SchnorrSignature.bytesToHex(SchnorrSignature.schnorr_sign(msg, seckeyNum));
                    if(sig.equals(sig_actual))
                        System.out.println(" * Passed signing test.");
                    else {
                        System.out.println(" * Failed signing test.");
                        System.out.println("   Excepted signature:"+ sig);
                        System.out.println("   Actual signature:"+ sig_actual);
                        all_passed = false;
                    }
                }
                boolean result_actual = SchnorrSignature.schnorr_verify(msg, pubkey, SchnorrSignature.hexStringToByteArray(sig));
                if(result==result_actual)
                    System.out.println(" * Passed verification test.");
                else {
                    System.out.println(" * Failed verification test.");
                    System.out.println("   Excepted verification result:"+ result);
                    System.out.println("     Actual verification result:"+ result_actual);
                    if(!"".equals(comment))
                        System.out.println("   Comment:"+ comment);
                    all_passed = false;
                }
            }

            if(all_passed)
                System.out.println("All test vectors passed.");
            else
                System.out.println("Some test vectors failed.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if(scanner!=null)
                scanner.close();
        }
    }
}
