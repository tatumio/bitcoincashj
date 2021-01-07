/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2017 Thomas König
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoincashj.script;

import org.bitcoincashj.core.*;
import org.bitcoincashj.params.MainNetParams;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import static org.bitcoincashj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class ScriptReverseBytesTest {
    @Test
    public void testReverseBytes() throws Exception {
        byte[] opReverseBytes = HEX.decode("6173616e74616c69766564617361646576696c61746e617361");
        ScriptBuilder opReverseBytesTestScriptBuilder = new ScriptBuilder().data(opReverseBytes).op(ScriptOpCodes.OP_DUP).op(ScriptOpCodes.OP_REVERSEBYTES).op(ScriptOpCodes.OP_EQUAL);
        Script opReverseBytesTestScript = opReverseBytesTestScriptBuilder.build();

        LinkedList<byte[]> stack = new LinkedList<>();
        Script.executeScript(null, 0, opReverseBytesTestScript, stack, Script.ALL_VERIFY_FLAGS);
        byte[] success = new byte[]{1};
        assertEquals(success[0], stack.get(0)[0]);

        byte[] opReverseBytesInvalid = HEX.decode("116173616e74616c69766564617361646576696c61746e617361");
        ScriptBuilder opReverseBytesTestScriptInvalidBuilder = new ScriptBuilder().data(opReverseBytesInvalid).op(ScriptOpCodes.OP_DUP).op(ScriptOpCodes.OP_REVERSEBYTES).op(ScriptOpCodes.OP_EQUAL);
        Script opReverseBytesTestScriptInvalid = opReverseBytesTestScriptInvalidBuilder.build();

        LinkedList<byte[]> stackInvalid = new LinkedList<>();
        Script.executeScript(null, 0, opReverseBytesTestScriptInvalid, stackInvalid, Script.ALL_VERIFY_FLAGS);
        assertEquals(0, stackInvalid.get(0).length);
    }

    @Test
    public void redeemTobiasExample() {
        /*
        This test takes Tobias Ruck's example OP_REVERSEBYTES P2SH address.
        Tobias deposited coins into 9 UTXOs to 3PVEFPCAVBuqq4qtsnPMiadAgFLDXrq6id
        The tx: 21e8c0172050469425f2d699b96bbf638db8143e9223a257ef4032dc9816ac62
        The raw tx: 0100000001adb1f29a49e600513ce64277f827116f0b78092a57bfaa5d3e36774ae65570b802000000644174d3d48575092bc9deb78f64d310e4004ccff246bfaa18901f28d8e396412862b01f65b70547f26a0d55f2aa36f5ab5f225655890135e612158adef95a11bb8d4121033cfa001d54e0ba57ca874685cc25992ada3a8ae43856b7be2ba3a482fbcd9f6cfeffffff0be80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd7302188784f80500000000001976a9141e5fa394f88399d1c879bc3ce61113838ef7cd6d88ac5ab10900
        This test takes an output from the deposit tx, and creates the proper redeem script to spend the coins.
        The script is OP_DUP OP_REVERSEBYTES OP_EQUAL.
         */
        NetworkParameters params = MainNetParams.get();
        ScriptBuilder originalScriptBuilder = new ScriptBuilder().op(ScriptOpCodes.OP_DUP).op(ScriptOpCodes.OP_REVERSEBYTES).op(ScriptOpCodes.OP_EQUAL);
        Script originalScript = originalScriptBuilder.build();
        String legacyAddress = ScriptBuilder.createP2SHOutputScript(originalScript).getToAddress(params).toString();
        System.out.println(legacyAddress);

        Transaction tobiasTx = new Transaction(params, Hex.decode("0100000001adb1f29a49e600513ce64277f827116f0b78092a57bfaa5d3e36774ae65570b802000000644174d3d48575092bc9deb78f64d310e4004ccff246bfaa18901f28d8e396412862b01f65b70547f26a0d55f2aa36f5ab5f225655890135e612158adef95a11bb8d4121033cfa001d54e0ba57ca874685cc25992ada3a8ae43856b7be2ba3a482fbcd9f6cfeffffff0be80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd73021887e80300000000000017a914ef18105eb5b3189c0c1f91c5c9c182addd7302188784f80500000000001976a9141e5fa394f88399d1c879bc3ce61113838ef7cd6d88ac5ab10900"));
        TransactionOutput utxo = tobiasTx.getOutput(8);
        Transaction spendTx = new Transaction(params);
        spendTx.setVersion(2);
        TransactionInput input = spendTx.addInput(utxo);
        String scriptHex = Integer.toHexString(ScriptOpCodes.OP_DUP) + Integer.toHexString(ScriptOpCodes.OP_REVERSEBYTES) + Integer.toHexString(ScriptOpCodes.OP_EQUAL);
        ScriptBuilder redeemScriptBuilder = new ScriptBuilder().data(Hex.decode("616e7574666f72616a61726f6674756e61")).data(Hex.decode(scriptHex));
        Script redeemScript = redeemScriptBuilder.build();
        spendTx.addOutput(utxo.getMinNonDustValue(), CashAddressFactory.create().getFromFormattedAddress(params, "bitcoincash:qzznfgmmxznnhdngj0564mqfezu33wcps577em9prn"));
        input.setScriptSig(redeemScript);
        byte[] serializedTx = spendTx.bitcoinSerialize();
        byte[] hexEncodedTx = Hex.encode(serializedTx);
        String rawTxString = new String(hexEncodedTx, StandardCharsets.UTF_8);
        System.out.println(rawTxString);
    }
}
