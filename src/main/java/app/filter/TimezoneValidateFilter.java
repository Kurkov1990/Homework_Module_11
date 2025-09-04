package app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;

@WebFilter(value = "/time")
public class TimezoneValidateFilter extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {

        String tzParam = req.getParameter("timezone");
        if (tzParam == null || tzParam.trim().isEmpty()) {
            chain.doFilter(req, resp);
            return;
        }

        String normalized = normalizeTimezone(tzParam);

        try {
            ZoneId zone = ZoneId.of(normalized);
            req.setAttribute("normalizedTimezone", normalized);

            HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
                @Override
                public String getParameter(String name) {
                    if ("timezone".equals(name)) {
                        return zone.getId();
                    }
                    return super.getParameter(name);
                }
            };
            chain.doFilter(wrapped, resp);
        } catch (DateTimeException exception) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write("<h2>Invalid timezone: " + tzParam + "</h2>");
        }
    }

    private static String normalizeTimezone(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        s = s.replace(' ', '+');
        if (s.matches("^(UTC|GMT)[+-]\\d{1,2}$")) {
            String sign = s.contains("+") ? "+" : "-";
            String[] parts = s.split("[+-]");
            int hours = Integer.parseInt(parts[1]);
            String hh = String.format("%02d", hours);
            s = parts[0] + sign + hh + ":00";
        }
        return s;
    }
}
