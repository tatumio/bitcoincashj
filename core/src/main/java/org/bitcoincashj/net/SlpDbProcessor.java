package org.bitcoincashj.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class SlpDbProcessor {
    private String[] slpDbInstances = new String[]{"slpdb.fountainhead.cash", "slpserve.imaginary.cash"};
    private String slpDbEndpoint = "/q/";

    public SlpDbProcessor() {

    }

    private String readData(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public JSONObject getTokenData(String base64Query) {
        int tries = 12;
        int backOff = 1000;
        JSONObject tokenObj = null;
        for (int x = tries; x > 0; x--) {
            int randServer = new Random().nextInt(slpDbInstances.length);
            String slpDbServer = slpDbInstances[randServer];

            try (InputStream is = new URL("https://" + slpDbServer + slpDbEndpoint + base64Query).openStream()) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String jsonText = readData(rd);
                JSONArray tokenArray = new JSONObject(jsonText).getJSONArray("t");

                if (tokenArray.length() > 0) {
                    tokenObj = tokenArray.getJSONObject(0);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(backOff);
                    backOff *= 2;
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return tokenObj;
    }

    public boolean isValidSlpTx(String base64Query) {
        boolean valid = false;
        int tries = 12;
        int backOff = 1000;
        //while(!valid && tries > 0) {
        for (int x = tries; x > 0; x--) {
            int randServer = new Random().nextInt(slpDbInstances.length);
            String slpDbServer = slpDbInstances[randServer];
            try (InputStream is = new URL("https://" + slpDbServer + slpDbEndpoint + base64Query).openStream()) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String jsonText = readData(rd);
                JSONArray confirmedArray = new JSONObject(jsonText).getJSONArray("c");
                JSONArray unconfirmedArray = new JSONObject(jsonText).getJSONArray("u");

                if (confirmedArray.length() > 0) {
                    valid = confirmedArray.getJSONObject(0).getBoolean("valid");
                    return valid;
                } else if (unconfirmedArray.length() > 0) {
                    valid = unconfirmedArray.getJSONObject(0).getBoolean("valid");
                    return valid;
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(backOff);
                    backOff *= 2;
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return valid;
    }
}
