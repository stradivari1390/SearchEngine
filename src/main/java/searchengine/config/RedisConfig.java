package searchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Page> redisTemplatePage(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Page> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Page.class));
        return template;
    }

    @Bean
    public RedisTemplate<String, Lemma> redisTemplateLemma(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Lemma> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Lemma.class));
        return template;
    }

    @Bean
    public RedisTemplate<String, Index> redisTemplateIndex(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Index> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Index.class));
        return template;
    }
}