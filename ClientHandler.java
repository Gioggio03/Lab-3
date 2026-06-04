package Server;

import Common.Constants;
import Common.Message;
import Utils.JSONConverter;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final CROSSServer server;

    // Stream per comunicazione TCP
    private BufferedReader reader;
    private PrintWriter writer;

    // Informazioni del client
    private String clientAddress;
    private String loggedInUsername;
    private volatile boolean connected;
    private AtomicBoolean isProcessingRequest;

    // Timeout per inattività
    private long lastActivityTime;

    public ClientHandler(Socket clientSocket, CROSSServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.clientAddress = clientSocket.getInetAddress().toString();
        this.connected = true;
        this.isProcessingRequest = new AtomicBoolean(false);
        this.lastActivityTime = System.currentTimeMillis();

        try {
            // Imposta timeout sul socket
            clientSocket.setSoTimeout(Constants.TCP_READ_TIMEOUT);

            // Inizializza stream
            this.reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );
            this.writer = new PrintWriter(
                    clientSocket.getOutputStream(), true
            );

            System.out.println("ClientHandler creato per: " + clientAddress);

        } catch (IOException e) {
            System.err.println("Errore nell'inizializzazione ClientHandler per " + clientAddress + ": " + e.getMessage());
            disconnect();
        }
    }

    @Override
    public void run() {
        System.out.println("ClientHandler avviato per: " + clientAddress);

        try {
            // Loop principale per gestire le richieste
            while (connected && !clientSocket.isClosed()) {
                try {
                    // Legge richiesta dal client
                    String requestJson = readRequest();

                    if (requestJson == null) {
                        // Connessione chiusa dal client
                        break;
                    }

                    // Aggiorna timestamp ultima attività
                    updateActivity();

                    // Processa la richiesta
                    processClientRequest(requestJson);

                } catch (SocketTimeoutException e) {
                    // Timeout di lettura - controlla se il client è ancora attivo
                    if (System.currentTimeMillis() - lastActivityTime > Constants.TCP_CONNECTION_TIMEOUT) {
                        System.out.println("Timeout connessione per " + clientAddress);
                        break;
                    }
                    // Continua il loop se non è scaduto il timeout generale

                } catch (IOException e) {
                    if (connected) {
                        System.err.println("Errore I/O con client " + clientAddress + ": " + e.getMessage());
                    }
                    break;

                } catch (Exception e) {
                    System.err.println("Errore imprevisto con client " + clientAddress + ": " + e.getMessage());
                    sendErrorResponse("Internal server error");
                }
            }

        } finally {
            // Cleanup alla disconnessione
            cleanup();
        }
    }

    /**
     * Legge una richiesta JSON dal client
     */
    private String readRequest() throws IOException {
        StringBuilder requestBuilder = new StringBuilder();
        String line;

        // Legge fino a trovare una riga vuota o fine stream
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                break;
            }
            requestBuilder.append(line);
        }

        String request = requestBuilder.toString().trim();
        return request.isEmpty() ? null : request;
    }

    /**
     * Processa una richiesta JSON dal client
     */
    private void processClientRequest(String requestJson) {
        if (isProcessingRequest.getAndSet(true)) {
            // Se sta già processando una richiesta, ignora
            sendErrorResponse("Server busy, please retry");
            return;
        }

        try {
            System.out.println("Richiesta da " + clientAddress + ": " + requestJson.substring(0, Math.min(100, requestJson.length())));

            // Converte JSON in Message
            Message request = JSONConverter.jsonToMessage(requestJson);

            if (request == null) {
                sendErrorResponse(Constants.ERROR_INVALID_JSON);
                return;
            }

            // Gestisce operazioni speciali che non richiedono login
            if (handleSpecialOperations(request)) {
                return;
            }

            // Aggiorna attività utente se loggato
            if (loggedInUsername != null) {
                server.getUserManager().updateUserActivity(loggedInUsername);
            }

            // Processa la richiesta tramite il server
            Message response = server.processRequest(request, loggedInUsername);

            // Gestisce il post-processing della risposta
            handleResponsePostProcessing(request, response);

            // Invia risposta al client
            sendResponse(response);

        } catch (Exception e) {
            System.err.println("Errore nel processamento richiesta da " + clientAddress + ": " + e.getMessage());
            sendErrorResponse(Constants.ERROR_SERVER_INTERNAL);

        } finally {
            isProcessingRequest.set(false);
        }
    }

    /**
     * Gestisce operazioni speciali che hanno comportamenti particolari
     */
    private boolean handleSpecialOperations(Message request) {
        String operation = request.getOperation();

        if ("login".equals(operation)) {
            // Per il login, gestisce il tracking della connessione
            Message response = server.processRequest(request, loggedInUsername);

            if (response.isSuccessResponse()) {
                String username = request.getStringValue("username");
                Long udpPortObj = request.getLongValue("udpPort");

                this.loggedInUsername = username;

                // Registra la connessione nel server
                server.registerConnection(username, this);

                // Registra indirizzo UDP con porta corretta se fornita
                if (udpPortObj != null && udpPortObj > 0) {
                    int udpPort = udpPortObj.intValue();
                    InetSocketAddress udpAddress = new InetSocketAddress(
                            clientSocket.getInetAddress(),
                            udpPort
                    );
                    server.registerUDPAddress(username, udpAddress);
                    System.out.println("DEBUG: Registered UDP address for " + username + ": " + udpAddress);
                } else {
                    System.out.println("DEBUG: No UDP port provided in login for " + username);
                }

                System.out.println("Login successful per " + username + " da " + clientAddress);
            }

            sendResponse(response);
            return true;

        } else if ("logout".equals(operation)) {
            // Per il logout, pulisce le connessioni
            Message response = server.processRequest(request, loggedInUsername);

            if (response.isSuccessResponse() && loggedInUsername != null) {
                server.unregisterConnection(loggedInUsername);
                System.out.println("Logout successful per " + loggedInUsername);
                this.loggedInUsername = null;
            }

            sendResponse(response);
            return true;
        }

        return false; // Non è un'operazione speciale
    }

    /**
     * Gestisce post-processing specifico per certe risposte
     */
    private void handleResponsePostProcessing(Message request, Message response) {
        String operation = request.getOperation();

        // Log delle operazioni importanti
        if (response.isSuccessResponse()) {
            switch (operation) {
                case "insertLimitOrder":
                case "insertMarketOrder":
                case "insertStopOrder":
                    System.out.println("Ordine inserito con successo da " + loggedInUsername +
                            " (ID: " + response.getOrderId() + ")");
                    break;

                case "cancelOrder":
                    System.out.println("Ordine cancellato da " + loggedInUsername);
                    break;

                case "register":
                    String username = request.getStringValue("username");
                    System.out.println("Nuova registrazione: " + username + " da " + clientAddress);
                    break;
            }
        }
    }

    /**
     * Invia una risposta al client
     */
    private void sendResponse(Message response) {
        try {
            String responseJson = JSONConverter.messageToJSON(response);
            if (responseJson != null) {
                writer.println(responseJson);
                writer.println(); // Riga vuota per delimitare il messaggio

                // Log delle risposte per debugging (solo prime 100 caratteri)
                String logResponse = responseJson.length() > 100 ?
                        responseJson.substring(0, 100) + "..." : responseJson;
                System.out.println("Risposta a " + clientAddress + ": " + logResponse);

            } else {
                sendErrorResponse("Failed to serialize response");
            }

        } catch (Exception e) {
            System.err.println("Errore nell'invio risposta a " + clientAddress + ": " + e.getMessage());
        }
    }

    /**
     * Invia una risposta di errore standard
     */
    private void sendErrorResponse(String errorMessage) {
        try {
            Message errorResponse = Message.createResponse(
                    Constants.RESPONSE_REGISTER_OTHER_ERROR,
                    errorMessage
            );

            String responseJson = JSONConverter.messageToJSON(errorResponse);
            if (responseJson != null) {
                writer.println(responseJson);
                writer.println();
            }

        } catch (Exception e) {
            System.err.println("Errore nell'invio error response: " + e.getMessage());
        }
    }

    /**
     * Aggiorna il timestamp dell'ultima attività
     */
    private void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Controlla se la connessione è attiva
     */
    public boolean isConnected() {
        return connected && !clientSocket.isClosed();
    }

    /**
     * Ottiene l'username dell'utente loggato
     */
    public String getLoggedInUsername() {
        return loggedInUsername;
    }

    /**
     * Ottiene l'indirizzo del client
     */
    public String getClientAddress() {
        return clientAddress;
    }

    /**
     * Ottiene il timestamp dell'ultima attività
     */
    public long getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * Forza la disconnessione del client
     */
    public void disconnect() {
        connected = false;

        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Errore nella chiusura socket per " + clientAddress + ": " + e.getMessage());
        }
    }

    /**
     * Cleanup quando la connessione si chiude
     */
    private void cleanup() {
        System.out.println("Cleanup ClientHandler per: " + clientAddress);

        // Se c'era un utente loggato, fai logout
        if (loggedInUsername != null) {
            server.getUserManager().logoutUser(loggedInUsername);
            server.unregisterConnection(loggedInUsername);
            System.out.println("Logout automatico per " + loggedInUsername + " (disconnessione)");
        }

        // Chiude stream e socket
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Errore nella chiusura risorse per " + clientAddress + ": " + e.getMessage());
        }

        connected = false;
        System.out.println("ClientHandler terminato per: " + clientAddress);
    }
}