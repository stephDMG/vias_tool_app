package service.contract.rbac;

import service.rbac.AccessControlService;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * CoverAccessGuard
 * <p>
 * Diese Klasse kapselt die Zugriffskontrolle (RBAC) für den COVER-Bereich.
 * Sie delegiert an das zentrale {@link AccessControlService}, dessen API
 * die Signatur {@code hasPermission(username, permission)} besitzt.
 * <p>
 * Wichtig:
 * - Berechtigungsschlüssel gemäß access-config.json: "view", "export".
 * - Optional kann ein {@link Supplier} für den aktuellen Benutzer
 * übergeben werden, sodass Methoden ohne Username-Parameter nutzbar sind.
 */
public class CoverAccessGuard {

    private static final String PERM_VIEW = "view";
    private static final String PERM_EXPORT = "export";

    private final AccessControlService accessControlService;
    private final Supplier<String> currentUserSupplier; // kann null sein

    /**
     * Konstruktor ohne Current-User-Supplier.
     * In diesem Fall müssen die Methoden mit Username-Parameter verwendet werden.
     *
     * @param accessControlService zentrales RBAC.
     */
    public CoverAccessGuard(AccessControlService accessControlService) {
        this(accessControlService, null);
    }

    /**
     * Konstruktor mit optionalem Current-User-Supplier.
     * Erlaubt Aufrufe ohne expliziten Username.
     *
     * @param accessControlService zentrales RBAC.
     * @param currentUserSupplier  liefert den aktuellen Benutzer (kann null sein).
     */
    public CoverAccessGuard(AccessControlService accessControlService,
                            Supplier<String> currentUserSupplier) {
        this.accessControlService = Objects.requireNonNull(accessControlService, "accessControlService");
        this.currentUserSupplier = currentUserSupplier; // darf null sein
    }

    // ---------------------------------------------------------------------
    // Varianten MIT Username-Parameter (immer verfügbar)
    // ---------------------------------------------------------------------

    /**
     * Prüft, ob der angegebene Benutzer COVER-Daten ansehen darf.
     */
    public boolean canView(String username) {
        return accessControlService.hasPermission(username, PERM_VIEW);
    }

    /**
     * Prüft, ob der angegebene Benutzer COVER-Daten exportieren darf.
     */
    public boolean canExport(String username) {
        return accessControlService.hasPermission(username, PERM_EXPORT);
    }

    /**
     * Erzwingt "view" für den angegebenen Benutzer.
     */
    public void checkView(String username) {
        if (!canView(username)) {
            throw new SecurityException("Zugriff verweigert: fehlende Berechtigung '" + PERM_VIEW + "'.");
        }
    }

    /**
     * Erzwingt "export" für den angegebenen Benutzer.
     */
    public void checkExport(String username) {
        if (!canExport(username)) {
            throw new SecurityException("Zugriff verweigert: fehlende Berechtigung '" + PERM_EXPORT + "'.");
        }
    }

    // ---------------------------------------------------------------------
    // Komfort-Varianten OHNE Username-Parameter (nur wenn Supplier gesetzt)
    // ---------------------------------------------------------------------

    /**
     * Prüft "view" für den aktuellen Benutzer (erfordert currentUserSupplier).
     */
    public boolean canView() {
        ensureSupplier();
        return canView(currentUserSupplier.get());
    }

    /**
     * Prüft "export" für den aktuellen Benutzer (erfordert currentUserSupplier).
     */
    public boolean canExport() {
        ensureSupplier();
        return canExport(currentUserSupplier.get());
    }

    /**
     * Erzwingt "view" für den aktuellen Benutzer (erfordert currentUserSupplier).
     */
    public void checkView() {
        ensureSupplier();
        checkView(currentUserSupplier.get());
    }

    /**
     * Erzwingt "export" für den aktuellen Benutzer (erfordert currentUserSupplier).
     */
    public void checkExport() {
        ensureSupplier();
        checkExport(currentUserSupplier.get());
    }

    private void ensureSupplier() {
        if (currentUserSupplier == null) {
            throw new IllegalStateException(
                    "Kein Current-User-Supplier gesetzt. Verwende die Methoden mit Username-Parameter.");
        }
    }
}
