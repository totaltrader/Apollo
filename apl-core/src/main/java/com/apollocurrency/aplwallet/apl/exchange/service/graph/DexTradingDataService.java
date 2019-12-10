package com.apollocurrency.aplwallet.apl.exchange.service.graph;

import com.apollocurrency.aplwallet.api.trading.ConversionType;
import com.apollocurrency.aplwallet.api.trading.SimpleTradingEntry;
import com.apollocurrency.aplwallet.api.trading.TradingDataOutput;
import com.apollocurrency.aplwallet.apl.core.app.Convert2;
import com.apollocurrency.aplwallet.apl.core.config.Property;
import com.apollocurrency.aplwallet.apl.exchange.dao.DexCandlestickDao;
import com.apollocurrency.aplwallet.apl.exchange.dao.DexOrderDao;
import com.apollocurrency.aplwallet.apl.exchange.model.DexCandlestick;
import com.apollocurrency.aplwallet.apl.exchange.model.DexCurrency;
import com.apollocurrency.aplwallet.apl.exchange.model.DexOrder;
import com.apollocurrency.aplwallet.apl.exchange.model.OrderDbIdPaginationDbRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.apollocurrency.aplwallet.apl.exchange.service.graph.CandlestickUtil.BASE_TIME_INTERVAL;
import static com.apollocurrency.aplwallet.apl.exchange.service.graph.CandlestickUtil.convertOrders;

@Singleton
public class DexTradingDataService {

    private static final int  DEFAULT_ORDER_SELECT_LIMIT = 100;

    private boolean enableTradingViewGraphDataFeeder; // not yet implemented
    private boolean enableTradingDataCache; // not yet implemented
    private DexCandlestickDao candlestickDao;
    private DexOrderDao orderDao;
    private int orderSelectLimit;



    @Inject
    public DexTradingDataService(@Property("apl.dex.graph.enableDataFeeder") boolean enableTradingViewGraphDataFeeder,
                                 @Property("apl.dex.graph.enableDataCache") boolean enableTradingDataCache,
                                 DexCandlestickDao candlestickDao,
                                 DexOrderDao orderDao) {
        this(enableTradingViewGraphDataFeeder, enableTradingDataCache, candlestickDao, orderDao, DEFAULT_ORDER_SELECT_LIMIT);
    }

    public DexTradingDataService(boolean enableTradingViewGraphDataFeeder,
                                 boolean enableTradingDataCache,
                                 DexCandlestickDao candlestickDao,
                                 DexOrderDao orderDao,
                                 int orderSelectLimit) {
        this.enableTradingViewGraphDataFeeder = enableTradingViewGraphDataFeeder;
        this.enableTradingDataCache = enableTradingDataCache;
        this.candlestickDao = Objects.requireNonNull(candlestickDao);
        this.orderDao = Objects.requireNonNull(orderDao);
        this.orderSelectLimit = orderSelectLimit;
    }

    public List<SimpleTradingEntry> getFromCandlesticks(int toTimestamp, int limit, DexCurrency currency, TimeFrame timeFrame) {
        validateLimit(limit);
        int startTime = toTimestamp - limit * timeFrame.muliplier * BASE_TIME_INTERVAL;
        int prevTime = ceilTo(timeFrame, startTime);
        List<DexCandlestick> candlesticks = candlestickDao.getForTimespan(prevTime, toTimestamp, currency);
        List<SimpleTradingEntry> entries = candlesticks.stream().map(this::fromCandleStick).collect(Collectors.toList());
        return getResultData(entries, timeFrame, prevTime, toTimestamp);
    }

    private int ceilTo(TimeFrame timeFrame, int time) {
        int interval = timeFrame.muliplier * BASE_TIME_INTERVAL;
        int remainder = time % interval;
        return time + (interval - remainder);
    }

    private int floorTo(TimeFrame timeFrame, int time) {
        int interval = timeFrame.muliplier * BASE_TIME_INTERVAL;
        int remainder = time % interval;
        return time - remainder;
    }

    public TradingDataOutput getForTimeFrame(int toTimestamp, int limit, DexCurrency currency, TimeFrame timeFrame) {
        validateLimit(limit);
        int startTime = toTimestamp - limit * timeFrame.muliplier * BASE_TIME_INTERVAL;
        int lastCandlestickTimestamp = getLastCandlestickTimestamp(currency);
        List<SimpleTradingEntry> data = new ArrayList<>();
        if (lastCandlestickTimestamp == -1 ||
                floorTo(timeFrame, lastCandlestickTimestamp) <= ceilTo(timeFrame, startTime) && floorTo(timeFrame, lastCandlestickTimestamp) <= toTimestamp) { // only orders
            data.addAll(getForTimeFrameFromDexOrders(ceilTo(timeFrame, startTime), toTimestamp, currency, timeFrame));
        } else if (floorTo(timeFrame, lastCandlestickTimestamp) > toTimestamp) { // only candlesticks
            data.addAll(getFromCandlesticks(toTimestamp, limit, currency, timeFrame));
        } else { // both candlesticks and orders
            int fromTimestamp = floorTo(timeFrame, lastCandlestickTimestamp);
            List<SimpleTradingEntry> dexOrderCandlesticks = getForTimeFrameFromDexOrders(fromTimestamp, toTimestamp, currency, timeFrame);
            limit -= dexOrderCandlesticks.size();
            data.addAll(getFromCandlesticks(fromTimestamp - 1, limit, currency, timeFrame)); // do not include last candlestick
            data.addAll(dexOrderCandlesticks);
        }
        return buildTradingDataOutput(toTimestamp, startTime, data);
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit should be greater than zero");
        }
    }

    public int getLastCandlestickTimestamp(DexCurrency currency) {
        DexCandlestick last = candlestickDao.getLast(currency);
        if (last != null) {
            return last.getTimestamp();
        } else {
            return -1;
        }
    }
    public List<SimpleTradingEntry> getForTimeFrameFromDexOrders(int fromTimestamp, int toTimestamp, DexCurrency currency, TimeFrame timeFrame) {
        List<DexCandlestick> orderCandlesticks = getOrderCandlesticks(fromTimestamp, toTimestamp, currency, timeFrame);
        List<SimpleTradingEntry> fullData = orderCandlesticks.stream().map(this::fromCandleStick).collect(Collectors.toList());
        return getResultData(fullData, timeFrame, fromTimestamp, toTimestamp);
    }

    private List<DexCandlestick> getOrderCandlesticks(int fromTimestamp, int toTimestamp, DexCurrency currency, TimeFrame timeFrame) {
        int fromEpochTime = Convert2.toEpochTime((long)fromTimestamp * 1000);
        int toEpochTime = Convert2.toEpochTime((long)toTimestamp * 1000);
        long fromDbId = 0;
        List<DexOrder> orders;
        Map<Integer, DexCandlestick> candlesticks = new HashMap<>();
        do {
            orders = orderDao.getOrdersFromDbIdBetweenTimestamps(OrderDbIdPaginationDbRequest.builder()
                    .limit(orderSelectLimit)
                    .coin(currency)
                    .fromTime(fromEpochTime)
                    .toTime(toEpochTime)
                    .fromDbId(fromDbId)
                    .build());
            convertOrders(orders, candlesticks, timeFrame, t-> null);
            if (orders.size() > 0) {
                fromDbId = orders.get(orders.size() - 1).getDbId();
            }
        } while (orders.size() == orderSelectLimit);
        return candlesticks.values().stream().sorted(Comparator.comparing(DexCandlestick::getTimestamp)).collect(Collectors.toList());
    }

    private List<SimpleTradingEntry> getResultData(List<SimpleTradingEntry> fullData, TimeFrame timeFrame, int startTime, int finishTime) {
        List<SimpleTradingEntry> result = new ArrayList<>();
        int interval = timeFrame.muliplier * BASE_TIME_INTERVAL;
        for (int candlestickTime = startTime; candlestickTime <= finishTime; candlestickTime += interval) {
            int candlestickFinishTime = candlestickTime + interval;
            int finalCandlestickTime = candlestickTime;
            List<SimpleTradingEntry> entries = fullData.stream().filter(e->e.getTime() < candlestickFinishTime && e.getTime() >= finalCandlestickTime).collect(Collectors.toList());
            SimpleTradingEntry compressed = compress(entries, candlestickTime);
            result.add(compressed);
        }
        return result;
    }


    private TradingDataOutput buildTradingDataOutput(int toTimestamp, int fromTimestamp, List<SimpleTradingEntry> data) {
        TradingDataOutput tradingDataOutput = new TradingDataOutput();
        tradingDataOutput.setResponse("Success");
        tradingDataOutput.setType(100);
        tradingDataOutput.setAggregated(false);
        tradingDataOutput.setData(data);
        tradingDataOutput.setTimeTo(toTimestamp);
        tradingDataOutput.setTimeFrom(fromTimestamp);
        tradingDataOutput.setFirstValueInArray(true);
        ConversionType conversionType = new ConversionType();
        conversionType.type = "force_direct";
        conversionType.conversionSymbol = "";
        tradingDataOutput.setConversionType(conversionType);
        tradingDataOutput.setHasWarning(false);
        return tradingDataOutput;
    }

    private SimpleTradingEntry compress(List<SimpleTradingEntry> entries, int time) {
            BigDecimal totalVolumeFrom = BigDecimal.ZERO;
            BigDecimal totalVolumeTo = BigDecimal.ZERO;
            BigDecimal maxPrice = BigDecimal.ZERO;
            BigDecimal openPrice = BigDecimal.ZERO;
            BigDecimal closePrice = BigDecimal.ZERO;
            BigDecimal minPrice = BigDecimal.ZERO;
            boolean openFound = false;
            for (SimpleTradingEntry entry : entries) {
                if (maxPrice.compareTo(entry.getHigh()) < 0) {
                    maxPrice = entry.getHigh();
                }
                if (minPrice.equals(BigDecimal.ZERO) || minPrice.compareTo(entry.getLow()) > 0) {
                    minPrice = entry.getLow();
                }
                totalVolumeFrom = totalVolumeFrom.add(entry.getVolumefrom());
                totalVolumeTo = totalVolumeTo.add(entry.getVolumeto());
                if (!openFound) {
                    openPrice = entry.getOpen();
                    openFound = true;
                }
                closePrice = entry.getClose();
            }
            return new SimpleTradingEntry(time, openPrice, closePrice, minPrice, maxPrice, totalVolumeFrom, totalVolumeTo);
    }

    private SimpleTradingEntry fromCandleStick(DexCandlestick c) {
        return new SimpleTradingEntry(c.getTimestamp(), c.getOpen(), c.getClose(), c.getMin(), c.getMax(), c.getFromVolume(), c.getToVolume());
    }

}
