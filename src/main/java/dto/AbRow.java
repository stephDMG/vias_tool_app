package dto;

// Deutsche Kommentare, wie gewünscht.

/**
 * Schlanker DTO für ABRECHNUNG mit nur benötigten Spalten.
 * Ziel: minimale RAM-Nutzung, primitive Typen wo möglich.
 */
public record AbRow(
        long aPointer,      // A.APointer
        long vPointer,      // A.VPointer (Join zu LU_ALLE)
        long pPointer,      // A.PPointer (Join zu LU_MASKEP)

        String luVmt,       // A.LU_VMT
        String luRnr,       // A.LU_RNR
        String luRnrMakler, // A.LU_RNR_Makler
        String luRnrR,      // A.LU_RNR_R
        String luVsn,       // A.LU_VSN
        String luZj,        // A.LU_ZJ (Periodenbezug / Jahr)
        String luRdt,       // A.LU_RDT
        String luBdt,       // A.LU_BDT
        String luFlg,       // A.LU_FLG
        String luWaehrung,  // A.LU_Waehrung
        String luVstld,     // A.LU_VSTLD
        String luSdWart,    // A.LU_SD_WART
        String luInk,       // A.LU_INK
        String luAbw,       // A.LU_ABW
        String luTes,       // A.LU_TES (für Grundfilter: 'SO','SOT','GR')

        double luNet100,    // A.LU_NET_100
        double luVst,       // A.LU_VST
        double luVstBetrag, // A.LU_VSTBetrag
        double luPraemie,   // A.LU_Praemie
        double luObt,       // A.LU_OBT
        String luSpakz,     // A.LU_SPAKZ (MAX nach Lexikon? fachlich prüfen)
        double luNet,       // A.LU_NET
        double luWProvision,// A.LU_WProvision
        double luRestbetrag,// A.LU_Restbetrag (nicht in DB-Filter; Filter später in RAM)
        String luMa1,       // A.LU_MA1
        String luMa2,       // A.LU_MA2
        String luMahnBemerkung // A.LU_MAHN_Bemerkung
) {
}
