package cat;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	Object[] bucket = new Object[12345];
    	String y = args[0];
    	Object x = bucket[ Math.abs(y.hashCode()) % bucket.length];
    	System.out.println( "Hello World!"  + x);
        x = bucket[ y.hashCode() % bucket.length];
        System.out.println( "Hello World!"  + x);
    }
}
