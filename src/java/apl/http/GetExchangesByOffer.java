/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2017-2018 Apollo Foundation
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Apollo Foundation,
 * no part of the Apl software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package apl.http;

import apl.Exchange;
import apl.db.DbIterator;
import apl.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static apl.http.JSONResponses.INCORRECT_OFFER;
import static apl.http.JSONResponses.MISSING_OFFER;

public final class GetExchangesByOffer extends APIServlet.APIRequestHandler {

    private static class GetExchangesByOfferHolder {
        private static final GetExchangesByOffer INSTANCE = new GetExchangesByOffer();
    }

    public static GetExchangesByOffer getInstance() {
        return GetExchangesByOfferHolder.INSTANCE;
    }

    private GetExchangesByOffer() {
        super(new APITag[] {APITag.MS}, "offer", "includeCurrencyInfo", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        // can't use ParameterParser.getCurrencyBuyOffer because offer may have been already deleted
        String offerValue = Convert.emptyToNull(req.getParameter("offer"));
        if (offerValue == null) {
            throw new ParameterException(MISSING_OFFER);
        }
        long offerId;
        try {
            offerId = Convert.parseUnsignedLong(offerValue);
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_OFFER);
        }
        boolean includeCurrencyInfo = "true".equalsIgnoreCase(req.getParameter("includeCurrencyInfo"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        JSONObject response = new JSONObject();
        JSONArray exchangesData = new JSONArray();
        try (DbIterator<Exchange> exchanges = Exchange.getOfferExchanges(offerId, firstIndex, lastIndex)) {
            while (exchanges.hasNext()) {
                exchangesData.add(JSONData.exchange(exchanges.next(), includeCurrencyInfo));
            }
        }
        response.put("exchanges", exchangesData);
        return response;
    }

}
