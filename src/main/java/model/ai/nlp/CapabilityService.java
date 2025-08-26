package model.ai.nlp;

public final class CapabilityService {
    public static String renderCapabilities() {
        return String.join("\n",
                "Ich kann für Sie folgende Abfragen durchführen:",
                "• Cover-Verträge filtern (Sparte LIKE '%COVER%' ist immer aktiv).",
                "• Filtern nach: Makler (ID), VSN, Status, Land, Beginn/Ablauf.",
                "• Spaltenauswahl steuern: z.B. '... außer land code, vsn'.",
                "• Spaltenreihenfolge festlegen: z.B. '... zuerst makler name, firma'.",
                "• Ergebnisanzahl begrenzen: z.B. '... limit 100'.",
                "",
                "BEISPIELE:",
                "1) Gib mir alle Verträge vom Makler 100120 außer land code",
                "2) Alle Cover zuerst VSN, Makler limit 200"
        );
    }
}