package Client;

import Common.Constants;
import Common.Message;
import Utils.JSONConverter;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class CROSSClient {

    // Configurazioni di connessione
    private String serverHost;
    private int serverTcpPort;
    private int serverUdpPort;

    // Connessione TCP
    private Socket tcpSocket;
    private BufferedReader tcpReader;
    private PrintWriter tcpWriter;

    // Socket UDP per notifiche
    private DatagramSocket udpSocket;
    private Thread udpListenerThread;

    // Stato del client
    private AtomicBoolean connected;
    private AtomicBoolean loggedIn;
    private String currentUsername;

    // Scanner per input utente
    private Scanner userInput;

    public CROSSClient() {
        this.connected = new AtomicBoolean(false);
        this.loggedIn = new AtomicBoolean(false);
        this.userInput = new Scanner(System.in);
    }

    /**
     * Inizializza il client con le configurazioni
     */
    public void initialize(String host, int tcpPort, int udpPort) {
        this.serverHost = host;
        this.serverTcpPort = tcpPort;
        this.serverUdpPort = udpPort;

        System.out.println("Client inizializzato:");
        System.out.println("Server: " + host + ":" + tcpPort);
        System.out.println("UDP Port: " + udpPort);
    }

    /**
     * Connette al server
     */
    public boolean connect() {
        try {
            // Connessione TCP
            tcpSocket = new Socket(serverHost, serverTcpPort);
            tcpSocket.setSoTimeout(Constants.TCP_READ_TIMEOUT);

            tcpReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            tcpWriter = new PrintWriter(tcpSocket.getOutputStream(), true);

            // Socket UDP per notifiche (porta locale dinamica)
            udpSocket = new DatagramSocket();

            connected.set(true);

            // Avvia listener UDP per notifiche
            startUDPListener();

            System.out.println("Connesso al server CROSS!");
            return true;

        } catch (IOException e) {
            System.err.println("Errore nella connessione al server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Disconnette dal server
     */
    public void disconnect() {
        connected.set(false);

        try {
            // Chiude connessioni
            if (tcpWriter != null) tcpWriter.close();
            if (tcpReader != null) tcpReader.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
            if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();

            // Ferma thread UDP
            if (udpListenerThread != null) {
                udpListenerThread.interrupt();
            }

            System.out.println("Disconnesso dal server");

        } catch (IOException e) {
            System.err.println("Errore nella disconnessione: " + e.getMessage());
        }
    }

    /**
     * Avvia il client con menu principale
     */
    public void start() {
        if (!connected.get()) {
            System.err.println("Client non connesso al server");
            return;
        }

        System.out.println("\n=== CROSS Trading Client ===");
        System.out.println("Bitcoin/USD Exchange System");

        // Loop principale del client
        while (connected.get()) {
            // Se non è loggato, mostra menu principale
            while (connected.get() && !loggedIn.get()) {
                if (!showMainMenu()) {
                    return; // Exit dal menu principale
                }
            }

            // Se è loggato, mostra menu trading
            while (connected.get() && loggedIn.get()) {
                if (!showTradingMenu()) {
                    break; // Esce dal menu trading (logout o exit)
                }
            }
        }
    }

    /**
     * Menu principale (prima del login)
     */
    private boolean showMainMenu() {
        System.out.println("\n=== MENU PRINCIPALE ===");
        System.out.println("1. Registrazione");
        System.out.println("2. Login");
        System.out.println("3. Aggiorna Credenziali");
        System.out.println("4. Esci");
        System.out.print("Scegli un'opzione (1-4): ");

        String choice = userInput.nextLine().trim();

        switch (choice) {
            case "1":
                handleRegister();
                break;

            case "2":
                handleLogin();
                // Se il login ha successo, il loop del menu principale si fermerà
                // perché loggedIn.set(true) farà uscire dal while(!loggedIn.get())
                break;

            case "3":
                handleUpdateCredentials();
                break;

            case "4":
                System.out.println("Arrivederci!");
                disconnect();
                return false;

            default:
                System.out.println("Opzione non valida. Scegli 1, 2, 3 o 4.");
                break;
        }

        return true;
    }

    /**
     * Menu trading (dopo il login)
     */
    private boolean showTradingMenu() {
        System.out.println("\n=== MENU TRADING ===");
        System.out.println("Benvenuto, " + currentUsername + "!");
        System.out.println();
        System.out.println("1. Inserisci Limit Order");
        System.out.println("2. Inserisci Market Order");
        System.out.println("3. Inserisci Stop Order");
        System.out.println("4. Cancella Ordine");
        System.out.println("5. Storico Prezzi");
        System.out.println("6. Logout");
        System.out.println("7. Esci");
        System.out.print("Scegli un'opzione (1-7): ");

        String choice = userInput.nextLine().trim();

        switch (choice) {
            case "1":
                handleInsertLimitOrder();
                break;

            case "2":
                handleInsertMarketOrder();
                break;

            case "3":
                handleInsertStopOrder();
                break;

            case "4":
                handleCancelOrder();
                break;

            case "5":
                handleGetPriceHistory();
                break;

            case "6":
                // Logout - torna al menu principale
                handleLogout();
                return false; // Esce dal loop del menu trading

            case "7":
                // Esci - chiude completamente il client
                System.out.println("Arrivederci!");
                handleLogout(); // Fa logout prima di uscire
                disconnect();
                return false;

            default:
                System.out.println("Opzione non valida. Scegli un numero da 1 a 7.");
                break;
        }

        return true;
    }

    /**
     * Gestisce registrazione
     */
    private void handleRegister() {
        System.out.println("\n--- REGISTRAZIONE ---");
        System.out.print("Username: ");
        String username = userInput.nextLine().trim();

        System.out.print("Password: ");
        String password = userInput.nextLine().trim();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Errore: Username e password non possono essere vuoti");
            return;
        }

        Message request = Message.createRegisterRequest(username, password);
        Message response = sendRequest(request);

        if (response != null) {
            if (response.isSuccessResponse()) {
                System.out.println("✓ Registrazione completata con successo!");
                System.out.println("Ora puoi effettuare il login con le tue credenziali");
            } else {
                System.out.println("✗ Errore nella registrazione: " + response.getErrorMessage());
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
        }
    }

    /**
     * Gestisce login
     */
    private void handleLogin() {
        System.out.println("\n--- LOGIN ---");
        System.out.print("Username: ");
        String username = userInput.nextLine().trim();

        System.out.print("Password: ");
        String password = userInput.nextLine().trim();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Errore: Username e password non possono essere vuoti");
            return;
        }

        // Ottieni la porta UDP locale
        int udpPort = udpSocket.getLocalPort();
        System.out.println("DEBUG: Client UDP listening on port: " + udpPort);

        // Crea richiesta con porta UDP
        Message request = Message.createLoginRequest(username, password, udpPort);
        Message response = sendRequest(request);

        if (response != null) {
            if (response.isSuccessResponse()) {
                loggedIn.set(true);
                currentUsername = username;
                System.out.println("✓ Login effettuato con successo!");
            } else {
                System.out.println("✗ Errore nel login: " + response.getErrorMessage());
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
        }
    }

    /**
     * Gestisce logout
     */
    private void handleLogout() {
        System.out.println("\n--- LOGOUT ---");

        Message request = Message.createLogoutRequest();
        Message response = sendRequest(request);

        if (response != null) {
            if (response.isSuccessResponse()) {
                loggedIn.set(false);
                System.out.println("✓ Logout effettuato con successo");
                System.out.println("A presto, " + currentUsername + "!");
                currentUsername = null;
            } else {
                System.out.println("✗ Errore nel logout: " + response.getErrorMessage());
                // Anche in caso di errore, forza il logout locale
                loggedIn.set(false);
                currentUsername = null;
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
            // Forza logout locale anche senza risposta server
            loggedIn.set(false);
            currentUsername = null;
        }
    }

    /**
     * Gestisce inserimento Limit Order
     */
    private void handleInsertLimitOrder() {
        System.out.println("\n--- INSERISCI LIMIT ORDER ---");

        // Tipo ordine (bid/ask)
        System.out.print("Tipo (bid per acquisto, ask per vendita): ");
        String type = userInput.nextLine().trim().toLowerCase();

        if (!type.equals("bid") && !type.equals("ask")) {
            System.out.println("✗ Tipo non valido. Usa 'bid' o 'ask'");
            return;
        }

        // Dimensione
        System.out.print("Dimensione (millesimi di BTC, es. 1000 = 1 BTC): ");
        String sizeStr = userInput.nextLine().trim();

        // Prezzo limite
        System.out.print("Prezzo limite (millesimi di USD, es. 50000000 = 50,000 USD): ");
        String priceStr = userInput.nextLine().trim();

        try {
            long size = Long.parseLong(sizeStr);
            long price = Long.parseLong(priceStr);

            if (size <= 0 || price <= 0) {
                System.out.println("✗ Dimensione e prezzo devono essere positivi");
                return;
            }

            Message request = Message.createLimitOrderRequest(type, size, price);
            Message response = sendRequest(request);

            handleOrderResponse(response, "Limit Order");

        } catch (NumberFormatException e) {
            System.out.println("✗ Errore: Inserire valori numerici validi");
        }
    }

    /**
     * Gestisce inserimento Market Order
     */
    private void handleInsertMarketOrder() {
        System.out.println("\n--- INSERISCI MARKET ORDER ---");

        // Tipo ordine (bid/ask)
        System.out.print("Tipo (bid per acquisto, ask per vendita): ");
        String type = userInput.nextLine().trim().toLowerCase();

        if (!type.equals("bid") && !type.equals("ask")) {
            System.out.println("✗ Tipo non valido. Usa 'bid' o 'ask'");
            return;
        }

        // Dimensione
        System.out.print("Dimensione (millesimi di BTC, es. 1000 = 1 BTC): ");
        String sizeStr = userInput.nextLine().trim();

        try {
            long size = Long.parseLong(sizeStr);

            if (size <= 0) {
                System.out.println("✗ Dimensione deve essere positiva");
                return;
            }

            Message request = Message.createMarketOrderRequest(type, size);
            Message response = sendRequest(request);

            handleOrderResponse(response, "Market Order");

        } catch (NumberFormatException e) {
            System.out.println("✗ Errore: Inserire un valore numerico valido");
        }
    }

    /**
     * Gestisce inserimento Stop Order
     */
    private void handleInsertStopOrder() {
        System.out.println("\n--- INSERISCI STOP ORDER ---");

        // Tipo ordine (bid/ask)
        System.out.print("Tipo (bid per acquisto, ask per vendita): ");
        String type = userInput.nextLine().trim().toLowerCase();

        if (!type.equals("bid") && !type.equals("ask")) {
            System.out.println("✗ Tipo non valido. Usa 'bid' o 'ask'");
            return;
        }

        // Dimensione
        System.out.print("Dimensione (millesimi di BTC, es. 1000 = 1 BTC): ");
        String sizeStr = userInput.nextLine().trim();

        // Stop Price
        System.out.print("Stop Price (millesimi di USD, es. 45000000 = 45,000 USD): ");
        String stopPriceStr = userInput.nextLine().trim();

        try {
            long size = Long.parseLong(sizeStr);
            long stopPrice = Long.parseLong(stopPriceStr);

            if (size <= 0 || stopPrice <= 0) {
                System.out.println("✗ Dimensione e stop price devono essere positivi");
                return;
            }

            Message request = Message.createStopOrderRequest(type, size, stopPrice);
            Message response = sendRequest(request);

            handleOrderResponse(response, "Stop Order");

        } catch (NumberFormatException e) {
            System.out.println("✗ Errore: Inserire valori numerici validi");
        }
    }

    /**
     * Gestisce cancellazione ordine
     */
    private void handleCancelOrder() {
        System.out.println("\n--- CANCELLA ORDINE ---");

        System.out.print("ID Ordine da cancellare: ");
        String orderIdStr = userInput.nextLine().trim();

        try {
            long orderId = Long.parseLong(orderIdStr);

            Message request = Message.createCancelOrderRequest(orderId);
            Message response = sendRequest(request);

            if (response != null) {
                if (response.isSuccessResponse()) {
                    System.out.println("✓ Ordine " + orderId + " cancellato con successo");
                } else {
                    System.out.println("✗ Errore nella cancellazione: " + response.getErrorMessage());
                }
            } else {
                System.out.println("✗ Errore di comunicazione con il server");
            }

        } catch (NumberFormatException e) {
            System.out.println("✗ Errore: ID ordine non valido");
        }
    }

    /**
     * Gestisce aggiornamento credenziali
     */
    private void handleUpdateCredentials() {
        System.out.println("\n--- AGGIORNA CREDENZIALI ---");
        System.out.println("Nota: devi fare logout per cambiare la password");

        System.out.print("Username: ");
        String username = userInput.nextLine().trim();

        System.out.print("Password attuale: ");
        String oldPassword = userInput.nextLine().trim();

        System.out.print("Nuova password: ");
        String newPassword = userInput.nextLine().trim();

        if (username.isEmpty() || oldPassword.isEmpty() || newPassword.isEmpty()) {
            System.out.println("✗ Tutti i campi sono obbligatori");
            return;
        }

        Message request = Message.createUpdateCredentialsRequest(username, oldPassword, newPassword);
        Message response = sendRequest(request);

        if (response != null) {
            if (response.isSuccessResponse()) {
                System.out.println("✓ Password aggiornata con successo!");
            } else {
                System.out.println("✗ Errore nell'aggiornamento: " + response.getErrorMessage());
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
        }
    }

    /**
     * Gestisce richiesta storico prezzi - VERSIONE CORRETTA
     */
    private void handleGetPriceHistory() {
        System.out.println("\n--- STORICO PREZZI ---");

        System.out.print("Mese (formato MMYYYY, es. 062025 per Giugno 2025): ");
        String month = userInput.nextLine().trim();

        if (!Constants.isValidMonthFormat(month)) {
            System.out.println("✗ Formato mese non valido. Usa MMYYYY (es. 062025)");
            return;
        }

        Message request = Message.createPriceHistoryRequest(month);
        Message response = sendRequest(request);

        if (response != null) {
            if (response.isSuccessResponse()) {
                // CORREZIONE: Estrai e formatta i dati dal campo 'data'
                displayPriceHistory(response.getData(), month);
            } else {
                System.out.println("✗ Errore nel recupero storico: " + response.getErrorMessage());
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
        }
    }

    /**
     * Mostra i dati del price history in formato leggibile
     */
    private void displayPriceHistory(Object data, String month) {
        if (data == null) {
            System.out.println("❌ Nessun dato ricevuto per il mese " + month);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> priceHistory = (java.util.Map<String, Object>) data;

            System.out.println("\n📊 === STORICO PREZZI " + month + " ===");

            // Mostra summary se presente
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> summary = (java.util.Map<String, Object>) priceHistory.get("summary");
            if (summary != null) {
                System.out.println("--- 📈 Sommario del Mese ---");
                System.out.println("Giorni di trading: " + summary.get("tradingDays"));
                System.out.println("Totale trades: " + summary.get("totalTrades"));
                System.out.println("Volume totale: " + summary.get("totalVolume") + " millesimi BTC");
                System.out.println("Apertura mese: " + formatPrice((Number) summary.get("monthOpen")));
                System.out.println("Chiusura mese: " + formatPrice((Number) summary.get("monthClose")));
                System.out.println("Massimo mese: " + formatPrice((Number) summary.get("monthHigh")));
                System.out.println("Minimo mese: " + formatPrice((Number) summary.get("monthLow")));
                System.out.println();
            }

            // Mostra dati giornalieri se presenti
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> dailyStats =
                    (java.util.List<java.util.Map<String, Object>>) priceHistory.get("dailyStats");

            if (dailyStats != null && !dailyStats.isEmpty()) {
                System.out.println("--- 📅 Dati Giornalieri ---");
                System.out.printf("%-12s %-12s %-12s %-12s %-12s %-10s %-8s%n",
                        "Data", "Apertura", "Chiusura", "Massimo", "Minimo", "Volume", "Trades");
                System.out.println("────────────────────────────────────────────────────────────────────────────────");

                for (java.util.Map<String, Object> day : dailyStats) {
                    System.out.printf("%-12s %-12s %-12s %-12s %-12s %-10s %-8s%n",
                            day.get("date"),
                            formatPrice((Number) day.get("open")),
                            formatPrice((Number) day.get("close")),
                            formatPrice((Number) day.get("high")),
                            formatPrice((Number) day.get("low")),
                            day.get("volume"),
                            day.get("trades")
                    );
                }
            } else {
                System.out.println("--- ℹ️  Nessun dato giornaliero disponibile ---");
            }

            // Verifica se ci sono errori nei dati
            if (priceHistory.containsKey("error")) {
                System.out.println("⚠️  Errore nei dati: " + priceHistory.get("error"));
            }

            // Verifica se ci sono messaggi informativi
            if (priceHistory.containsKey("message")) {
                System.out.println("ℹ️  " + priceHistory.get("message"));
            }

            System.out.println("═══════════════════════════════════════════════════════════════════════════════");

        } catch (ClassCastException e) {
            System.err.println("❌ Errore nel formato dei dati ricevuti: " + e.getMessage());
            System.out.println("Dati grezzi ricevuti: " + data.toString());
        } catch (Exception e) {
            System.err.println("❌ Errore nella visualizzazione dei dati: " + e.getMessage());
            System.out.println("Dati grezzi ricevuti: " + data.toString());
        }
    }

    /**
     * Formatta un prezzo da millesimi a formato USD leggibile
     */
    private String formatPrice(Number price) {
        if (price == null) return "N/A";
        double dollars = price.longValue() / 1000.0;
        return String.format("$%.2f", dollars);
    }

    /**
     * Gestisce risposta per ordini
     */
    private void handleOrderResponse(Message response, String orderType) {
        if (response != null) {
            if (response.getOrderId() != null && response.getOrderId() > 0) {
                System.out.println("✓ " + orderType + " inserito con successo!");
                System.out.println("Order ID: " + response.getOrderId());
            } else {
                System.out.println("✗ Errore nell'inserimento " + orderType);
            }
        } else {
            System.out.println("✗ Errore di comunicazione con il server");
        }
    }

    /**
     * Invia richiesta al server e riceve risposta
     */
    private Message sendRequest(Message request) {
        if (!connected.get()) {
            System.err.println("Non connesso al server");
            return null;
        }

        try {
            // Invia richiesta
            String requestJson = JSONConverter.messageToJSON(request);
            if (requestJson == null) {
                System.err.println("Errore nella serializzazione richiesta");
                return null;
            }

            tcpWriter.println(requestJson);
            tcpWriter.println(); // Riga vuota per delimitare

            // Riceve risposta
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = tcpReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    break;
                }
                responseBuilder.append(line);
            }

            String responseJson = responseBuilder.toString();
            if (responseJson.trim().isEmpty()) {
                System.err.println("Risposta vuota dal server");
                return null;
            }

            return JSONConverter.jsonToMessage(responseJson);

        } catch (IOException e) {
            System.err.println("Errore nella comunicazione con il server: " + e.getMessage());
            return null;
        }
    }

    /**
     * Avvia listener UDP per notifiche
     */
    private void startUDPListener() {
        udpListenerThread = new Thread(() -> {
            byte[] buffer = new byte[1024];

            while (connected.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String notificationJson = new String(packet.getData(), 0, packet.getLength());
                    Message notification = JSONConverter.jsonToMessage(notificationJson);

                    if (notification != null && notification.isNotification()) {
                        handleNotification(notification);
                    }

                } catch (IOException e) {
                    if (connected.get()) {
                        System.err.println("Errore nella ricezione notifica UDP: " + e.getMessage());
                    }
                    break;
                }
            }
        });

        udpListenerThread.setDaemon(true);
        udpListenerThread.start();
    }

    /**
     * Gestisce notifiche UDP dal server
     */
    private void handleNotification(Message notification) {
        if ("closedTrades".equals(notification.getNotification())) {
            var trades = notification.getTrades();
            if (trades != null && !trades.isEmpty()) {
                System.out.println("\n🔔 NOTIFICA TRADE ESEGUITO!");
                System.out.println("═══════════════════════════");
                for (var trade : trades) {
                    String typeStr = "bid".equals(trade.getType()) ? "ACQUISTO" : "VENDITA";
                    System.out.println("Ordine " + trade.getOrderId() + " - " + typeStr);
                    System.out.println("Quantità: " + trade.getSize() + " millesimi BTC");
                    System.out.println("Prezzo: " + trade.getPrice() + " millesimi USD");
                    System.out.println("───────────────────────────");
                }
                System.out.println("Premi INVIO per continuare...");
            }
        }
    }
}