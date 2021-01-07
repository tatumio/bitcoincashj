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

package org.bitcoincashj.wallet;

import org.bitcoincashj.crypto.HDPath;
import org.bitcoincashj.script.Script;

/**
 * Defines a structure for hierarchical deterministic wallets.
 */
public interface KeyChainGroupStructure {
    /**
     * Map desired output script type to an account path
     */
    HDPath accountPathFor(Script.ScriptType outputScriptType);

    /**
     * Default {@link KeyChainGroupStructure} implementation. Based on BIP32 "Wallet structure".
     */
    KeyChainGroupStructure DEFAULT = new KeyChainGroupStructure() {
        @Override
        public HDPath accountPathFor(Script.ScriptType outputScriptType) {
            if (outputScriptType == null || outputScriptType == Script.ScriptType.P2PKH)
                return DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH;
            else
                throw new IllegalArgumentException(outputScriptType.toString());
        }
    };

    KeyChainGroupStructure SLP = new KeyChainGroupStructure() {
        @Override
        public HDPath accountPathFor(Script.ScriptType outputScriptType) {
            if (outputScriptType == null || outputScriptType == Script.ScriptType.P2PKH)
                return DeterministicKeyChain.BIP44_ACCOUNT_SLP_PATH;
            else
                throw new IllegalArgumentException(outputScriptType.toString());
        }
    };
}
