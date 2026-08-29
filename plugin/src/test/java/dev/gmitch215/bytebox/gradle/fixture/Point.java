package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.json.JSONType;

/** A record, whose components are read in declaration order and passed to the constructor. */
@JSONType
public record Point(int x, int y, String label) {}
