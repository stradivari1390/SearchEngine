# Search Engine Project

This is a search engine project developed in Java and using various libraries and frameworks. The project is designed to index a limited amount of sites and their web pages and provide a relevant search functionality.

## Technologies Used

* Java
* Spring Framework
* Spring Boot
* Spring Data JPA
* Redis
* MySQL
* MySQL Connector
* Jsoup
* Lombok
* HtmlCleaner
* Log4j 2
* Thymeleaf
* Apache Lucene Morphology

## API Endpoints

* `GET /api/startIndexing` - Starts the indexing process. Returns a response object containing information about the indexing process.
* `GET /api/stopIndexing` - Stops the indexing process. Returns a response object containing information about the indexing process.
* `POST /api/indexPage` - Indexes a specific web page. Accepts a URL as a parameter. Returns a response object containing information about the indexing process.
* `GET /api/search` - Searches for a given query in the indexed pages. Accepts query, site, offset and limit parameters. Returns a response object containing the search results.
* `GET /api/statistics` - Shows statistics on indexed pages.

## Configuration

The project uses a YAML configuration file (`application.yaml`) to configure various settings. The settings include:

* Site list for indexing and batch size for inserting in DB
* User agent and referrer for HTTP requests
* Redis settings
* Logging settings
* JDBC settings for database connection

## Usage

To use the project, you can clone the repository and run the application using a Java IDE or the command line.
Also you need to run Redis server and MySQL server

```bash
$ git clone https://github.com/stradivari1390/SearchEngine.git
$ cd SearchEngine
$ mvn spring-boot:run
```

## Contributors
The project was developed by __Stanislav Romanov__. If you'd like to contribute to the project, please feel free to submit a pull request.

## License
This application makes use of the Lucene open-source library, which is subject to the Apache Software License 2.0.
More information about Lucene can be found at http://lucene.apache.org.