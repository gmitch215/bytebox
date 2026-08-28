package fixture;

public class Thrower {
	public static void main(String[] args) {
		System.out.println("about to throw");
		throw new IllegalStateException("java said no");
	}
}
