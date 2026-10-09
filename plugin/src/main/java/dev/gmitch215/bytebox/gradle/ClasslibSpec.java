package dev.gmitch215.bytebox.gradle;

import org.gradle.api.provider.Property;

/**
 * The {@code classlib { }} block: parts of the Java class library that cost bytes in every module
 * that has them, so a project switches them on.
 *
 * <p>Everything is off by default, and a project that sets nothing gets the same module it would
 * have got before the block existed.
 *
 * @since 1.0.3
 */
public abstract class ClasslibSpec {

	/**
	 * {@return whether {@code ZoneRules.getTransitions()} and {@code getTransitionRules()} answer}
	 *
	 * <p>Off, both refuse, because the platform's timezone database answers every question about an
	 * offset but cannot list a zone's changes. On, they are read from the database that ships with
	 * ThreeTen-Backport, which adds its 108 KB to the module, and {@code nextTransition} and
	 * {@code previousTransition} answer from that history too, so they find a change at any distance
	 * where the search answers {@code null} past 400 days. Offsets and daylight saving still come
	 * from the platform.
	 */
	public abstract Property<Boolean> getZoneHistory();

	/**
	 * {@return whether {@code String.format} takes the {@code %t} and {@code %T} conversions}
	 *
	 * <p>Off, both refuse and name {@code DateTimeFormatter}. On, they format a {@code long}, a
	 * {@code Date}, a {@code Calendar} or a {@code java.time} value as a JVM does, in English: every
	 * conversion except {@code %tZ} and {@code %tc}, an epoch value before 1582-10-15, and any locale
	 * other than {@code Locale.US}, {@code Locale.ENGLISH} or none, which refuse. The code is linked
	 * only by a program that formats a date.
	 */
	public abstract Property<Boolean> getDateFormat();
}
