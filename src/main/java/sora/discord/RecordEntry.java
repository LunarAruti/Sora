package sora.discord;

public class RecordEntry {

    String name;
    String issuer;
    String notes;
    String references;
    int points;
    int date;
    int caseId;



    // constructors
    // front end, only name and points are required
    public RecordEntry(String name, int points, String issuer) {
        this.name = name;
    }
    public RecordEntry(String name, int points, String issuer, String notes) {

    }
    public RecordEntry(String name, int points, String issuer, String notes, String references) {

    }
}
