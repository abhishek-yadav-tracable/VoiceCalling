package org.example.voicecampaign.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${voice-campaign.rate-limit.requests-per-second:10000}")
    private int requestsPerSecond;

    @Value("${voice-campaign.rate-limit.cache-ttl-hours:1}")
    private int cacheTtlHours;

    @Value("${voice-campaign.rate-limit.cache-max-size:100000}")
    private int cacheMaxSize;

    private volatile Cache<String, Bucket> buckets;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String clientId = getClientId(request);
        Bucket bucket = getBuckets().get(clientId, this::createBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
        }
    }

    private Cache<String, Bucket> getBuckets() {
        if (buckets == null) {
            synchronized (this) {
                if (buckets == null) {
                    buckets = Caffeine.newBuilder()
                            .expireAfterAccess(cacheTtlHours, TimeUnit.HOURS)
                            .maximumSize(cacheMaxSize)
                            .build();
                }
            }
        }
        return buckets;
    }

    private Bucket createBucket(String clientId) {
        Bandwidth limit = Bandwidth.simple(requestsPerSecond, Duration.ofSeconds(1));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientId(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
