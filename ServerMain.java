package Server;

import Common.*;
import Utils.JSONConverter;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class CROSSServer implements OrderBook.TradeExecutionCallback {

    // Configurazioni del server
    private int tcpPort;
    private int udpPort;
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;

    // Thread pool per gestire client multipli
    private ExecutorService threadPool;
    private volatile boolean running = false;

    // Componenti principali
    private final OrderBook orderBook;
    private final UserManager userManager;
    private final PersistenceManager persistenceManager;

    // Generatore di ID univoci per gli ordini
    private AtomicLong orderIdGenerator;

    // Mappa delle connessioni attive (username -> ClientHandler)
    private ConcurrentHashMap<String, ClientHandler> activeConnections;

    // Lista degli indirizzi UDP per le notifiche
    private ConcurrentHashMap<String, InetSocketAddress> udpNotificationAddresses;

    public CROSSServer() {
        this.orderBook = new OrderBook();
        this.orderBook.setTradeCallback(this); // Imposta questo server come callback per i trades
        this.userManager = new UserManager();
        this.persistenceManager = new PersistenceManager();
        this.orderIdGenerator = new AtomicLong(1);
        this.activeConnections = new ConcurrentHashMap<>();
        this.udpNotificationAddresses = new ConcurrentHashMap<>();
    }

    /**
     * Inizializza il server con le configurazioni
     */
    public void initialize(int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;

        // Carica utenti e ordini persistiti
        this.userManager.loadUsers();

        // Carica e ripristina ordini attivi dal backup
        restoreActiveOrders();

        // Inizializza thread pool
        this.threadPool = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE);

        System.out.println("Server inizializzato su TCP:" + tcpPort + " UDP:" + udpPort);
    }

    /**
     * Avvia il server
     */
    public void start() {
        try {
            // Avvia server TCP
            serverSocket = new ServerSocket(tcpPort);
            udpSocket = new DatagramSocket(udpPort);
            running = true;

            System.out.println("CROSS Server avviato!");
            System.out.println("TCP Server in ascolto sulla porta: " + tcpPort);
            System.out.println("UDP Server in ascolto sulla porta: " + udpPort);

            // Avvia il thread per il salvataggio periodico
            startPeriodicSaving();

            // Loop principale per accettare connessioni
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nuova connessione da: " + clientSocket.getInetAddress());

                    // Crea handler per il client e lo esegue nel thread pool
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    threadPool.execute(clientHandler);

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Errore nell'accettare connessione: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Errore nell'avvio del server: " + e.getMessage());
        }
    }

    /**
     * Ferma il server
     */
    public void stop() {
        running = false;

        try {
            // Chiude tutte le connessioni attive
            for (ClientHandler handler : activeConnections.values()) {
                handler.disconnect();
            }

            // Chiude i socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }

            // Ferma il thread pool
            if (threadPool != null) {
                threadPool.shutdown();
                try {
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        threadPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    threadPool.shutdownNow();
                }
            }

            // Salva i dati prima di chiudere
            persistenceManager.saveUsers(userManager.getAllUsers());
            saveActiveOrdersBackup();

            System.out.println("Server fermato.");

        } catch (IOException e) {
            System.err.println("Errore nella chiusura del server: " + e.getMessage());
        }
    }

    /**
     * Ripristina gli ordini attivi dal backup
     */
    private void restoreActiveOrders() {
        try {
            List<Order> activeOrders = persistenceManager.loadActiveOrdersBackup();
            List<TradeRecord> completedTrades = persistenceManager.getAllTrades();

            int restoredCount = 0;
            long maxOrderId = 0;

            // Trova l'ID più alto tra gli ordini attivi
            for (Order order : activeOrders) {
                try {
                    // Ripristina solo ordini attivi o parzialmente eseguiti
                    if (order.getStatus() == Order.OrderStatus.ACTIVE ||
                            order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {

                        // Reinserisce nell'OrderBook
                        orderBook.addOrder(order);
                        restoredCount++;

                        // Tiene traccia dell'ID più alto
                        if (order.getOrderId() > maxOrderId) {
                            maxOrderId = order.getOrderId();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Errore nel ripristino ordine " + order.getOrderId() + ": " + e.getMessage());
                }
            }

            // Trova l'ID più alto anche tra i trades completati
            for (TradeRecord trade : completedTrades) {
                if (trade.getOrderId() > maxOrderId) {
                    maxOrderId = trade.getOrderId();
                }
            }

            // Aggiorna il generatore di ID per evitare conflitti
            if (maxOrderId > 0) {
                orderIdGenerator.set(maxOrderId + 1);
            }

            if (activeOrders.isEmpty()) {
                System.out.println("Nessun ordine attivo da ripristinare");
            } else {
                System.out.println("Ripristinati " + restoredCount + " ordini attivi dal backup");
            }

            System.out.println("Controllati " + completedTrades.size() + " trades completati per ID univoci");
            System.out.println("Prossimo Order ID: " + orderIdGenerator.get());

        } catch (Exception e) {
            System.err.println("Errore nel ripristino ordini attivi: " + e.getMessage());
        }
    }

    /**
     * Salva backup degli ordini attivi correnti
     */
    private void saveActiveOrdersBackup() {
        try {
            // Ottiene snapshot dell'OrderBook
            var snapshot = orderBook.getSnapshot();

            // Usa la lista di tutti gli ordini attivi già preparata nello snapshot
            List<Order> activeOrders = snapshot.getAllActiveOrders();

            // Salva nel file
            persistenceManager.saveActiveOrdersBackup(activeOrders);

            if (!activeOrders.isEmpty()) {
                System.out.println("Backup di " + activeOrders.size() + " ordini attivi salvato");
            }

        } catch (Exception e) {
            System.err.println("Errore nel salvataggio backup ordini attivi: " + e.getMessage());
        }
    }

    /**
     * Processa una richiesta da un client
     */
    public Message processRequest(Message request, String clientUsername) {
        if (request == null || !request.isRequest()) {
            return Message.createResponse(Constants.RESPONSE_REGISTER_OTHER_ERROR,
                    Constants.ERROR_INVALID_JSON);
        }

        String operation = request.getOperation();

        switch (operation) {
            case "register":
                return handleRegister(request);

            case "login":
                return handleLogin(request, clientUsername);

            case "logout":
                return handleLogout(clientUsername);

            case "updateCredentials":
                return handleUpdateCredentials(request);

            case "insertLimitOrder":
                return handleInsertLimitOrder(request, clientUsername);

            case "insertMarketOrder":
                return handleInsertMarketOrder(request, clientUsername);

            case "insertStopOrder":
                return handleInsertStopOrder(request, clientUsername);

            case "cancelOrder":
                return handleCancelOrder(request, clientUsername);

            case "getPriceHistory":
                return handleGetPriceHistory(request);

            default:
                return Message.createResponse(Constants.RESPONSE_REGISTER_OTHER_ERROR,
                        Constants.ERROR_UNKNOWN_OPERATION);
        }
    }

    /**
     * Gestisce la registrazione di un nuovo utente
     */
    private Message handleRegister(Message request) {
        String username = request.getStringValue("username");
        String password = request.getStringValue("password");

        // Validazione parametri
        if (!User.isValidForRegistration(username, password)) {
            return Message.createResponse(Constants.RESPONSE_INVALID_PASSWORD, "Invalid username or password");
        }

        // Controlla se username già esiste
        if (userManager.userExists(username)) {
            return Message.createResponse(Constants.RESPONSE_USERNAME_NOT_AVAILABLE, "Username not available");
        }

        // Crea nuovo utente
        User newUser = new User(username, password);
        userManager.addUser(newUser);

        System.out.println("Nuovo utente registrato: " + username);
        return Message.createResponse(Constants.RESPONSE_OK, "Registration successful");
    }

    /**
     * Gestisce il login di un utente
     */
    private Message handleLogin(Message request, String currentUsername) {
        String username = request.getStringValue("username");
        String password = request.getStringValue("password");

        // Controlla se l'utente è già loggato
        if (userManager.isUserLoggedIn(username)) {
            return Message.createResponse(Constants.RESPONSE_LOGIN_USER_ALREADY_LOGGED_IN, "User already logged in");
        }

        // Verifica credenziali
        if (!userManager.authenticateUser(username, password)) {
            return Message.createResponse(Constants.RESPONSE_LOGIN_USERNAME_PASSWORD_MISMATCH, "Invalid credentials");
        }

        // Effettua login
        userManager.loginUser(username);
        System.out.println("Utente loggato: " + username);

        return Message.createResponse(Constants.RESPONSE_OK, "Login successful");
    }

    /**
     * Gestisce il logout di un utente
     */
    private Message handleLogout(String username) {
        if (username == null || !userManager.isUserLoggedIn(username)) {
            return Message.createResponse(Constants.RESPONSE_LOGOUT_ERROR, "User not logged in");
        }

        userManager.logoutUser(username);
        activeConnections.remove(username);
        udpNotificationAddresses.remove(username);

        System.out.println("Utente disconnesso: " + username);
        return Message.createResponse(Constants.RESPONSE_OK, "Logout successful");
    }

    /**
     * Gestisce aggiornamento credenziali
     */
    private Message handleUpdateCredentials(Message request) {
        String username = request.getStringValue("username");
        String oldPassword = request.getStringValue("old_password");
        String newPassword = request.getStringValue("new_password");

        // Controlla se l'utente è attualmente loggato
        if (userManager.isUserLoggedIn(username)) {
            return Message.createResponse(Constants.RESPONSE_UPDATE_USER_LOGGED_IN, "Cannot change password while logged in");
        }

        // Verifica e aggiorna password
        int result = userManager.updatePassword(username, oldPassword, newPassword);

        switch (result) {
            case 100:
                return Message.createResponse(Constants.RESPONSE_OK, "Password updated successfully");
            case 101:
                return Message.createResponse(Constants.RESPONSE_UPDATE_INVALID_NEW_PASSWORD, "Invalid new password");
            case 102:
                return Message.createResponse(Constants.RESPONSE_UPDATE_USERNAME_PASSWORD_MISMATCH, "Invalid current password");
            case 103:
                return Message.createResponse(Constants.RESPONSE_UPDATE_SAME_PASSWORD, "New password must be different");
            default:
                return Message.createResponse(Constants.RESPONSE_UPDATE_OTHER_ERROR, "Update failed");
        }
    }

    /**
     * Gestisce inserimento Limit Order
     */
    private Message handleInsertLimitOrder(Message request, String username) {
        if (!userManager.isUserLoggedIn(username)) {
            return Message.createErrorOrderResponse();
        }

        try {
            String type = request.getStringValue("type");
            Long size = request.getLongValue("size");
            Long price = request.getLongValue("price");

            if (!isValidOrderParameters(type, size, price)) {
                return Message.createErrorOrderResponse();
            }

            long orderId = orderIdGenerator.getAndIncrement();
            Order order = new Order(orderId, username, type, size, price);

            List<TradeRecord> trades = orderBook.addOrder(order);

            System.out.println("Limit Order inserito: " + orderId + " per " + username);
            return Message.createOrderResponse(orderId);

        } catch (Exception e) {
            System.err.println("Errore nell'inserimento Limit Order: " + e.getMessage());
            return Message.createErrorOrderResponse();
        }
    }

    /**
     * Gestisce inserimento Market Order
     */
    private Message handleInsertMarketOrder(Message request, String username) {
        if (!userManager.isUserLoggedIn(username)) {
            return Message.createErrorOrderResponse();
        }

        try {
            String type = request.getStringValue("type");
            Long size = request.getLongValue("size");

            if (!Constants.isValidOrderType(type) || !Constants.isValidOrderSize(size)) {
                return Message.createErrorOrderResponse();
            }

            long orderId = orderIdGenerator.getAndIncrement();
            Order order = new Order(orderId, username, type, size);

            List<TradeRecord> trades = orderBook.addOrder(order);

            System.out.println("Market Order inserito: " + orderId + " per " + username);
            return Message.createOrderResponse(orderId);

        } catch (Exception e) {
            System.err.println("Errore nell'inserimento Market Order: " + e.getMessage());
            return Message.createErrorOrderResponse();
        }
    }

    /**
     * Gestisce inserimento Stop Order
     */
    private Message handleInsertStopOrder(Message request, String username) {
        if (!userManager.isUserLoggedIn(username)) {
            return Message.createErrorOrderResponse();
        }

        try {
            String type = request.getStringValue("type");
            Long size = request.getLongValue("size");
            Long stopPrice = request.getLongValue("price");

            if (!isValidOrderParameters(type, size, stopPrice)) {
                return Message.createErrorOrderResponse();
            }

            long orderId = orderIdGenerator.getAndIncrement();
            Order order = new Order(orderId, username, type, size, stopPrice, true);

            List<TradeRecord> trades = orderBook.addOrder(order);

            System.out.println("Stop Order inserito: " + orderId + " per " + username);
            return Message.createOrderResponse(orderId);

        } catch (Exception e) {
            System.err.println("Errore nell'inserimento Stop Order: " + e.getMessage());
            return Message.createErrorOrderResponse();
        }
    }

    /**
     * Gestisce cancellazione ordine
     */
    private Message handleCancelOrder(Message request, String username) {
        if (!userManager.isUserLoggedIn(username)) {
            return Message.createResponse(Constants.RESPONSE_CANCEL_ORDER_ERROR, "User not logged in");
        }

        try {
            Long orderId = request.getLongValue("orderId");
            if (orderId == null) {
                return Message.createResponse(Constants.RESPONSE_CANCEL_ORDER_ERROR, "Invalid order ID");
            }

            boolean success = orderBook.cancelOrder(orderId, username);

            if (success) {
                System.out.println("Ordine cancellato: " + orderId + " da " + username);
                return Message.createResponse(Constants.RESPONSE_OK, "Order cancelled successfully");
            } else {
                return Message.createResponse(Constants.RESPONSE_CANCEL_ORDER_ERROR, "Cannot cancel order");
            }

        } catch (Exception e) {
            System.err.println("Errore nella cancellazione ordine: " + e.getMessage());
            return Message.createResponse(Constants.RESPONSE_CANCEL_ORDER_ERROR, "Cancel failed");
        }
    }

    /**
     * Gestisce richiesta storico prezzi
     */
    private Message handleGetPriceHistory(Message request) {
        String month = request.getStringValue("month");

        if (!Constants.isValidMonthFormat(month)) {
            return Message.createErrorResponse("Formato mese non valido. Usa MMYYYY (es. 072025)");
        }

        try {
            // Usa PersistenceManager per ottenere i dati
            Map<String, Object> priceHistory = persistenceManager.getPriceHistoryForMonth(month);

            if (priceHistory != null && !priceHistory.containsKey("error")) {
                // Successo - crea risposta con i dati
                return Message.createSuccessResponse(priceHistory);
            } else {
                // Errore nei dati
                String errorMsg = priceHistory != null ?
                        (String) priceHistory.get("error") :
                        "Nessun dato disponibile per il mese " + month;
                return Message.createErrorResponse(errorMsg);
            }

        } catch (Exception e) {
            System.err.println("Errore in handleGetPriceHistory: " + e.getMessage());
            return Message.createErrorResponse("Errore interno nel recupero price history: " + e.getMessage());
        }
    }

    /**
     * Callback chiamato quando vengono eseguiti dei trades
     */
    @Override
    public void onTradeExecuted(List<TradeRecord> trades) {
        if (trades.isEmpty()) return;

        // Salva i trades nello storico
        persistenceManager.saveTradeRecords(trades);

        // Invia notifiche UDP agli utenti coinvolti
        sendTradeNotifications(trades);

        System.out.println("Eseguiti " + trades.size() + " trades");
    }

    /**
     * Invia notifiche UDP per i trades eseguiti
     */
    private void sendTradeNotifications(List<TradeRecord> trades) {
        // Raggruppa i trades per utente (basandosi sugli ordini attivi)
        // Per semplicità, invia a tutti gli utenti connessi

        Message notification = Message.createTradeNotification(trades);
        String jsonNotification = JSONConverter.messageToJSON(notification);

        if (jsonNotification == null) {
            System.err.println("DEBUG: Failed to convert notification to JSON");
            return;
        }

        byte[] data = jsonNotification.getBytes();

        System.out.println("DEBUG: Sending UDP notifications to " + udpNotificationAddresses.size() + " users");

        for (Map.Entry<String, InetSocketAddress> entry : udpNotificationAddresses.entrySet()) {
            try {
                System.out.println("DEBUG: Sending UDP to " + entry.getKey() + " at " + entry.getValue());
                DatagramPacket packet = new DatagramPacket(data, data.length, entry.getValue());
                udpSocket.send(packet);
                System.out.println("DEBUG: UDP sent successfully to " + entry.getKey());
            } catch (IOException e) {
                System.err.println("Errore nell'invio notifica UDP a " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Registra un indirizzo UDP per le notifiche
     */
    public void registerUDPAddress(String username, InetSocketAddress address) {
        udpNotificationAddresses.put(username, address);
    }

    /**
     * Registra una connessione attiva
     */
    public void registerConnection(String username, ClientHandler handler) {
        activeConnections.put(username, handler);
    }

    /**
     * Rimuove una connessione
     */
    public void unregisterConnection(String username) {
        activeConnections.remove(username);
        udpNotificationAddresses.remove(username);
    }

    /**
     * Valida i parametri di un ordine
     */
    private boolean isValidOrderParameters(String type, Long size, Long price) {
        return Constants.isValidOrderType(type) &&
                Constants.isValidOrderSize(size) &&
                Constants.isValidOrderPrice(price);
    }

    /**
     * Avvia il salvataggio periodico dei dati
     */
    private void startPeriodicSaving() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Salva utenti
                persistenceManager.saveUsers(userManager.getAllUsers());

                // Salva backup ordini attivi
                saveActiveOrdersBackup();

                System.out.println("Dati salvati automaticamente");
            } catch (Exception e) {
                System.err.println("Errore nel salvataggio automatico: " + e.getMessage());
            }
        }, Constants.PERSISTENCE_INTERVAL, Constants.PERSISTENCE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    // Getter per testing e debugging
    public OrderBook getOrderBook() { return orderBook; }
    public UserManager getUserManager() { return userManager; }
    public boolean isRunning() { return running; }
    public PersistenceManager getPersistenceManager() {
        return this.persistenceManager;
    }
}