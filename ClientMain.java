package Client;


import Common.Constants;

import java.io.*;
import java.util.Properties;

public class ClientMain {

    private static final String CONFIG_FILE = "config/client_config.txt";

    public static void main(String[] args) {
        System.out.println("=== CROSS Trading Client ===");
        System.out.println("Bitcoin/USD Exchange System");
        System.out.println("Inizializzazione client...\n");

        try {
            // Carica configurazioni
            ClientConfig config = loadConfiguration();

            // Crea e inizializza client
            CROSSClient client = new CROSSClient();

            // Mostra informazioni di connessione
            displayConnectionInfo(config);

            // Tenta la connessione al server
            System.out.println("Connessione al server...");
            client.initialize(config.serverHost, config.serverTcpPort, config.serverUdpPort);

            if (client.connect()) {
                System.out.println("✓ Connessione stabilita con successo!");
                System.out.println("══════════════════════════════════════");

                // Avvia il client interattivo
                client.start();

            } else {
                System.err.println("✗ Impossibile connettersi al server");
                System.err.println("Verificare che il server sia avviato e raggiungibile");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Errore fatale nell'avvio del client: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Carica la configurazione dal file
     */
    private static ClientConfig loadConfiguration() {
        ClientConfig config = new ClientConfig();

        try {
            // Crea file di configurazione default se non esiste
            createDefaultConfigIfNeeded();

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                props.load(fis);
            }

            // Legge configurazioni con valori default
            config.serverHost = props.getProperty("server.host", "localhost");
            config.serverTcpPort = Integer.parseInt(props.getProperty("server.tcp.port", String.valueOf(Constants.DEFAULT_TCP_PORT)));
            config.serverUdpPort = Integer.parseInt(props.getProperty("server.udp.port", String.valueOf(Constants.DEFAULT_UDP_PORT)));
            config.connectionTimeout = Integer.parseInt(props.getProperty("connection.timeout", String.valueOf(Constants.TCP_CONNECTION_TIMEOUT)));
            config.enableDebugLogging = Boolean.parseBoolean(props.getProperty("debug.logging", "false"));

            System.out.println("Configurazione caricata da: " + CONFIG_FILE);

        } catch (Exception e) {
            System.err.println("Errore nel caricamento configurazione: " + e.getMessage());
            System.out.println("Uso configurazioni default");

            // Usa configurazioni default
            config.serverHost = "localhost";
            config.serverTcpPort = Constants.DEFAULT_TCP_PORT;
            config.serverUdpPort = Constants.DEFAULT_UDP_PORT;
            config.connectionTimeout = Constants.TCP_CONNECTION_TIMEOUT;
            config.enableDebugLogging = false;
        }

        return config;
    }

    /**
     * Crea file di configurazione default se non esiste
     */
    private static void createDefaultConfigIfNeeded() {
        File configFile = new File(CONFIG_FILE);

        // Crea directory config se non esiste
        File configDir = configFile.getParentFile();
        if (configDir != null && !configDir.exists()) {
            configDir.mkdirs();
        }

        // Crea file config se non esiste
        if (!configFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.println("# CROSS Client Configuration");
                writer.println();
                writer.println("# Indirizzo del server CROSS");
                writer.println("server.host=localhost");
                writer.println();
                writer.println("# Porta TCP del server");
                writer.println("server.tcp.port=" + Constants.DEFAULT_TCP_PORT);
                writer.println();
                writer.println("# Porta UDP del server per notifiche");
                writer.println("server.udp.port=" + Constants.DEFAULT_UDP_PORT);
                writer.println();
                writer.println("# Timeout connessione (millisecondi)");
                writer.println("connection.timeout=" + Constants.TCP_CONNECTION_TIMEOUT);
                writer.println();
                writer.println("# Abilita logging debug");
                writer.println("debug.logging=false");
                writer.println();
                writer.println("# Configurazioni aggiuntive");
                writer.println("# retry.attempts=3");
                writer.println("# retry.delay=5000");

                System.out.println("Creato file di configurazione default: " + CONFIG_FILE);

            } catch (IOException e) {
                System.err.println("Errore nella creazione file configurazione: " + e.getMessage());
            }
        }
    }

    /**
     * Mostra informazioni di connessione
     */
    private static void displayConnectionInfo(ClientConfig config) {
        System.out.println("=== Configurazione Client ===");
        System.out.println("Server Host: " + config.serverHost);
        System.out.println("Server TCP Port: " + config.serverTcpPort);
        System.out.println("Server UDP Port: " + config.serverUdpPort);
        System.out.println("Connection Timeout: " + config.connectionTimeout + "ms");
        System.out.println("Debug Logging: " + config.enableDebugLogging);
        System.out.println();

        // Mostra informazioni sui formati dati
        System.out.println("=== Informazioni Sistema ===");
        System.out.println("• Size: quantità in millesimi di BTC");
        System.out.println("  Esempio: 1000 = 1 BTC, 500 = 0.5 BTC");
        System.out.println("• Price: prezzo in millesimi di USD");
        System.out.println("  Esempio: 50000000 = 50,000 USD");
        System.out.println("• Storico: formato mese MMYYYY");
        System.out.println("  Esempio: 012024 = Gennaio 2024");
        System.out.println();

        // Mostra informazioni sui tipi di ordine
        System.out.println("=== Tipi di Ordine ===");
        System.out.println("• Market Order: esecuzione immediata al miglior prezzo");
        System.out.println("• Limit Order: esecuzione solo al prezzo specificato o migliore");
        System.out.println("• Stop Order: diventa Market Order quando raggiunge lo stop price");
        System.out.println();
        System.out.println("• bid = ordine di acquisto");
        System.out.println("• ask = ordine di vendita");
        System.out.println();
    }

    /**
     * Classe per configurazione client
     */
    private static class ClientConfig {
        String serverHost;
        int serverTcpPort;
        int serverUdpPort;
        int connectionTimeout;
        boolean enableDebugLogging;
    }
}
