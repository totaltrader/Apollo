package com.apollocurrrency.aplwallet.inttest.model;

public enum RequestType {
    dgsRefund,
    dgsFeedback,
    dgsDelivery,
    dgsPurchase,
    dgsPriceChange,
    getDGSGood,
    dgsQuantityChange,
    dgsDelisting,
    dgsListing,
    shufflingVerify,
    startShuffler,
    shufflingProcess,
    shufflingRegister,
    shufflingCancel,
    getShuffling,
    getAccountCurrencies,
    shufflingCreate,
    scheduleCurrencyBuy,
    currencyBuy,
    currencySell,
    publishExchangeOffer,
    currencyReserveIncrease,
    currencyReserveClaim,
    currencyMint,
    transferCurrency,
    getAllCurrencies,
    getCurrency,
    getCurrencyAccounts,
    deleteCurrency,
    getForging,
    stopForging,
    startForging,
    importKey,
    exportKey,
    getECBlock,
    getBlockchainStatus,
    getMyInfo,
    addPeer,
    enable2FA,
    getPeer,
    deleteKey,
    generateAccount,
    deleteAccountProperty,
    requestType,
    getTransaction,
    getPrivateBlockchainTransactions,
    getAccount,
    getAccountBlockCount,
    getAccountBlocks,
    getAccountBlockIds,
    getAccountId,
    getAccountLedger,
    getAccountLedgerEntry,
    getAccountLessors,
    getAccountProperties,
    getAccountPublicKey,
    getBlockchainTransactions,
    getBalance,
    getGuaranteedBalance,
    getUnconfirmedTransactionIds,
    getUnconfirmedTransactions,
    setAccountInfo,
    startFundingMonitor,
    stopFundingMonitor,
    getAllPhasingOnlyControls,
    getPhasingOnlyControl,
    setPhasingOnlyControl,
    searchAccounts,
    getPeers,
    getBlocks,
    sendMoney,
    setAlias,
    setAccountProperty,
    getAlias,
    getAliases,
    getAliasCount,
    deleteAlias,
    sellAlias,
    buyAlias,
    getAliasesLike,
    encryptTo,
    decryptFrom,
    downloadPrunableMessage,
    sendMessage,
    getAllPrunableMessages,
    getPrunableMessage,
    readMessage,
    verifyPrunableMessage,
    issueAsset,
    getAccountAssetCount,
    getAccountAssets,
    getAsset,
    getAllAssets,
    getAssets,
    placeBidOrder,
    placeAskOrder,
    getAccountCurrentBidOrders,
    getAccountCurrentAskOrders,
    getAllOpenBidOrders,
    getAllOpenAskOrders,
    getAccountCurrentBidOrderIds,
    getAccountCurrentAskOrderIds,
    getAllTrades,
    getAssetAccountCount,
    getAssetAccounts,
    cancelBidOrder,
    cancelAskOrder,
    getAssetIds,
    getAssetTransfers,
    transferAsset,
    getAssetsByIssuer,
    getBidOrders,
    getAskOrders,
    getAskOrder,
    getBidOrder,
    getBidOrderIds,
    getAskOrderIds,
    getLastTrades,
    getOrderTrades,
    deleteAssetShares,
    getExpectedAssetDeletes,
    getExpectedOrderCancellations,
    getExpectedBidOrders,
    getExpectedAskOrders,
    getAssetDeletes,
    searchAssets,
    getBlock,
    getBlockId,
    sendMoneyPrivate,
    dividendPayment,
    getAssetDividends,
    getExpectedAssetTransfers,
    issueCurrency,
    getPoll,
    createPoll,
    castVote,
    getPollVotes,
    getPollResult,
    uploadTaggedData,
    downloadTaggedData,
    getAllTaggedData,
    getTaggedData,
    getDataTagCount,
    searchTaggedData,
    extendTaggedData
}