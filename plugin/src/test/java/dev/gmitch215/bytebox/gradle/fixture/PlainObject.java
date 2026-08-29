package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.durable.DurableObject;

/** Takes requests and nothing else, so no alarm or socket export should appear. */
public class PlainObject implements DurableObject {}
