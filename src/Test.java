public class Test {
    public static void main(String[] args) {
        try {
            Dog d = new Dog("Jimbob", true, new Food(
                    "Pizza", true, 9.81
            ), new Occupation[]{
                    new Occupation("Google", 1000000, "Main Cheerleader", new int[]{}),
                    new Occupation("McDonalds", 10, "Lead Software Engineer", new int[]{0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340, 350, 360})
            });
            String json = DamnSON.serialize(d);
            DamnSON.prettyPrint(json);
        }
        catch (DamnSONException d){
            System.out.println("womp womp");
        }
    }
}
