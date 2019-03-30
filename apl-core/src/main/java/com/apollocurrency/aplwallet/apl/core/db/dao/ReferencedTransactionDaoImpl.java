package com.apollocurrency.aplwallet.apl.core.db.dao;

import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.db.*;
import com.apollocurrency.aplwallet.apl.core.db.dao.mapper.ReferencedTransactionRowMapper;
import com.apollocurrency.aplwallet.apl.core.db.dao.mapper.TransactionRowMapper;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.ReferencedTransaction;
import org.jdbi.v3.core.Jdbi;

import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Singleton
public class ReferencedTransactionDaoImpl extends EntityDbTable<ReferencedTransaction> implements ReferencedTransactionDao {
    private static final KeyFactory<ReferencedTransaction> KEY_FACTORY = new LongKeyFactory<ReferencedTransaction>() {
        @Override
        public DbKey newKey(ReferencedTransaction referencedTransaction) {
            return new LongKey(referencedTransaction.getTransactionId());
        }
    };
    private static final String TABLE = "referenced_transaction";

    public ReferencedTransactionDaoImpl() {
        super(TABLE, KEY_FACTORY);
    }

    private static final ReferencedTransactionRowMapper REFERENCED_ROW_MAPPER = new ReferencedTransactionRowMapper();
    private static final TransactionRowMapper TRANSACTION_ROW_MAPPER = new TransactionRowMapper();

    @Override
    protected ReferencedTransaction load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
        return REFERENCED_ROW_MAPPER.map(rs, null);
    }

    @Override
    protected void save(Connection con, ReferencedTransaction referencedTransaction) throws SQLException {
        save(referencedTransaction);
    }

    @Override
    public Optional<Long> getReferencedTransactionIdFor(long transactionId) {
        Jdbi jdbi = getDatabaseManager().getJdbi();
        return jdbi.withHandle(handle ->
          handle.createQuery("SELECT referenced_transaction_id FROM referenced_transaction where transaction_id = :transactionId")
                    .bind("transactionId", transactionId)
                    .mapTo(Long.class)
                    .findFirst()
        );
    }

    @Override
    public List<Long> getAllReferencedTransactionIds() {
        Jdbi jdbi = getDatabaseManager().getJdbi();
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT referenced_transaction_id FROM referenced_transaction")
                        .mapTo(Long.class)
                        .list()
        );
    }

    @Override
    public int save(ReferencedTransaction referencedTransaction) {
        Jdbi jdbi = getDatabaseManager().getJdbi();
        return jdbi.withHandle(handle ->
                handle.inTransaction()
                handle.createUpdate("INSERT INTO referenced_transaction (transaction_id, referenced_transaction_id, height) VALUES (:transactionId, :referencedTransactionId, :height)")
                        .bindBean(referencedTransaction)
                        .execute()
        );
    }

    @Override
    public List<Transaction> getReferencingTransactions(long transactionId, int from, Integer limit) {
        Jdbi jdbi = getDatabaseManager().getJdbi();
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT transaction.* FROM transaction, referenced_transaction "
                        + "WHERE referenced_transaction.referenced_transaction_id = :transactionId "
                        + "AND referenced_transaction.transaction_id = transaction.id "
                        + "ORDER BY transaction.block_timestamp DESC, transaction.transaction_index DESC "
                        + "OFFSET :from LIMIT :limit")
                        .bind("transactionId", transactionId)
                        .bind("from", from)
                        .bind("limit", limit)
                        .map(TRANSACTION_ROW_MAPPER)
                        .list()
        );
    }
}
