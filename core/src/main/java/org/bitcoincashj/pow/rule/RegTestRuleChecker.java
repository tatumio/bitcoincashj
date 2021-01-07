package org.bitcoincashj.pow.rule;

import org.bitcoincashj.core.*;
import org.bitcoincashj.pow.AbstractPowRulesChecker;
import org.bitcoincashj.store.BlockStore;
import org.bitcoincashj.store.BlockStoreException;

public class RegTestRuleChecker extends AbstractPowRulesChecker {
    public RegTestRuleChecker(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    public void checkRules(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore,
                           AbstractBlockChain blockChain) throws VerificationException, BlockStoreException {
        // always pass
    }
}
