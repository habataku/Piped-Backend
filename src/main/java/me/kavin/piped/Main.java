package me.kavin.piped;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.QueryStringDecoder;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DownloaderImpl;
import me.kavin.piped.utils.ResponseHelper;
import me.kavin.piped.utils.SponsorBlockUtils;
import reactor.core.publisher.Flux;
import reactor.netty.ByteBufFlux;
import reactor.netty.NettyOutbound;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

public class Main {

    public static void main(String[] args) throws Exception {

	NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

	HttpServer.create().port(Constants.PORT).route(routes -> {

	    routes.get("/webhooks/pubsub", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res, query.parameters().get("hub.challenge").get(0), 200, "private");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.post("/webhooks/pubsub", (req, res) -> {

		try {
		    req.receive().asInputStream().subscribe(in -> {
			try {
			    SyndFeed feed = new SyndFeedInput().build(new XmlReader(in));

				    feed.getEntries().forEach(entry -> {
					System.out.println(entry.getLinks().get(0).getHref());
					System.out.println(entry.getAuthors().get(0).getUri());
				    });

			} catch (Exception e) {
			    e.printStackTrace();
			}
		    });
		    return writeResponse(res, "ok", 200, "private");
		} catch (Exception e) {
			    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/sponsors/{videoId}", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res, SponsorBlockUtils.getSponsors(req.param("videoId"),
			    query.parameters().get("category").get(0)), 200, "public, max-age=3600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/streams/{videoId}", (req, res) -> {

		try {
		    // The stream links are valid for 6 hours.
		    return writeResponse(res, ResponseHelper.streamsResponse(req.param("videoId")), 200,
			    "public, s-maxage=21540");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/channels/{channelId}", (req, res) -> {

		try {
		    return writeResponse(res, ResponseHelper.channelResponse(req.param("channelId")), 200,
			    "public, max-age=600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/nextpage/channels/{channelId}", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res, ResponseHelper.channelPageResponse(req.param("channelId"),
			    query.parameters().get("url").get(0)), 200, "public, max-age=3600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/playlists/{playlistId}", (req, res) -> {

		try {
		    return writeResponse(res, ResponseHelper.playlistResponse(req.param("playlistId")), 200,
			    "public, max-age=600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/nextpage/playlists/{playlistId}", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res, ResponseHelper.playlistPageResponse(req.param("playlistId"),
			    query.parameters().get("url").get(0)), 200, "public, max-age=3600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/suggestions", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res,
			    ResponseHelper.suggestionsResponse(query.parameters().get("query").get(0)), 200,
			    "public, max-age=600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/search", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res, ResponseHelper.searchResponse(query.parameters().get("q").get(0)), 200,
			    "public, max-age=600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/nextpage/search", (req, res) -> {

		QueryStringDecoder query = new QueryStringDecoder(req.uri());

		try {
		    return writeResponse(res,
			    ResponseHelper.searchPageResponse(query.parameters().get("q").get(0),
				    query.parameters().get("url").get(0), query.parameters().get("id").get(0)),
			    200, "public, max-age=3600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	    routes.get("/trending", (req, res) -> {

		try {
		    return writeResponse(res, ResponseHelper.trendingResponse(), 200, "public, max-age=3600");
		} catch (Exception e) {
		    e.printStackTrace();
		    return writeResponse(res, ExceptionUtils.getStackTrace(e), 500, "private");
		}

	    });

	}).bindNow();

	Thread.sleep(Long.MAX_VALUE);
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, String resp, int code, String cache) {
	return res.compression(true).addHeader("Access-Control-Allow-Origin", "*").addHeader("Cache-Control", cache)
		.send(ByteBufFlux.fromString(Flux.just(resp), java.nio.charset.StandardCharsets.UTF_8,
			ByteBufAllocator.DEFAULT));
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, byte[] resp, int code, String cache) {
	return res.compression(true).addHeader("Access-Control-Allow-Origin", "*").addHeader("Cache-Control", cache)
		.sendByteArray(Flux.just(resp));
    }

    public static NettyOutbound writeResponse(HttpServerResponse res, Flux<String> resp, int code, String cache) {
	return res.compression(true).addHeader("Access-Control-Allow-Origin", "*").addHeader("Cache-Control", cache)
		.send(ByteBufFlux.fromString(resp, java.nio.charset.StandardCharsets.UTF_8, ByteBufAllocator.DEFAULT));
    }
}
