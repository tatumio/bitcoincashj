/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
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

package org.bitcoincashj.params;

import com.google.common.base.Preconditions;
import org.bitcoincashj.core.*;
import org.bitcoincashj.store.BlockStore;
import org.bitcoincashj.store.BlockStoreException;
import org.bitcoincashj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * Parameters for Bitcoin-like networks.
 */
public abstract class AbstractBitcoinNetParams extends NetworkParameters {

    /**
     * Scheme part for Bitcoin URIs.
     */
    public static final String BITCOIN_SCHEME = "bitcoincash";

    public static final int REWARD_HALVING_INTERVAL = 210000;
    public static final int MAX_BITS = 0x1d00ffff;
    public static final String MAX_BITS_STRING = "1d00ffff";
    public static final BigInteger MAX_TARGET = Utils.decodeCompactBits(MAX_BITS);

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    private static BigInteger LARGEST_HASH = BigInteger.ONE.shiftLeft(256);

    private static final Logger log = LoggerFactory.getLogger(AbstractBitcoinNetParams.class);

    public AbstractBitcoinNetParams() {
        super();
        interval = TARGET_TIMESPAN / TARGET_SPACING;
        subsidyDecreaseBlockCount = 210000;
    }

    /**
     * Checks if we are at a reward halving point.
     *
     * @param previousHeight The height of the previous stored block
     * @return If this is a reward halving point
     */
    public final boolean isRewardHalvingPoint(final int previousHeight) {
        return ((previousHeight + 1) % REWARD_HALVING_INTERVAL) == 0;
    }

    /**
     * <p>A utility method that calculates how much new Bitcoin would be created by the block at the given height.
     * The inflation of Bitcoin is predictable and drops roughly every 4 years (210,000 blocks). At the dawn of
     * the system it was 50 coins per block, in late 2012 it went to 25 coins per block, and so on. The size of
     * a coinbase transaction is inflation plus fees.</p>
     *
     * <p>The half-life is controlled by {@link NetworkParameters#getSubsidyDecreaseBlockCount()}.</p>
     *
     * @param height the height of the block to calculate inflation for
     */
    public Coin getBlockInflation(int height) {
        return Coin.FIFTY_COINS.shiftRight(height / getSubsidyDecreaseBlockCount());
    }

    /**
     * Checks if we are at a difficulty transition point.
     *
     * @param storedPrev The previous stored block
     * @param parameters The network parameters
     * @return If this is a difficulty transition point
     */
    public static boolean isDifficultyTransitionPoint(StoredBlock storedPrev, NetworkParameters parameters) {
        return ((storedPrev.getHeight() + 1) % parameters.getInterval()) == 0;
    }

    public static boolean isDifficultyTransitionPoint(int height, NetworkParameters parameters) {
        return ((height + 1) % parameters.getInterval()) == 0;
    }

    /**
     * determines whether monolith upgrade is activated based on MTP
     *
     * @param storedPrev The previous stored block
     * @param store      BlockStore containing at least 11 blocks
     * @param parameters The network parameters
     * @return
     */
    public static boolean isMonolithEnabled(StoredBlock storedPrev, BlockStore store, NetworkParameters parameters) {
        if (storedPrev.getHeight() < 524626) { //current height at time of writing, well below the activation block height
            return false;
        }
        try {
            long mtp = BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, store);
            return isMonolithEnabled(mtp, parameters);
        } catch (BlockStoreException e) {
            throw new RuntimeException("Cannot determine monolith activation without BlockStore");
        }
    }

    /**
     * Compute the a target based on the work done between 2 blocks and the time
     * required to produce that work.
     */
    public static BigInteger ComputeTarget(StoredBlock pindexFirst,
                                           StoredBlock pindexLast) {

        Preconditions.checkState(pindexLast.getHeight() > pindexFirst.getHeight());

        /*
         * From the total work done and the time it took to produce that much work,
         * we can deduce how much work we expect to be produced in the targeted time
         * between blocks.
         */
        BigInteger work = pindexLast.getChainWork().subtract(pindexFirst.getChainWork());
        work = work.multiply(BigInteger.valueOf(TARGET_SPACING));

        // In order to avoid difficulty cliffs, we bound the amplitude of the
        // adjustement we are going to do.
        //assert(pindexLast->nTime > pindexFirst->nTime);
        long nActualTimespan = pindexLast.getHeader().getTimeSeconds() - pindexFirst.getHeader().getTimeSeconds();
        if (nActualTimespan > 288 * TARGET_SPACING) {
            nActualTimespan = 288 * TARGET_SPACING;
        } else if (nActualTimespan < 72 * TARGET_SPACING) {
            nActualTimespan = 72 * TARGET_SPACING;
        }

        work = work.divide(BigInteger.valueOf(nActualTimespan));

        /*
         * We need to compute T = (2^256 / W) - 1 but 2^256 doesn't fit in 256 bits.
         * By expressing 1 as W / W, we get (2^256 - W) / W, and we can compute
         * 2^256 - W as the complement of W.
         */
        return LARGEST_HASH.divide(work).subtract(BigInteger.ONE);//target.add(BigInteger.ONE))
    }

    /**
     * Compute aserti-2d DAA target
     */
    public static BigInteger computeAsertTarget(final NetworkParameters networkParameters, BigInteger refTarget, BigInteger referenceBlockAncestorTime, BigInteger referenceBlockHeight,
                                                BigInteger evalBlockTime, BigInteger evalBlockHeight, StoredBlock storedPrev, Block nextBlock) {
        Preconditions.checkState(evalBlockHeight.compareTo(referenceBlockHeight) >= 0);

        if (storedPrev != null && nextBlock != null) {
            if (networkParameters.allowMinDifficultyBlocks() &&
                    (nextBlock.getTimeSeconds() >
                            storedPrev.getHeader().getTimeSeconds() + 2 * TARGET_SPACING)) {
                return new BigInteger(MAX_BITS_STRING, 16);
            }
        }

        BigInteger heightDiff = evalBlockHeight.subtract(referenceBlockHeight);
        BigInteger timeDiff = evalBlockTime.subtract(referenceBlockAncestorTime);
        //used by asert. two days in seconds.
        BigInteger halfLife = BigInteger.valueOf(networkParameters.getAsertHalfLife());
        BigInteger rbits = BigInteger.valueOf(16L);
        BigInteger radix = BigInteger.ONE.shiftLeft(rbits.intValue());

        BigInteger target = refTarget;
        BigInteger exponent;
        BigInteger heightDiffWithOffset = heightDiff.add(BigInteger.ONE);
        BigInteger targetHeightOffsetMultiple = TARGET_SPACING_BIGINT.multiply(heightDiffWithOffset);
        exponent = timeDiff.subtract(targetHeightOffsetMultiple);
        exponent = exponent.shiftLeft(rbits.intValue());
        exponent = exponent.divide(halfLife);
        BigInteger numShifts = exponent.shiftRight(rbits.intValue());
        exponent = exponent.subtract(numShifts.shiftLeft(rbits.intValue()));

        BigInteger factor = BigInteger.valueOf(195766423245049L).multiply(exponent);
        factor = factor.add(BigInteger.valueOf(971821376L).multiply(exponent.pow(2)));
        factor = factor.add(BigInteger.valueOf(5127L).multiply(exponent.pow(3)));
        factor = factor.add(BigInteger.valueOf(2L).pow(47));
        factor = factor.shiftRight(48);
        target = target.multiply(radix.add(factor));

        if (numShifts.compareTo(BigInteger.ZERO) < 0) {
            target = target.shiftRight(-numShifts.intValue());
        } else {
            target = target.shiftLeft(numShifts.intValue());
        }

        target = target.shiftRight(16);

        if (target.equals(BigInteger.ZERO)) {
            return BigInteger.valueOf(Utils.encodeCompactBits(BigInteger.ONE));
        }
        if (target.compareTo(MAX_TARGET) > 0) {
            return new BigInteger(MAX_BITS_STRING, 16);
        }

        return BigInteger.valueOf(Utils.encodeCompactBits(target));
    }

    public static BigInteger computeAsertTarget(NetworkParameters networkParameters, BigInteger refTarget, BigInteger referenceBlockAncestorTime, BigInteger referenceBlockHeight,
                                                BigInteger evalBlockTime, BigInteger evalBlockHeight) {
        return computeAsertTarget(networkParameters, refTarget, referenceBlockAncestorTime, referenceBlockHeight, evalBlockTime, evalBlockHeight, null, null);
    }

    public static BigInteger computeAsertTarget(NetworkParameters networkParameters, int referenceBlockBits, BigInteger referenceBlockAncestorTime, BigInteger referenceBlockHeight,
                                                BigInteger evalBlockTime, BigInteger evalBlockHeight) {
        BigInteger refTarget = Utils.decodeCompactBits(referenceBlockBits);
        return computeAsertTarget(networkParameters, refTarget, referenceBlockAncestorTime, referenceBlockHeight, evalBlockTime, evalBlockHeight);
    }

    /**
     * determines whether monolith upgrade is activated based on the given MTP.  Useful for overriding MTP for testing.
     *
     * @param medianTimePast
     * @param parameters     The network parameters
     * @return
     */
    public static boolean isMonolithEnabled(long medianTimePast, NetworkParameters parameters) {
        return medianTimePast >= parameters.getMonolithActivationTime();
    }

    public static boolean isAsertEnabled(StoredBlock storedPrev, BlockStore blockStore, NetworkParameters parameters) {
        try {
            long mtp = BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, blockStore);
            return mtp >= parameters.getAsertUpdateTime();
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public Coin getMaxMoney() {
        return MAX_MONEY;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return Transaction.MIN_NONDUST_OUTPUT;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return new MonetaryFormat();
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        return version.getBitcoinProtocolVersion();
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return new BitcoinSerializer(this, parseRetain);
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }
}
