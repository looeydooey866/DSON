import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

//TODO watch out for " in strings
public class DamnSON {
    public static int recLevel = 0;
    public static String serialize(Object o) throws DamnSONException{
        assert recLevel <= 500;
        StringBuilder result = new StringBuilder();
        result.append("{");
        try {
            Class<?> c = o.getClass();
            Field[] i = c.getDeclaredFields();
            for (Field x : i){
                if (!Modifier.isPublic(x.getModifiers()))
                    continue;
                if (x.isAnnotationPresent(DoNotSerialize.class))
                    continue;
                String name = x.getName().toLowerCase();
                if (x.isAnnotationPresent(Rename.class)){
                    name = x.getAnnotation(Rename.class).value();
                }
                String value = getValue(x.get(o));
                result.append(name).append(":").append(value).append(",");
            }
            result.deleteCharAt(result.length()-1);
        }
        catch(Exception e){
            e.printStackTrace();
            throw new DamnSONException();
        }
        result.append("}");
        return result.toString();
    }

    public static String getValue(Object o) throws DamnSONException {
        Class<?>[] rawTrivials = {Integer.class, Double.class, Float.class, Boolean.class};
        if (Arrays.stream(rawTrivials).anyMatch(x -> x.isInstance(o))) {
            return o.toString();
        } else if (o instanceof String) {
            return String.format("\"%s\"", o);
        } else if (o instanceof Character) {
            return String.format("'%s'", o);
        } else if (o.getClass().isArray()) {
            StringBuilder result = new StringBuilder();
            result.append("[");
            int up = 0, down = Array.getLength(o);
            while (down > 0) {
                result.append(getValue(Array.get(o, up)));
                if (down != 1)
                    result.append(",");
                else
                    result.append("]");
                up++;
                down--;
            }
            return result.toString();
        } else if (o instanceof List<?> ls){
            return getValue(ls.toArray());
        }else if (o instanceof Set<?> s) {
            return getValue(s.toArray());
        } else if (o instanceof Map<?, ?> mp) {
            StringBuilder result = new StringBuilder();
            result.append('[');
            AtomicInteger down = new AtomicInteger(mp.size());
            mp.forEach((i, j) -> {
                try {
                    result.append('{').append("key:").append(getValue(i)).append(',').append("value:").append(getValue(j)).append('}');
                    if (down.getAndDecrement() != 1){
                        result.append(',');
                    }
                } catch (DamnSONException e) {
                    throw new RuntimeException();
                }
            });
            result.append(']');
            return result.toString();
        } else {
            return serialize(o);
        }
    }

    public static void prettyPrint(String json){
        StringBuilder result = new StringBuilder();
        int tl = 0;
        Stack<Character> st = new Stack<>();
        for (int i=0;i<json.length();i++){
            if (json.charAt(i) == '['){
                st.push('[');
            }
            else if (json.charAt(i) == ']'){
                st.pop();
            }
            if (json.charAt(i) == '}'){
                st.pop();
                result.append('\n').append(getTab(--tl));
            }
            result.append(json.charAt(i));
            if (json.charAt(i) == '{'){
                st.push('{');
                result.append('\n').append(getTab(++tl));
            }
            if (json.charAt(i) == ',' && st.peek() == '{'){
                result.append('\n').append(getTab(tl));
            }
            if (json.charAt(i) == ':'){
                result.append(' ');
            }
        }
        System.out.println(result);
    }

    private static String getTab(int i){
        return " ".repeat(i*4);
    }

    public static DamnSONObject get(Object o){
        return new DamnSONObject(o);
    }

    //hasnext can be replaced by length of json and just how many times u advanced, optimization for later

    public static class DamnSONException extends Exception{
        //well not that much to do here.
    }
}

//regarding the serialization/deserialization steps of maps/sets
//those types are typically things you need for access at runtime, their intrinsic
//form is more like array and object, but you need the sorting and quick lookup provided
//by these datatypes, so basically storing sets as arrays and maps as entries would be a
//better solution to reflect the "data passing" nature of JSON, as that's its primary use anyway

class DamnSONObject {
    private Object object;
    private Class<?> c;
    private List<Field> fields;
    private Scanner parser;
    private Map<String,Field> mp;

    public DamnSONObject(Object o){
        this.object = o;
        c = o.getClass();
        fields = new ArrayList<>();
        mp = new HashMap<>();
        for (Field f : c.getDeclaredFields()){
            if (f.isAnnotationPresent(DoNotSerialize.class))
                continue;
            fields.add(f);
            String name = f.getName().toLowerCase();
            if (f.isAnnotationPresent(Rename.class)){
                name = f.getAnnotation(Rename.class).value();
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

    public void parse(String query) throws DamnSON.DamnSONException {
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

    private void parseJSON() throws DamnSON.DamnSONException {
        expect('{');
        if (peekChar() == '}')
            return;
        do{
            parseField();
        } while (peekChar() != ',');//could also be a check for }
        expect('}');
    }

    private void parseField() throws DamnSON.DamnSONException {
        StringBuilder field = new StringBuilder();
        do{
            field.append(parser.next());
        } while(peekChar() != ':');
        expect(':');
        System.err.println("Found the field to be : " + field.toString());
        Field f = mp.get(field.toString());
        Class<?> clazz = f.getType();
        try {
            f.set(object, parseObject(clazz));
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private Object parseObject(Class<?> clazz) throws DamnSON.DamnSONException {
        if (isPrimitive(getWrapper(clazz))){
            return getPrimitive(clazz);
        }
        else if (clazz.isArray()){
            expect('[');
            Class<?> arclaz = getWrapper(clazz.getComponentType());
            List<Object> ls = new ArrayList<>();
            if (isPrimitive(arclaz)){
                while (peekChar() != ']'){
                    ls.add(getPrimitive(arclaz));
                    if (peekChar() == ',')
                        parser.next();
                }
            }
            else {
                while (peekChar() != ']'){
                    ls.add(parseObject(arclaz));
                    if (peekChar() == ','){
                        parser.next();
                    }
                }
            }
            expect(']');
            Object ar = Array.newInstance(arclaz,ls.size());
            for (int i=0;i<ls.size();i++){
                Array.set(ar, i, ls.get(i));
            }
            return ar;
        }
        else if (clazz == List.class){
            expect('[');
            //TODO fix this part since component type is not for list btw
            Class<?> arclaz = getWrapper(clazz.getComponentType());
            System.err.println(arclaz.toString());
            List<Object> ls = new ArrayList<>();
            if (isPrimitive(arclaz)){
                while (peekChar() != ']'){
                    ls.add(getPrimitive(arclaz));
                    if (peekChar() == ',')
                        parser.next();
                }
            }
            else {
                while (peekChar() != ']'){
                    ls.add(parseObject(arclaz));
                    if (peekChar() == ','){
                        parser.next();
                    }
                }
            }
            expect(']');
            return ls;
        }
        else if (clazz == Set.class){
            expect('[');
            Class<?> arclaz = getWrapper(clazz.getComponentType());
            Set<Object> ss = new HashSet<>();
            if (isPrimitive(arclaz)){
                while (peekChar() != ']'){
                    ss.add(getPrimitive(arclaz));
                    if (peekChar() == ',')
                        parser.next();
                }
            }
            else {
                while (peekChar() != ']'){
                    ss.add(parseObject(arclaz));
                    if (peekChar() == ','){
                        parser.next();
                    }
                }
            }
            expect(']');
            return ss;
        }
        else if (clazz == Map.class){
            //key, valeu pair, should we make a entry class
        }
        else {
            //this is hard, we have to try and get the object then
            //and  assume the user did not null it in the start
            //is that how?
            return null;
        }
        return null;
    }

    private int parseInt(){
        int res = 0;
        char nx = peekChar();
        System.err.println(nx);
        while (nx >= '0' && nx <= '9'){
            res *= 10;
            res += nx - '0';
            parser.next();
            nx = peekChar();
            System.err.println(nx);
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

    private String parseString() throws DamnSON.DamnSONException {
        //I think you're starting to see a pattern here... a *builder* pattern...
        StringBuilder result = new StringBuilder();
        expect('"');
        while (peekChar() != '"'){
            result.append(parser.next());
        }
        expect('"');
        return result.toString();
    }

    private char parseChar() throws DamnSON.DamnSONException {
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

    private void expect(String s) throws DamnSON.DamnSONException {
        for (int i=0;i<s.length();i++)
            expect(s.charAt(i));
    }

    private void expect(char c) throws DamnSON.DamnSONException {
        if (!parser.hasNext()) {
            throw new DamnSON.DamnSONException();
        }
        String x = parser.next();
        if (x.charAt(0) != c)
            throw new DamnSON.DamnSONException();
    }

    private Class<?> getWrapper(Class<?> clazz){
        if (clazz == int.class){
            return Integer.class;
        }
        if (clazz == boolean.class){
            return Boolean.class;
        }
        if (clazz == float.class){
            return Float.class;
        }
        if (clazz == double.class){
            return Double.class;
        }
        if (clazz == char.class){
            return Character.class;
        }
        return clazz;
    }

    private boolean isPrimitive(Class<?> clazz){
        Class<?>[] classes = new Class<?>[]{Integer.class,Boolean.class,Float.class,Double.class,String.class,Character.class};
        return Arrays.stream(classes).anyMatch(i->clazz==i);
    }

    private Object getPrimitive(Class<?> clazz) throws DamnSON.DamnSONException {
        if (clazz == Integer.class){
            return parseInt();
        }
        if (clazz == Double.class){
            return parseDouble();
        }
        if (clazz == Float.class){
            return parseFloat();
        }
        if (clazz == String.class){
            return parseString();
        }
        if (clazz == Character.class){
            return parseChar();
        }
        if (clazz == Boolean.class){
            return parseBoolean();
        }
        return null;
    }

    public static void main(String[] args){

        DamnSONObject o = new DamnSONObject("123}");
        o.parser = new Scanner("123}");
        o.parser.useDelimiter("");

        System.out.println(o.parseInt());
    }
}