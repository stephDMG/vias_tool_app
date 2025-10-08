package service.rbac;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.util.*;

/**
 * Lädt und prüft Benutzerberechtigungen aus der Ressource
 * /config/access-config.json. Bietet eine einfache API, um festzustellen,
 * ob ein Benutzer eine bestimmte Berechtigung besitzt.
 */
public class AccessControlService {
    private final Map<String, UserConfig> userPermissions;

    public AccessControlService() {
        this.userPermissions = loadUserConfig();
    }

    private Map<String, UserConfig> loadUserConfig() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config/access-config.json")))) {
            Gson gson = new Gson();
            UserPermissions permissions = gson.fromJson(reader, UserPermissions.class);


            Map<String, UserConfig> norm = new HashMap<>();
            for (Map.Entry<String, UserConfig> e : permissions.getUsers().entrySet()) {
                norm.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
            }
            return norm;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Extrait le username sans domaine et en minuscule.
     */
    private String normalizeUser(String username) {
        if (username == null) return "";
        String u = username.trim();
        int i = Math.max(u.lastIndexOf('\\'), u.lastIndexOf('/')); // DOMAINuser ou DOMAIN/user
        if (i >= 0 && i + 1 < u.length()) u = u.substring(i + 1);
        return u.toLowerCase(Locale.ROOT);
    }

    /**
     * Prüft, ob der angegebene Benutzer die gegebene Berechtigung besitzt.
     *
     * @param username   Windows-/Anwendungsbenutzername
     * @param permission Berechtigungs-Schlüssel (z. B. "op-status" oder "all")
     * @return true, wenn vorhanden; sonst false
     */
    public boolean hasPermission(String username, String permission) {
        String key = normalizeUser(username);
        UserConfig user = userPermissions.get(key);
        if (user == null) return false;

        return user.getPermissions().contains("all") || user.getPermissions().contains(permission);
    }

    // Classes pour la désérialisation du JSON
    static class UserPermissions {
        Map<String, UserConfig> users;

        public Map<String, UserConfig> getUsers() {
            return users;
        }
    }

    static class UserConfig {
        String role;
        List<String> permissions;

        public String getRole() {
            return role;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }
}