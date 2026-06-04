package Common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Message {
    // Campi per le richieste (Client → Server)
    private String operation;
    private Map<String, Object> values;

    // Campi per le risposte (Server → Client)
    private Integer response;
    private String errorMessage;
    private Long orderId;

    // Campi per le notifiche (Server → Client via UDP)
    private String notification;
    private List<TradeRecord> trades;

    // Campi aggiuntivi per getPriceHistory
    private Object data; // Per dati generici come price history

    // Costruttore vuoto per JSON parsing
    public Message() {
        this.values = new HashMap<>();
    }

    // ======= COSTRUTTORI PER RICHIESTE =======

    // Costruttore generico per richieste
    public Message(String operation) {
        this();
        this.operation = operation;
    }

    // Register/Login requests
    public static Message createRegisterRequest(String username, String password) {
        Message msg = new Message("register");
        msg.addValue("username", username);
        msg.addValue("password", password);
        return msg;
    }

    public static Message createLoginRequest(String username, String password) {
        Message msg = new Message("login");
        msg.addValue("username", username);
        msg.addValue("password", password);
        return msg;
    }

    // Nuovo metodo per login con porta UDP
    public static Message createLoginRequest(String username, String password, int udpPort) {
        Message msg = new Message("login");
        msg.addValue("username", username);
        msg.addValue("password", password);
        msg.addValue("udpPort", udpPort);
        return msg;
    }

    public static Message createLogoutRequest() {
        return new Message("logout");
    }

    public static Message createUpdateCredentialsRequest(String username, String oldPassword, String newPassword) {
        Message msg = new Message("updateCredentials");
        msg.addValue("username", username);
        msg.addValue("old_password", oldPassword);
        msg.addValue("new_password", newPassword);
        return msg;
    }

    // Order requests
    public static Message createLimitOrderRequest(String type, long size, long price) {
        Message msg = new Message("insertLimitOrder");
        msg.addValue("type", type);
        msg.addValue("size", size);
        msg.addValue("price", price);
        return msg;
    }

    public static Message createMarketOrderRequest(String type, long size) {
        Message msg = new Message("insertMarketOrder");
        msg.addValue("type", type);
        msg.addValue("size", size);
        return msg;
    }

    public static Message createStopOrderRequest(String type, long size, long price) {
        Message msg = new Message("insertStopOrder");
        msg.addValue("type", type);
        msg.addValue("size", size);
        msg.addValue("price", price);
        return msg;
    }

    public static Message createCancelOrderRequest(long orderId) {
        Message msg = new Message("cancelOrder");
        msg.addValue("orderId", orderId);
        return msg;
    }

    public static Message createPriceHistoryRequest(String month) {
        Message msg = new Message("getPriceHistory");
        msg.addValue("month", month);
        return msg;
    }

    // ======= COSTRUTTORI PER RISPOSTE =======

    /**
     * Crea una risposta di successo con dati
     */
    public static Message createSuccessResponse(Object data) {
        Message msg = new Message();
        msg.response = Constants.RESPONSE_OK;
        msg.errorMessage = "Success";
        msg.data = data;
        return msg;
    }

    /**
     * Crea una risposta di errore
     */
    public static Message createErrorResponse(String errorMessage) {
        Message msg = new Message();
        msg.response = Constants.RESPONSE_REGISTER_OTHER_ERROR;
        msg.errorMessage = errorMessage;
        return msg;
    }

    public static Message createResponse(int responseCode, String errorMessage) {
        Message msg = new Message();
        msg.response = responseCode;
        msg.errorMessage = errorMessage;
        return msg;
    }

    public static Message createOrderResponse(long orderId) {
        Message msg = new Message();
        msg.orderId = orderId;
        return msg;
    }

    public static Message createErrorOrderResponse() {
        Message msg = new Message();
        msg.orderId = -1L;
        return msg;
    }

    // ======= COSTRUTTORI PER NOTIFICHE =======

    public static Message createTradeNotification(List<TradeRecord> trades) {
        Message msg = new Message();
        msg.notification = "closedTrades";
        msg.trades = trades;
        return msg;
    }

    // ======= METODI DI UTILITÀ =======

    public void addValue(String key, Object value) {
        if (this.values == null) {
            this.values = new HashMap<>();
        }
        this.values.put(key, value);
    }

    public Object getValue(String key) {
        return this.values != null ? this.values.get(key) : null;
    }

    public String getStringValue(String key) {
        Object value = getValue(key);
        return value != null ? value.toString() : null;
    }

    public Long getLongValue(String key) {
        Object value = getValue(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    public boolean isRequest() {
        return operation != null;
    }

    public boolean isResponse() {
        return response != null;
    }

    public boolean isNotification() {
        return notification != null;
    }

    public boolean isSuccessResponse() {
        return response != null && response == 100;
    }

    // ======= GETTER E SETTER =======

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }

    public Integer getResponse() { return response; }
    public void setResponse(Integer response) { this.response = response; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getNotification() { return notification; }
    public void setNotification(String notification) { this.notification = notification; }

    public List<TradeRecord> getTrades() { return trades; }
    public void setTrades(List<TradeRecord> trades) { this.trades = trades; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    @Override
    public String toString() {
        if (isRequest()) {
            return String.format("Message{operation='%s', values=%s}", operation, values);
        } else if (isResponse()) {
            return String.format("Message{response=%d, errorMessage='%s', orderId=%s}", response, errorMessage, orderId);
        } else if (isNotification()) {
            return String.format("Message{notification='%s', trades=%d}", notification, trades != null ? trades.size() : 0);
        }
        return "Message{empty}";
    }
}