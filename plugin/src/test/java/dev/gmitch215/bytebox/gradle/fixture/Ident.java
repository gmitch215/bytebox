package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.io.SerialType;
import java.io.Serializable;

/** A record, whose identifier is zero by rule and whose fields are constructor arguments. */
@SerialType
public record Ident(long id, String name) implements Serializable {}
