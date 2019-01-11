package com.apollocurrency.aplwallet.apl.core.http;

import com.apollocurrency.aplwallet.apl.core.app.DigitalGoodsStore;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.AplCore;
import com.apollocurrency.aplwallet.apl.core.app.Block;
import com.apollocurrency.aplwallet.apl.core.app.Account;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.app.Alias;
import com.apollocurrency.aplwallet.apl.core.db.DbIterator;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BlockEventSourceProcessor implements Runnable {
    private BlockEventSource eventSource;
    private static final Logger LOG = LoggerFactory.getLogger(BlockEventSourceProcessor.class);
    private long accountId;

    public BlockEventSourceProcessor(BlockEventSource eventSource, long accountId) {
        this.eventSource = eventSource;
        this.accountId = accountId;
    }

    @Override
    public void run() {
        Blockchain blockchain = AplCore.getBlockchain();
        Block block = blockchain.getLastBlock();
        Block currentBlock;
        try {
            eventSource.emitEvent(getMessage());
            while (!eventSource.isShutdown()) {


                currentBlock = blockchain.getLastBlock();
                if (currentBlock.getHeight() > block.getHeight()) {

                    eventSource.emitEvent(getMessage());

                    block = currentBlock;
                }
                TimeUnit.SECONDS.sleep(2);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Unable to send sse event", e);
        }
        LOG.trace("Exit event source {}", eventSource.isShutdown());
    }

    protected JSONObject getBlockchainData(Blockchain blockchain) {
        JSONArray transactionsArray = new JSONArray();
        try (DbIterator<? extends Transaction> iter = blockchain.getTransactions(accountId,
                0, (byte) -1, (byte) -1, 0, false,
                false, false, 0, 9, false,
                false, false)) {
            while (iter.hasNext()) {
                Transaction transaction = iter.next();
                transactionsArray.add(JSONData.transaction(false, transaction));
            }
        }
        JSONArray purchasesJSON = new JSONArray();

        try (DbIterator<DigitalGoodsStore.Purchase> purchases = DigitalGoodsStore.Purchase.getPendingSellerPurchases(accountId, 0, 9)) {
            while (purchases.hasNext()) {
                purchasesJSON.add(JSONData.purchase(purchases.next()));
            }
        }
        int sellerPurchaseCount = DigitalGoodsStore.Purchase.getSellerPurchaseCount(accountId, false, false);
        int aliasCount = Alias.getAccountAliasCount(accountId);
        JSONArray assetJson = new JSONArray();
        try (DbIterator<Account.AccountAsset> accountAssets = Account.getAccountAssets(accountId, -1, 0, 2)) {
            while (accountAssets.hasNext()) {
                assetJson.add(JSONData.accountAsset(accountAssets.next(), false, true));
            }
        }
        JSONArray currencyJSON = new JSONArray();
        try (DbIterator<Account.AccountCurrency> accountCurrencies = Account.getAccountCurrencies(accountId, -1, 0, 2)) {
            while (accountCurrencies.hasNext()) {
                currencyJSON.add(JSONData.accountCurrency(accountCurrencies.next(), false, true));
            }
        }
        int messageCount = blockchain.getTransactionCount(accountId, (byte) 1, (byte) 0);
        int currencyCount = Account.getAccountCurrencyCount(accountId, -1);
        int assetCount = Account.getAccountAssetCount(accountId, -1);
        JSONObject accountJson = putAccount(accountId);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("transactions", transactionsArray);
        jsonObject.put("purchases", purchasesJSON);
        jsonObject.put("purchaseCount", sellerPurchaseCount);
        jsonObject.put("currencyCount", currencyCount);
        jsonObject.put("assetCount", assetCount);
        jsonObject.put("aliasCount", aliasCount);
        jsonObject.put("assets", assetJson);
        jsonObject.put("currencies", currencyJSON);
        jsonObject.put("messageCount", messageCount);
        jsonObject.put("account", accountJson);
        return jsonObject;
    }

    public String getMessage() {
        Blockchain blockchain = AplCore.getBlockchain();
        JSONObject jsonObject = getBlockchainData(blockchain);
        jsonObject.put("block", JSONData.block(AplCore.getBlockchain().getLastBlock(), false, false));
        return jsonObject.toJSONString();
    }

    private JSONObject putAccount(long accountId) {
        Account account = Account.getAccount(accountId);
        JSONObject response = JSONData.accountBalance(account, false);
        JSONData.putAccount(response, "account", account.getId());

        byte[] publicKey = Account.getPublicKey(account.getId());
        if (publicKey != null) {
            response.put("publicKey", Convert.toHexString(publicKey));
        }
        Account.AccountInfo accountInfo = account.getAccountInfo();
        if (accountInfo != null) {
            response.put("name", Convert.nullToEmpty(accountInfo.getName()));
            response.put("description", Convert.nullToEmpty(accountInfo.getDescription()));
        }
        return response;
    }
}