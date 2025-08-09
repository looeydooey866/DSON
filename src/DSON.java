import TestSuite.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility class containing all DSON functionality.
 * @author MaximusHartanto
 */
public class DSON {
    public static void main(String[] args) throws DSONException {DSONObject.main(null);}
    /**
     * Serializes an object into standard-JSON format.
     * This function inspects the object's fields and collates it into a JSON string.
     * <br>
     * Do note that non-public fields and fields marked with {@code @DoNotSerialize} will not be included.
     * <br>
     * Do also note that fields mraked with the {@code Rename} annotation will be renamed to the user-provided value.
     * @implNote all fields will be lowercased by default. For example, if you have a field named "intList", it will be reflected as "intlist" in the JSON string.
     * @param o the object to be serialized into JSON.
     * @return the JSON-formatted object in a single-line string. Use the {@code prettyPrint()} function to print it properly formatted.
     * @throws DSONException if an exception is thrown, something has gone wrong during the serialization process.
     * @author MaximusHartanto
     */
    public static String serialize(Object o) throws DSONException{
        StringBuilder result = new StringBuilder();
        result.append("{");
        try {
            Class<?> objectClass = o.getClass();
            //Gets metadata of all the fields in the object, including its name
            int success = 0;
            Field[] fields = objectClass.getDeclaredFields();
            for (Field field : fields){
                //Bypassing privacy
                if (!field.trySetAccessible()){
                    continue;
                }
                //Do not parse a field that the user does not want parsed
                if (field.isAnnotationPresent(DoNotSerialize.class))
                    continue;
                //Get the field name and rename it if told to
                String fieldName = (
                        field.isAnnotationPresent(Rename.class)
                                ? field.getAnnotation(Rename.class).value()
                                : field.getName().toLowerCase()
                );
                //Parses the object the field contains
                String value = getValue(field.get(o));
                result.append(fieldName).append(":").append(value).append(",");
                success++;
            }
            //Removes the last comma
            //Why? It is not that easy to find out if the current field will be the last one, due to the annotations and publicity check
            //Sure it can be precalculated, but this is the most convenient way
            //Another approach is to add all the fields to an arraylist before printing to the stringbuilder
            if (success > 0) {
                result.deleteCharAt(result.length() - 1);
            }
        }
        catch(Exception e){
            e.printStackTrace();
            throw new DSONException();
        }
        result.append("}");
        return result.toString();
    }

    /**
     * Converts an object into JSON-Object form.
     * If the object's type is primitive, its absolute string value will be returned.
     * Otherwise, a JSON object will be created with its own set of field-value keys.
     * <br>
     * Note - {@code Arrays}, {@code Lists} and {@code Sets} will all be serialized into comma-separated []-array form, and {@code Maps} will be serialized into a list of {@code key: value} objects.
     * @param o the object of which the value is stored.
     * @return a string containing the object's value in valid JSON form.
     * @throws DSONException something has went wrong during the serialization process.
     * @author MaximusHartanto
     */
    public static String getValue(Object o) throws DSONException {
        //Check if primitive type
        Class<?>[] primitiveTypes = {Integer.class, Double.class, Float.class, Boolean.class};
        if (Arrays.stream(primitiveTypes).anyMatch(primitive -> primitive.isInstance(o))) {
            return o.toString();
        } else if (o instanceof String) {
            //Wrap around quotes
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
            //Yes, these two cases boil down to recursive calls. This is done for simplicity's sake
            return getValue(ls.toArray());
        } else if (o instanceof Set<?> s) {
            return getValue(s.toArray());
        } else if (o instanceof Map<?, ?> mp) {
            //Maps are lists of entry objects. See test number 5 for an example.
            StringBuilder result = new StringBuilder();
            result.append('[');
            //Don't ask
            AtomicInteger down = new AtomicInteger(mp.size());
            mp.forEach((i, j) -> {
                try {
                    //Ignore this gigantic shishkebab of code over here
                    result.append('{').append("key:").append(getValue(i)).append(',').append("value:").append(getValue(j)).append('}');
                    if (down.getAndDecrement() != 1){
                        result.append(',');
                    }
                } catch (DSONException e) {
                    throw new RuntimeException();
                }
            });
            result.append(']');
            return result.toString();
        } else {
            return serialize(o);
        }
    }

    /**
     * Pretty-prints a JSON string for pretty-viewing. This will print out the JSON string with properly formatted newlines and spaces.
     * <br><br>
     * @implNote  This function is still erroneous, as quote-checking has not been implemented yet.
     * @param json the JSON string to be pretty-printed.
     * @author MaximusHartanto
     */
    public static void prettyPrint(String json){
        StringBuilder result = new StringBuilder();
        int tl = 0;
        //Why? Commas should be newlined in JSON objects, but not in JSON lists. This stack helps us see if we are currently in a list or in a JSON object.
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

    /**
     * Returns spacing that emulates 4-width tabs i times.
     * @param i the amount of tabs to be emulated.
     * @return a string containing the whitespace of length i * 4.
     * @author MaximusHartanto
     */
    private static String getTab(int i){
        return " ".repeat(i*4);
    }

    /**
     * Constructs a new DSONObject from an object o. A DSONObject is used for deserializing JSON into Java objects.
     * @param o the object to be converted into a DSONObject.
     * @return a DSONObject.
     * @author MaximusHartanto
     */
    public static DSONObject get(Object o){
        return new DSONObject(o);
    }

    /**
     * Exceptions related to DSON serialization.
     * @author MaximusHartanto
     */
    public static class DSONException extends Exception{
    }

    /**
     * An object which contains JSON deserialization methods. DSON objects are constructed using DSON's {@code get()} function.
     * Fields which are non-private and fields that are marked with the {@code DoNotSerialize} annotation will not be deserialized.
     * Additionally, fields with the {@code Rename} annotation will accept a different field name from JSON.
     * @author MaximusHartanto
     */
    public static class DSONObject {
        private final Object object;
        private final Class<?> objectClass;
        private final List<Field> classFields = new ArrayList<>();
        private final Map<String,Field> fieldGetter = new HashMap<>();
        private final Map<Class<?>,Class<?>> typeGetter = new HashMap<>();
        private final Map<Class<?>,Class<?>[]> mapTypeGetter = new HashMap<>();
        private Scanner queryParser;

        /**
         * Constructs an empty DSON object. This constructor is intended for internal use.
         */
        private DSONObject(){
            object = null;
            objectClass = null;
        }

        /**
         * Constructs a DSON object from an object.
         * When the DSON object is parsed, the original object's fields will be updated with new values.
         * @param o the object to be parsed
         */
        private DSONObject(Object o){
            this.object = o;
            this.objectClass = o.getClass();
            for (Field field : this.objectClass.getDeclaredFields()){
                if (!field.trySetAccessible()){
                    continue;
                }
                //This will NPE if the pesky json attempts to access the field
                if (field.isAnnotationPresent(DoNotSerialize.class))
                    continue;
                classFields.add(field);
                String canonicalizedName = (
                    field.isAnnotationPresent(Rename.class)
                        ? field.getAnnotation(Rename.class).value()
                        : field.getName().toLowerCase()
                );
                fieldGetter.put(canonicalizedName, field);
            }
            //The performance diff when these two are inlined to the
            //loop above can also be examined btw

            //These two functions get metadata about list types (annoying type erasure)
            //E.g. if the field is a List<Integer> these two would get the Integer class out of it
            getContainerTypes();
            getMapTypes();
        }

        /**
         * Populates the typeGetter map with the inner types of {@code Lists} and {@code Sets}. The result would be something like: List<TestSuite.Apple> -> TestSuite.Apple.
         */
        private void getContainerTypes(){
            for (Field field : classFields){
                Class<?> fieldClass = field.getType();
                if (isList(fieldClass) || isSet(fieldClass)){
                    //Yea don't mind this...
                    ParameterizedType ptype = (ParameterizedType) field.getGenericType();
                    Class<?> genericClass = (Class<?>) ptype.getActualTypeArguments()[0];
                    typeGetter.put(fieldClass, genericClass);
                }
            }
        }
        /**
         * Populates the mapTypeGetter map with the inner key & value types of {@code Maps}. The result would be something like: Map<Integer, String> -> {Integer, String}.
         */
        private void getMapTypes(){
            for (Field field : classFields){
                Class<?> fieldClass = field.getType();
                if (isMap(fieldClass)){
                    ParameterizedType ptype = (ParameterizedType) field.getGenericType();
                    Type[] types = ptype.getActualTypeArguments();
                    Class<?> keyClass = (Class<?>) types[0];
                    Class<?> valueClass = (Class<?>) types[1];
                    mapTypeGetter.put(fieldClass, new Class<?>[]{keyClass,valueClass});
                }
            }
        }

        /**
         * Prepares a String for parsing by removing redundant whitespace. It is not trivial as Strings also contain whitespace.
         * @param s the string to be pre-formatted.
         * @return a new properly formatted String.
         */
        private static String fixFormat(String s){
            StringBuilder result = new StringBuilder();
            //To be real, the only case we need to handle is strings, since we need to keep all the whitespace within them
            //TODO create test for this
            boolean isInsideString = false;
            for (int i = 0; i < s.length(); i++){
                char cur = s.charAt(i);
                //If a string is passed in json, it will be \"
                //If a string is passed within json, it will be \\\"
                if (cur == '"' && (i == 0 || !(s.charAt(i-1) == '\\'))){
                    isInsideString ^= true;
                } else if (!isInsideString){
                    if (cur == ' ' || cur == '\n')
                        continue;
                }
                result.append(cur);
            }
            return result.toString();
        }

        /**
         * Deserializes a JSON string into this DSON object.
         * This populates the object's fields with data retrieved from JSON.
         * @implNote Do note that all object field names will be lowercased by default, the lowercase name will be used to search for fields within the JSON.
         * For example, if you have a field called theNumberThree, the entry with the name "thenumberthree: ..." will be associated with that field.
         * @param JSON the JSON string to be deserialized.
         * The string will be automatically formatted and no removal of whitespace/newlines is necessary.
         * @throws DSONException an error has occured during deserialization.
         */
        public void parse(String JSON) throws DSONException{
            setParser(JSON);
            parseJSON();
        }

        /**
         * Prepares the parser for parsing the query. This includes removing whitespace and setting the parser to scan one character at a time.
         * @param query the string to be parsed and prepared for the parser.
         */
        private void setParser(String query){
            String fixedQuery = fixFormat(query);
            queryParser = new Scanner(fixedQuery);
            //This lets us extract characters one at a time from the Scanner
            //Not very efficient however, since charAt(0) is necessary to get the character
            //In the future, a bufferedInputStream can be used instead for more efficiency
            queryParser.useDelimiter("");
        }

        /**
         * Overrides the query parser by setting it to another Scanner completely. For internal use only.
         * @param parser the Scanner to be used as the queryParser.
         */
        private void overrideParser(Scanner parser){
            this.queryParser = parser;
        }

        /**
         * Peeks one character ahead to see the next character in the Scanner (a bit hacky). If the scanner is currently reading "hello world", this will return 'h'.
         * @return the lookahead character in the Scanner.
         */
        private char peekOne(){
            //Has to be called every time since the parser has to re-match.
            queryParser.hasNext(".*");
            return queryParser.match().group(0).charAt(0);
        }

        /**
         * Checks if the parser has reached the end of the JSON string, and if there are no more characters to parse.
         * @return a boolean, {@code true} if there are no more characters to be parsed.
         */
        private boolean endOfLine(){
            return !queryParser.hasNext();
        }

        /**
         * Expects a character to be the next character in the JSON string. For example, if {@code expect(':')} is ran, the next character MUST be a colon or an exception will be thrown.
         * <br><br>
         * Do note that during the process, the character will be consumed at the same time. This is because consuming the character is inevitable in any case.
         * <br><br>
         * Do also note that this function checks if the parser has no more tokens to read.
         * @param c the character to be expected to be the next token.
         * @throws DSONException this means that the next character does not match with the intended one.
         */
        private void expect(char c) throws DSONException{
            if (endOfLine() || queryParser.next().charAt(0) != c)
                throw new DSONException();
        }

        /**
         * If the next character is equal to c, this function will consume the next character. Otherwise, nothing happens.
         * <br><br>
         * This function is helpful when some syntax can be in two forms. For example, fields can either have quote marks, e.g. {@code "key" : 35} or not, e.g. {@code key : 35}.
         * @param c the optional character
         */
        private void option(char c){
            if (peekOne() == c)
                queryParser.next();
        }

        /**
         * Advances one token ahead in the parser. Does not return anything.
         */
        private void advanceOne(){
            queryParser.next();
        }

        /**
         * Advances one token ahead and returns the token consumed by this function.
         * @return the character consumed by this function
         */
        private char nextChar(){
            return queryParser.next().charAt(0);
        }

        /**
         * Returns the next character parsed as a {@code String} rather than a character. This is helpful for some cases to remove
         * a charAt(0).
         * @return the next character as a String
         */
        private String nextCharAsString(){
            return queryParser.next();
        }

        /**
         * Parses an integer from the current point of the parser. This function reads digits until a non-digit character is encountered.
         * @return an integer value based on the value parsed.
         */
        private int parseInt(){
            int result = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                result = result * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            return result;
        }

        /**
         * Parses a double from the current point of the parser. This function can accept these decimal formats:
         * <br><br>
         * {@code 1. Whole numbers (e.g. 1)}
         * <br><br>
         * {@code 2. Decimals (e.g. 0.1, 0.123, .4)}
         * <br><br>
         * The function will stop parsing once a non-digit character is reached (after the first '.', of course)
         * @return the parsed double value.
         */
        private double parseDouble(){
            double whole = 0, decimal = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                whole = whole * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            if (next == '.'){
                advanceOne();
                next = peekOne();
                while (next >= '0' && next <= '9'){
                    decimal += (next - '0');
                    decimal /= 10;
                    advanceOne();
                    next = peekOne();
                }
            }
            return whole + decimal;
        }

        /**
         * Parses a float from the current point of the parser. This function can accept these decimal formats:
         * <br><br>
         * {@code 1. Whole numbers (e.g. 1)}
         * <br><br>
         * {@code 2. Decimals (e.g. 0.1, 0.123, .4f)}
         * <br><br>
         * The function will stop parsing once a non-digit character is reached (after the first '.', of course (and the optional f modifier))
         * @return the parsed float value.
         */
        private float parseFloat(){
            float whole = 0, decimal = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                whole = whole * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            if (next == '.'){
                advanceOne();
                next = peekOne();
                while (next >= '0' && next <= '9'){
                    decimal += (next - '0');
                    decimal /= 10;
                    advanceOne();
                    next = peekOne();
                }
            }
            option('f');
            return whole + decimal;
        }

        /**
         * Parses a boolean from the current parser position. Do note that Json is case-sensitive. This function will only accept 'true' and 'false' as boolean values, and they must be unwrapped in quotes.
         * @return the boolean value parsed.
         * @throws DSONException if the input format is invalid, an exception will be thrown.
         */
        private boolean parseBoolean() throws DSONException {
            StringBuilder result = new StringBuilder();
            char next = peekOne();
            while (next >= 'a' && next <= 'z'){
                result.append(next);
                advanceOne();
                next = peekOne();
            }
            String value = result.toString();
            if (value.equals("true")){
                return true;
            }
            if (value.equals("false")){
                return false;
            }
            throw new DSONException();
        }

        /**
         * Parses a string from the current parsing position. Strings should be wrapped in {@code \"quotes\"}.
         * @return the parsed String value.
         * @throws DSONException if the format is invalid, an exception will be thrown.
         */
        private String parseString() throws DSONException{
            StringBuilder result = new StringBuilder();
            expect('\"');
            while (peekOne() != '\"'){
                result.append(nextCharAsString());
            }
            expect('\"');
            return result.toString();
        }

        /**
         * Parses a singular character from the current parsing position.  Characters should be wrapped in {@code \'quotes\'}.
         * @return the parsed character value.
         * @throws DSONException if the format is invalid, an exception will be thrown.
         */
        private char parseChar() throws DSONException{
            expect('\'');
            char result = nextChar();
            expect('\'');
            return result;
        }

        /**
         * Checks if a given Class is one of a primitive class. Primitive classes include:
         * <br>
         * {@code ints},
         * {@code booleans},
         * {@code floats},
         * {@code doubles},
         * {@code chars} and
         * {@code Strings}.
         * @param checkClass the class to be checked.
         * @return a boolean - true if the class is primitive.
         */
        private boolean isPrimitive(Class<?> checkClass){
            return (checkClass.isPrimitive() || checkClass == String.class);
        }

        /**
         * Converts a primitive class to its Java wrapper class. The conversions are listed as:
         * <br>
         * {@code int -> Integer},
         * {@code boolean -> Boolean},
         * {@code float -> Float},
         * {@code double -> Double},
         * {@code char -> Character},
         * {@code String -> String},
         * {@code any other class -> itself}.
         * @param primitiveClass the class to be converted.
         * @return the class's Java wrapper class, if it exists.
         */
        private Class<?> primitiveToWrapper(Class<?> primitiveClass){
            if (primitiveClass == int.class){
                return Integer.class;
            }
            if (primitiveClass == boolean.class){
                return Boolean.class;
            }
            if (primitiveClass == float.class){
                return Float.class;
            }
            if (primitiveClass == double.class){
                return Double.class;
            }
            if (primitiveClass == char.class){
                return Character.class;
            }
            return primitiveClass;
        }

        /**
         * Converts a Java wrapper class to its associated primitive class. The conversions are listed as:
         * <br>
         * {@code Integer -> int},
         * {@code Boolean -> boolean},
         * {@code Float -> float},
         * {@code Double -> double},
         * {@code Character -> char},
         * {@code String -> String},
         * {@code any other class -> itself}.
         * @param wrapperClass the class to be converted.
         * @return the class's primitive class, if it exists.
         */
        private Class<?> wrapperToPrimitive(Class<?> wrapperClass){
            if (wrapperClass == Integer.class){
                return int.class;
            }
            if (wrapperClass == Boolean.class){
                return boolean.class;
            }
            if (wrapperClass == Float.class){
                return float.class;
            }
            if (wrapperClass == Double.class){
                return double.class;
            }
            if (wrapperClass == Character.class){
                return char.class;
            }
            return wrapperClass;
        }

        /**
         * Parses a primitive class using its associated parse function.
         * If the class is not a primitive, nothing will happen.
         * Additionally, this function checks for the primitive class's wrapper class and pure primitive classes is not supported.
         * @param primitiveClass the wrapper class to be parsed.
         * @return an Object with the parsed value.
         * @throws DSONException If something has gone wrong during the parsing process, an exception will be thrown.
         */
        private Object parsePrimitive(Class<?> primitiveClass) throws DSONException{
            if (primitiveClass == Integer.class){
                return parseInt();
            }
            if (primitiveClass == Double.class){
                return parseDouble();
            }
            if (primitiveClass == Float.class){
                return parseFloat();
            }
            if (primitiveClass == String.class){
                return parseString();
            }
            if (primitiveClass == Character.class){
                return parseChar();
            }
            if (primitiveClass == Boolean.class){
                return parseBoolean();
            }
            return null;
        }

        /**
         * Checks if a class is a Java array type, e.g. {@code int[] a}.
         * @param arrayClass the class to be checked.
         * @return a boolean - true if the given class is one of an Array type.
         */
        private boolean isTypicalArray(Class<?> arrayClass){
            return arrayClass.isArray();
        }

        /**
         * Parses an array in the current parsing position. This function expects an array starting and ending with square brackets, and separated with commas. An empty array is allowed.
         * @param arrayClass the class of the array to be parsed.
         * @return an Object, which is the parsed array value.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private Object parseTypicalArray(Class<?> arrayClass) throws DSONException {
            expect('[');
            Class<?> underlyingType = arrayClass.getComponentType();
            List<Object> objects = new ArrayList<>();
            char lookahead = peekOne();
            while (lookahead != ']'){
                objects.add(parseObject(underlyingType));
                lookahead = peekOne();
                if (lookahead != ']') {
                    expect(',');
                    lookahead = peekOne();
                }
            }
            expect(']');
            Object result = Array.newInstance(underlyingType, objects.size());
            for (int i = 0; i < objects.size(); i++)
                Array.set(result, i, objects.get(i));
            return result;
        }

        /**
         * Checks if a given class is one of a List class.
         * @param listClass the class to be checked.
         * @return a boolean - true if the class is one of a List class.
         */
        private boolean isList(Class<?> listClass){
            return listClass == List.class;
        }

        /**
         * Parses a list from the current parsing position. The type will be deduced from the class object. Square brackets and separator commas are expected during parsing.
         * @param listClass the class of the object to be parsed.
         * @return a List object, containing the values from the JSON.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private List<?> parseList(Class<?> listClass) throws DSONException {
            Class<?> underlyingClass = typeGetter.get(listClass);
            expect('[');
            char lookahead = peekOne();
            List<Object> result = new ArrayList<>();
            while (lookahead != ']'){
                Object element = parseObject(underlyingClass);
                result.add(element);
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    lookahead = peekOne();
                }
            }
            expect(']');
            return result;
        }

        /**
         * Checks if a class is one of a Set class.
         * @param setClass the class to be checked.
         * @return a boolean - true if the class is one of a Set class.
         */
        private boolean isSet(Class<?> setClass){
            return setClass == Set.class;
        }

        /**
         * Parses a set class from the current parsing position. Do note that sets have the same syntax as arrays, e.g. square brackets and commas. The parsed type will be deduced from the class object.
         * @param setClass the class of the object to be parsed.
         * @return a Set object, containing the parsed data from JSON.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private Set<?> parseSet(Class<?> setClass) throws DSONException{
            Class<?> underlyingClass = typeGetter.get(setClass);
            expect('[');
            char lookahead = peekOne();
            Set<Object> result = new HashSet<>();
            while (lookahead != ']'){
                Object element = parseObject(underlyingClass);
                result.add(element);
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    lookahead = peekOne();
                }
            }
            expect(']');
            return result;
        }

        /**
         * Checks if a given class is one of a Map class.
         * @param mapClass the class to be checked.
         * @return a boolean - true if the class is one of a Map class.
         */
        private boolean isMap(Class<?> mapClass){
            return mapClass == Map.class;
        }

        /**
         * Parses a Map from the current parsing position. Maps are lists of Entry objects, and entry objects contain a "key" field and a "value" field. The type will be automatically deduced from the class passed into the function.
         * @param mapClass the class of the map to be parsed. Type metadata will be deduced from here.
         * @return the Map object with data parsed from the JSON.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private Map<?,?> parseMap(Class<?> mapClass) throws DSONException{
            Class<?>[] mapArguments = mapTypeGetter.get(mapClass);
            Class<?> keyClass = mapArguments[0];
            Class<?> valueClass = mapArguments[1];
            expect('[');
            char lookahead = peekOne();
            Map<Object,Object> result = new HashMap<>();
            //Key and value can be interchangeable
            StringBuilder commonBuilder = new StringBuilder();
            //Now, we parse every key-value object
            while (lookahead != ']'){
                Object key = null, value = null;
                expect('{');
                lookahead = peekOne();
                commonBuilder.setLength(0);
                //Linter doesn't like this but it is arguably more readable
                //I *assume* it will compile to the same bytecode anyway, its just an alias...
                StringBuilder firstArgument = commonBuilder;
                while (lookahead != ':'){
                    firstArgument.append(lookahead);
                    advanceOne();
                    lookahead = peekOne();
                }
                expect(':');
                if (firstArgument.toString().equals("key")){
                    key = parseObject(keyClass);
                }
                else if (firstArgument.toString().equals("value")){
                    value = parseObject(valueClass);
                }
                expect(',');
                lookahead = peekOne();

                // -- NEXT ITEM --

                commonBuilder.setLength(0);
                StringBuilder secondArgument = commonBuilder;
                while (lookahead != ':'){
                    secondArgument.append(lookahead);
                    advanceOne();
                    lookahead = peekOne();
                }
                expect(':');
                if (secondArgument.toString().equals("key")){
                    key = parseObject(keyClass);
                }
                else if (secondArgument.toString().equals("value")){
                    value = parseObject(valueClass);
                }
                expect('}');
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    lookahead = peekOne();
                }
                result.put(key, value);
            }
            expect(']');
            return result;
        }

        /**
         * Gets an instance of a Class, e.g. invokes its no-arg constructor and gets the object created by it. The class must have a no-arg constructor defined for inner nested objects.
         * @param objectClass the class of the object to be instantiated.
         * @return the instantiated object.
         * @throws DSONException if the object does not have a suitable constructor, an exception will be thrown.
         */
        private static Object getClassInstance(Class<?> objectClass) throws DSONException {
            try {
                Constructor<?>[] constructors = objectClass.getConstructors();
                for (Constructor<?> c : constructors) {
                    if (c.getParameterCount() == 0) {
                        return c.newInstance();
                    }
                }
            }
            catch (Exception e){
                throw new DSONException();
            }
            throw new DSONException();
        }

        /**
         * Parses an object of a class.
         * This function automatically deduces the correct parsing function from the Class's type metadata.
         * This function can also be called recursively in the case of object Lists or Arrays, as well as nested objects within classes.
         * @param objectClass the class to be parsed.
         * @return an Object, which is the parsed value of the class.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private Object parseObject(Class<?> objectClass) throws DSONException{
            if (isPrimitive(wrapperToPrimitive(objectClass))){
                return parsePrimitive(primitiveToWrapper(objectClass));
            }
            else if (isTypicalArray(objectClass)){
                return parseTypicalArray(objectClass);
            }
            else if (isList(objectClass)){
                return parseList(objectClass);
            }
            else if (isSet(objectClass)){
                return parseSet(objectClass);
            }
            else if (isMap(objectClass)){
                return parseMap(objectClass);
            }
            else{
                Object innerObject = getClassInstance(objectClass);
                DSONObject dson = new DSONObject(innerObject);
                dson.overrideParser(queryParser);
                dson.parseJSON();
                return innerObject;
            }
        }

        /**
         * Parses a JSON string. This function is the entry point for parsing a JSON.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private void parseJSON() throws DSONException{
            expect('{');
            while (true){
                parseField();
                if (peekOne() == '}')
                    break;
                advanceOne();
            }
            expect('}');
        }

        /**
         * Parses a field from the current parsing position. A field in JSON is a key: value pair, separated by commas. However, there cannot be a comma after the last field.
         * @throws DSONException if something has gone wrong during the parsing process, an exception will be thrown.
         */
        private void parseField() throws DSONException{
            StringBuilder fieldName = new StringBuilder();
            option('\"');
            while (Character.isLetterOrDigit(peekOne())){
                fieldName.append(nextCharAsString());
            }
            option('\"');
            expect(':');

            Field field = fieldGetter.get(fieldName.toString());
            Class<?> fieldClass = field.getType();
            Object value = parseObject(fieldClass);
            try {
                field.set(object, value);
            } catch (Exception e){
                throw new DSONException();
            }
        }

        /**
         * Tests the DSON parser.
         * If an assert fails or an exception is thrown, it means that a test has failed.
         */
        public static void main(String[] args) throws DSONException {
            DSONObject test = new DSONObject();

            //parseInt
            String integerTest = "1024";
            int integerResult = 1024;
            test.setParser(integerTest);
            assert test.parseInt() == integerResult;

            //parseDouble
            String doubleTest = "1024.2048";
            double doubleResult = 1024.2048;
            test.setParser(doubleTest);
            assert Math.abs(test.parseDouble() - doubleResult) <= 0.0000001;

            //parseFloat
            int floatTestCount = 5;
            String[] floatTests = new String[]{"3", "3f", "3.f", ".1f","0.000123"};
            float[] floatResults = new float[]{3.0f, 3.0f, 3.0f, 0.1f, 0.000123f};
            for (int i = 0; i < floatTestCount; i++){
                test.setParser(floatTests[i]);
                assert Math.abs(test.parseFloat() - floatResults[i]) <= 0.000001f;
            }

            //parseBoolean
            int booleanTestCount = 2;
            String[] booleanTests = new String[]{"true,", "false}}"};
            boolean[] booleanResults = new boolean[]{true, false};
            for (int i = 0; i < booleanTestCount; i++){
                test.setParser(booleanTests[i]);
                assert test.parseBoolean() == booleanResults[i];
            }

            //Test on primitive fields
            TestObject testObject1 = new TestObject();
            String testJSON1 = "{name:\"jimbob\",age:255}";
            DSONObject obj = new DSONObject(testObject1);
            obj.setParser(testJSON1);
            obj.parseJSON();
            String json = serialize(testObject1);
            assert json.equals(testJSON1);

            //Test on simple arrays
            TestObject2 testObject2 = new TestObject2();
            String testJSON2 = """
                    {
                        numbers: [1, 2, 3, 5],
                        strings: ["jim", "bob", "bruhman"]
                    }
                    """;
            DSONObject obj2 = new DSONObject(testObject2);
            obj2.setParser(testJSON2);
            obj2.parseJSON();
            String json2 = serialize(testObject2);
            assert fixFormat(testJSON2).equals(json2);

            //Test on simple array of ojbects
            TestObject3 testObject3 = new TestObject3();
            String testJSON3 = """
                    {
                        apples: [
                            {
                                weight: 3,
                                tasty: true
                            },
                            {
                                weight:100,
                                tasty: false
                            },
                            {
                                weight: 40,
                                tasty: true
                            }
                        ]
                    }
                    """;
            DSONObject obj3 = new DSONObject(testObject3);
            obj3.setParser(testJSON3);
            obj3.parseJSON();
            String json3 = serialize(testObject3);
            assert fixFormat(testJSON3).equals(json3);

            //Test on lists and sets
            TestObject4 testObject4 = new TestObject4();
            String testJSON4 = """
                    {
                        apples: [
                            {
                                weight: 3,
                                tasty: true
                            },
                            {
                                weight:   100,
                                tasty: false
                            },
                            {
                                weight: 40,
                                tasty: true
                            }
                        ],
                        numbers: [1, 2, 3, 4, 5]
                    }
                    """;
            DSONObject obj4 = new DSONObject(testObject4);
            obj4.setParser(testJSON4);
            obj4.parseJSON();
            String json4 = serialize(testObject4);
            assert fixFormat(testJSON4).equals(json4);

            TestObject5 testObject5 = new TestObject5();
            String testJSON5 = """
                    {
                        applemap: [
                        {
                            key: "HEAVY_APPLE_1",
                            value: {
                                weight: 100,
                                tasty: false
                            }
                        },
                        {
                            key: "LIGHT_APPLE_1",
                            value: {
                                weight: 1,
                                tasty: true
                            }
                        },
                        {
                            key: "LIGHT_APPLE_2",
                            value: {
                                weight: 3,
                                tasty: false
                            }
                        }]
                    }
                    """;
            DSONObject obj5 = new DSONObject(testObject5);
            obj5.setParser(testJSON5);
            obj5.parseJSON();
            String json5 = serialize(testObject5);
            assert fixFormat(testJSON5).equals(json5);

            //Testing " on field names, which is allowed in JSON
            TestObject testObject6 = new TestObject();
            String testJSON6 = "{\"name\":\"jimbob\",\"age\":255}";
            DSONObject obj6 = new DSONObject(testObject6);
            obj6.setParser(testJSON6);
            obj6.parseJSON();
            String json6 = serialize(testObject6);
            assert json6.equals(testJSON1);

            //TODO add tests for rename btw
        }
    }
}