package cat;

public class App {
	public static void main(String[] argumentsNameHereIsABitLongerThan42Characters) throws Exception {
		int variableNameShorterThan42 = 1; // violation expected here: java/codestyle.xml/ShortVariable
		System.out.println(variableNameShorterThan42);
	}
}
