package com.pekall.pctool;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import android.content.UriMatcher;
import android.net.Uri;

import com.example.tutorial.AddressBookProtos.AddressBook;
import com.pekall.pctool.model.FakeBusinessLogicFacade;
import com.pekall.pctool.protos.AppInfoProtos.AppInfoPList;

public class MainServerHandler extends SimpleChannelUpstreamHandler {

	private static final int APPS = 1;
	private static final int TEST = 2;

	private static final UriMatcher sURIMatcher = new UriMatcher(
			UriMatcher.NO_MATCH);

	static {
		sURIMatcher.addURI("localhost", "apps", APPS);
		sURIMatcher.addURI("localhost", "test", TEST);
	}

	private FakeBusinessLogicFacade mLogicFacade;

	public MainServerHandler(FakeBusinessLogicFacade facade) {
		this.mLogicFacade = facade;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		HttpRequest request = (HttpRequest) e.getMessage();

		String path = request.getUri();
		HttpMethod method = request.getMethod();

		path = sanitizeUri(path);

		Slog.d("path:" + path + ", method: " + method);

		Uri url = Uri.parse("content://localhost" + path);

		int match = sURIMatcher.match(url);
		
		Slog.d("url = " + url);
		Slog.d("match = " + match);

		switch (match) {
		case APPS: {
			handleApps(e);
			break;
		}
		case TEST: {
			handleTest(e);
			break;
		}
		default: {
			handleNotFound(e);
			break;
		}
		}

		
	}

	private void handleApps(MessageEvent e) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		ChannelBuffer buffer = new DynamicChannelBuffer(2048);
		AppInfoPList appInfoPList = mLogicFacade.getAppInfoPList();
		buffer.writeBytes(appInfoPList.toByteArray());
		response.setContent(buffer);
		response.setHeader(CONTENT_TYPE, "application/x-protobuf");
		response.setHeader(CONTENT_LENGTH, response.getContent().writerIndex());

		Channel ch = e.getChannel();
		// Write the initial line and the header.
		ChannelFuture future = ch.write(response);
		future.addListener(ChannelFutureListener.CLOSE);
	}

	private void handleTest(MessageEvent e) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		ChannelBuffer buffer = new DynamicChannelBuffer(2048);
		AddressBook addressBook = mLogicFacade.getAddressBook();
		buffer.writeBytes(addressBook.toByteArray());
		response.setContent(buffer);
		response.setHeader(CONTENT_TYPE, "application/x-protobuf");
		response.setHeader(CONTENT_LENGTH, response.getContent().writerIndex());

		Channel ch = e.getChannel();
		// Write the initial line and the header.
		ChannelFuture future = ch.write(response);
		future.addListener(ChannelFutureListener.CLOSE);
	}

	private void handleNotFound(MessageEvent e) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		Channel ch = e.getChannel();
		// Write the initial line and the header.
		ChannelFuture future = ch.write(response);
		future.addListener(ChannelFutureListener.CLOSE);
	}

	private static String sanitizeUri(String uri) {
		// Decode the path.
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			try {
				uri = URLDecoder.decode(uri, "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				throw new Error();
			}
		}
		return uri;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		Channel ch = e.getChannel();
		Throwable cause = e.getCause();
		if (cause instanceof TooLongFrameException) {
			sendError(ctx, BAD_REQUEST);
			return;
		}

		cause.printStackTrace();
		if (ch.isConnected()) {
			sendError(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
		response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.setContent(ChannelBuffers.copiedBuffer(
				"Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

		// Close the connection as soon as the error message is sent.
		ctx.getChannel().write(response)
				.addListener(ChannelFutureListener.CLOSE);
	}
}
