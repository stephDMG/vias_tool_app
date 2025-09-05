// LmpRow.java
package dto;

/**
 * Schlanker DTO für LU_MASKEP (nur Join + Name).
 */
public record LmpRow(
        long pPointer,   // LMP.PPointer
        String luNam     // LMP.LU_NAM
) {
}
