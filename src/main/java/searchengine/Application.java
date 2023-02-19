/** This application makes use of the Lucene open-source library,
 * which is subject to the Apache Software License 2.0.
 * More information about Lucene can be found at http://lucene.apache.org.*/

package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}