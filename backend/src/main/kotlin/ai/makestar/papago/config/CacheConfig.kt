package ai.makestar.papago.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()

        // Register cache names upfront
        cacheManager.setCacheNames(listOf("glossary", "translationResults"))

        // Default configuration for glossary cache
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
        )

        return cacheManager
    }

    @Bean
    fun translationResultsCacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("translationResults")
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .recordStats()
        )
        return cacheManager
    }
}
