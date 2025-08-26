package model.ai.planning;

import model.ai.AiColumnSpec;
import model.ai.AiReportTemplate;
import model.ai.ir.*; // Importiert alle unsere neuen IR-Klassen

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Baut aus dem IR einen SQL-Plan:
 * - {COLUMNS} werden aus AiReportTemplate gemappt (Exclude + Order-First).
 * - {CONDITIONS} enthält dynamische WHERE-Bedingungen aus den Predicates.
 * - Parameter werden als PreparedStatement-Parameter gesammelt.
 */
public class CoverPlanner {

    private final AiReportTemplate template;
    private final Map<String, AiColumnSpec> columnLibrary;

    private String parseAndFormatDate(String dateStr){
        DateTimeFormatter[] inputFormatters = {
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("d.M.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
        };
        // Das DB-Format
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        for (DateTimeFormatter formatter : inputFormatters) {
            try {
                // Versuche, das Datum zu parsen
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.format(dbFormatter);
            } catch (DateTimeParseException e) {
                // Ignoriere Fehler
            }
        }
        return null;
    }

    public CoverPlanner(AiReportTemplate template) {
        this.template = template;
        this.columnLibrary = new HashMap<>();
        // Erstelle eine durchsuchbare Bibliothek, die Keywords auf Specs abbildet
        template.getAvailableColumns().forEach((key, spec) -> {
            String canonical = key.toLowerCase(); // z.B. "vsn", "makler_nr", "land"

            // 1) kanonischer Key
            this.columnLibrary.put(canonical, spec);

            // 2) alle Keywords
            for (String kw : spec.getKeywords()) {
                this.columnLibrary.put(kw.toLowerCase(), spec);
            }
        });
    }

    public SqlPlan fromIR(QueryIR ir) {
        SqlPlan plan = new SqlPlan();


        List<AiColumnSpec> finalColumns = calculateFinalColumns(ir.projections);
        String columnsPart = finalColumns.stream()
                .map(AiColumnSpec::getSqlDefinition)
                .collect(Collectors.joining(",\n    "));
        plan.headers.addAll(finalColumns.stream().map(AiColumnSpec::getColumnAlias).toList());

        List<String> conditions = new ArrayList<>();
        for (FilterGroup group : ir.filters) {
            for (Predicate p : group.predicates) {
                if ("makler_nr".equalsIgnoreCase(p.field) && p.op == Op.EQUALS) {
                    conditions.add("RTRIM(LTRIM(LAL.LU_VMT)) = ?");
                    plan.params.add(p.value.toString().trim().toUpperCase());
                } else if ("makler name".equalsIgnoreCase(p.field) && p.op == Op.LIKE) {
                    conditions.add("UPPER(RTRIM(LTRIM(MAK.LU_NAM))) LIKE UPPER(?)");
                    plan.params.add(p.value.toString());
                } else if ("vertragsstatus".equalsIgnoreCase(p.field) && p.op == Op.EQUALS) {
                    conditions.add("RTRIM(LTRIM(LAL.LU_STA)) = ?");
                    plan.params.add(p.value.toString().trim().toUpperCase());
                } else if ("land".equalsIgnoreCase(p.field) && p.op == Op.EQUALS) {
                    conditions.add("(RTRIM(LTRIM(V05.LU_LANDNAME)) = ? OR RTRIM(LTRIM(LUM.LU_NAT)) = ?)");
                    plan.params.add(p.value.toString().trim().toUpperCase());
                    plan.params.add(p.value.toString().trim().toUpperCase());
                } else if ("beginn".equalsIgnoreCase(p.field) && p.op == Op.BETWEEN) { // <-- Korrektur: else if
                    if (p.value instanceof List<?> dates && dates.size() == 2) {
                        conditions.add("LAL.LU_BEG BETWEEN ? AND ?");
                        plan.params.add(parseAndFormatDate(dates.get(0).toString()));
                        plan.params.add(parseAndFormatDate(dates.get(1).toString()));
                    }
                } else if ("beginn".equalsIgnoreCase(p.field) && p.op == Op.GREATER_THAN_OR_EQUAL) {
                    conditions.add("LAL.LU_BEG >= ?");
                    plan.params.add(parseAndFormatDate(p.value.toString()));
                } else if ("beginn".equalsIgnoreCase(p.field) && p.op == Op.LESS_THAN_OR_EQUAL) {
                    conditions.add("LAL.LU_BEG <= ?");
                    plan.params.add(parseAndFormatDate(p.value.toString()));
                } else if ("ablauf".equalsIgnoreCase(p.field) && p.op == Op.BETWEEN) {
                    if (p.value instanceof List<?> dates && dates.size() == 2) {
                        conditions.add("LAL.LU_ABL BETWEEN ? AND ?");
                        plan.params.add(parseAndFormatDate(dates.get(0).toString()));
                        plan.params.add(parseAndFormatDate(dates.get(1).toString()));
                    }
                } else if ("ablauf".equalsIgnoreCase(p.field) && p.op == Op.GREATER_THAN_OR_EQUAL) {
                    conditions.add("LAL.LU_ABL >= ?");
                    plan.params.add(parseAndFormatDate(p.value.toString()));
                } else if ("ablauf".equalsIgnoreCase(p.field) && p.op == Op.LESS_THAN_OR_EQUAL) {
                    conditions.add("LAL.LU_ABL <= ?");
                    plan.params.add(parseAndFormatDate(p.value.toString()));
                }
            }
        }
        if (conditions.isEmpty()) {
            conditions.add("1=1");
        }
        String conditionsPart = String.join(" AND ", conditions);

        // 3. SQL aus Template zusammensetzen
        String sql = template.getSqlTemplate()
                .replace("{COLUMNS}", columnsPart)
                .replace("{CONDITIONS}", conditionsPart);

        // 4. ORDER BY-Klausel hinzufügen
        if (!ir.sortOrders.isEmpty()) {
            String orderByPart = ir.sortOrders.stream()
                    .map(s -> {
                        String resolvedField;
                        if ("firma".equalsIgnoreCase(s.field)) {
                            resolvedField = "LUM.LU_NAM";
                        } else if ("makler name".equalsIgnoreCase(s.field)) {
                            resolvedField = "MAK.LU_NAM";
                        } else if ("land".equalsIgnoreCase(s.field)) {
                            resolvedField = "V05.LU_LANDNAME";
                        } else {
                            resolvedField = s.field;
                        }
                        return resolvedField + " " + s.direction.name();
                    })
                    .collect(Collectors.joining(", "));
            sql += "\nORDER BY " + orderByPart;
        }
        // 5. LIMIT-Klausel hinzufügen
        if (ir.limit != null && ir.limit > 0) {
            sql += "\nLIMIT ?";
            plan.params.add(ir.limit);
        }

        plan.sql = sql;
        return plan;
    }





    // Korrektur: Logik für Whitelist- und Blacklist-Projektionen
    private List<AiColumnSpec> calculateFinalColumns(List<Projection> projections) {
        // Die Basis-Spalten aus der Vorlage
        List<AiColumnSpec> base = new ArrayList<>(template.getAvailableColumns().values());

        if (projections.isEmpty()) {
            return base; // Keine Angabe -> alle Spalten zurückgeben
        }

        // Prüfen, ob es explizit ausgeschlossene Spalten gibt
        boolean hasExclusions = projections.stream().anyMatch(p -> p.exclude);

        if (hasExclusions) {
            // Logik für Blacklist (bestehender Code)
            Set<AiColumnSpec> excludedSpecs = projections.stream()
                    .filter(p -> p.exclude)
                    .map(p -> columnLibrary.get(p.field))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<AiColumnSpec> result = new ArrayList<>();
            base.forEach(spec -> {
                if (!excludedSpecs.contains(spec)) {
                    result.add(spec);
                }
            });
            return result;
        } else {
            // NEU: Logik für Whitelist (nur explizit angeforderte Spalten)
            Map<String, AiColumnSpec> requestedSpecs = projections.stream()
                    .filter(p -> !p.exclude)
                    .map(p -> columnLibrary.get(p.field))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            AiColumnSpec::getSqlDefinition,
                            spec -> spec,
                            (a, b) -> a, // Bei Duplikaten das erste behalten
                            LinkedHashMap::new
                    ));

            // 'zuerst'-Spalten an den Anfang stellen
            List<AiColumnSpec> orderedSpecs = projections.stream()
                    .filter(p -> p.order == 0)
                    .map(p -> columnLibrary.get(p.field))
                    .filter(Objects::nonNull)
                    .toList();

            List<AiColumnSpec> result = new ArrayList<>(orderedSpecs);
            requestedSpecs.values().forEach(spec -> {
                if (!orderedSpecs.contains(spec)) {
                    result.add(spec);
                }
            });
            return result;
        }
    }
}