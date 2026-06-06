package Server;

import Common.Constants;
import Common.TradeRecord;
import Common.User;
import Common.Order;
import Utils.JSONConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PersistenceManager {

    // Cache dei trades per evitare letture continue dal file
    private final List<TradeRecord> cachedTrades;

    // Lock per thread safety durante I/O
    private final Object fileLock = new Object();

    public PersistenceManager() {
        this.cachedTrades = new ArrayList<>();

        // Crea le directory necessarie
        createDirectoriesIfNeeded();

        // Carica i trades esistenti in cache
        loadTradesFromFile();
    }

    /**
     * Crea le directory necessarie per i file di persistenza
     */
    private void createDirectoriesIfNeeded() {
        try {
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                System.out.println("Creata directory: data/");
            }

            createFileIfNotExists(Constants.USERS_FILE);
            createFileIfNotExists(Constants.ORDERS_HISTORY_FILE);
            createFileIfNotExists(Constants.ACTIVE_ORDERS_BACKUP_FILE);

        } catch (Exception e) {
            System.err.println("Errore nella creazione delle directory: " + e.getMessage());
        }
    }

    /**
     * Crea un file vuoto se non esiste
     */
    private void createFileIfNotExists(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                // Crea la struttura corretta per orders_history.json
                if (filename.equals(Constants.ORDERS_HISTORY_FILE)) {
                    Files.write(Paths.get(filename), "{\"trades\":[]}".getBytes());
                } else {
                    Files.write(Paths.get(filename), "[]".getBytes());
                }
                System.out.println("Creato file: " + filename);
            }
        } catch (IOException e) {
            System.err.println("Errore nella creazione del file " + filename + ": " + e.getMessage());
        }
    }

    /**
     * Salva la lista degli utenti
     */
    public void saveUsers(List<User> users) {
        synchronized (fileLock) {
            try {
                String jsonContent = JSONConverter.usersToJSON(users);
                if (jsonContent != null) {
                    Files.write(Paths.get(Constants.USERS_FILE), jsonContent.getBytes());
                    System.out.println("Salvati " + users.size() + " utenti");
                }
            } catch (IOException e) {
                System.err.println("Errore nel salvataggio utenti: " + e.getMessage());
            }
        }
    }
    /**
     * Salva i trade records nello storico
     */
    public void saveTradeRecords(List<TradeRecord> newTrades) {
        if (newTrades == null || newTrades.isEmpty()) {
            return;
        }

        synchronized (fileLock) {
            try {
                // Aggiunge i nuovi trades alla cache
                cachedTrades.addAll(newTrades);

                // Salva tutti i trades nel file
                String jsonContent = JSONConverter.tradeRecordsToJSON(cachedTrades);
                if (jsonContent != null) {
                    Files.write(Paths.get(Constants.ORDERS_HISTORY_FILE), jsonContent.getBytes());
                    System.out.println("✅ Salvati " + newTrades.size() + " nuovi trades (totale: " + cachedTrades.size() + ")");
                } else {
                    System.err.println("❌ Errore nella conversione trades in JSON");
                }

            } catch (IOException e) {
                System.err.println("❌ Errore nel salvataggio trades: " + e.getMessage());
            }
        }
    }

    /**
     * Carica i trade records dal file
     */
    private void loadTradesFromFile() {
        synchronized (fileLock) {
            try {
                File file = new File(Constants.ORDERS_HISTORY_FILE);
                if (!file.exists()) {
                    System.out.println("File orders_history.json non esiste, verrà creato al primo salvataggio");
                    return;
                }

                String jsonContent = new String(Files.readAllBytes(Paths.get(Constants.ORDERS_HISTORY_FILE)));
                if (jsonContent.trim().isEmpty()) {
                    System.out.println("File orders_history.json vuoto");
                    return;
                }

                // Gestisce sia formato {"trades":[]} che []
                List<TradeRecord> trades = null;

                if (jsonContent.trim().startsWith("{")) {
                    // Formato con wrapper {"trades": [...]}
                    trades = JSONConverter.jsonToTradeRecords(jsonContent);
                } else if (jsonContent.trim().startsWith("[")) {
                    // Formato array diretto [...]
                    trades = JSONConverter.jsonToTradeRecords("{\"trades\":" + jsonContent + "}");
                }

                if (trades != null && !trades.isEmpty()) {
                    cachedTrades.clear();
                    cachedTrades.addAll(trades);
                    System.out.println("✅ Caricati " + trades.size() + " trades dallo storico");
                } else {
                    System.out.println("Nessun trade trovato nel file o errore nel parsing");
                }

            } catch (IOException e) {
                System.err.println("Errore nel caricamento trades: " + e.getMessage());
            }
        }
    }

    /**
     * Ottiene tutti i trade records
     */
    public List<TradeRecord> getAllTrades() {
        synchronized (fileLock) {
            return new ArrayList<>(cachedTrades);
        }
    }

    /**
    * Ottiene trades per mese
     */
    public List<TradeRecord> getTradesForMonth(String month) {
        if (!Constants.isValidMonthFormat(month)) {
            System.out.println("❌ Formato mese non valido: " + month + " (deve essere MMYYYY)");
            return new ArrayList<>();
        }

        synchronized (fileLock) {
            try {
                int reqMonth = Integer.parseInt(month.substring(0, 2));
                int reqYear = Integer.parseInt(month.substring(2));

                System.out.println("🔍 Filtro trades per: Anno=" + reqYear + ", Mese=" + reqMonth);
                System.out.println("🔍 Trades totali in cache: " + cachedTrades.size());

                List<TradeRecord> matchingTrades = new ArrayList<>();

                for (TradeRecord trade : cachedTrades) {
                    try {
                        // CORREZIONE: Il timestamp è in SECONDI (epoch seconds)
                        Instant instant = Instant.ofEpochSecond(trade.getTimestamp());
                        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();

                        int year = date.getYear();
                        int tradeMonth = date.getMonthValue();

                        System.out.println("  Trade " + trade.getOrderId() + ": " + date +
                                " (anno=" + year + ", mese=" + tradeMonth + ")");

                        if (year == reqYear && tradeMonth == reqMonth) {
                            matchingTrades.add(trade);
                            System.out.println("    ✅ MATCH!");
                        }
                    } catch (Exception e) {
                        System.err.println("    ❌ Errore conversione timestamp per trade " + trade.getOrderId() + ": " + e.getMessage());
                    }
                }

                System.out.println("🎯 Risultato: " + matchingTrades.size() + " trades per " + month);
                return matchingTrades;

            } catch (Exception e) {
                System.err.println("❌ Errore nel filtro: " + e.getMessage());
                return new ArrayList<>();
            }
        }
    }

    /**
     * ✅ VERSIONE CORRETTA: Calcola price history per mese
     */
    public Map<String, Object> getPriceHistoryForMonth(String month) {
        System.out.println("\n📊 === getPriceHistoryForMonth ===");
        System.out.println("📊 Mese richiesto: " + month);

        if (!Constants.isValidMonthFormat(month)) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Formato mese non valido. Usa MMYYYY (es. 072025)");
            return errorResult;
        }

        synchronized (fileLock) {
            try {
                // Se la cache è vuota, prova a ricaricare
                if (cachedTrades.isEmpty()) {
                    System.out.println("📊 Cache vuota, tentativo di ricaricamento...");
                    loadTradesFromFile();

                    // Se ancora vuota, crea dati di test
                    if (cachedTrades.isEmpty()) {
                        System.out.println("📊 Nessun dato disponibile, creo dati di test...");
                        createTestTradesForMonth(month);
                    }
                }

                // Ottieni trades per il mese
                List<TradeRecord> monthTrades = getTradesForMonth(month);

                if (monthTrades.isEmpty()) {
                    // Nessun trade trovato per questo mese
                    Map<String, Object> emptyResult = new HashMap<>();
                    emptyResult.put("month", month);
                    emptyResult.put("message", "Nessun trade trovato per il mese " + month);
                    emptyResult.put("summary", createEmptySummary());
                    emptyResult.put("dailyStats", new ArrayList<>());
                    return emptyResult;
                }

                System.out.println("📊 Elaborazione " + monthTrades.size() + " trades per " + month);

                // Calcola statistiche
                return calculatePriceHistory(month, monthTrades);

            } catch (Exception e) {
                System.err.println("📊 Errore in getPriceHistoryForMonth: " + e.getMessage());
                e.printStackTrace();

                // Ritorna errore
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Errore interno: " + e.getMessage());
                return errorResult;
            }
        }
    }

    /**
     * Calcola le statistiche di price history dai trades
     */
    private Map<String, Object> calculatePriceHistory(String month, List<TradeRecord> trades) {
        try {
            // Ordina i trades per timestamp
            trades.sort(Comparator.comparingLong(TradeRecord::getTimestamp));

            // Raggruppa per giorno
            Map<String, List<TradeRecord>> tradesByDay = new LinkedHashMap<>();

            for (TradeRecord trade : trades) {
                try {
                    Instant instant = Instant.ofEpochSecond(trade.getTimestamp());
                    LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                    String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

                    tradesByDay.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(trade);
                } catch (Exception e) {
                    System.err.println("Errore nel processare trade " + trade.getOrderId() + ": " + e.getMessage());
                }
            }

            // Calcola statistiche giornaliere
            List<Map<String, Object>> dailyStats = new ArrayList<>();

            for (Map.Entry<String, List<TradeRecord>> entry : tradesByDay.entrySet()) {
                String date = entry.getKey();
                List<TradeRecord> dayTrades = entry.getValue();

                if (!dayTrades.isEmpty()) {
                    Map<String, Object> dayStats = calculateDayStats(date, dayTrades);
                    dailyStats.add(dayStats);
                }
            }

            // Calcola summary del mese
            Map<String, Object> summary = calculateMonthlySummary(trades, dailyStats.size());

            // Risultato finale
            Map<String, Object> result = new HashMap<>();
            result.put("month", month);
            result.put("dailyStats", dailyStats);
            result.put("summary", summary);

            System.out.println("📊 Calcolate statistiche per " + dailyStats.size() + " giorni");
            return result;

        } catch (Exception e) {
            System.err.println("Errore nel calcolo price history: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Calcola statistiche per un singolo giorno
     */
    private Map<String, Object> calculateDayStats(String date, List<TradeRecord> dayTrades) {
        // Ordina per timestamp
        dayTrades.sort(Comparator.comparingLong(TradeRecord::getTimestamp));

        long open = dayTrades.get(0).getPrice();
        long close = dayTrades.get(dayTrades.size() - 1).getPrice();
        long high = dayTrades.stream().mapToLong(TradeRecord::getPrice).max().orElse(0);
        long low = dayTrades.stream().mapToLong(TradeRecord::getPrice).min().orElse(0);
        long volume = dayTrades.stream().mapToLong(TradeRecord::getSize).sum();

        Map<String, Object> dayStats = new HashMap<>();
        dayStats.put("date", date);
        dayStats.put("open", open);
        dayStats.put("close", close);
        dayStats.put("high", high);
        dayStats.put("low", low);
        dayStats.put("volume", volume);
        dayStats.put("trades", dayTrades.size());

        return dayStats;
    }

    /**
     * Calcola summary mensile
     */
    private Map<String, Object> calculateMonthlySummary(List<TradeRecord> allTrades, int tradingDays) {
        long totalTrades = allTrades.size();
        long totalVolume = allTrades.stream().mapToLong(TradeRecord::getSize).sum();
        long monthOpen = allTrades.get(0).getPrice();
        long monthClose = allTrades.get(allTrades.size() - 1).getPrice();
        long monthHigh = allTrades.stream().mapToLong(TradeRecord::getPrice).max().orElse(0);
        long monthLow = allTrades.stream().mapToLong(TradeRecord::getPrice).min().orElse(0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTrades", totalTrades);
        summary.put("totalVolume", totalVolume);
        summary.put("monthOpen", monthOpen);
        summary.put("monthClose", monthClose);
        summary.put("monthHigh", monthHigh);
        summary.put("monthLow", monthLow);
        summary.put("tradingDays", tradingDays);

        return summary;
    }

    /**
     * Crea un summary vuoto
     */
    private Map<String, Object> createEmptySummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTrades", 0);
        summary.put("totalVolume", 0);
        summary.put("monthOpen", 0);
        summary.put("monthClose", 0);
        summary.put("monthHigh", 0);
        summary.put("monthLow", 0);
        summary.put("tradingDays", 0);
        return summary;
    }

    /**
     * Crea trades di test per un mese specifico
     */
    private void createTestTradesForMonth(String month) {
        try {
            int reqMonth = Integer.parseInt(month.substring(0, 2));
            int reqYear = Integer.parseInt(month.substring(2));

            List<TradeRecord> testTrades = new ArrayList<>();

            // Crea alcuni trades per il mese richiesto
            LocalDate startDate = LocalDate.of(reqYear, reqMonth, 1);
            long baseTimestamp = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();

            for (int i = 0; i < 10; i++) {
                long timestamp = baseTimestamp + (i * 3600); // Un trade ogni ora
                long price = 50000000 + (i * 1000000); // Prezzi crescenti da 50k a 59k USD

                TradeRecord trade = new TradeRecord(
                        9000 + i,
                        "TestUser" + i,
                        i % 2 == 0 ? "bid" : "ask",
                        "limit",
                        1000, // 1 BTC
                        price,
                        timestamp
                );

                testTrades.add(trade);
            }

            // Salva i trades di test
            saveTradeRecords(testTrades);
            System.out.println("✅ Creati " + testTrades.size() + " trades di test per " + month);

        } catch (Exception e) {
            System.err.println("Errore nella creazione trades di test: " + e.getMessage());
        }
    }

    /**
     * Debug: mostra tutti i trades con conversione timestamp
     */
    public void debugAllTrades() {
        synchronized (fileLock) {
            System.out.println("\n📊 === DEBUG TUTTI I TRADES ===");
            System.out.println("Trades in cache: " + cachedTrades.size());

            if (cachedTrades.isEmpty()) {
                System.out.println("❌ Nessun trade in cache!");

                // Verifica file
                File file = new File(Constants.ORDERS_HISTORY_FILE);
                System.out.println("File " + Constants.ORDERS_HISTORY_FILE + " esiste: " + file.exists());
                if (file.exists()) {
                    System.out.println("Dimensione file: " + file.length() + " bytes");
                }
                return;
            }

            System.out.println("\n📅 CONVERSIONE TIMESTAMP:");
            System.out.println("┌──────────┬─────────────┬────────────┬──────┬──────┬──────┐");
            System.out.println("│ Order ID │  Timestamp  │    Data    │ Anno │ Mese │ Gior │");
            System.out.println("├──────────┼─────────────┼────────────┼──────┼──────┼──────┤");

            for (int i = 0; i < Math.min(10, cachedTrades.size()); i++) {
                TradeRecord trade = cachedTrades.get(i);
                try {
                    Instant instant = Instant.ofEpochSecond(trade.getTimestamp());
                    LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();

                    System.out.printf("│ %-8d │ %-11d │ %-10s │ %-4d │ %-4d │ %-4d │%n",
                            trade.getOrderId(), trade.getTimestamp(), date.toString(),
                            date.getYear(), date.getMonthValue(), date.getDayOfMonth());
                } catch (Exception e) {
                    System.out.printf("│ %-8d │ %-11d │ %-10s │ %-4s │ %-4s │ %-4s │%n",
                            trade.getOrderId(), trade.getTimestamp(), "ERROR", "ERR", "ERR", "ERR");
                }
            }
            System.out.println("└──────────┴─────────────┴────────────┴──────┴──────┴──────┘");

            // Mostra mesi disponibili
            Set<String> availableMonths = new HashSet<>();
            for (TradeRecord trade : cachedTrades) {
                try {
                    Instant instant = Instant.ofEpochSecond(trade.getTimestamp());
                    LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                    String monthStr = String.format("%02d%04d", date.getMonthValue(), date.getYear());
                    availableMonths.add(monthStr);
                } catch (Exception e) {
                    // Ignora errori di conversione
                }
            }

            System.out.println("\n🗓️  Mesi disponibili per getPriceHistory: " + availableMonths);
            System.out.println("=== FINE DEBUG ===\n");
        }
    }

    /**
     * Salva backup ordini attivi
     */
    public void saveActiveOrdersBackup(List<Order> activeOrders) {
        synchronized (fileLock) {
            try {
                String jsonContent = JSONConverter.ordersToJSON(activeOrders);
                if (jsonContent != null) {
                    Files.write(Paths.get(Constants.ACTIVE_ORDERS_BACKUP_FILE), jsonContent.getBytes());
                    System.out.println("Backup ordini attivi salvato: " + activeOrders.size() + " ordini");
                }
            } catch (IOException e) {
                System.err.println("Errore nel salvataggio backup ordini: " + e.getMessage());
            }
        }
    }

    /**
     * Carica backup ordini attivi
     */
    public List<Order> loadActiveOrdersBackup() {
        synchronized (fileLock) {
            try {
                File file = new File(Constants.ACTIVE_ORDERS_BACKUP_FILE);
                if (!file.exists()) {
                    return new ArrayList<>();
                }

                String jsonContent = new String(Files.readAllBytes(Paths.get(Constants.ACTIVE_ORDERS_BACKUP_FILE)));
                if (jsonContent.trim().isEmpty() || jsonContent.trim().equals("[]")) {
                    return new ArrayList<>();
                }

                List<Order> orders = JSONConverter.jsonToOrders(jsonContent);
                if (orders != null) {
                    System.out.println("Caricati " + orders.size() + " ordini attivi dal backup");
                    return orders;
                } else {
                    return new ArrayList<>();
                }

            } catch (IOException e) {
                System.err.println("Errore nel caricamento backup ordini: " + e.getMessage());
                return new ArrayList<>();
            }
        }
    }

    /**
     * Aggiunge dati di test per il mese corrente
     */
    public void addCurrentMonthTestData() {
        // Crea trades per il mese corrente
        LocalDate now = LocalDate.now();
        String currentMonth = String.format("%02d%04d", now.getMonthValue(), now.getYear());

        System.out.println("🧪 Creazione trades di test per il mese corrente: " + currentMonth);
        createTestTradesForMonth(currentMonth);
    }
}