public class Occupation {
    public String company;
    public int revenue;
    public String position;

    private int firedWorkers;
    @DoNotSerialize
    public int[] holidays;

    public Occupation(String company, int revenue, String position, int[] holidays){
        this.company = company;
        this.revenue = revenue;
        this.position = position;
        this.holidays = holidays ;
    }
}
