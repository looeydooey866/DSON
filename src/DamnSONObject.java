import java.lang.reflect.Field;
import java.util.*;

//hasnext can be replaced by length of json and just how many times u advanced, optimization for later
public class DamnSONObject {
    private Object o;
    private Class<?> c;
    private List<Field> fields;
    private Scanner parser;
    private Map<String,Field> mp;

    public DamnSONObject(Object o){
        this.o = o;
        c = o.getClass();
        fields = new ArrayList<>();
        mp = new HashMap<>();
        for (Field f : c.getFields()){
            if (f.isAnnotationPresent(DoNotSerialize.class))
                continue;
            fields.add(f);
            String name = f.getName().toLowerCase();
            if (f.isAnnotationPresent(Rename.class)){
                name = f.getAnnotation(Rename.class).name();
            }
            mp.put(name, f);
        }
    }

    private static void removeJunk(String s){
        StringBuilder result = new StringBuilder();
        boolean str = false;
        for (int i=0;i<s.length();i++){
            if (s.charAt(i) == '"'){
                str ^= true;
            }
            else if (str && s.charAt(i) == ' ');
            else if (s.charAt(i) != '\n'){
                result.append(s.charAt(i));
            }
        }
        s = result.toString();
    }

    public void parse(String query) throws DamnSONException{
        removeJunk(query);
        System.out.println(query);
        parser = new Scanner(query);
        parser.useDelimiter("");
        parseJSON();
        //this means that there is no precedence based on
        //the datatype e.g. set vs array, simply use type given from o.
        //meaning that we can rdp but by referring to o's typeage instead of raw op precedence
        //but what if there are nested objects? user will instantiate first right? we cannot assume def. constructor anwyay
    }

    private void parseJSON() throws DamnSONException{
        expect('{');
        if (peekChar() == '}')
            return;
        do{
            parseField();
        } while (peekChar() != ',');//could also be a check for }
        expect('}');
    }

    private void parseField() throws DamnSONException{
        StringBuilder field = new StringBuilder();
        do{
            field.append(parser.next());
        } while(peekChar() != ':');
        expect(':');
        Field f = mp.get(field.toString());
        try {
            Object ob = f.get(o);
            if (ob instanceof Integer){
                f.set(ob, parseInt());
            }
            else if (ob instanceof Double){
                f.set(ob, parseDouble());
            }
            else if (ob instanceof Float){
                f.set(ob, parseFloat());
            }
            else if (ob instanceof Boolean){
                f.set(ob, parseBoolean());
            }
            else if (ob instanceof String){
                f.set(ob, parseString());
            }
            else if (ob instanceof Character){
                f.set(ob, parseChar());
            }
            else if (f.getClass().isArray()){
                //start with [, and then end it off when you find ]
                //if you encounter a nested object, just do a damnson object and repeat
                
            }
            else if (ob instanceof List<?> ls){

            }
            else if (ob instanceof Set<?> st){
                //TODO
            }
            else if (ob instanceof Map<?,?> mp){
                //TODO
            }
        }
        catch(IllegalAccessException e){
            System.err.println("This shouldn't happen");
            e.printStackTrace();
        }
    }

    private int parseInt(){
        int res = 0;
        char nx = peekChar();
        while (nx >= '0' && nx <= '9'){
            res += nx - '0';
            res *= 10;
            parser.next();
            nx = peekChar();
        }
        return res;
    }

    private double parseDouble(){
        StringBuilder result = new StringBuilder();
        char nx = peekChar();
        while ((nx >= '0' && nx <= '9') || nx == '.'){
            result.append(nx);
            parser.next();
            nx = peekChar();
        }
        //two . -> double trouble (get it) fix later
        return Double.parseDouble(result.toString());
    }

    private float parseFloat(){
        StringBuilder result = new StringBuilder();
        char nx = peekChar();
        while ((nx >= '0' && nx <= '9') || nx == '.'){
            result.append(nx);
            parser.next();
            nx = peekChar();
        }
        //two . -> double trouble (get it) fix later
        return Float.parseFloat(result.toString());
    }

    private boolean parseBoolean(){
        //so for this one we need to accept all cases
        StringBuilder result = new StringBuilder();
        //basically while the next character is alhphabetic
        //why? if its not then won't we read the boolean wrongly anyway?
        char nx = Character.toLowerCase(peekChar());
        while (nx >= 'a' && nx <= 'z'){
            result.append(nx);
            parser.next();
            nx = Character.toLowerCase(peekChar());
        }
        //good luck bro i believe in you
        return Boolean.parseBoolean(result.toString());
    }

    private String parseString() throws DamnSONException{
        //I think you're starting to see a pattern here... a *builder* pattern...
        StringBuilder result = new StringBuilder();
        expect('"');
        while (peekChar() != '"'){
            result.append(parser.next());
        }
        expect('"');
        return result.toString();
    }

    private char parseChar() throws DamnSONException {
        expect('\'');
        char result = peekChar();
        parser.next();
        expect('\'');
        return result;
    }

    private Character peekChar(){
        parser.hasNext(".*");
        return parser.match().group(0).charAt(0);
    }

    private void expect(String s) throws DamnSONException{
        for (int i=0;i<s.length();i++)
            expect(s.charAt(i));
    }

    private void expect(char c) throws DamnSONException{
        if (!parser.hasNext()) {
            throw new DamnSONException();
        }
        String x = parser.next();
        if (x.charAt(0) != c)
            throw new DamnSONException();
    }
}
