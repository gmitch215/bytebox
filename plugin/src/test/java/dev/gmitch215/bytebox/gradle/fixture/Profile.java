package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.json.JSONType;
import java.util.List;

/**
 * A bean rather than a record: private fields reached through accessors, one public field reached
 * directly, a list, an enum and another annotated type.
 */
@JSONType
public class Profile {

	/** Public, so the codec reads and writes it without an accessor. */
	public boolean active;

	private String name;
	private List<String> tags;
	private Colour colour;
	private Point home;

	/** {@return the name} */
	public String getName() {
		return name;
	}

	/** @param name the name */
	public void setName(String name) {
		this.name = name;
	}

	/** {@return the tags} */
	public List<String> getTags() {
		return tags;
	}

	/** @param tags the tags */
	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	/** {@return the colour} */
	public Colour getColour() {
		return colour;
	}

	/** @param colour the colour */
	public void setColour(Colour colour) {
		this.colour = colour;
	}

	/** {@return the home point} */
	public Point getHome() {
		return home;
	}

	/** @param home the home point */
	public void setHome(Point home) {
		this.home = home;
	}
}
