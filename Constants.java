package Common;

public class Constants {

    // ======= CONFIGURAZIONI DI RETE =======

    // Porte default (possono essere sovrascritte dai file di configurazione)
    public static final int DEFAULT_TCP_PORT = 8080;
    public static final int DEFAULT_UDP_PORT = 8081;

    // Timeout in millisecondi
    public static final int TCP_CONNECTION_TIMEOUT = 30000;  // 30 secondi
    public static final int TCP_READ_TIMEOUT = 60000;        // 60 secondi
    public static final int UDP_TIMEOUT = 5000;              // 5 secondi

    // Configurazioni per il thread pool del server
    public static final int THREAD_POOL_SIZE = 20;
    public static final int MAX_QUEUE_SIZE = 100;

    // ======= CODICI DI RISPOSTA =======

    // Codici di successo
    public static final int RESPONSE_OK = 100;

    // Codici di errore per register
    public static final int RESPONSE_INVALID_PASSWORD = 101;
    public static final int RESPONSE_USERNAME_NOT_AVAILABLE = 102;
    public static final int RESPONSE_REGISTER_OTHER_ERROR = 103;

    // Codici di errore per updateCredentials
    public static final int RESPONSE_UPDATE_INVALID_NEW_PASSWORD = 101;
    public static final int RESPONSE_UPDATE_USERNAME_PASSWORD_MISMATCH = 102;
    public static final int RESPONSE_UPDATE_SAME_PASSWORD = 103;
    public static final int RESPONSE_UPDATE_USER_LOGGED_IN = 104;
    public static final int RESPONSE_UPDATE_OTHER_ERROR = 105;

    // Codici di errore per login
    public static final int RESPONSE_LOGIN_USERNAME_PASSWORD_MISMATCH = 101;
    public static final int RESPONSE_LOGIN_USER_ALREADY_LOGGED_IN = 102;
    public static final int RESPONSE_LOGIN_OTHER_ERROR = 103;

    // Codici di errore per logout
    public static final int RESPONSE_LOGOUT_ERROR = 101;

    // Codici di errore per cancelOrder
    public static final int RESPONSE_CANCEL_ORDER_ERROR = 101;

    // Valore per ordini con errore
    public static final long ORDER_ERROR_ID = -1L;

    // ======= CONFIGURAZIONI DI BUSINESS =======

    // Timeout per logout automatico (in secondi)
    public static final long USER_INACTIVITY_TIMEOUT = 1800; // 30 minuti

    // Limiti per gli ordini
    public static final long MAX_ORDER_SIZE = (1L << 31) - 1;  // (2^31)-1 come da specifica
    public static final long MAX_ORDER_PRICE = (1L << 31) - 1; // (2^31)-1 come da specifica
    public static final long MIN_ORDER_SIZE = 1;               // Minimo 1 millesimo di BTC
    public static final long MIN_ORDER_PRICE = 1;              // Minimo 1 millesimo di USD

    // ======= CONFIGURAZIONI PER FILE =======

    // Nomi dei file per la persistenza
    public static final String USERS_FILE = "data/users.json";
    public static final String ORDERS_HISTORY_FILE = "data/orders_history.json";
    public static final String ACTIVE_ORDERS_BACKUP_FILE = "data/active_orders_backup.json";

    // Frequenza di salvataggio (in millisecondi)
    public static final long PERSISTENCE_INTERVAL = 60000; // 1 minuto

    // ======= CONFIGURAZIONI PER DATE =======

    // Formato per il mese in getPriceHistory (MMYYYY)
    public static final String MONTH_FORMAT_REGEX = "^(0[1-9]|1[0-2])\\d{4}$"; // es. 012024

    // ======= MESSAGGI DI ERRORE STANDARD =======

    public static final String ERROR_INVALID_JSON = "Invalid JSON format";
    public static final String ERROR_MISSING_OPERATION = "Missing operation field";
    public static final String ERROR_UNKNOWN_OPERATION = "Unknown operation";
    public static final String ERROR_USER_NOT_LOGGED_IN = "User not logged in";
    public static final String ERROR_INSUFFICIENT_PARAMETERS = "Insufficient parameters";
    public static final String ERROR_INVALID_ORDER_TYPE = "Invalid order type (must be 'ask' or 'bid')";
    public static final String ERROR_INVALID_ORDER_SIZE = "Invalid order size";
    public static final String ERROR_INVALID_ORDER_PRICE = "Invalid order price";
    public static final String ERROR_ORDER_NOT_FOUND = "Order not found";
    public static final String ERROR_SERVER_INTERNAL = "Internal server error";

    // ======= CONFIGURAZIONI PER NOTIFICHE =======

    // Tipi di notifiche
    public static final String NOTIFICATION_CLOSED_TRADES = "closedTrades";

    // ======= CONFIGURAZIONI PER ORDER BOOK =======

    // Priorità per l'algoritmo di matching (time/price priority)
    public static final String MATCHING_ALGORITHM = "TIME_PRICE_PRIORITY";

    // ======= METODI DI UTILITÀ =======

    /**
     * Verifica se un codice di risposta indica successo
     */
    public static boolean isSuccessResponse(int responseCode) {
        return responseCode == RESPONSE_OK;
    }

    /**
     * Verifica se un tipo di ordine è valido
     */
    public static boolean isValidOrderType(String type) {
        return "ask".equals(type) || "bid".equals(type);
    }

    /**
     * Verifica se una dimensione di ordine è valida
     */
    public static boolean isValidOrderSize(long size) {
        return size >= MIN_ORDER_SIZE && size <= MAX_ORDER_SIZE;
    }

    /**
     * Verifica se un prezzo di ordine è valido
     */
    public static boolean isValidOrderPrice(long price) {
        return price >= MIN_ORDER_PRICE && price <= MAX_ORDER_PRICE;
    }

    /**
     * Verifica se un formato mese è valido (MMYYYY)
     */
    public static boolean isValidMonthFormat(String month) {
        return month != null && month.matches(MONTH_FORMAT_REGEX);
    }

    /**
     * Costruttore privato per impedire istanziazione
     */
    private Constants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
