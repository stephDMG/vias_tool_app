package gui.controller.utils.format;

public class FormatterField {
    private String name;
    private String type; // z.B. "MONEY" oder "DATE"

    public FormatterField() {
    }

    public FormatterField(String name, String type) {
        this.name = name;
        this.type = type;
    }

    // Konstruktor, Getter und Setter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}