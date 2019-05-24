/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.app;

import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.db.BlockDao;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DbIterator;
import com.apollocurrency.aplwallet.apl.core.db.TransactionalDataSource;
import com.apollocurrency.aplwallet.apl.core.db.cdi.Transactional;
import com.apollocurrency.aplwallet.apl.core.db.dao.BlockIndexDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.TransactionIndexDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.BlockIndex;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.TransactionIndex;
import com.apollocurrency.aplwallet.apl.core.shard.ShardManagement;
import com.apollocurrency.aplwallet.apl.core.transaction.PrunableTransaction;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.util.AplException;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BlockchainImpl implements Blockchain {

    private BlockDao blockDao;
    private TransactionDao transactionDao;
    private BlockchainConfig blockchainConfig;
    private EpochTime timeService;
    private PropertiesHolder propertiesHolder;
    private TransactionIndexDao transactionIndexDao;
    private BlockIndexDao blockIndexDao;
    private DatabaseManager databaseManager;

    public BlockchainImpl() {
    }

    @Inject
    public BlockchainImpl(BlockDao blockDao, TransactionDao transactionDao, BlockchainConfig blockchainConfig, EpochTime timeService,
                          PropertiesHolder propertiesHolder, TransactionIndexDao transactionIndexDao, BlockIndexDao blockIndexDao, DatabaseManager databaseManager) {
        this.blockDao = blockDao;
        this.transactionDao = transactionDao;
        this.blockchainConfig = blockchainConfig;
        this.timeService = timeService;
        this.propertiesHolder = propertiesHolder;
        this.transactionIndexDao = transactionIndexDao;
        this.blockIndexDao = blockIndexDao;
        this.databaseManager = databaseManager;
    }

    private final AtomicReference<Block> lastBlock = new AtomicReference<>();



    @Override
    public Block getLastBlock() {
        return lastBlock.get();
    }

    public void setLastBlock(Block block) {
        lastBlock.set(block);
    }

    @Override
    public int getHeight() {
        Block last = lastBlock.get();
        return last == null ? 0 : last.getHeight();
    }

    @Override
    public int getLastBlockTimestamp() {
        Block last = lastBlock.get();
        return last == null ? 0 : last.getTimestamp();
    }

    @Override
    public Block getLastBlock(int timestamp) {
        Block block = lastBlock.get();
        if (timestamp >= block.getTimestamp()) {
            return block;
        }
        return blockDao.findLastBlock(timestamp);
    }

    @Override
    @Transactional(readOnly = true)
    public Block getBlock(long blockId) {
        Block block = lastBlock.get();
        if (block.getId() == blockId) {
            return block;
        }
        TransactionalDataSource dataSource = getDataSourceWithSharding(blockId);

        return blockDao.findBlock(blockId, dataSource);
    }

    @Override
    public boolean hasBlock(long blockId) {
        return lastBlock.get().getId() == blockId || blockDao.hasBlock(blockId);
    }

/*
    @Override
    public DbIterator<Block> getAllBlocks() {
        return lookupBlockDao().getAllBlocks();
    }
*/

    @Override
    public DbIterator<Block> getBlocks(int from, int to) {
        int blockchainHeight = getHeight();
        int calculatedFrom = blockchainHeight - from;
        int calculatedTo = blockchainHeight - to;
        return blockDao.getBlocks(calculatedFrom, calculatedTo);
    }

/*
    @Override
    public DbIterator<Block> getBlocks(long accountId, int timestamp) {
        return getBlocks(accountId, timestamp, 0, -1);
    }
*/

    @Override
    public DbIterator<Block> getBlocks(long accountId, int timestamp, int from, int to) {
        return blockDao.getBlocks(accountId, timestamp, from, to);
    }

    @Override
    public Block findLastBlock() {
        return blockDao.findLastBlock();
    }

    @Override
    public Block loadBlock(Connection con, ResultSet rs, boolean loadTransactions) {
        return blockDao.loadBlock(con, rs, loadTransactions);
    }

    @Override
    public void saveBlock(Connection con, Block block) {
        blockDao.saveBlock(con, block);
    }

    @Override
    public void commit(Block block) {
        blockDao.commit(block);
    }

    @Override
    public int getBlockCount(long accountId) {
        return blockDao.getBlockCount(accountId);
    }

/*
    @Override
    public DbIterator<Block> getBlocks(Connection con, PreparedStatement pstmt) {
        return lookupBlockDao().getBlocks(con, pstmt);
    }
*/

    @Override
    @Transactional(readOnly = true)
    public List<Long> getBlockIdsAfter(long blockId, int limit) {
        // Check the block cache

        List<Long> result = new ArrayList<>(limit);

//        synchronized (blockDao.getBlockCache()) {
//            Block block = blockDao.getBlockCache().get(blockId);
//            if (block != null) {
//                Collection<Block> cacheMap = blockDao.getHeightMap().tailMap(block.getHeight() + 1).values();
//                for (Block cacheBlock : cacheMap) {
//                    if (result.size() >= limit) {
//                        break;
//                    }
//                    result.add(cacheBlock.getId());
//                }
//                return result;
//            }
//        }
        BlockIndex blockIndex = blockIndexDao.getByBlockId(blockId);
        if (blockIndex != null) {
            result.addAll(blockIndexDao.getBlockIdsAfter(blockIndex.getBlockHeight(), limit));
        }
        long lastBlockId = blockId;
        int idsRemaining = limit;
        if (result.size() > 0 && result.size() < limit) {
            lastBlockId = result.get(result.size() - 1);
            idsRemaining -= result.size();
        }
        List<Long> remainingIds = blockDao.getBlockIdsAfter(lastBlockId, idsRemaining);
        result.addAll(remainingIds);
        return result;
    }

    @Override
    public List<byte[]> getBlockSignaturesFrom(int fromHeight, int toHeight) {
        return blockDao.getBlockSignaturesFrom(fromHeight, toHeight);
    }


    @Override
    public List<Block> getBlocksAfter(long blockId, List<Long> blockIdList) {
        // Check the block cache
        if (blockIdList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Block> result = new ArrayList<>();
        TransactionalDataSource dataSource;
        long fromBlockId = blockId;
        int prevSize;
        do {
            dataSource = getDataSourceWithSharding(fromBlockId); //should return datasource, where such block exist or default datasource
            prevSize = result.size();
            blockDao.getBlocksAfter(fromBlockId, blockIdList, result, dataSource, prevSize);
            if (result.size() - 1 < 0) {
                fromBlockId = blockId;
            } else {
                fromBlockId = blockIdList.get(result.size() - 1);
            }
        } while (result.size() != prevSize && dataSource != databaseManager.getDataSource() && getDataSourceWithSharding(fromBlockId) != dataSource);

        return result;
    }

    @Override
    public long getBlockIdAtHeight(int height) {
        Block block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block.getId();
        }
        TransactionalDataSource dataSource = getDataSourceWithShardingByHeight(height);
        return blockDao.findBlockIdAtHeight(height, dataSource);
    }

    @Override
    public Block getBlockAtHeight(int height) {
        Block block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block;
        }
        TransactionalDataSource dataSource = getDataSourceWithShardingByHeight(height);
        return blockDao.findBlockAtHeight(height, dataSource);
    }

    @Override
    public Block getECBlock(int timestamp) {
        Block block = getLastBlock(timestamp);
        if (block == null) {
            return getBlockAtHeight(0);
        }
        int height = Math.max(block.getHeight() - 720, 0);
        TransactionalDataSource dataSource = getDataSourceWithShardingByHeight(height);
        return blockDao.findBlockAtHeight(height, dataSource);
    }

    @Override
    public void deleteBlocksFromHeight(int height) {
        blockDao.deleteBlocksFromHeight(height);
    }

    @Override
    public Block deleteBlocksFrom(long blockId) {
        return blockDao.deleteBlocksFrom(blockId);
    }

    @Override
    public void deleteAll() {
        blockDao.deleteAll();
    }

    @Override
    public Transaction getTransaction(long transactionId) {
        return transactionDao.findTransaction(transactionId);
    }

    @Override
    public Transaction findTransaction(long transactionId, int height) {
        return transactionDao.findTransaction(transactionId, height);
    }

    @Override
    public Transaction getTransactionByFullHash(String fullHash) {
        return transactionDao.findTransactionByFullHash(Convert.parseHexString(fullHash));
    }

    @Override
    public Transaction findTransactionByFullHash(byte[] fullHash) {
        return transactionDao.findTransactionByFullHash(fullHash);
    }

    @Override
    public Transaction findTransactionByFullHash(byte[] fullHash, int height) {
        return transactionDao.findTransactionByFullHash(fullHash, height);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTransaction(long transactionId) {
        return transactionDao.hasTransaction(transactionId) || transactionIndexDao.getByTransactionId(transactionId) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTransaction(long transactionId, int height) {
        boolean hasTransaction = transactionDao.hasTransaction(transactionId, height);
        if (!hasTransaction) {
            Integer transactionHeight = transactionIndexDao.getTransactionHeightByTransactionId(transactionId);
            hasTransaction = transactionHeight != null && transactionHeight <= height;
        }
        return hasTransaction;
    }


    @Override
    @Transactional(readOnly = true)
    public boolean hasTransactionByFullHash(String fullHash) {
        return hasTransactionByFullHash(Convert.parseHexString(fullHash));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTransactionByFullHash(byte[] fullHash) {
        return transactionDao.hasTransactionByFullHash(fullHash) || hasShardTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    private boolean hasShardTransactionByFullHash(byte[] fullHash, int height) {
        long id = Convert.fullHashToId(fullHash);
        TransactionIndex transactionIndex = transactionIndexDao.getByTransactionId(id);
        byte[] hash = getTransactionIndexFullHash(transactionIndex);
        return Arrays.equals(hash, fullHash)
                && transactionIndexDao.getTransactionHeightByTransactionId(id) <= height;
    }


    @Override
    @Transactional(readOnly = true)
    public boolean hasTransactionByFullHash(byte[] fullHash, int height) {
        return transactionDao.hasTransactionByFullHash(fullHash, height) || hasShardTransactionByFullHash(fullHash, height);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTransactionHeight(byte[] fullHash, int heightLimit) {
        Transaction transaction = transactionDao.findTransactionByFullHash(fullHash, heightLimit);
        Integer txHeight = null;
        if (transaction != null) {
            txHeight = transaction.getHeight();
        } else if (hasShardTransactionByFullHash(fullHash, heightLimit)){
            txHeight = transactionIndexDao.getTransactionHeightByTransactionId(Convert.fullHashToId(fullHash));
        }
        return txHeight;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getFullHash(long transactionId) {
        byte[] fullHash = transactionDao.getFullHash(transactionId);
        if (fullHash == null) {
            TransactionIndex transactionIndex = transactionIndexDao.getByTransactionId(transactionId);
            fullHash = getTransactionIndexFullHash(transactionIndex);
        }
        return fullHash;
    }

    private byte[] getTransactionIndexFullHash(TransactionIndex transactionIndex) {
        byte[] fullHash = null;
        if (transactionIndex != null) {
            fullHash = Convert.toFullHash(transactionIndex.getTransactionId(), transactionIndex.getPartialTransactionHash());
        }
        return fullHash;
    }

    @Override
    public Transaction loadTransaction(Connection con, ResultSet rs) throws AplException.NotValidException {
        return transactionDao.loadTransaction(con, rs);
    }

    @Override
    public int getTransactionCount() {
        return transactionDao.getTransactionCount();
    }

/*
    @Override
    public DbIterator<Transaction> getAllTransactions() {
        return transactionDao.getAllTransactions();
    }
*/

/*
    @Override
    public DbIterator<Transaction> getTransactions(long accountId, byte type, byte subtype, int blockTimestamp,
                                                       boolean includeExpiredPrunable) {
        return getTransactions(
                accountId, 0, type, subtype,
                blockTimestamp, false, false, false,
                0, -1, includeExpiredPrunable, false, true);
    }
*/

    @Override
    public DbIterator<Transaction> getTransactions(long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                   int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                   int from, int to, boolean includeExpiredPrunable, boolean executedOnly, boolean includePrivate) {

        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        int prunableExpiration = Math.max(0, propertiesHolder.INCLUDE_EXPIRED_PRUNABLE() && includeExpiredPrunable ?
                timeService.getEpochTime() - blockchainConfig.getMaxPrunableLifetime() :
                timeService.getEpochTime() - blockchainConfig.getMinPrunableLifetime());

        return transactionDao.getTransactions(
                accountId, numberOfConfirmations, type, subtype,
                blockTimestamp, withMessage, phasedOnly, nonPhasedOnly,
                from, to, includeExpiredPrunable, executedOnly, includePrivate, height, prunableExpiration);
    }

    @Override
    public DbIterator<Transaction> getTransactions(byte type, byte subtype, int from, int to) {
        return transactionDao.getTransactions(type, subtype, from, to);
    }

    @Override
    public int getTransactionCount(long accountId, byte type, byte subtype) {
        return transactionDao.getTransactionCount(accountId, type, subtype);
    }

    @Override
    public DbIterator<Transaction> getTransactions(Connection con, PreparedStatement pstmt) {
        return transactionDao.getTransactions(con, pstmt);
    }

    @Override
    public List<PrunableTransaction> findPrunableTransactions(Connection con, int minTimestamp, int maxTimestamp) {
        return transactionDao.findPrunableTransactions(con, minTimestamp, maxTimestamp);
    }


    @Override
    public Set<Long> getBlockGenerators(int limit) {
        int startHeight = getHeight() - 10_000;
        return blockDao.getBlockGenerators(startHeight, limit);
    }

    private TransactionalDataSource getDataSourceWithSharding(long blockId) {
        Long shardId = blockIndexDao.getShardIdByBlockId(blockId);
        return getShardDataSourceOrDefault(shardId);
    }

    private TransactionalDataSource getShardDataSourceOrDefault(Long shardId) {
        TransactionalDataSource dataSource = null;
        if (shardId != null) {
            dataSource = ((ShardManagement) databaseManager).getShardDataSourceById(shardId);
        }
        if (dataSource == null) {
            dataSource = databaseManager.getDataSource();
        }
        return dataSource;

    }

    private TransactionalDataSource getDataSourceWithShardingByHeight(int blockHeight) {
        Long shardId = blockIndexDao.getShardIdByBlockHeight(blockHeight);
        return getShardDataSourceOrDefault(shardId);
    }


}
