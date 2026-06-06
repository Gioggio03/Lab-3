package Utils;


import Common.Message;
import Common.Order;
import Common.TradeRecord;
import Common.User;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;


import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class JSONConverter {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()  // Per JSON formattato (facoltativo)
            .create();

    // ======= CONVERSIONI MESSAGE =======

    /**
     * Converte un oggetto Message in JSON string
     */
    public static String messageToJSON(Message message) {
        try {
            return gson.toJson(message);
        } catch (Exception e) {
            System.err.println("Errore nella conversione Message → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converte una stringa JSON in oggetto Message
     */
    public static Message jsonToMessage(String json) {
        try {
            return gson.fromJson(json, Message.class);
        } catch (JsonSyntaxException e) {
            System.err.println("Errore nella conversione JSON → Message: " + e.getMessage());
            return null;
        }
    }

    // ======= CONVERSIONI TRADE RECORDS =======

    /**
     * Converte una lista di TradeRecord in JSON string
     */
    public static String tradeRecordsToJSON(List<TradeRecord> trades) {
        try {
            // Crea la struttura come nel file del prof: {"trades": [...]}
            Map<String, List<TradeRecord>> wrapper = Map.of("trades", trades);
            return gson.toJson(wrapper);
        } catch (Exception e) {
            System.err.println("Errore nella conversione TradeRecords → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converte JSON string in lista di TradeRecord
     */
    public static List<TradeRecord> jsonToTradeRecords(String json) {
        try {
            // Parsing del wrapper {"trades": [...]}
            Type wrapperType = new TypeToken<Map<String, List<TradeRecord>>>(){}.getType();
            Map<String, List<TradeRecord>> wrapper = gson.fromJson(json, wrapperType);
            return wrapper.get("trades");
        } catch (Exception e) {
            System.err.println("Errore nella conversione JSON → TradeRecords: " + e.getMessage());
            return null;
        }
    }

    // ======= CONVERSIONI USER =======

    /**
     * Converte una lista di User in JSON string per persistenza
     */
    public static String usersToJSON(List<User> users) {
        try {
            return gson.toJson(users);
        } catch (Exception e) {
            System.err.println("Errore nella conversione Users → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converte JSON string in lista di User
     */
    public static List<User> jsonToUsers(String json) {
        try {
            Type listType = new TypeToken<List<User>>(){}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            System.err.println("Errore nella conversione JSON → Users: " + e.getMessage());
            return null;
        }
    }

    // ======= CONVERSIONI ORDER =======

    /**
     * Converte una lista di Order in JSON string
     */
    public static String ordersToJSON(List<Order> orders) {
        try {
            return gson.toJson(orders);
        } catch (Exception e) {
            System.err.println("Errore nella conversione Orders → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converte JSON string in lista di Order
     */
    public static List<Order> jsonToOrders(String json) {
        try {
            Type listType = new TypeToken<List<Order>>(){}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            System.err.println("Errore nella conversione JSON → Orders: " + e.getMessage());
            return null;
        }
    }

    // ======= METODI GENERICI =======

    /**
     * Converte un qualsiasi oggetto in JSON (per debugging)
     */
    public static String objectToJSON(Object obj) {
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            System.err.println("Errore nella conversione Object → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Controlla se una stringa è un JSON valido
     */
    public static boolean isValidJSON(String json) {
        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    // ======= METODI PER PRICE HISTORY =======

    /**
     * Converte dati di price history in JSON
     * (La struttura dipende da come implementi getPriceHistory)
     */
    public static String priceHistoryToJSON(Object priceHistoryData) {
        try {
            return gson.toJson(priceHistoryData);
        } catch (Exception e) {
            System.err.println("Errore nella conversione PriceHistory → JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Test method per verificare che tutto funzioni
     */
    public static void main(String[] args) {
        // Test rapido della conversione
        Message testMsg = Message.createLoginRequest("test", "password");
        String json = messageToJSON(testMsg);
        System.out.println("JSON: " + json);

        Message parsed = jsonToMessage(json);
        System.out.println("Parsed: " + parsed);
    }
}
