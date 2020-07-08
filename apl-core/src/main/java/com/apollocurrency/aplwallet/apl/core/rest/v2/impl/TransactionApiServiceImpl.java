package com.apollocurrency.aplwallet.apl.core.rest.v2.impl;

import com.apollocurrency.aplwallet.api.v2.NotFoundException;
import com.apollocurrency.aplwallet.api.v2.TransactionApiService;
import com.apollocurrency.aplwallet.api.v2.model.BaseResponse;
import com.apollocurrency.aplwallet.api.v2.model.ListResponse;
import com.apollocurrency.aplwallet.api.v2.model.TransactionInfoResp;
import com.apollocurrency.aplwallet.api.v2.model.TxRequest;
import com.apollocurrency.aplwallet.apl.core.app.AplException;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.Transaction;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.rest.ApiErrors;
import com.apollocurrency.aplwallet.apl.core.rest.v2.ResponseBuilderV2;
import com.apollocurrency.aplwallet.apl.core.rest.v2.converter.TransactionInfoMapper;
import com.apollocurrency.aplwallet.apl.core.rest.v2.converter.UnTxReceiptMapper;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequestScoped
public class TransactionApiServiceImpl implements TransactionApiService {

    private final TransactionProcessor transactionProcessor;
    private final Blockchain blockchain;
    private final UnTxReceiptMapper unTxReceiptMapper;
    private final TransactionInfoMapper transactionInfoMapper;

    @Inject
    public TransactionApiServiceImpl(TransactionProcessor transactionProcessor,
                                     Blockchain blockchain,
                                     UnTxReceiptMapper unTxReceiptMapper,
                                     TransactionInfoMapper transactionInfoMapper) {
        this.transactionProcessor = Objects.requireNonNull(transactionProcessor);
        this.blockchain = Objects.requireNonNull(blockchain);
        this.unTxReceiptMapper = Objects.requireNonNull(unTxReceiptMapper);
        this.transactionInfoMapper = Objects.requireNonNull(transactionInfoMapper);
    }

    /*
     * response=UnTxReceipt
     */
    public Response broadcastTx(TxRequest body, SecurityContext securityContext) throws NotFoundException {
        ResponseBuilderV2 builder = ResponseBuilderV2.startTiming();
        BaseResponse receipt = broadcastOneTx(body);
        return builder.bind(receipt).build();
    }

    public Response broadcastTxBatch(List<TxRequest> body, SecurityContext securityContext) throws NotFoundException {
        ResponseBuilderV2 builder = ResponseBuilderV2.startTiming();
        ListResponse listResponse = new ListResponse();
        for (TxRequest req : body) {
            BaseResponse receipt = broadcastOneTx(req);
            listResponse.getResult().add(receipt);
        }
        return builder.bind(listResponse).build();
    }

    private BaseResponse broadcastOneTx(TxRequest req) {
        BaseResponse receipt;
        try {
            if (log.isTraceEnabled()) {
                log.trace("Broadcast transaction: tx={}", req.getTx());
            }
            byte[] tx = Convert.parseHexString(req.getTx());
            Transaction.Builder txBuilder = Transaction.newTransactionBuilder(tx);
            Transaction newTx = txBuilder.build();
            transactionProcessor.broadcast(newTx);
            receipt = unTxReceiptMapper.convert(newTx);
            if (log.isTraceEnabled()) {
                log.trace("UnTxReceipt={}", receipt);
            }
        } catch (NumberFormatException e) {
            receipt = ResponseBuilderV2.createErrorResponse(
                ApiErrors.CUSTOM_ERROR_MESSAGE,
                "Cant't parse byte array, cause " + e.getMessage());

        } catch (AplException.ValidationException e) {
            receipt = ResponseBuilderV2.createErrorResponse(
                ApiErrors.CONSTRAINT_VIOLATION,
                e.getMessage());
        }
        return receipt;
    }

    public Response getTxById(String transactionIdStr, SecurityContext securityContext) throws NotFoundException {
        ResponseBuilderV2 builder = ResponseBuilderV2.startTiming();
        long transactionId;
        Transaction transaction;
        try {
            transactionId = Convert.parseUnsignedLong(transactionIdStr);
        } catch (RuntimeException e) {
            return builder
                .error(ApiErrors.CUSTOM_ERROR_MESSAGE, "Cant't parse transaction id, cause " + e.getMessage())
                .build();
        }
        transaction = blockchain.getTransaction(transactionId);
        if (transaction == null) {
            transaction = transactionProcessor.getUnconfirmedTransaction(transactionId);
        }
        if (transaction == null) {
            throw new NotFoundException("Transaction not found. id=" + transactionIdStr);
        }
        TransactionInfoResp resp = transactionInfoMapper.convert(transaction);
        if (log.isTraceEnabled()) {
            log.trace("TransactionResp={}", resp);
        }
        return builder.bind(resp).build();
    }
}