package org.bitcoincashj.core.flipstarter;

import java.util.ArrayList;

public class FlipstarterPledgePayload {
    public ArrayList<Input> inputs;
    public Data data;
    public String data_signature;

    public static class Input {
        public String previous_output_transaction_hash;
        public long previous_output_index;
        public long sequence_number;
        public String unlocking_script;

        public Input(String prevOutTxId, long prevOutIndex, long sequenceNumber, String unlockingScript) {
            this.previous_output_transaction_hash = prevOutTxId;
            this.previous_output_index = prevOutIndex;
            this.sequence_number = sequenceNumber;
            this.unlocking_script = unlockingScript;
        }
    }

    public static class Data {
        public String alias;
        public String comment;

        public Data(String alias, String comment) {
            this.alias = alias;
            this.comment = comment;
        }
    }

    public FlipstarterPledgePayload(ArrayList<Input> inputs, Data data, String dataSignature) {
        this.inputs = inputs;
        this.data = data;
        this.data_signature = dataSignature;
    }
}
