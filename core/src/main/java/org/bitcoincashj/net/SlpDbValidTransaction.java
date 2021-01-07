package org.bitcoincashj.net;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class SlpDbValidTransaction {
    private String json;

    public SlpDbValidTransaction(String txId) {
        JSONObject json = new JSONObject();
        json.put("v", 3);
        JSONObject q = new JSONObject();
        q.put("db", new JSONArray().put("c").put("u"));
        JSONObject findJson = new JSONObject();
        findJson.put("tx.h", txId);
        findJson.put("slp.valid", true);
        q.put("find", findJson);
        JSONObject project = new JSONObject();
        project.put("tx.h", 1);
        project.put("slp.valid", 1);
        project.put("_id", 0);
        q.put("project", project);
        q.put("limit", 10);
        json.put("q", q);
        JSONObject r = new JSONObject();
        r.put("f", "[.[] | {valid: .slp.valid}]");
        json.put("r", r);

        this.json = json.toString();
    }

    public String getJson() {
        return this.json;
    }

    public String getEncoded() {
        return new String(Base64.encode(this.json.getBytes()), StandardCharsets.UTF_8);
    }
}
