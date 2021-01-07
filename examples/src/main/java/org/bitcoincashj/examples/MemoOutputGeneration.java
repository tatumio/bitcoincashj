/*
 * Copyright by the original author or authors.
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

package org.bitcoincashj.examples;

import org.bitcoincashj.core.*;
import org.bitcoincashj.core.memo.MemoOpReturnOutput;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.wallet.SendRequest;
import org.bitcoincashj.wallet.Wallet;

/**
 * The following example shows you how to create a SendRequest to send coins from a wallet to a given address.
 */
public class MemoOutputGeneration {

    public static void main(String[] args) throws Exception {

        //Create transaction for memo action
        SendRequest req = SendRequest.memoAction(Wallet.createBasic(MainNetParams.get()), new MemoOpReturnOutput.SetName("myusername"));

        //Memo action example
        MemoOpReturnOutput memoOutput = new MemoOpReturnOutput.PostMemo("hello world!");
        System.out.println(memoOutput.getScript());

        //Adding that action as an output in a transaction
        SendRequest req2 = SendRequest.forTx(new Transaction(MainNetParams.get()));
        req2.tx.addOutput(Coin.ZERO, memoOutput.getScript());
    }
}
