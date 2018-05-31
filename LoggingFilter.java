import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Spring-фильтр для логирования HTTP-request & HTTP-response
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Value("${requestResponseLogging}")
    private boolean isLoggingEnabled;

    private final AtomicInteger requestCounter = new AtomicInteger();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (isLoggingEnabled) {
            int counter = requestCounter.incrementAndGet();

            final StringBuffer sbRequest = new StringBuffer("Request\n----------------------------");
            final StringBuffer sbResponse = new StringBuffer("Response\n----------------------------");

            ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

            sbRequest.append("\nID: ").append(counter);
            sbRequest.append("\nAddress: ").append(requestWrapper.getRequestURL());
            sbRequest.append("\nHttp-Method: ").append(requestWrapper.getMethod());
            if (requestWrapper.getParameterMap().size() > 0) sbRequest.append("\nURL options: ")
                    .append(printUrlParameters(requestWrapper.getParameterMap()));
            sbRequest.append("\nHeaders: ").append(new ServletServerHttpRequest(requestWrapper).getHeaders());
            sbRequest.append("\nBody: {").append(IOUtils.toString(requestWrapper.getInputStream())).append("}");

            logger.info(sbRequest.toString());

            filterChain.doFilter(requestWrapper, responseWrapper);

            sbResponse.append("\nID: ").append(counter);
            sbResponse.append("\nResponse-Code: ").append(responseWrapper.getStatusCode());
            sbResponse.append("\nEncoding: ").append(responseWrapper.getCharacterEncoding());
            sbResponse.append("\nContent-Type: ").append(responseWrapper.getContentType());
            sbResponse.append("\nHeaders: ").append(new ServletServerHttpResponse(responseWrapper).getHeaders());
            sbResponse.append("\nBody: ").append(new String(responseWrapper.getContentAsByteArray()));

            logger.info(sbResponse.toString());

            responseWrapper.copyBodyToResponse();
        }
    }

    private String printUrlParameters(Map<String, String[]> parameterMap) {
        return parameterMap.entrySet().stream().map((v) ->
                v.getKey() + "=" + Arrays.stream(v.getValue()).collect(Collectors.joining(", "))
        ).collect(Collectors.joining("; "));
    }
}
