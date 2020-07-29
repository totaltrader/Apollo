/*
 * Copyright © 2018-2019 Apollo Foundation
 */
package com.apollocurrency.aplwallet.apl.core.transaction.messages;

import com.apollocurrency.aplwallet.apl.core.transaction.types.cc.ColoredCoinsTransactionType;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

/**
 * @author al
 */
public final class ColoredCoinsBidOrderCancellation extends ColoredCoinsOrderCancellationAttachment {

    public ColoredCoinsBidOrderCancellation(ByteBuffer buffer) {
        super(buffer);
    }

    public ColoredCoinsBidOrderCancellation(JSONObject attachmentData) {
        super(attachmentData);
    }

    public ColoredCoinsBidOrderCancellation(long orderId) {
        super(orderId);
    }

    @Override
    public TransactionType getTransactionTypeSpec() {
        return ColoredCoinsTransactionType.BID_ORDER_CANCELLATION;
    }

}
