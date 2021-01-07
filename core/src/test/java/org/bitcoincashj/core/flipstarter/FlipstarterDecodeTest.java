package org.bitcoincashj.core.flipstarter;

import com.google.gson.Gson;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class FlipstarterDecodeTest {
    @Test
    public void runDecode() {
        String payload = "ewAiAG8AdQB0AHAAdQB0AHMAIgA6AFsAewAiAHYAYQBsAHUAZQAiADoANQAyADAAMAAwADAAMAAwADAAMAAsACIAYQBkAGQAcgBlAHMAcwAiADoAIgBiAGkAdABjAG8AaQBuAGMAYQBzAGgAOgBxAHEAYQBlAGQAdgBjAGMAaAA5AG0AbABjADAAaAA0AGgAYQA1AGoAbAA5ADAAcgAzAHMAMgB0AHcAawA1AGgANgBjADkAYwBnAGYAZAB6ADIAegAiAH0AXQAsACIAZABhAHQAYQAiADoAewAiAGEAbABpAGEAcwAiADoAIgAnBkQGRAY5BkYGKQYiACwAIgBjAG8AbQBtAGUAbgB0ACIAOgAiACcGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBicGRAZEBjkGRgYpBiIAfQAsACIAZABvAG4AYQB0AGkAbwBuACIAOgB7ACIAYQBtAG8AdQBuAHQAIgA6ADEAMwA3ADMAOAAxADcAOAB9ACwAIgBlAHgAcABpAHIAZQBzACIAOgAxADYAMAA2ADMANAA4ADgAMAAwAH0A";
        byte[] decodedPayload = Base64.decode(payload);
        String json = new String(decodedPayload, StandardCharsets.UTF_16LE);
        System.out.println(json);
        FlipstarterInvoicePayload invoicePayload = new Gson().fromJson(json, FlipstarterInvoicePayload.class);
        System.out.println(invoicePayload.outputs.get(0).address);
    }

    @Test
    public void runSignatureDecode() {
        //Unlocking script flags are Sighash.ALL, Sighash.FORKID, and Sighash.ANYONECANPAY
        String payload = "eyJpbnB1dHMiOiBbeyJwcmV2aW91c19vdXRwdXRfdHJhbnNhY3Rpb25faGFzaCI6ICIxMjkxNmY5ZWVhZjYzMWQ3MzEwNjU3YzE2NGJkMzM0MjUwYzJhNjY1ZjhiZjNkZGE2MWNlNzdhOTg0NDc3ODg0IiwgInByZXZpb3VzX291dHB1dF9pbmRleCI6IDAsICJzZXF1ZW5jZV9udW1iZXIiOiA0Mjk0OTY3Mjk1LCAidW5sb2NraW5nX3NjcmlwdCI6ICI0ODMwNDUwMjIxMDA4MDFmYjJhMGYwNmEzNDQ1NWU4YjQzNWZjZTRlNTkzZTA5M2MwYzI1ZWE0OGE1N2UzN2IwZGJiNmY5ODI1OTU1MDIyMDc1NGVlZGU0OTEwZDAxNDUxM2FiNjI3NjA5YmIwZWY4NGFmZTZkZDdmYmEzY2ZlZDc4Y2Q4MmYyMzBlODAwN2ZjMTIxMDM1YmQxYWRlOTQwZThiMGEzNDA4ZWRhNTBkZDVkYmEyZWExYmQ2MTVjNzBkZTc3MzYxYjNlNjAwMTMzZjQyM2Q0In1dLCAiZGF0YSI6IHsiYWxpYXMiOiAiIiwgImNvbW1lbnQiOiAiIn0sICJkYXRhX3NpZ25hdHVyZSI6IG51bGx9";
        byte[] decodedPayload = Base64.decode(payload);
        String json = new String(decodedPayload, StandardCharsets.UTF_8);
        System.out.println(json);
        FlipstarterPledgePayload pledgePayload = new Gson().fromJson(json, FlipstarterPledgePayload.class);
        System.out.println(pledgePayload.inputs.get(0).unlocking_script);
    }
}
