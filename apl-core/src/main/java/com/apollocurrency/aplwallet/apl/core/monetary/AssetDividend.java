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

package com.apollocurrency.aplwallet.apl.core.monetary;

import com.apollocurrency.aplwallet.apl.core.dao.state.derived.EntityDbTable;
import com.apollocurrency.aplwallet.apl.core.dao.state.keyfactory.DbKey;
import com.apollocurrency.aplwallet.apl.core.dao.state.keyfactory.LongKeyFactory;
import com.apollocurrency.aplwallet.apl.core.db.DbClause;
import com.apollocurrency.aplwallet.apl.core.db.DbIterator;
import com.apollocurrency.aplwallet.apl.core.service.blockchain.Blockchain;
import com.apollocurrency.aplwallet.apl.core.service.blockchain.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsDividendPayment;
import com.apollocurrency.aplwallet.apl.util.Listener;
import com.apollocurrency.aplwallet.apl.util.Listeners;

import javax.enterprise.inject.spi.CDI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Deprecated
public final class AssetDividend {

    private static final Listeners<AssetDividend, Event> listeners = new Listeners<>();
    /**
     * @deprecated
     */
    private static final LongKeyFactory<AssetDividend> dividendDbKeyFactory = new LongKeyFactory<AssetDividend>("id") {

        @Override
        public DbKey newKey(AssetDividend assetDividend) {
            return assetDividend.dbKey;
        }

    };
    /**
     * @deprecated
     */
    private static final EntityDbTable<AssetDividend> assetDividendTable = new EntityDbTable<AssetDividend>("asset_dividend", dividendDbKeyFactory) {

        @Override
        public AssetDividend load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AssetDividend(rs, dbKey);
        }

        @Override
        public void save(Connection con, AssetDividend assetDividend) throws SQLException {
            assetDividend.save(con);
        }

    };
    private final long id;
    private final DbKey dbKey;
    private final long assetId;
    private final long amountATMPerATU;
    private final int dividendHeight;
    private final long totalDividend;
    private final long numAccounts;
    private final int timestamp;
    private final int height;


    private AssetDividend(long transactionId, ColoredCoinsDividendPayment attachment,
                          long totalDividend, long numAccounts) {
        this.id = transactionId;
        this.dbKey = dividendDbKeyFactory.newKey(this.id);
        this.assetId = attachment.getAssetId();
        this.amountATMPerATU = attachment.getAmountATMPerATU();
        this.dividendHeight = attachment.getHeight();
        this.totalDividend = totalDividend;
        this.numAccounts = numAccounts;
        Blockchain blockchain = CDI.current().select(BlockchainImpl.class).get();
        this.timestamp = blockchain.getLastBlockTimestamp();
        this.height = blockchain.getHeight();
    }

    private AssetDividend(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.assetId = rs.getLong("asset_id");
        this.amountATMPerATU = rs.getLong("amount");
        this.dividendHeight = rs.getInt("dividend_height");
        this.totalDividend = rs.getLong("total_dividend");
        this.numAccounts = rs.getLong("num_accounts");
        this.timestamp = rs.getInt("timestamp");
        this.height = rs.getInt("height");
    }

    /**
     * @deprecated
     */
    public static boolean addListener(Listener<AssetDividend> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    /**
     * @deprecated
     */
    public static boolean removeListener(Listener<AssetDividend> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    /**
     * @deprecated
     */
    public static boolean addListener(Listener<AssetDividend> listener) {
        return addListener(listener, Event.ASSET_DIVIDEND);
    }

    /**
     * @deprecated
     */
    public static boolean removeListener(Listener<AssetDividend> listener) {
        return removeListener(listener, Event.ASSET_DIVIDEND);
    }

    /**
     * @deprecated
     */
    public static DbIterator<AssetDividend> getAssetDividends(long assetId, int from, int to) {
        return assetDividendTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to);
    }

    /**
     * @deprecated
     */
    public static AssetDividend getLastDividend(long assetId) {
        try (DbIterator<AssetDividend> dividends = assetDividendTable.getManyBy(new DbClause.LongClause("asset_id", assetId), 0, 0)) {
            if (dividends.hasNext()) {
                return dividends.next();
            }
        }
        return null;
    }

    /**
     * @deprecated
     */
    public static AssetDividend addAssetDividend(long transactionId, ColoredCoinsDividendPayment attachment,
                                                 long totalDividend, long numAccounts) {
        AssetDividend assetDividend = new AssetDividend(transactionId, attachment, totalDividend, numAccounts);
        assetDividendTable.insert(assetDividend);
        listeners.notify(assetDividend, Event.ASSET_DIVIDEND);
        return assetDividend;
    }

    /**
     * @deprecated
     */
    public static void init() {
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_dividend (id, asset_id, "
            + "amount, dividend_height, total_dividend, num_accounts, `timestamp`, height) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.assetId);
            pstmt.setLong(++i, this.amountATMPerATU);
            pstmt.setInt(++i, this.dividendHeight);
            pstmt.setLong(++i, this.totalDividend);
            pstmt.setLong(++i, this.numAccounts);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, this.height);
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public long getAssetId() {
        return assetId;
    }

    public long getAmountATMPerATU() {
        return amountATMPerATU;
    }

    public int getDividendHeight() {
        return dividendHeight;
    }

    public long getTotalDividend() {
        return totalDividend;
    }

    public long getNumAccounts() {
        return numAccounts;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getHeight() {
        return height;
    }

    public enum Event {
        ASSET_DIVIDEND
    }

}
