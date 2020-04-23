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

import com.apollocurrency.aplwallet.apl.core.account.LedgerEvent;
import com.apollocurrency.aplwallet.apl.core.account.model.Account;
import com.apollocurrency.aplwallet.apl.core.account.service.AccountAssetService;
import com.apollocurrency.aplwallet.apl.core.account.service.AccountAssetServiceImpl;
import com.apollocurrency.aplwallet.apl.core.account.service.AccountService;
import com.apollocurrency.aplwallet.apl.core.account.service.AccountServiceImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DbClause;
import com.apollocurrency.aplwallet.apl.core.db.DbIterator;
import com.apollocurrency.aplwallet.apl.core.db.DbKey;
import com.apollocurrency.aplwallet.apl.core.db.LongKeyFactory;
import com.apollocurrency.aplwallet.apl.core.db.TransactionalDataSource;
import com.apollocurrency.aplwallet.apl.core.db.derived.VersionedDeletableEntityDbTable;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsAskOrderPlacement;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsBidOrderPlacement;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsOrderPlacementAttachment;
import com.apollocurrency.aplwallet.apl.util.StackTraceUtils;
import com.apollocurrency.aplwallet.apl.util.ThreadUtils;
import com.apollocurrency.aplwallet.apl.util.annotation.DatabaseSpecificDml;
import com.apollocurrency.aplwallet.apl.util.annotation.DmlMarker;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.inject.spi.CDI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
public abstract class Order {

    private static Blockchain blockchain = CDI.current().select(BlockchainImpl.class).get();
    private static AccountService accountService = CDI.current().select(AccountServiceImpl.class).get();
    private static AccountAssetService accountAssetService = CDI.current().select(AccountAssetServiceImpl.class).get();
    private static DatabaseManager databaseManager;

    private final long id;
    private final long accountId;
    private final long assetId;
    private final long priceATM;
    private final int creationHeight;
    private final short transactionIndex;
    private final int transactionHeight;
    private long quantityATU;
    private Order(Transaction transaction, ColoredCoinsOrderPlacementAttachment attachment) {
        this.id = transaction.getId();
        this.accountId = transaction.getSenderId();
        this.assetId = attachment.getAssetId();
        this.quantityATU = attachment.getQuantityATU();
        this.priceATM = attachment.getPriceATM();
        this.creationHeight = blockchain.getHeight();
        this.transactionIndex = transaction.getIndex();
        this.transactionHeight = transaction.getHeight();
    }

    private Order(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.accountId = rs.getLong("account_id");
        this.assetId = rs.getLong("asset_id");
        this.priceATM = rs.getLong("price");
        this.quantityATU = rs.getLong("quantity");
        this.creationHeight = rs.getInt("creation_height");
        this.transactionIndex = rs.getShort("transaction_index");
        this.transactionHeight = rs.getInt("transaction_height");
    }

    private static void matchOrders(long assetId) {

        Order.Ask askOrder;
        Order.Bid bidOrder;
        log.debug(">> match orders, assetId={}, stack={}", assetId, StackTraceUtils.lastNStacktrace(5));
        int index = 0;
        while ((askOrder = Ask.getNextOrder(assetId)) != null
                && (bidOrder = Bid.getNextOrder(assetId)) != null) {
            log.debug(">> match orders LOOP, assetId={}, index={}, askOrder={}, bidOrder={}",
                assetId, index, askOrder, bidOrder);
            if (askOrder.getPriceATM() > bidOrder.getPriceATM()) {
                log.debug(">> match orders, STOP LOOP, assetId={}", assetId);
                break;
            }

            Trade trade = Trade.addTrade(assetId, askOrder, bidOrder);
            log.debug("match orders TRADE, assetId={}, trade={}", assetId, trade);

            askOrder.updateQuantityATU(Math.subtractExact(askOrder.getQuantityATU(), trade.getQuantityATU()));
            Account askAccount = accountService.getAccount(askOrder.getAccountId());
            accountService.addToBalanceAndUnconfirmedBalanceATM(askAccount, LedgerEvent.ASSET_TRADE, askOrder.getId(),
                    Math.multiplyExact(trade.getQuantityATU(), trade.getPriceATM()));
            accountAssetService.addToAssetBalanceATU(askAccount, LedgerEvent.ASSET_TRADE, askOrder.getId(), assetId, -trade.getQuantityATU());

            bidOrder.updateQuantityATU(Math.subtractExact(bidOrder.getQuantityATU(), trade.getQuantityATU()));
            Account bidAccount = accountService.getAccount(bidOrder.getAccountId());
            accountAssetService.addToAssetAndUnconfirmedAssetBalanceATU(bidAccount, LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    assetId, trade.getQuantityATU());
            accountService.addToBalanceATM(bidAccount, LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    -Math.multiplyExact(trade.getQuantityATU(), trade.getPriceATM()));
            accountService.addToUnconfirmedBalanceATM(bidAccount, LedgerEvent.ASSET_TRADE, bidOrder.getId(),
                    Math.multiplyExact(trade.getQuantityATU(), (bidOrder.getPriceATM() - trade.getPriceATM())));
            log.debug("<< match orders, END LOOP, assetId={}, index={}", assetId, index);
            index++;
        }
        log.debug("<< DONE match orders, assetId={}", assetId);
    }

    public static void init() {
        Ask.init();
        Bid.init();
    }

    /*
    private int compareTo(Order o) {
        if (height < o.height) {
            return -1;
        } else if (height > o.height) {
            return 1;
        } else {
            if (id < o.id) {
                return -1;
            } else if (id > o.id) {
                return 1;
            } else {
                return 0;
            }
        }

    }
    */
    static <T extends Order> void insertOrDeleteOrder(VersionedDeletableEntityDbTable<T> table, long quantityATU, T order) {
        int height = blockchain.getHeight();
        if (quantityATU > 0) {
            log.debug(">> Update POSITIVE quantity = {}, height={}", order, height);
            table.insert(order);
            log.debug("<< Update POSITIVE quantity = {}, height={}", order, height);
        } else if (quantityATU == 0) {
            log.debug(">> Delete ZERO quantity = {}, height={}", order, height);
            table.deleteAtHeight(order, height);
            log.debug("<< Delete ZERO quantity = {}, height={}", order, height);
        } else {
            throw new IllegalArgumentException("Negative quantity: " + quantityATU
                    + " for order: " + order.getId());
        }
    }

    private void save(Connection con, String table) throws SQLException {
        log.debug("save table={}, entity={}", table, this);
        try (
                @DatabaseSpecificDml(DmlMarker.MERGE)
                PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + table + " (id, account_id, asset_id, "
                + "price, quantity, creation_height, transaction_index, transaction_height, height, latest, deleted) KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, FALSE)")
        ) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.priceATM);
            pstmt.setLong(++i, this.quantityATU);
            pstmt.setInt(++i, this.creationHeight);
            pstmt.setShort(++i, this.transactionIndex);
            pstmt.setInt(++i, this.transactionHeight);
            pstmt.setInt(++i, blockchain.getHeight());
            pstmt.executeUpdate();
        }
    }

    public final long getId() {
        return id;
    }

    public final long getAccountId() {
        return accountId;
    }

    public final long getAssetId() {
        return assetId;
    }

    public final long getPriceATM() {
        return priceATM;
    }

    public final long getQuantityATU() {
        return quantityATU;
    }

    private void setQuantityATU(long quantityATU) {
        this.quantityATU = quantityATU;
    }

    public final int getHeight() {
        return creationHeight;
    }

    public final int getTransactionIndex() {
        return transactionIndex;
    }

    public final int getTransactionHeight() {
        return transactionHeight;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": id=" + id + ", account=" + accountId
                + ", asset=" + assetId + ", price=" + priceATM + ", quantity=" + quantityATU
                + ", height=" + creationHeight + ", transactionIndex=" + transactionIndex + ", transactionHeight=" + transactionHeight;
    }

    private static TransactionalDataSource lookupDataSource() {
        if (databaseManager == null) {
            databaseManager = CDI.current().select(DatabaseManager.class).get();
        }
        return databaseManager.getDataSource();
    }

    @Slf4j
    public static final class Ask extends Order {

        private static final LongKeyFactory<Ask> askOrderDbKeyFactory = new LongKeyFactory<Ask>("id") {

            @Override
            public DbKey newKey(Ask ask) {
                return ask.dbKey;
            }

        };

        private static final VersionedDeletableEntityDbTable<Ask> askOrderTable = new VersionedDeletableEntityDbTable<Ask>("ask_order", askOrderDbKeyFactory) {

            @Override
            public Ask load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new Ask(rs, dbKey);
            }

            @Override
            public void save(Connection con, Ask ask) throws SQLException {
                log.debug("Save ask = {}, stack = {}", ask, ThreadUtils.last5Stacktrace());
                ask.save(con, table);
            }

            @Override
            public String defaultSort() {
                return " ORDER BY creation_height DESC ";
            }

        };
        private final DbKey dbKey;

        private Ask(Transaction transaction, ColoredCoinsAskOrderPlacement attachment) {
            super(transaction, attachment);
            this.dbKey = askOrderDbKeyFactory.newKey(super.id);
        }

        private Ask(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        public static int getCount() {
            return askOrderTable.getCount();
        }

        public static Ask getAskOrder(long orderId) {
            return askOrderTable.get(askOrderDbKeyFactory.newKey(orderId));
        }

        public static DbIterator<Ask> getAll(int from, int to) {
            return askOrderTable.getAll(from, to);
        }

        public static DbIterator<Ask> getAskOrdersByAccount(long accountId, int from, int to) {
            return askOrderTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
        }

        public static DbIterator<Ask> getAskOrdersByAsset(long assetId, int from, int to) {
            return askOrderTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to);
        }

        public static DbIterator<Ask> getAskOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
            DbClause dbClause = new DbClause.LongClause("account_id", accountId).and(new DbClause.LongClause("asset_id", assetId));
            return askOrderTable.getManyBy(dbClause, from, to);
        }

        public static DbIterator<Ask> getSortedOrders(long assetId, int from, int to) {
            return askOrderTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to,
                    " ORDER BY price ASC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
        }

        private static Ask getNextOrder(long assetId) {
            try (Connection con = lookupDataSource().getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM ask_order WHERE asset_id = ? "
                         + "AND latest = TRUE ORDER BY price ASC, creation_height ASC, transaction_height ASC, transaction_index ASC LIMIT 1")) {
                pstmt.setLong(1, assetId);
                try (DbIterator<Ask> askOrders = askOrderTable.getManyBy(con, pstmt, true)) {
                    return askOrders.hasNext() ? askOrders.next() : null;
                }
            }
            catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        public static void addOrder(Transaction transaction, ColoredCoinsAskOrderPlacement attachment) {
            Ask order = new Ask(transaction, attachment);
            log.debug(">> addOrder() askOrder={}", order);
            askOrderTable.insert(order);
            int height = blockchain.getHeight();
            log.debug("<< addOrder() askOrder={}, height={}", order, height);
            matchOrders(attachment.getAssetId());
        }

        public static void removeOrder(long orderId) {
            int height = blockchain.getHeight();
            log.debug(">> removeOrder() askOrderId={}, height={}", orderId, height);
            askOrderTable.deleteAtHeight(getAskOrder(orderId), height);
        }

        public static void init() {}

        private void save(Connection con, String table) throws SQLException {
            super.save(con, table);
        }

        private void updateQuantityATU(long quantityATU) {
            super.setQuantityATU(quantityATU);
            insertOrDeleteOrder(askOrderTable, quantityATU, this);
        }

        /*
        @Override
        public int compareTo(Ask o) {
            if (this.getPriceATM() < o.getPriceATM()) {
                return -1;
            } else if (this.getPriceATM() > o.getPriceATM()) {
                return 1;
            } else {
                return super.compareTo(o);
            }
        }
        */

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("Ask{");
            sb.append("id=").append(super.id);
            sb.append(", accountId=").append(super.accountId);
            sb.append(", assetId=").append(super.assetId);
            sb.append(", priceATM=").append(super.priceATM);
            sb.append(", creationHeight=").append(super.creationHeight);
            sb.append(", transactionIndex=").append(super.transactionIndex);
            sb.append(", transactionHeight=").append(super.transactionHeight);
            sb.append(", quantityATU=").append(super.quantityATU);
            sb.append(", dbKey=").append(dbKey);
            sb.append('}');
            return sb.toString();
        }
    }

    public static final class Bid extends Order {

        private static final LongKeyFactory<Bid> bidOrderDbKeyFactory = new LongKeyFactory<Bid>("id") {

            @Override
            public DbKey newKey(Bid bid) {
                return bid.dbKey;
            }

        };

        private static final VersionedDeletableEntityDbTable<Bid> bidOrderTable = new VersionedDeletableEntityDbTable<Bid>("bid_order", bidOrderDbKeyFactory) {

            @Override
            public Bid load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
                return new Bid(rs, dbKey);
            }

            @Override
            public void save(Connection con, Bid bid) throws SQLException {
                log.debug("Save bid = {}", bid);
                bid.save(con, table);
            }

            @Override
            public String defaultSort() {
                return " ORDER BY creation_height DESC ";
            }

        };
        private final DbKey dbKey;

        private Bid(Transaction transaction, ColoredCoinsBidOrderPlacement attachment) {
            super(transaction, attachment);
            this.dbKey = bidOrderDbKeyFactory.newKey(super.id);
        }

        private Bid(ResultSet rs, DbKey dbKey) throws SQLException {
            super(rs);
            this.dbKey = dbKey;
        }

        public static int getCount() {
            return bidOrderTable.getCount();
        }

        public static Bid getBidOrder(long orderId) {
            return bidOrderTable.get(bidOrderDbKeyFactory.newKey(orderId));
        }

        public static DbIterator<Bid> getAll(int from, int to) {
            return bidOrderTable.getAll(from, to);
        }

        public static DbIterator<Bid> getBidOrdersByAccount(long accountId, int from, int to) {
            return bidOrderTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
        }

        public static DbIterator<Bid> getBidOrdersByAsset(long assetId, int from, int to) {
            return bidOrderTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to);
        }

        public static DbIterator<Bid> getBidOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
            DbClause dbClause = new DbClause.LongClause("account_id", accountId).and(new DbClause.LongClause("asset_id", assetId));
            return bidOrderTable.getManyBy(dbClause, from, to);
        }

        public static DbIterator<Bid> getSortedOrders(long assetId, int from, int to) {
            return bidOrderTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to,
                    " ORDER BY price DESC, creation_height ASC, transaction_height ASC, transaction_index ASC ");
        }

        private static Bid getNextOrder(long assetId) {
            try (Connection con = lookupDataSource().getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM bid_order WHERE asset_id = ? "
                         + "AND latest = TRUE ORDER BY price DESC, creation_height ASC, transaction_height ASC, transaction_index ASC LIMIT 1")) {
                pstmt.setLong(1, assetId);
                try (DbIterator<Bid> bidOrders = bidOrderTable.getManyBy(con, pstmt, true)) {
                    return bidOrders.hasNext() ? bidOrders.next() : null;
                }
            }
            catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        public static void addOrder(Transaction transaction, ColoredCoinsBidOrderPlacement attachment) {
            Bid order = new Bid(transaction, attachment);
            log.debug(">> addOrder() bidOrder={}", order);
            int height = blockchain.getHeight();
            bidOrderTable.insert(order);
            log.debug("<< addOrder() BidOrder={}, height={}", order, height);
            matchOrders(attachment.getAssetId());
        }

        public static void removeOrder(long orderId) {
            int height = blockchain.getHeight();
            log.debug(">> removeOrder() bidOrderId={}, height={}", orderId, height);
            bidOrderTable.deleteAtHeight(getBidOrder(orderId), height);
        }

        public static void init() {}

        private void save(Connection con, String table) throws SQLException {
            super.save(con, table);
        }

        private void updateQuantityATU(long quantityATU) {
            super.setQuantityATU(quantityATU);
            insertOrDeleteOrder(bidOrderTable, quantityATU, this);
        }

        /*
        @Override
        public int compareTo(Bid o) {
            if (this.getPriceATM() > o.getPriceATM()) {
                return -1;
            } else if (this.getPriceATM() < o.getPriceATM()) {
                return 1;
            } else {
                return super.compareTo(o);
            }
        }
        */

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("Bid{");
            sb.append("id=").append(super.id);
            sb.append(", accountId=").append(super.accountId);
            sb.append(", assetId=").append(super.assetId);
            sb.append(", priceATM=").append(super.priceATM);
            sb.append(", creationHeight=").append(super.creationHeight);
            sb.append(", transactionIndex=").append(super.transactionIndex);
            sb.append(", transactionHeight=").append(super.transactionHeight);
            sb.append(", quantityATU=").append(super.quantityATU);
            sb.append(", dbKey=").append(dbKey);
            sb.append('}');
            return sb.toString();
        }
    }
}
