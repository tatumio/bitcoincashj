/*
 * Copyright 2018 the bitcoinj-cash developers
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

package org.bitcoincashj.pow.factory;

import org.bitcoincashj.core.Block;
import org.bitcoincashj.core.NetworkParameters;
import org.bitcoincashj.core.StoredBlock;
import org.bitcoincashj.pow.AbstractRuleCheckerFactory;
import org.bitcoincashj.pow.RulesPoolChecker;
import org.bitcoincashj.pow.rule.AsertDifficultyRuleChecker;
import org.bitcoincashj.store.BlockStore;

public class AsertRuleCheckerFactory extends AbstractRuleCheckerFactory {

    public AsertRuleCheckerFactory(NetworkParameters parameters) {
        super(parameters);
    }

    @Override
    public RulesPoolChecker getRuleChecker(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) {
        RulesPoolChecker rulesChecker = new RulesPoolChecker(networkParameters);
        rulesChecker.addRule(new AsertDifficultyRuleChecker(networkParameters));
        return rulesChecker;
    }

}
