package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.durable.AlarmObject;
import dev.gmitch215.bytebox.durable.DurableState;
import dev.gmitch215.bytebox.durable.SocketObject;
import dev.gmitch215.bytebox.socket.WebSocket;

/** A Durable Object taking every handler, so the generated class carries all of them. */
public class FullObject implements SocketObject, AlarmObject {

	@Override
	public void alarm(DurableState state, Env env) {}

	@Override
	public void message(WebSocket socket, String text, DurableState state, Env env) {}
}
