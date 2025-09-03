package app.servlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/time")
public class TimeServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(TimeServlet.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final String COOKIE_TZ = "lastTimezone";
    private static final int COOKIE_TZ_MAX_AGE = 60 * 60 * 24;

    private transient TemplateEngine templateEngine;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        JakartaServletWebApplication webApp = JakartaServletWebApplication.buildApplication(getServletContext());
        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/html; charset=UTF-8");
            resp.setHeader("Cache-Control", "no-store");
            String tzParam = (String) req.getAttribute("normalizedTimezone");
            Map<String, String> cookies = getCookies(req);
            String tzCookie = cookies.get(COOKIE_TZ);

            ZoneId zone;
            String source;

            if (!isBlank(tzParam)) {
                zone = ZoneId.of(tzParam);
                writeCookie(resp, COOKIE_TZ, tzParam, COOKIE_TZ_MAX_AGE, req.isSecure());
                source = "query";
            } else if (!isBlank(tzCookie)) {
                zone = safeZoneIdOrUtc(tzCookie);
                source = "cookie";
            } else {
                zone = ZoneId.of("UTC");
                source = "default";
            }

            String formattedTime = ZonedDateTime.now(zone).format(FORMATTER);

            JakartaServletWebApplication app = JakartaServletWebApplication.buildApplication(getServletContext());
            IWebExchange webExchange = app.buildExchange(req, resp);
            WebContext ctx = new WebContext(webExchange, webExchange.getLocale());
            ctx.setVariable("currentTime", formattedTime);
            ctx.setVariable("timezoneId", zone.getId());
            ctx.setVariable("source", source);

            templateEngine.process("time", ctx, resp.getWriter());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in TimeServlet", e);
            sendError(resp);
        }
    }

    private static void sendError(HttpServletResponse resp) {
        try {
            if (!resp.isCommitted()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.setContentType("text/html; charset=UTF-8");
                resp.getWriter().write("<h2>Internal server error</h2>");
            }
        } catch (IOException ioe) {
            Logger.getLogger(TimeServlet.class.getName()).log(Level.SEVERE, "Failed to send error response", ioe);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Map<String, String> getCookies(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (Cookie c : cookies) {
            result.put(c.getName(), c.getValue());
        }
        return result;
    }

    private static void writeCookie(HttpServletResponse resp, String name, String value, int maxAgeSeconds, boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        resp.addCookie(cookie);
    }

    private static ZoneId safeZoneIdOrUtc(String id) {
        if (isBlank(id)) return ZoneId.of("UTC");
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
