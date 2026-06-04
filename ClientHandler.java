package Common;

public class TradeRecord {
    private long orderId;
    private String username;    // Username del proprietario dell'ordine
    private String type;        // "ask" o "bid"
    private String orderType;   // "market", "limit", "stop"
    private long size;          // dimensione in millesimi di BTC
    private long price;         // prezzo di esecuzione in millesimi di USD
    private long timestamp;     // epoch seconds

    // Costruttore vuoto per JSON parsing
    public TradeRecord() {}

    // Costruttore completo (per creare trade records nell'OrderBook)
    public TradeRecord(long orderId, String username, String type, String orderType, long size, long price, long timestamp) {
        this.orderId = orderId;
        this.username = username;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = timestamp;
    }

    // Costruttore da Order (quando un ordine viene completato)
    public TradeRecord(Order order) {
        this.orderId = order.getOrderId();
        this.username = order.getUsername();
        this.type = order.getType();
        this.orderType = order.getOrderType();
        this.size = order.getSize();
        this.price = order.getExecutionPrice() != null ? order.getExecutionPrice() : 0;
        this.timestamp = order.getTimestamp();
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

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("TradeRecord{orderId=%d, username='%s', type='%s', orderType='%s', size=%d, price=%d, timestamp=%d}",
                orderId, username, type, orderType, size, price, timestamp);
    }
}