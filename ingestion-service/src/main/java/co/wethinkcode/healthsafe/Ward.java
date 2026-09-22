package co.wethinkcode.healthsafe;

public class Ward {
    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;

    public Ward(String wardId, String wing, String department, Integer bedsAvailable) {
        this.wardId = wardId;
        this.wing = wing;
        this.department = department;
        this.bedsAvailable = bedsAvailable;
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
