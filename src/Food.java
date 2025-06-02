public class Food {
    public String name;
    public boolean containsPineapples;
    public double rating;

    public Food(String name, boolean containsPineapples, double rating){
        assert rating >= 0.0 && rating <= 10.0;

        this.name = name;
        this.containsPineapples = containsPineapples;
        this.rating = rating;
    }
}
