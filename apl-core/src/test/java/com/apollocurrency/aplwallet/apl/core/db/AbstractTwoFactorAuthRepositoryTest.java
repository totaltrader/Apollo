/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.db;

import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.dao.appdata.TwoFactorAuthRepository;
import com.apollocurrency.aplwallet.apl.core.entity.appdata.TwoFactorAuthEntity;
import com.apollocurrency.aplwallet.apl.core.utils.Convert2;
import com.apollocurrency.aplwallet.apl.data.TwoFactorAuthTestData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@Slf4j
public abstract class AbstractTwoFactorAuthRepositoryTest {

    @Container
    public static GenericContainer mariaDBContainer;

    static {
        mariaDBContainer = new MariaDBContainer("mariadb:10.5")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withExposedPorts(3306)
            .withLogConsumer(new Slf4jLogConsumer(log));
        BlockchainConfig blockchainConfig = mock(BlockchainConfig.class);
        doReturn("APL").when(blockchainConfig).getAccountPrefix();
        Convert2.init(blockchainConfig);
    }

    protected TwoFactorAuthRepository repository;

    public AbstractTwoFactorAuthRepositoryTest(TwoFactorAuthRepository repository) {
        this.repository = repository;
    }

    protected AbstractTwoFactorAuthRepositoryTest() {
    }

    public void setRepository(TwoFactorAuthRepository repository) {
        this.repository = repository;
    }

    @Test
    public void testGet() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        TwoFactorAuthEntity entity = repository.get(td.ACC_1.getId());
        assertEquals(td.ENTITY1, entity);
    }

    @Test
    public void testGetNotFound() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        TwoFactorAuthEntity entity = repository.get(td.newAccount.getId());
        assertNull(entity);
    }

    @Test
    public void testAdd() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        boolean saved = repository.add(td.NEW_ENTITY);
        assertTrue(saved);
        TwoFactorAuthEntity entity = repository.get(td.newAccount.getId());
        assertEquals(td.NEW_ENTITY, entity);

    }

    @Test
    public void testAddAlreadyExist() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        boolean saved = repository.add(td.ENTITY2);
        assertFalse(saved);
    }

    @Test
    public void testUpdate() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        TwoFactorAuthEntity entity = new TwoFactorAuthEntity(td.ENTITY2.getAccount(), td.ENTITY2.getSecret(), false);
        boolean saved = repository.update(entity);
        assertTrue(saved);
        assertEquals(repository.get(td.ACC_2.getId()), entity);
    }

    @Test
    public void testUpdateNotExist() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        TwoFactorAuthEntity entity = new TwoFactorAuthEntity(td.NEW_ENTITY.getAccount(), td.NEW_ENTITY.getSecret(), false);
        boolean saved = repository.update(entity);
        assertFalse(saved);
    }

    @Test
    public void testDelete() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        boolean deleted = repository.delete(td.ACC_1.getId());
        assertTrue(deleted);
    }

    @Test
    public void testDeleteNothingToDelete() {
        TwoFactorAuthTestData td = new TwoFactorAuthTestData();
        boolean deleted = repository.delete(td.newAccount.getId());
        assertFalse(deleted);
    }
}
