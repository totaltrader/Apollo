/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.db.dao;

import static com.apollocurrency.aplwallet.apl.data.IndexTestData.TRANSACTION_INDEX_1;
import static com.apollocurrency.aplwallet.apl.data.IndexTestData.TRANSACTION_INDEX_3;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.apollocurrency.aplwallet.apl.core.app.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.app.EpochTime;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSync;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSyncImpl;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.app.TransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.config.DaoConfig;
import com.apollocurrency.aplwallet.apl.core.db.BlockDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DerivedDbTablesRegistryImpl;
import com.apollocurrency.aplwallet.apl.core.db.cdi.transaction.JdbiHandleFactory;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.ReferencedTransaction;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfig;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfigImpl;
import com.apollocurrency.aplwallet.apl.data.TransactionTestData;
import com.apollocurrency.aplwallet.apl.extension.DbExtension;
import com.apollocurrency.aplwallet.apl.util.NtpTime;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

@EnableWeld
class ReferencedTransactionDaoTest {

    @Inject
    private JdbiHandleFactory jdbiHandleFactory;
    @RegisterExtension
    static DbExtension extension = new DbExtension();
    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            PropertiesHolder.class, TransactionImpl.class, BlockchainConfig.class, BlockchainImpl.class, DaoConfig.class,
            JdbiHandleFactory.class, ReferencedTransactionDaoImpl.class,
            GlobalSync.class, TransactionTestData.class,
            GlobalSyncImpl.class,
            DerivedDbTablesRegistryImpl.class,
            FullTextConfigImpl.class,
            EpochTime.class, BlockDaoImpl.class, TransactionDaoImpl.class)
            .addBeans(MockBean.of(extension.getDatabaseManger(), DatabaseManager.class))
            .addBeans(MockBean.of(extension.getDatabaseManger().getJdbi(), Jdbi.class))
            .addBeans(MockBean.of(mock(TransactionProcessor.class), TransactionProcessor.class))
            .addBeans(MockBean.of(mock(NtpTime.class), NtpTime.class))
            .build();


    @Inject
    private ReferencedTransactionDao dao;

    @AfterEach
    void cleanup() {
        jdbiHandleFactory.close();
    }

    @Test
    void testGetAll() {
        List<Long> allReferencedTransactionIds = dao.getAllReferencedTransactionIds();
        TransactionTestData td = new TransactionTestData();
        Set<Long> expectedIds = td.REFERENCED_TRANSACTIONS.stream().map(ReferencedTransaction::getReferencedTransactionId).collect(Collectors.toSet());
        assertEquals(expectedIds, new HashSet<>(allReferencedTransactionIds));
    }

    @Test
    void testGetById() {
        TransactionTestData td = new TransactionTestData();
        Optional<Long> referencedId = dao.getReferencedTransactionIdFor(td.TRANSACTION_3.getId());
        assertTrue(referencedId.isPresent());
        assertEquals(td.REFERENCED_TRANSACTION_5.getTransactionId(), referencedId.get());
    }

    @Test
    void testGetByIdForTransactionWithoutReferencedTransaction() {
        TransactionTestData td = new TransactionTestData();
        Optional<Long> referencedId = dao.getReferencedTransactionIdFor(td.TRANSACTION_12.getId());

        assertTrue(referencedId.isEmpty());
    }

    @Test
    void testgetByIdForShardTransaction() {
        TransactionTestData td = new TransactionTestData();

        Optional<Long> referencedId = dao.getReferencedTransactionIdFor(TRANSACTION_INDEX_1.getTransactionId());

        assertTrue(referencedId.isPresent());
        assertEquals(td.REFERENCED_TRANSACTION_1.getReferencedTransactionId(), referencedId.get());
    }

    @Test
    void testGetByIdForShardTransactionWithoutReferencedTransactionId() {
        Optional<Long> referencedId = dao.getReferencedTransactionIdFor(TRANSACTION_INDEX_3.getTransactionId());

        assertTrue(referencedId.isEmpty());
    }

    @Test
    void testSave() {
        TransactionTestData td = new TransactionTestData();

        int saveCount = dao.save(td.NOT_SAVED_REFERENCED_SHARD_TRANSACTION);

        assertEquals(1, saveCount);

        Optional<Long> referencedId = dao.getReferencedTransactionIdFor(td.NOT_SAVED_REFERENCED_SHARD_TRANSACTION.getTransactionId());

        assertTrue(referencedId.isPresent());
        assertEquals(td.NOT_SAVED_REFERENCED_SHARD_TRANSACTION.getReferencedTransactionId(), referencedId.get());
    }

    @Test
    void testGetReferencingTransactions() {
        TransactionTestData td = new TransactionTestData();
        List<Transaction> referencingTransactions = dao.getReferencingTransactions(td.TRANSACTION_8.getId(), 0, 100);

        assertEquals(Arrays.asList(td.TRANSACTION_11, td.TRANSACTION_9), referencingTransactions);

    }

    @Test
    void testGetReferencingTransactionsForShardTransaction() {
        TransactionTestData td = new TransactionTestData();
        List<Transaction> referencingTransactions = dao.getReferencingTransactions(td.REFERENCED_TRANSACTION_2.getReferencedTransactionId(), 0, 100);

        assertEquals(Collections.emptyList(), referencingTransactions);
    }

}