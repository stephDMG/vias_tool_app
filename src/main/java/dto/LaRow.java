// LaRow.java
package dto;

/**
 * Schlanker DTO für LU_ALLE (nur Join- und Anzeige-/Statistikfelder).
 */
public record LaRow(
        long vPointer,        // LA.VPointer
        String luVsnMakler,   // LA.LU_VSN_Makler
        String statCode1,     // LA.LU_STATISTIK_CODE1
        String statCode2,
        String statCode3,
        String statCode4,
        String statCode5,
        String statCode6
) {
}
