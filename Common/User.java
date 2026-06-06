package Common;

public class User {
    private String username;
    private String password;
    private boolean isLoggedIn;
    private long lastActivity;  // timestamp dell'ultima attività (per timeout)
    private long registrationTimestamp;

    // Costruttore vuoto per JSON parsing
    public User() {}

    // Costruttore per nuova registrazione
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.isLoggedIn = false;
        this.lastActivity = 0;
        this.registrationTimestamp = System.currentTimeMillis() / 1000;
    }

    // Getter e Setter
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.isLoggedIn = loggedIn; }

    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }

    public long getRegistrationTimestamp() { return registrationTimestamp; }
    public void setRegistrationTimestamp(long registrationTimestamp) { this.registrationTimestamp = registrationTimestamp; }

    // Metodi di utilità

    /**
     * Verifica se la password fornita corrisponde a quella dell'utente
     */
    public boolean verifyPassword(String inputPassword) {
        return this.password != null && this.password.equals(inputPassword);
    }

    /**
     * Effettua il login dell'utente aggiornando timestamp
     */
    public void login() {
        this.isLoggedIn = true;
        this.lastActivity = System.currentTimeMillis() / 1000;
    }

    /**
     * Effettua il logout dell'utente
     */
    public void logout() {
        this.isLoggedIn = false;
        this.lastActivity = 0;
    }

    /**
     * Aggiorna il timestamp dell'ultima attività
     */
    public void updateActivity() {
        if (this.isLoggedIn) {
            this.lastActivity = System.currentTimeMillis() / 1000;
        }
    }

    /**
     * Verifica se l'utente è inattivo da troppo tempo
     * @param timeoutSeconds timeout in secondi
     * @return true se l'utente è inattivo da più di timeoutSeconds
     */
    public boolean isInactive(long timeoutSeconds) {
        if (!this.isLoggedIn) return false;

        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - this.lastActivity) > timeoutSeconds;
    }

    /**
     * Cambia la password dell'utente
     * @param oldPassword password attuale
     * @param newPassword nuova password
     * @return true se il cambio è avvenuto con successo
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        // Verifica password attuale
        if (!verifyPassword(oldPassword)) {
            return false;
        }

        // Verifica che la nuova password sia diversa
        if (oldPassword.equals(newPassword)) {
            return false;
        }

        // Verifica che la nuova password non sia vuota
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }

        this.password = newPassword;
        return true;
    }

    /**
     * Validazione per nuova registrazione
     */
    public static boolean isValidForRegistration(String username, String password) {
        return username != null && !username.trim().isEmpty() &&
                password != null && !password.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("User{username='%s', isLoggedIn=%s, lastActivity=%d}",
                username, isLoggedIn, lastActivity);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return username != null ? username.equals(user.username) : user.username == null;
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}
