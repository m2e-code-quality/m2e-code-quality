package cat;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        // this triggers BooleanInstantiation
        Boolean b = new Boolean("true");
        // this triggers UnnecessaryReturn
        return;
    }
}
