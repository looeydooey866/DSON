public class Dog {
    public String name;
    public boolean isRotating;
    @Rename(name = "preferredDogTreat")
    public Food food;
    public Occupation[] occupations;

    public Dog(String name, boolean isRotating, Food food, Occupation[] occupations){
        this.name = name;
        this.isRotating = isRotating;
        this.food = food;
        this.occupations = occupations;
    }
}
