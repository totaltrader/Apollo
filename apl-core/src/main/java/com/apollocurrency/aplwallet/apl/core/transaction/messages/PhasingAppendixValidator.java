/*
 *  Copyright © 2018-2020 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.transaction.messages;

import com.apollocurrency.aplwallet.apl.core.app.AplException;
import com.apollocurrency.aplwallet.apl.core.app.VoteWeighting;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.entity.blockchain.Block;
import com.apollocurrency.aplwallet.apl.core.entity.blockchain.Transaction;
import com.apollocurrency.aplwallet.apl.core.model.PhasingParams;
import com.apollocurrency.aplwallet.apl.core.service.appdata.TimeService;
import com.apollocurrency.aplwallet.apl.core.service.blockchain.Blockchain;
import com.apollocurrency.aplwallet.apl.core.service.state.PhasingPollService;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.util.Constants;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class PhasingAppendixValidator implements AppendixValidator<PhasingAppendix> {
    private final Blockchain blockchain;
    private final PhasingPollService phasingPollService;
    private final BlockchainConfig blockchainConfig;
    private final TimeService timeService;

    @Inject
    public PhasingAppendixValidator(Blockchain blockchain, PhasingPollService phasingPollService, BlockchainConfig blockchainConfig, TimeService timeService) {
        this.blockchain = blockchain;
        this.phasingPollService = phasingPollService;
        this.blockchainConfig = blockchainConfig;
        this.timeService = timeService;
    }

    @Override
    public  void validate(Transaction transaction, PhasingAppendix appendix, int height) throws AplException.ValidationException {
        generalValidation(transaction, appendix);

        validateFinishHeight(appendix.getFinishHeight(), appendix);
    }

    public void generalValidation(Transaction transaction, PhasingAppendix appendix) throws AplException.ValidationException {
        PhasingParams params = appendix.getParams();
        phasingPollService.validate(params);

        int currentHeight = blockchain.getHeight();
        byte[][] linkedFullHashes = appendix.getLinkedFullHashes();
        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.TRANSACTION) {
            if (linkedFullHashes.length == 0 || linkedFullHashes.length > Constants.MAX_PHASING_LINKED_TRANSACTIONS) {
                throw new AplException.NotValidException("Invalid number of linkedFullHashes " + linkedFullHashes.length);
            }
            Set<Long> linkedTransactionIds = new HashSet<>(linkedFullHashes.length);
            for (byte[] hash : linkedFullHashes) {
                if (Convert.emptyToNull(hash) == null || hash.length != 32) {
                    throw new AplException.NotValidException("Invalid linkedFullHash " + Convert.toHexString(hash));
                }
                if (!linkedTransactionIds.add(Convert.fullHashToId(hash))) {
                    throw new AplException.NotValidException("Duplicate linked transaction ids");
                }
                checkLinkedTransaction(hash, currentHeight, transaction.getHeight());
            }
            if (params.getQuorum() > linkedFullHashes.length) {
                throw new AplException.NotValidException("Quorum of " + params.getQuorum() + " cannot be achieved in by-transaction voting with "
                    + linkedFullHashes.length + " linked full hashes only");
            }
        } else {
            if (linkedFullHashes.length != 0) {
                throw new AplException.NotValidException("LinkedFullHashes can only be used with VotingModel.TRANSACTION");
            }
        }
        byte[] hashedSecret = appendix.getHashedSecret();
        byte algorithm = appendix.getAlgorithm();

        if (params.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
            if (params.getQuorum() != 1) {
                throw new AplException.NotValidException("Quorum must be 1 for by-hash voting");
            }
            if (hashedSecret.length == 0 || hashedSecret.length > Byte.MAX_VALUE) {
                throw new AplException.NotValidException("Invalid hashedSecret " + Convert.toHexString(hashedSecret));
            }
            if (PhasingPollService.getHashFunction(algorithm) == null) {
                throw new AplException.NotValidException("Invalid hashedSecretAlgorithm " + algorithm);
            }
        } else {
            if (hashedSecret.length != 0) {
                throw new AplException.NotValidException("HashedSecret can only be used with VotingModel.HASH");
            }
            if (algorithm != 0) {
                throw new AplException.NotValidException("HashedSecretAlgorithm can only be used with VotingModel.HASH");
            }
        }
    }

    public void validateFinishHeight(Integer finishHeight, PhasingAppendix appendix) throws AplException.NotCurrentlyValidException {
        Block lastBlock = blockchain.getLastBlock();
        int currentHeight = lastBlock.getHeight();
        int appendixFinishHeight = appendix.getFinishHeight();
        if (appendixFinishHeight <= currentHeight + (appendix.getParams().getVoteWeighting().acceptsVotes() ? 2 : 1)
            || appendixFinishHeight >= currentHeight + Constants.MAX_PHASING_DURATION) {
            throw new AplException.NotCurrentlyValidException("Invalid finish height " + finishHeight);
        }

    }

    public void validateFinishHeightAndTime(Integer height, Integer time, PhasingAppendix phasingAppendix) throws AplException.NotCurrentlyValidException {
        int finishHeight = phasingAppendix.getFinishHeight();
        if ((finishHeight != -1 && time != -1) || (finishHeight == -1 && time == -1)) {
            throw new AplException.NotCurrentlyValidException("Only one parameter should be filled 'phasingFinishHeight or phasingFinishTime'");
        }

        Block lastBlock = blockchain.getLastBlock();
        int lastBlockHeight = lastBlock.getHeight();
        int currentTime = timeService.getEpochTime();

        if (time == -1 &&
            (finishHeight <= lastBlockHeight + (phasingAppendix.getParams().getVoteWeighting().acceptsVotes() ? 2 : 1) ||
                finishHeight >= lastBlockHeight + Constants.MAX_PHASING_DURATION)) {
            throw new AplException.NotCurrentlyValidException("Invalid finish height " + height);
        }


        if (finishHeight == -1 && time >= currentTime + Constants.MAX_PHASING_TIME_DURATION_SEC) {
            throw new AplException.NotCurrentlyValidException("Invalid finish time " + time);
        }

    }

    private void checkLinkedTransaction(byte[] hash, int currentHeight, int transactionHeight) throws AplException.NotValidException, AplException.NotCurrentlyValidException {
        Integer txHeight = blockchain.getTransactionHeight(hash, currentHeight);
        if (txHeight != null) {

            if (transactionHeight - txHeight > blockchainConfig.getCurrentConfig().getReferencedTransactionHeightSpan()) {
                throw new AplException.NotValidException("Linked transaction cannot be more than 60 days older than the phased transaction");
            }
            if (phasingPollService.isTransactionPhased(Convert.fullHashToId(hash))) {
                throw new AplException.NotCurrentlyValidException("Cannot link to an already existing phased transaction");
            }
        }
    }

    @Override
    public void validateAtFinish(Transaction transaction, PhasingAppendix phasingAppendix, int blockHeight) throws AplException.ValidationException {
        phasingPollService.checkApprovable(phasingAppendix.getParams());
    }

}
