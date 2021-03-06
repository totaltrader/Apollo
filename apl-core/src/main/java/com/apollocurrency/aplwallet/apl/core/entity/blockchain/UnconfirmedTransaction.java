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
 * Copyright © 2018-2020 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.entity.blockchain;

import com.apollocurrency.aplwallet.apl.core.entity.state.account.AccountControlPhasing;
import com.apollocurrency.aplwallet.apl.core.entity.state.account.AccountControlType;
import com.apollocurrency.aplwallet.apl.core.entity.state.derived.DerivedEntity;
import com.apollocurrency.aplwallet.apl.core.signature.Signature;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionType;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionTypes;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.AbstractAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.Attachment;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.EncryptToSelfMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.EncryptedMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.MessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PhasingAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PrunableEncryptedMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PrunablePlainMessageAppendix;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.PublicKeyAnnouncementAppendix;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.apollocurrency.aplwallet.apl.core.transaction.TransactionTypes.TransactionTypeSpec.SET_PHASING_ONLY;

//TODO Rename DerivedEntity to be more universal
public class UnconfirmedTransaction extends DerivedEntity implements Transaction {

    private final Transaction transaction;
    private final long arrivalTimestamp;
    private final long feePerByte;

    public UnconfirmedTransaction(Transaction transaction, long arrivalTimestamp) {
        super(null, transaction.getHeight());
        this.transaction = transaction;
        this.arrivalTimestamp = arrivalTimestamp;
        this.feePerByte = transaction.getFeeATM() / transaction.getFullSize();
    }

    public UnconfirmedTransaction(Transaction transaction, long arrivalTimestamp, long feePerByte) {
        super(transaction.getDbId(), transaction.getHeight());
        this.transaction = transaction;
        this.arrivalTimestamp = arrivalTimestamp;
        this.feePerByte = feePerByte;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public long getArrivalTimestamp() {
        return arrivalTimestamp;
    }

    public long getFeePerByte() {
        return feePerByte;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UnconfirmedTransaction && transaction.equals(((UnconfirmedTransaction) o).getTransaction());
    }

    @Override
    public int hashCode() {
        return transaction.hashCode();
    }

    @Override
    public boolean isUnconfirmedDuplicate(Map<TransactionTypes.TransactionTypeSpec, Map<String, Integer>> unconfirmedDuplicates) {
        return transaction.isUnconfirmedDuplicate(unconfirmedDuplicates);
    }

    @Override
    public long getId() {
        return transaction.getId();
    }

    @Override
    public long getDbId() {
        throw new UnsupportedOperationException("Transaction is unconfirmed! Db id is not exist");
    }

    @Override
    public String getStringId() {
        return transaction.getStringId();
    }

    @Override
    public long getSenderId() {
        return transaction.getSenderId();
    }

    @Override
    public boolean hasValidSignature() {
        return transaction.hasValidSignature();
    }

    @Override
    public void withValidSignature() {
        transaction.withValidSignature();
    }

    @Override
    public byte[] getSenderPublicKey() {
        return transaction.getSenderPublicKey();
    }

    @Override
    public long getRecipientId() {
        return transaction.getRecipientId();
    }

    @Override
    public int getHeight() {
        return transaction.getHeight();
    }

    public void setHeight(int height) {
        this.transaction.setHeight(height);
    }

    @Override
    public long getBlockId() {
        return transaction.getBlockId();
    }

    @Override
    public Block getBlock() {
        return transaction.getBlock();
    }

    public void setBlock(Block block) {
        throw new UnsupportedOperationException("Incorrect method 'setBlock()' call on 'unconfirmed' transaction instance.");
    }

    public void unsetBlock() {
        throw new UnsupportedOperationException("Incorrect method 'unsetBlock()' call on 'unconfirmed' transaction instance.");
    }

    @Override
    public int getTimestamp() {
        return transaction.getTimestamp();
    }

    @Override
    public int getBlockTimestamp() {
        return transaction.getBlockTimestamp();
    }

    @Override
    public short getDeadline() {
        return transaction.getDeadline();
    }

    @Override
    public int getExpiration() {
        return transaction.getExpiration();
    }

    @Override
    public long getAmountATM() {
        return transaction.getAmountATM();
    }

    @Override
    public long getFeeATM() {
        return transaction.getFeeATM();
    }

    @Override
    public void setFeeATM(long feeATM) {
        if (transaction.getSignature() != null) {
            throw new UnsupportedOperationException("Unable to set fee for already signed transaction");
        } else {
            transaction.setFeeATM(feeATM);
        }
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return transaction.getReferencedTransactionFullHash();
    }

    @Override
    public byte[] referencedTransactionFullHash() {
        return transaction.referencedTransactionFullHash();
    }

    @Override
    public void sign(Signature signature) {
        throw new UnsupportedOperationException("Transaction should be already signed");
    }

    @Override
    public Signature getSignature() {
        return transaction.getSignature();
    }

    @Override
    public String getFullHashString() {
        return transaction.getFullHashString();
    }

    @Override
    public byte[] getFullHash() {
        return transaction.getFullHashString() != null ? transaction.getFullHashString().getBytes() : new byte[]{};
    }

    @Override
    public TransactionType getType() {
        return transaction.getType();
    }

    @Override
    public Attachment getAttachment() {
        return transaction.getAttachment();
    }

    @Override
    public byte[] getCopyTxBytes() {
        return transaction.getCopyTxBytes();
    }

    @Override
    public byte[] bytes() {
        return transaction.bytes();
    }

    @Override
    public byte[] getUnsignedBytes() {
        return transaction.getUnsignedBytes();
    }

    @Override
    public byte getVersion() {
        return transaction.getVersion();
    }

    @Override
    public int getFullSize() {
        return transaction.getFullSize();
    }

    @Override
    public MessageAppendix getMessage() {
        return transaction.getMessage();
    }

    @Override
    public PrunablePlainMessageAppendix getPrunablePlainMessage() {
        return transaction.getPrunablePlainMessage();
    }

    public boolean hasPrunablePlainMessage() {
        return transaction.getPrunablePlainMessage() != null;
    }

    @Override
    public EncryptedMessageAppendix getEncryptedMessage() {
        return transaction.getEncryptedMessage();
    }

    @Override
    public PrunableEncryptedMessageAppendix getPrunableEncryptedMessage() {
        return transaction.getPrunableEncryptedMessage();
    }

    public boolean hasPrunableEncryptedMessage() {
        return transaction.getPrunableEncryptedMessage() != null;
    }


    public EncryptToSelfMessageAppendix getEncryptToSelfMessage() {
        return transaction.getEncryptToSelfMessage();
    }

    @Override
    public PhasingAppendix getPhasing() {
        return transaction.getPhasing();
    }

    @Override
    public boolean attachmentIsPhased() {
        return transaction.attachmentIsPhased();
    }

    @Override
    public PublicKeyAnnouncementAppendix getPublicKeyAnnouncement() {
        return transaction.getPublicKeyAnnouncement();
    }

    @Override
    public List<AbstractAppendix> getAppendages() {
        return transaction.getAppendages();
    }

    @Override
    public int getECBlockHeight() {
        return transaction.getECBlockHeight();
    }

    @Override
    public long getECBlockId() {
        return transaction.getECBlockId();
    }

    @Override
    public boolean ofType(TransactionTypes.TransactionTypeSpec spec) {
        return transaction.ofType(spec);
    }

    @Override
    public boolean isNotOfType(TransactionTypes.TransactionTypeSpec spec) {
        return transaction.isNotOfType(spec);
    }

    @Override
    public short getIndex() {
        return -1;
    }

    @Override
    public void setIndex(int index) {
    }

    /**
     * @deprecated see method with longer parameters list below
     */
    public boolean attachmentIsDuplicate(Map<TransactionTypes.TransactionTypeSpec, Map<String, Integer>> duplicates, boolean atAcceptanceHeight) {
        if (!transaction.attachmentIsPhased() && !atAcceptanceHeight) {
            // can happen for phased transactions having non-phasable attachment
            return false;
        }
        if (atAcceptanceHeight) {
            // all are checked at acceptance height for block duplicates
            if (transaction.getType().isBlockDuplicate(this, duplicates)) {
                return true;
            }
            // phased are not further checked at acceptance height
            if (attachmentIsPhased()) {
                return false;
            }
        }
        // non-phased at acceptance height, and phased at execution height
        return transaction.getType().isDuplicate(this, duplicates);
    }

    public boolean attachmentIsDuplicate(Map<TransactionTypes.TransactionTypeSpec, Map<String, Integer>> duplicates,
                                         boolean atAcceptanceHeight,
                                         Set<AccountControlType> senderAccountControls,
                                         AccountControlPhasing accountControlPhasing) {
        if (!transaction.attachmentIsPhased() && !atAcceptanceHeight) {
            // can happen for phased transactions having non-phasable attachment
            return false;
        }
        if (atAcceptanceHeight) {
            if (this.isBlockDuplicate(
                this, duplicates, senderAccountControls, accountControlPhasing)) {
                return true;
            }
            // all are checked at acceptance height for block duplicates
            if (transaction.getType().isBlockDuplicate(this, duplicates)) {
                return true;
            }
            // phased are not further checked at acceptance height
            if (attachmentIsPhased()) {
                return false;
            }
        }
        // non-phased at acceptance height, and phased at execution height
        return transaction.getType().isDuplicate(this, duplicates);
    }

    private boolean isBlockDuplicate(Transaction transaction,
                                    Map<TransactionTypes.TransactionTypeSpec, Map<String, Integer>> duplicates,
                                    Set<AccountControlType> senderAccountControls,
                                     AccountControlPhasing accountControlPhasing) {
        return
            senderAccountControls.contains(AccountControlType.PHASING_ONLY)
                && (accountControlPhasing != null && accountControlPhasing.getMaxFees() != 0)
                && transaction.getType().getSpec() != SET_PHASING_ONLY
                && TransactionType.isDuplicate(SET_PHASING_ONLY,
                Long.toUnsignedString(transaction.getSenderId()), duplicates, true);
    }


    @Override
    public String toString() {
        return "UnconfirmedTransaction{" +
            "transaction=" + transaction +
            ", arrivalTimestamp=" + arrivalTimestamp +
            ", feePerByte=" + feePerByte +
            '}';
    }
}
