package cat;

/**
 * Hello world!
 *
 */
public abstract class App {
	Object baz;


	Object bar() {
		if (baz == null) { // baz may be non-null yet not fully created
			synchronized (this) {
				if (baz == null) {
					baz = new Object();
				}
			}
		}
		return baz;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Test");
		for (int i = 0; i < 10; i++) { // only references 'i'
			for (int k = 0; k < 20; i++) { // references both 'i' and 'k'
				System.out.println("Hello");
			}
		}
		if (true) { // bad form

			throw new Exception();
		}

	}
}
