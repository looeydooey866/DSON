import java.util.Map;
import java.util.Set;

public class Dog {
    public String name;
    public boolean isRotating;
    @Rename("preferredDogTreat")
    public Food food;
    public Occupation[] occupations;
    public Set<String> likedDays;
    public Map<Food, Integer> eatFrequency;

    public Dog(String name, boolean isRotating, Food food, Occupation[] occupations, Set<String> likedDays, Map<Food,Integer> eatFrequency){
        this.name = name;
        this.isRotating = isRotating;
        this.food = food;
        this.occupations = occupations;
        this.likedDays = likedDays;
        this.eatFrequency= eatFrequency;
    }
}
