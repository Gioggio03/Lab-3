package Server;

import Common.Constants;
import Common.User;
import Utils.JSONConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserManager {

    // Mappa degli utenti registrati (username -> User)
    private final ConcurrentHashMap<String, User> registeredUsers;

    // Set degli utenti attualmente loggati
    private final Set<String> loggedInUsers;

    // Scheduler per controllo timeout inattività
    private final ScheduledExecutorService scheduler;

    public UserManager() {
        this.registeredUsers = new ConcurrentHashMap<>();
        this.loggedInUsers = Collections.synchronizedSet(new HashSet<>());
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Avvia il controllo periodico per logout automatico
        startInactivityCheck();
    }

    /**
     * Carica gli utenti dal file JSON
     */
    public void loadUsers() {
        try {
            File usersFile = new File(Constants.USERS_FILE);

            // Crea la directory se non esiste
            File parentDir = usersFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Se il file non esiste, inizia con lista vuota
            if (!usersFile.exists()) {
                System.out.println("File utenti non trovato, inizializzazione con lista vuota");
                return;
            }

            // Legge il contenuto del file
            String jsonContent = new String(Files.readAllBytes(Paths.get(Constants.USERS_FILE)));

            if (jsonContent.trim().isEmpty()) {
                System.out.println("File utenti vuoto, inizializzazione con lista vuota");
                return;
            }

            // Converte da JSON
            List<User> users = JSONConverter.jsonToUsers(jsonContent);

            if (users != null) {
                for (User user : users) {
                    registeredUsers.put(user.getUsername(), user);
                    // Assicurati che tutti gli utenti siano marcati come non loggati all'avvio
                    user.setLoggedIn(false);
                }
                System.out.println("Caricati " + users.size() + " utenti registrati");
            } else {
                System.err.println("Errore nel parsing del file utenti");
            }

        } catch (IOException e) {
            System.err.println("Errore nel caricamento utenti: " + e.getMessage());
        }
    }

    /**
     * Salva gli utenti su file JSON
     */
    public void saveUsers() {
        try {
            // Crea la directory se non esiste
            File usersFile = new File(Constants.USERS_FILE);
            File parentDir = usersFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Converte la lista di utenti in JSON
            List<User> usersList = new ArrayList<>(registeredUsers.values());
            String jsonContent = JSONConverter.usersToJSON(usersList);

            if (jsonContent != null) {
                Files.write(Paths.get(Constants.USERS_FILE), jsonContent.getBytes());
                System.out.println("Salvati " + usersList.size() + " utenti");
            } else {
                System.err.println("Errore nella conversione utenti in JSON");
            }

        } catch (IOException e) {
            System.err.println("Errore nel salvataggio utenti: " + e.getMessage());
        }
    }

    /**
     * Aggiunge un nuovo utente
     */
    public boolean addUser(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }

        // Controlla se l'username esiste già
        if (registeredUsers.containsKey(user.getUsername())) {
            return false;
        }

        registeredUsers.put(user.getUsername(), user);
        System.out.println("Nuovo utente registrato: " + user.getUsername());
        return true;
    }

    /**
     * Verifica se un utente esiste
     */
    public boolean userExists(String username) {
        return username != null && registeredUsers.containsKey(username);
    }

    /**
     * Ottiene un utente per username
     */
    public User getUser(String username) {
        return username != null ? registeredUsers.get(username) : null;
    }

    /**
     * Autentica un utente
     */
    public boolean authenticateUser(String username, String password) {
        User user = getUser(username);
        return user != null && user.verifyPassword(password);
    }

    /**
     * Effettua il login di un utente
     */
    public boolean loginUser(String username) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }

        // Controlla se è già loggato
        if (loggedInUsers.contains(username)) {
            return false;
        }

        // Effettua il login
        user.login();
        loggedInUsers.add(username);

        System.out.println("Login effettuato per: " + username);
        return true;
    }

    /**
     * Effettua il logout di un utente
     */
    public boolean logoutUser(String username) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }

        // Effettua il logout
        user.logout();
        loggedInUsers.remove(username);

        System.out.println("Logout effettuato per: " + username);
        return true;
    }

    /**
     * Verifica se un utente è loggato
     */
    public boolean isUserLoggedIn(String username) {
        return username != null && loggedInUsers.contains(username);
    }

    /**
     * Aggiorna l'attività di un utente
     */
    public void updateUserActivity(String username) {
        User user = getUser(username);
        if (user != null && isUserLoggedIn(username)) {
            user.updateActivity();
        }
    }

    /**
     * Aggiorna la password di un utente
     */
    public int updatePassword(String username, String oldPassword, String newPassword) {
        User user = getUser(username);

        // Controlla se l'utente esiste
        if (user == null) {
            return Constants.RESPONSE_UPDATE_USERNAME_PASSWORD_MISMATCH;
        }

        // Controlla se l'utente è attualmente loggato
        if (isUserLoggedIn(username)) {
            return Constants.RESPONSE_UPDATE_USER_LOGGED_IN;
        }

        // Valida la nuova password
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Constants.RESPONSE_UPDATE_INVALID_NEW_PASSWORD;
        }

        // Controlla se la nuova password è uguale alla vecchia
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            return Constants.RESPONSE_UPDATE_SAME_PASSWORD;
        }

        // Verifica la password attuale e aggiorna
        if (user.changePassword(oldPassword, newPassword)) {
            System.out.println("Password aggiornata per: " + username);
            return Constants.RESPONSE_OK;
        } else {
            return Constants.RESPONSE_UPDATE_USERNAME_PASSWORD_MISMATCH;
        }
    }

    /**
     * Ottiene tutti gli utenti registrati
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(registeredUsers.values());
    }

    /**
     * Ottiene tutti gli utenti loggati
     */
    public Set<String> getLoggedInUsers() {
        return new HashSet<>(loggedInUsers);
    }

    /**
     * Forza il logout di utenti inattivi
     */
    public void forceLogoutInactiveUsers() {
        Set<String> usersToLogout = new HashSet<>();

        for (String username : loggedInUsers) {
            User user = getUser(username);
            if (user != null && user.isInactive(Constants.USER_INACTIVITY_TIMEOUT)) {
                usersToLogout.add(username);
            }
        }

        for (String username : usersToLogout) {
            logoutUser(username);
            System.out.println("Logout automatico per inattività: " + username);
        }

        if (!usersToLogout.isEmpty()) {
            System.out.println("Disconnessi " + usersToLogout.size() + " utenti per inattività");
        }
    }

    /**
     * Avvia il controllo periodico per l'inattività
     */
    private void startInactivityCheck() {
        scheduler.scheduleAtFixedRate(
                this::forceLogoutInactiveUsers,
                60, // Primo controllo dopo 1 minuto
                60, // Poi ogni minuto
                TimeUnit.SECONDS
        );

        System.out.println("Controllo inattività utenti avviato (ogni 60 secondi)");
    }

    /**
     * Ferma il UserManager e libera le risorse
     */
    public void shutdown() {
        // Salva tutti i dati prima di chiudere
        saveUsers();

        // Forza logout di tutti gli utenti
        for (String username : new HashSet<>(loggedInUsers)) {
            logoutUser(username);
        }

        // Ferma lo scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        System.out.println("UserManager terminato");
    }

    /**
     * Ottiene statistiche degli utenti
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegistered", registeredUsers.size());
        stats.put("currentlyLoggedIn", loggedInUsers.size());
        stats.put("loggedInUsers", new ArrayList<>(loggedInUsers));
        return stats;
    }

}
