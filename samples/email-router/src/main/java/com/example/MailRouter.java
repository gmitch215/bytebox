package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.InboundMail;
import dev.gmitch215.bytebox.Mail;

/**
 * An inbound email router.
 *
 * <p>Cloudflare drops a message a handler returns without acting on, so {@code InboundMail} records
 * what was done and raises if nothing was. That makes the silent drop impossible to reach by
 * accident, and {@code drop()} is how to say the silence was meant.
 *
 * <p>Inbound messages are capped at 25 MiB. A reply needs the original to have passed DMARC, has to
 * come from the receiving domain, and is allowed once per message.
 */
public class MailRouter implements Mail {

	@Override
	public void email(InboundMail mail, Env env, ExecutionCtx ctx) {
		if (mail.rawSize() > 1_000_000) {
			mail.reject("messages over 1 MB are not accepted");
			return;
		}

		String sender = mail.from();
		if (env.kv().get("blocked:" + sender) != null) {
			mail.reject("this address is not accepted");
			return;
		}

		// acting and then carrying on is the point: the disposition is recorded on the message
		Bytebox.log("routing " + mail.rawSize() + " bytes from " + sender);
		env.kv().put("last-sender", sender);
		mail.forward("inbox@example.com");
	}
}
