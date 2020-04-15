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

package com.apollocurrency.aplwallet.apl.core.http.post;

import com.apollocurrency.aplwallet.apl.core.account.model.Account;
import com.apollocurrency.aplwallet.apl.core.http.APITag;
import com.apollocurrency.aplwallet.apl.core.http.HttpParameterParserUtil;
import com.apollocurrency.aplwallet.apl.core.order.entity.AskOrder;
import com.apollocurrency.aplwallet.apl.core.order.service.qualifier.AskOrderService;
import com.apollocurrency.aplwallet.apl.core.order.service.impl.AskOrderServiceImpl;
import com.apollocurrency.aplwallet.apl.core.order.service.OrderService;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.Attachment;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsAskOrderCancellation;
import com.apollocurrency.aplwallet.apl.core.transaction.messages.ColoredCoinsAskOrderPlacement;
import com.apollocurrency.aplwallet.apl.util.AplException;
import org.json.simple.JSONStreamAware;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;

import static com.apollocurrency.aplwallet.apl.core.http.JSONResponses.UNKNOWN_ORDER;

@Vetoed
public final class CancelAskOrder extends CreateTransaction {
    private final OrderService<AskOrder, ColoredCoinsAskOrderPlacement> askOrderService =
        CDI.current().select(AskOrderServiceImpl.class, AskOrderService.Literal.INSTANCE).get();

    public CancelAskOrder() {
        super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, "order");
    }

    @Override
    public JSONStreamAware processRequest(HttpServletRequest req) throws AplException {
        long orderId = HttpParameterParserUtil.getUnsignedLong(req, "order", true);
        Account account = HttpParameterParserUtil.getSenderAccount(req);
        AskOrder orderData = askOrderService.getOrder(orderId);
        if (orderData == null || orderData.getAccountId() != account.getId()) {
            return UNKNOWN_ORDER;
        }
        Attachment attachment = new ColoredCoinsAskOrderCancellation(orderId);
        return createTransaction(req, account, attachment);
    }
}
