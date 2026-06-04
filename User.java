package Common;

public class Order {
    // Campi obbligatori
    private long orderId;
    private String username;
    private String type;           // "ask" o "bid"
    private String orderType;      // "market", "limit", "stop"
    private long size;             // dimensione originale in millesimi di BTC
    private long remainingSize;    // quanto resta da evadere
    private long timestamp;        // epoch seconds
    private OrderStatus status;

    // Campi opzionali
    private Long limitPrice;       // per "limit" orders (millesimi di USD)
    private Long stopPrice;        // per "stop" orders (millesimi di USD)
    private Long executionPrice;   // prezzo finale di esecuzione (millesimi di USD)

    public enum OrderStatus {
        ACTIVE, PARTIALLY_FILLED, COMPLETED, CANCELLED
    }

    // Costruttore vuoto per JSON parsing
    public Order() {}

    // Market Order
    public Order(long orderId, String username, String type, long size) {
        this.orderId = orderId;
        this.username = username;
        this.type = type;
        this.orderType = "market";
        this.size = size;
        this.remainingSize = size;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.status = OrderStatus.ACTIVE;
    }

    // Limit Order
    public Order(long orderId, String username, String type, long size, long limitPrice) {
        this.orderId = orderId;
        this.username = username;
        this.type = type;
        this.orderType = "limit";
        this.size = size;
        this.remainingSize = size;
        this.limitPrice = limitPrice;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.status = OrderStatus.ACTIVE;
    }

    // Stop Order
    public Order(long orderId, String username, String type, long size, long stopPrice, boolean isStopOrder) {
        this.orderId = orderId;
        this.username = username;
        this.type = type;
        this.orderType = "stop";
        this.size = size;
        this.remainingSize = size;
        this.stopPrice = stopPrice;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.status = OrderStatus.ACTIVE;
    }

    // Getter e Setter
    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getRemainingSize() { return remainingSize; }
    public void setRemainingSize(long remainingSize) { this.remainingSize = remainingSize; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Long getLimitPrice() { return limitPrice; }
    public void setLimitPrice(Long limitPrice) { this.limitPrice = limitPrice; }

    public Long getStopPrice() { return stopPrice; }
    public void setStopPrice(Long stopPrice) { this.stopPrice = stopPrice; }

    public Long getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(Long executionPrice) { this.executionPrice = executionPrice; }

    // Metodi di utilità
    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }

    public boolean canBeCancelled() {
        return status == OrderStatus.ACTIVE || status == OrderStatus.PARTIALLY_FILLED;
    }

    public void executePartially(long executedSize, long price) {
        this.remainingSize -= executedSize;
        this.executionPrice = price;

        if (this.remainingSize <= 0) {
            this.status = OrderStatus.COMPLETED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, user='%s', type='%s', orderType='%s', size=%d, remaining=%d, status=%s}",
                orderId, username, type, orderType, size, remainingSize, status);
    }
}
