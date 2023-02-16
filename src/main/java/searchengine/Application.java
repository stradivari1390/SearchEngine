/** This application makes use of the Lucene open-source library,
 * which is subject to the Apache Software License 2.0.
 * More information about Lucene can be found at http://lucene.apache.org.*/

package searchengine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.IOException;

@SpringBootApplication
public class Application {

    private static Process redisProcess;

    public static void main(String[] args) throws IOException {
        redisProcess = Runtime.getRuntime().exec("redis-server");
        SpringApplication app = new SpringApplication(Application.class);
        app.addListeners(new ApplicationShutdownListener());
        app.run(args);
    }

    private static class ApplicationShutdownListener implements ApplicationListener<ContextClosedEvent> {
        @Autowired
        private RedisConnectionFactory connectionFactory;

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            flushDb();
            redisProcess.destroy();
            System.out.println("Shutting down application...");
        }

        public void flushDb() {
            RedisConnection connection = connectionFactory.getConnection();
            try {
                connection.flushDb();
            } finally {
                connection.close();
            }
        }
    }
}