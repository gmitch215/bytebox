package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Alarm;
import dev.gmitch215.bytebox.Consumer;
import dev.gmitch215.bytebox.Cron;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.InboundMail;
import dev.gmitch215.bytebox.Mail;
import dev.gmitch215.bytebox.MessageBatch;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Scheduled;
import dev.gmitch215.bytebox.Tail;
import dev.gmitch215.bytebox.TraceItem;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;

/** Implements every trigger, so the generators have to emit all six exports. */
public class EveryTrigger implements Worker, Scheduled, Mail, Consumer<TSObject>, Tail, Alarm {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return null;
	}

	@Override
	public void scheduled(Cron cron, Env env, ExecutionCtx ctx) {}

	@Override
	public void email(InboundMail mail, Env env, ExecutionCtx ctx) {
		mail.drop();
	}

	@Override
	public void queue(MessageBatch<TSObject> batch, Env env, ExecutionCtx ctx) {}

	@Override
	public void tail(List<TraceItem> events, Env env, ExecutionCtx ctx) {}

	@Override
	public void alarm(Env env, ExecutionCtx ctx) {}
}
