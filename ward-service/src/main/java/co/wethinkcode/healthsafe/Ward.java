package co.wethinkcode.healthsafe;

public class Ward {

    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;

    public Ward() {
    }

    public String getWardId() {
        return wardId;
    }

    public String getWing() {
        return wing;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getBedsAvailable() {
        return bedsAvailable;
    }
}
