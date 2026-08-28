/**
 * Raw outbound TCP, over {@code cloudflare:sockets}.
 *
 * <p>What this cannot reach decides whether it is the right tool. Cloudflare refuses a connection to
 * its own IP ranges, to localhost and to private network addresses, and blocks port 25 outright. One
 * consequence catches people out: {@code smtp.mx.cloudflare.net} is a Cloudflare address, so a Worker
 * cannot SMTP to Cloudflare Email Sending even with valid credentials — use the
 * {@link dev.gmitch215.bytebox.binding.EmailSender} binding instead. External SMTP, IMAP and POP3
 * hosts are reachable.
 *
 * <p>Every plan allows six simultaneous outgoing connections, so a socket left open holds a sixth of
 * the invocation's allowance. {@link dev.gmitch215.bytebox.socket.Socket} is
 * {@code AutoCloseable} for that reason.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.socket;
