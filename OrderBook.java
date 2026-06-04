package Server;

import Common.Order;
import Common.TradeRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OrderBook {

    // Strutture dati per gli ordini attivi
    // TreeMap per ordinamento automatico, LinkedList per ordine temporale a parità di prezzo
    private final TreeMap<Long, LinkedList<Order>> bidOrders;  // Prezzi decrescenti
    private final TreeMap<Long, LinkedList<Order>> askOrders;  // Prezzi crescenti

    // Mappa per accesso rapido agli ordini per ID
    private final ConcurrentHashMap<Long, Order> orderById;

    // Ordini di tipo Stop in attesa
    private final List<Order> stopOrders;

    // Lock per thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Prezzo dell'ultimo trade (per gestire Stop Orders)
    private volatile Long lastTradePrice = null;

    // Callback per notificare trades completati
    private TradeExecutionCallback tradeCallback;

    public interface TradeExecutionCallback {
        void onTradeExecuted(List<TradeRecord> trades);
    }

    public OrderBook() {
        // Bid: prezzi decrescenti (il più alto per primo)
        this.bidOrders = new TreeMap<>(Collections.reverseOrder());
        // Ask: prezzi crescenti (il più basso per primo)
        this.askOrders = new TreeMap<>();
        this.orderById = new ConcurrentHashMap<>();
        this.stopOrders = new ArrayList<>();
    }

    public void setTradeCallback(TradeExecutionCallback callback) {
        this.tradeCallback = callback;
    }

    /**
     * Aggiunge un ordine all'order book e tenta il matching
     */
    public List<TradeRecord> addOrder(Order order) {
        lock.writeLock().lock();
        try {
            List<TradeRecord> trades = new ArrayList<>();

            // Se è Market Order, prova esecuzione immediata
            if ("market".equals(order.getOrderType())) {
                trades = executeMarketOrder(order);
            }
            // Se è Limit Order, prova matching e poi aggiunge se non completato
            else if ("limit".equals(order.getOrderType())) {
                trades = executeLimitOrder(order);
            }
            // Se è Stop Order, lo aggiunge alla lista di stop orders
            else if ("stop".equals(order.getOrderType())) {
                stopOrders.add(order);
                orderById.put(order.getOrderId(), order);
            }

            // Aggiorna il prezzo dell'ultimo trade se ci sono stati trades
            if (!trades.isEmpty()) {
                lastTradePrice = trades.get(trades.size() - 1).getPrice();
                // Controlla se ci sono Stop Orders da attivare
                checkStopOrders();
            }

            // Notifica i trades se c'è un callback
            if (tradeCallback != null && !trades.isEmpty()) {
                tradeCallback.onTradeExecuted(trades);
            }

            return trades;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Esegue un Market Order
     */
    private List<TradeRecord> executeMarketOrder(Order marketOrder) {
        List<TradeRecord> trades = new ArrayList<>();

        if ("bid".equals(marketOrder.getType())) {
            // Market buy: prende dai migliori ask (prezzi più bassi)
            trades = matchWithBestOrders(marketOrder, askOrders);
        } else if ("ask".equals(marketOrder.getType())) {
            // Market sell: prende dai migliori bid (prezzi più alti)
            trades = matchWithBestOrders(marketOrder, bidOrders);
        }

        // Se il Market Order non è stato completamente eseguito, viene scartato
        if (marketOrder.getRemainingSize() > 0) {
            marketOrder.setStatus(Order.OrderStatus.CANCELLED);
        }

        return trades;
    }

    /**
     * Esegue un Limit Order
     */
    private List<TradeRecord> executeLimitOrder(Order limitOrder) {
        List<TradeRecord> trades = new ArrayList<>();

        if ("bid".equals(limitOrder.getType())) {
            // Limit buy: matcha con ask orders a prezzo <= limit price
            trades = matchLimitOrder(limitOrder, askOrders, true);
        } else if ("ask".equals(limitOrder.getType())) {
            // Limit sell: matcha con bid orders a prezzo >= limit price
            trades = matchLimitOrder(limitOrder, bidOrders, false);
        }

        // Se rimane qualcosa da eseguire, aggiunge all'order book
        if (limitOrder.getRemainingSize() > 0 && !limitOrder.isCompleted()) {
            addToOrderBook(limitOrder);
        }

        return trades;
    }

    /**
     * Matcha un Market Order con i migliori ordini disponibili
     */
    private List<TradeRecord> matchWithBestOrders(Order marketOrder, TreeMap<Long, LinkedList<Order>> oppositeOrders) {
        List<TradeRecord> trades = new ArrayList<>();

        while (marketOrder.getRemainingSize() > 0 && !oppositeOrders.isEmpty()) {
            Map.Entry<Long, LinkedList<Order>> bestEntry = oppositeOrders.firstEntry();
            LinkedList<Order> ordersAtPrice = bestEntry.getValue();

            if (ordersAtPrice.isEmpty()) {
                oppositeOrders.remove(bestEntry.getKey());
                continue;
            }

            Order matchingOrder = ordersAtPrice.getFirst();
            List<TradeRecord> newTrades = executeTradeWithBothRecords(marketOrder, matchingOrder, matchingOrder.getLimitPrice());

            trades.addAll(newTrades);

            // Rimuove ordini completati
            if (matchingOrder.isCompleted()) {
                ordersAtPrice.removeFirst();
                orderById.remove(matchingOrder.getOrderId());

                if (ordersAtPrice.isEmpty()) {
                    oppositeOrders.remove(bestEntry.getKey());
                }
            }
        }

        return trades;
    }

    /**
     * Matcha un Limit Order
     */
    private List<TradeRecord> matchLimitOrder(Order limitOrder, TreeMap<Long, LinkedList<Order>> oppositeOrders, boolean isBuyOrder) {
        List<TradeRecord> trades = new ArrayList<>();
        Long limitPrice = limitOrder.getLimitPrice();

        Iterator<Map.Entry<Long, LinkedList<Order>>> iterator = oppositeOrders.entrySet().iterator();

        while (iterator.hasNext() && limitOrder.getRemainingSize() > 0) {
            Map.Entry<Long, LinkedList<Order>> entry = iterator.next();
            Long price = entry.getKey();
            LinkedList<Order> ordersAtPrice = entry.getValue();

            // Controlla se il prezzo è compatibile
            boolean canMatch = isBuyOrder ? (price <= limitPrice) : (price >= limitPrice);

            if (!canMatch) {
                break; // Non ci sono più prezzi compatibili
            }

            while (!ordersAtPrice.isEmpty() && limitOrder.getRemainingSize() > 0) {
                Order matchingOrder = ordersAtPrice.getFirst();
                List<TradeRecord> newTrades = executeTradeWithBothRecords(limitOrder, matchingOrder, price);

                trades.addAll(newTrades);

                if (matchingOrder.isCompleted()) {
                    ordersAtPrice.removeFirst();
                    orderById.remove(matchingOrder.getOrderId());
                }
            }

            if (ordersAtPrice.isEmpty()) {
                iterator.remove();
            }
        }

        return trades;
    }

    /**
     * Esegue un trade tra due ordini e crea i record per entrambi
     */

    private List<TradeRecord> executeTradeWithBothRecords(Order order1, Order order2, Long tradePrice) {
        long tradeSize = Math.min(order1.getRemainingSize(), order2.getRemainingSize());
        List<TradeRecord> trades = new ArrayList<>();

        if (tradeSize <= 0) {
            return trades;
        }

        // Aggiorna gli ordini
        order1.executePartially(tradeSize, tradePrice);
        order2.executePartially(tradeSize, tradePrice);

        long timestamp = System.currentTimeMillis() / 1000;

        // Crea trade record per il primo ordine (con username)
        TradeRecord trade1 = new TradeRecord(order1.getOrderId(), order1.getUsername(), order1.getType(),
                order1.getOrderType(), tradeSize, tradePrice, timestamp);

        // Crea trade record per il secondo ordine (con username)
        TradeRecord trade2 = new TradeRecord(order2.getOrderId(), order2.getUsername(), order2.getType(),
                order2.getOrderType(), tradeSize, tradePrice, timestamp);

        trades.add(trade1);
        trades.add(trade2);

        return trades;
    }

    /**
     * Aggiunge un ordine all'order book
     */
    private void addToOrderBook(Order order) {
        TreeMap<Long, LinkedList<Order>> targetMap = "bid".equals(order.getType()) ? bidOrders : askOrders;
        Long price = order.getLimitPrice();

        targetMap.computeIfAbsent(price, k -> new LinkedList<>()).addLast(order);
        orderById.put(order.getOrderId(), order);
    }

    /**
     * Controlla se ci sono Stop Orders da attivare
     */
    private void checkStopOrders() {
        if (lastTradePrice == null) return;

        Iterator<Order> iterator = stopOrders.iterator();
        List<Order> toActivate = new ArrayList<>();

        while (iterator.hasNext()) {
            Order stopOrder = iterator.next();
            Long stopPrice = stopOrder.getStopPrice();

            boolean shouldActivate = false;

            if ("bid".equals(stopOrder.getType())) {
                // Stop buy: attiva se prezzo >= stop price
                shouldActivate = lastTradePrice >= stopPrice;
            } else if ("ask".equals(stopOrder.getType())) {
                // Stop sell: attiva se prezzo <= stop price
                shouldActivate = lastTradePrice <= stopPrice;
            }

            if (shouldActivate) {
                toActivate.add(stopOrder);
                iterator.remove();
                orderById.remove(stopOrder.getOrderId());
            }
        }

        // Attiva gli stop orders come market orders
        for (Order stopOrder : toActivate) {
            Order marketOrder = new Order(stopOrder.getOrderId(), stopOrder.getUsername(),
                    stopOrder.getType(), stopOrder.getSize());
            List<TradeRecord> stopTrades = executeMarketOrder(marketOrder);

            // I trades degli stop orders vengono già gestiti dal callback principale
            // Quindi non è necessario fare nulla di specifico qui
        }
    }

    /**
     * Cancella un ordine
     */
    public boolean cancelOrder(long orderId, String username) {
        lock.writeLock().lock();
        try {
            Order order = orderById.get(orderId);

            if (order == null || !order.getUsername().equals(username) || !order.canBeCancelled()) {
                return false;
            }

            // Rimuove dall'order book
            if ("limit".equals(order.getOrderType())) {
                TreeMap<Long, LinkedList<Order>> targetMap = "bid".equals(order.getType()) ? bidOrders : askOrders;
                LinkedList<Order> ordersAtPrice = targetMap.get(order.getLimitPrice());

                if (ordersAtPrice != null) {
                    ordersAtPrice.remove(order);
                    if (ordersAtPrice.isEmpty()) {
                        targetMap.remove(order.getLimitPrice());
                    }
                }
            } else if ("stop".equals(order.getOrderType())) {
                stopOrders.remove(order);
            }

            orderById.remove(orderId);
            order.setStatus(Order.OrderStatus.CANCELLED);

            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Ottiene lo stato attuale dell'order book
     */
    public OrderBookSnapshot getSnapshot() {
        lock.readLock().lock();
        try {
            // Crea lista combinata di tutti gli ordini attivi
            List<Order> allActiveOrders = new ArrayList<>();

            // Aggiungi ordini bid
            for (LinkedList<Order> orders : bidOrders.values()) {
                allActiveOrders.addAll(orders);
            }

            // Aggiungi ordini ask
            for (LinkedList<Order> orders : askOrders.values()) {
                allActiveOrders.addAll(orders);
            }

            // Aggiungi stop orders
            allActiveOrders.addAll(stopOrders);

            return new OrderBookSnapshot(
                    new TreeMap<>(bidOrders),
                    new TreeMap<>(askOrders),
                    lastTradePrice,
                    stopOrders.size(),
                    allActiveOrders
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Classe per snapshot dell'order book
     */
    public static class OrderBookSnapshot {
        private final TreeMap<Long, LinkedList<Order>> bidOrders;
        private final TreeMap<Long, LinkedList<Order>> askOrders;
        private final Long lastTradePrice;
        private final int stopOrdersCount;
        private final List<Order> allActiveOrders;

        public OrderBookSnapshot(TreeMap<Long, LinkedList<Order>> bidOrders,
                                 TreeMap<Long, LinkedList<Order>> askOrders,
                                 Long lastTradePrice, int stopOrdersCount,
                                 List<Order> allActiveOrders) {
            this.bidOrders = bidOrders;
            this.askOrders = askOrders;
            this.lastTradePrice = lastTradePrice;
            this.stopOrdersCount = stopOrdersCount;
            this.allActiveOrders = allActiveOrders;
        }

        // Getter methods
        public TreeMap<Long, LinkedList<Order>> getBidOrders() { return bidOrders; }
        public TreeMap<Long, LinkedList<Order>> getAskOrders() { return askOrders; }
        public Long getLastTradePrice() { return lastTradePrice; }
        public int getStopOrdersCount() { return stopOrdersCount; }
        public List<Order> getAllActiveOrders() { return allActiveOrders; }

        public Long getBestBidPrice() {
            return bidOrders.isEmpty() ? null : bidOrders.firstKey();
        }

        public Long getBestAskPrice() {
            return askOrders.isEmpty() ? null : askOrders.firstKey();
        }

        public Long getBidAskSpread() {
            Long bestBid = getBestBidPrice();
            Long bestAsk = getBestAskPrice();
            return (bestBid != null && bestAsk != null) ? bestAsk - bestBid : null;
        }
    }
}