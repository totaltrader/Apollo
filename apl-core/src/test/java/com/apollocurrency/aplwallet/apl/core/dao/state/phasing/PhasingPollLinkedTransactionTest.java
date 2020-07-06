/*
 *  Copyright © 2018-2020 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.dao.state.phasing;

import com.apollocurrency.aplwallet.apl.core.app.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSyncImpl;
import com.apollocurrency.aplwallet.apl.core.service.appdata.TimeService;
import com.apollocurrency.aplwallet.apl.core.service.appdata.impl.TimeServiceImpl;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.app.TransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.config.DaoConfig;
import com.apollocurrency.aplwallet.apl.core.config.NtpTimeConfig;
import com.apollocurrency.aplwallet.apl.core.db.BlockDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DerivedDbTablesRegistryImpl;
import com.apollocurrency.aplwallet.apl.core.db.LongKey;
import com.apollocurrency.aplwallet.apl.core.db.ValuesDbTableTest;
import com.apollocurrency.aplwallet.apl.core.db.cdi.transaction.JdbiHandleFactory;
import com.apollocurrency.aplwallet.apl.core.db.derived.DerivedDbTable;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfigImpl;
import com.apollocurrency.aplwallet.apl.core.service.prunable.PrunableMessageService;
import com.apollocurrency.aplwallet.apl.core.service.state.PhasingPollService;
import com.apollocurrency.aplwallet.apl.core.entity.state.phasing.PhasingPollLinkedTransaction;
import com.apollocurrency.aplwallet.apl.core.shard.BlockIndexService;
import com.apollocurrency.aplwallet.apl.core.shard.BlockIndexServiceImpl;
import com.apollocurrency.aplwallet.apl.data.PhasingTestData;
import com.apollocurrency.aplwallet.apl.data.TransactionTestData;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Tag("slow")
@EnableWeld
@Execution(ExecutionMode.CONCURRENT)
public class PhasingPollLinkedTransactionTest extends ValuesDbTableTest<PhasingPollLinkedTransaction> {
    private PropertiesHolder propertiesHolder = mock(PropertiesHolder.class);
    private NtpTimeConfig ntpTimeConfig = new NtpTimeConfig();
    private TimeService timeService = new TimeServiceImpl(ntpTimeConfig.time());

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
        BlockchainConfig.class, BlockchainImpl.class, DaoConfig.class,
        GlobalSyncImpl.class,
        PhasingPollLinkedTransactionTable.class,
        FullTextConfigImpl.class,
        DerivedDbTablesRegistryImpl.class,
        BlockDaoImpl.class, TransactionDaoImpl.class)
        .addBeans(MockBean.of(getDatabaseManager(), DatabaseManager.class))
        .addBeans(MockBean.of(getDatabaseManager().getJdbi(), Jdbi.class))
        .addBeans(MockBean.of(getDatabaseManager().getJdbiHandleFactory(), JdbiHandleFactory.class))
        .addBeans(MockBean.of(mock(PhasingPollService.class), PhasingPollService.class))
        .addBeans(MockBean.of(mock(PrunableMessageService.class), PrunableMessageService.class))
        .addBeans(MockBean.of(mock(TransactionProcessor.class), TransactionProcessor.class))
        .addBeans(MockBean.of(mock(BlockIndexService.class), BlockIndexService.class, BlockIndexServiceImpl.class))
        .addBeans(MockBean.of(propertiesHolder, PropertiesHolder.class))
        .addBeans(MockBean.of(ntpTimeConfig, NtpTimeConfig.class))
        .addBeans(MockBean.of(timeService, TimeService.class))
        .build();
    @Inject
    PhasingPollLinkedTransactionTable table;
    PhasingTestData ptd;
    TransactionTestData ttd;

    public PhasingPollLinkedTransactionTest() {
        super(PhasingPollLinkedTransaction.class);
    }

    @BeforeEach
    @Override
    public void setUp() {
        ptd = new PhasingTestData();
        ttd = new TransactionTestData();
        super.setUp();
    }

    @Override
    public DerivedDbTable<PhasingPollLinkedTransaction> getDerivedDbTable() {
        return table;
    }

    @Test
    void testGetAllForPollWithLinkedTransactions() {
        List<PhasingPollLinkedTransaction> linkedTransactions = table.get(ptd.POLL_3.getId());

        assertEquals(Arrays.asList(ptd.LINKED_TRANSACTION_0, ptd.LINKED_TRANSACTION_1, ptd.LINKED_TRANSACTION_2), linkedTransactions);
    }

    @Test
    void testGetAllForPollWithoutLinkedTransactions() {
        List<PhasingPollLinkedTransaction> linkedTransactions = table.get(ptd.POLL_1.getId());

        assertTrue(linkedTransactions.isEmpty(), "Linked transactions should not exist for poll2");
    }

    @Test
    void testGetByDbKeyForPollWithLinkedTransactions() {
        List<PhasingPollLinkedTransaction> linkedTransactions = table.get(new LongKey(ptd.POLL_3.getId()));

        assertEquals(Arrays.asList(ptd.LINKED_TRANSACTION_0, ptd.LINKED_TRANSACTION_1, ptd.LINKED_TRANSACTION_2), linkedTransactions);
    }

    @Test
    void testGetByDbKeyForPollWithoutLinkedTransactions() {
        List<PhasingPollLinkedTransaction> linkedTransactions = table.get(new LongKey(ptd.POLL_1.getId()));

        assertTrue(linkedTransactions.isEmpty(), "Linked transactions should not exist for poll2");
    }

    @Test
    void testGetLinkedPhasedTransactions() throws SQLException {
        List<Transaction> transactions = table.getLinkedPhasedTransactions(ptd.LINKED_TRANSACTION_1_HASH);

        assertEquals(List.of(ttd.TRANSACTION_12), transactions);
    }

    @Test
    void testGetLinkedPhasedTransactionsForNonLinkedTransaction() throws SQLException {
        List<Transaction> transactions = table.getLinkedPhasedTransactions(ttd.TRANSACTION_12.getFullHash());

        assertTrue(transactions.isEmpty(), "Linked transactions should not exist for transaction #12");
    }


    @Override
    protected List<PhasingPollLinkedTransaction> getAll() {
        return List.of(ptd.LINKED_TRANSACTION_0, ptd.LINKED_TRANSACTION_1, ptd.LINKED_TRANSACTION_2, ptd.FAKE_LINKED_TRANSACTION_0, ptd.FAKE_LINKED_TRANSACTION_1, ptd.FAKE_LINKED_TRANSACTION_2);
    }


    @Override
    protected List<PhasingPollLinkedTransaction> dataToInsert() {
        return List.of(ptd.NEW_LINKED_TRANSACTION_1, ptd.NEW_LINKED_TRANSACTION_2);
    }

}