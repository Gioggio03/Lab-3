package Server;

import Common.Constants;

import java.io.*;

import java.util.Properties;
public class ServerMain {

    private static CROSSServer server;
    private static final String CONFIG_FILE = "config/server_config.txt";

    public static void main(String[] args) {
        System.out.println("=== CROSS Server ===");
        System.out.println("Exchange Order Book Service");
        System.out.println("Starting server...\n");

        try {
            // Carica configurazioni
            ServerConfig config = loadConfiguration();

            // Crea e inizializza server
            server = new CROSSServer();
            server.initialize(config.tcpPort, config.udpPort);

            // Registra shutdown hook per chiusura pulita
            registerShutdownHook();

            // Mostra informazioni di avvio
            displayStartupInfo(config);

            // Avvia server in thread separato
            Thread serverThread = new Thread(() -> {
                try {
                    server.start();
                } catch (Exception e) {
                    System.err.println("Errore nell'avvio server: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            serverThread.setDaemon(false);
            serverThread.start();


        } catch (Exception e) {
            System.err.println("Errore fatale nell'avvio del server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Carica la configurazione dal file
     */
    private static ServerConfig loadConfiguration() {
        ServerConfig config = new ServerConfig();

        try {
            createDefaultConfigIfNeeded();

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                props.load(fis);
            }

            config.tcpPort = Integer.parseInt(props.getProperty("tcp.port", String.valueOf(Constants.DEFAULT_TCP_PORT)));
            config.udpPort = Integer.parseInt(props.getProperty("udp.port", String.valueOf(Constants.DEFAULT_UDP_PORT)));
            config.threadPoolSize = Integer.parseInt(props.getProperty("thread.pool.size", String.valueOf(Constants.THREAD_POOL_SIZE)));
            config.connectionTimeout = Integer.parseInt(props.getProperty("connection.timeout", String.valueOf(Constants.TCP_CONNECTION_TIMEOUT)));
            config.userInactivityTimeout = Long.parseLong(props.getProperty("user.inactivity.timeout", String.valueOf(Constants.USER_INACTIVITY_TIMEOUT)));
            config.enableDebugLogging = Boolean.parseBoolean(props.getProperty("debug.logging", "false"));
            config.persistenceInterval = Long.parseLong(props.getProperty("persistence.interval", String.valueOf(Constants.PERSISTENCE_INTERVAL)));

            System.out.println("Configurazione caricata da: " + CONFIG_FILE);

        } catch (Exception e) {
            System.err.println("Errore nel caricamento configurazione: " + e.getMessage());
            System.out.println("Uso configurazioni default");

            config.tcpPort = Constants.DEFAULT_TCP_PORT;
            config.udpPort = Constants.DEFAULT_UDP_PORT;
            config.threadPoolSize = Constants.THREAD_POOL_SIZE;
            config.connectionTimeout = Constants.TCP_CONNECTION_TIMEOUT;
            config.userInactivityTimeout = Constants.USER_INACTIVITY_TIMEOUT;
            config.enableDebugLogging = false;
            config.persistenceInterval = Constants.PERSISTENCE_INTERVAL;
        }

        return config;
    }

    /**
     * Crea file di configurazione default se non esiste
     */
    private static void createDefaultConfigIfNeeded() {
        File configFile = new File(CONFIG_FILE);

        File configDir = configFile.getParentFile();
        if (configDir != null && !configDir.exists()) {
            configDir.mkdirs();
        }

        if (!configFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.println("# CROSS Server Configuration");
                writer.println("tcp.port=" + Constants.DEFAULT_TCP_PORT);
                writer.println("udp.port=" + Constants.DEFAULT_UDP_PORT);
                writer.println("thread.pool.size=" + Constants.THREAD_POOL_SIZE);
                writer.println("connection.timeout=" + Constants.TCP_CONNECTION_TIMEOUT);
                writer.println("user.inactivity.timeout=" + Constants.USER_INACTIVITY_TIMEOUT);
                writer.println("persistence.interval=" + Constants.PERSISTENCE_INTERVAL);
                writer.println("debug.logging=false");

                System.out.println("Creato file di configurazione default: " + CONFIG_FILE);

            } catch (IOException e) {
                System.err.println("Errore nella creazione file configurazione: " + e.getMessage());
            }
        }
    }

    /**
     * Registra shutdown hook per chiusura pulita
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== Arresto Server in Corso ===");

            if (server != null) {
                try {
                    server.stop();
                    System.out.println("Server arrestato correttamente");
                } catch (Exception e) {
                    System.err.println("Errore nell'arresto server: " + e.getMessage());
                }
            }

            System.out.println("Shutdown completato");
        }));
    }

    /**
     * Mostra informazioni di avvio
     */
    private static void displayStartupInfo(ServerConfig config) {
        System.out.println("=== Configurazione Server ===");
        System.out.println("TCP Port: " + config.tcpPort);
        System.out.println("UDP Port: " + config.udpPort);
        System.out.println();
        System.out.println("========================================");
    }

    /**
     * Forza salvataggio dati
     */
    private static void forceSaveData() {
        if (server == null || server.getUserManager() == null) {
            System.out.println("Server non disponibile");
            return;
        }

        try {
            server.getUserManager().saveUsers();
            System.out.println("✅ Dati salvati con successo");
        } catch (Exception e) {
            System.err.println("❌ Errore nel salvataggio: " + e.getMessage());
        }
    }

    /**
     * Formatta un prezzo da millesimi a un formato leggibile
     */
    private static String formatPrice(Number price) {
        if (price == null) return "N/A";
        double dollars = price.longValue() / 1000.0;
        return String.format("$%.2f", dollars);
    }

    /**
     * Classe per configurazione server
     */
    private static class ServerConfig {
        int tcpPort;
        int udpPort;
        int threadPoolSize;
        int connectionTimeout;
        long userInactivityTimeout;
        boolean enableDebugLogging;
        long persistenceInterval;
    }
}